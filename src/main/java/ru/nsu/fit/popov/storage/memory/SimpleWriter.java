package ru.nsu.fit.popov.storage.memory;

import ru.nsu.fit.popov.storage.broadcast.UniformBroadcast;
import ru.nsu.fit.popov.storage.net.Address;
import ru.nsu.fit.popov.storage.net.BaseMessage;
import ru.nsu.fit.popov.storage.util.Connector;
import ru.nsu.fit.popov.storage.util.Creator;
import se.sics.kompics.*;
import se.sics.kompics.network.Network;

import java.util.*;

public class SimpleWriter extends ComponentDefinition {

    static class Init extends se.sics.kompics.Init<SimpleWriter> {
        private final Address myAddress;
        private final Collection<Address> addresses;
        private final Component networkComponent;

        private final Map<String, Data> memory;
        private final ReplicationPolicy policy;

        Init(Address myAddress, Collection<Address> addresses, Component networkComponent,
             Map<String, Data> memory, ReplicationPolicy policy) {
            this.myAddress = myAddress;
            this.addresses = addresses;
            this.networkComponent = networkComponent;
            this.memory = memory;
            this.policy = policy;
        }
    }

    static class Request implements KompicsEvent {
        private final KeyData keyData;

        Request(KeyData keyData) {
            this.keyData = keyData;
        }
    }

    enum InnerCode {
        SUCCESS,
        RETRY,
        NOT_ENOUGH_NODES
    }

    static class Response implements KompicsEvent {
        final InnerCode code;

        private Response(InnerCode code) {
            this.code = code;
        }
    }

    public static class Port extends PortType {
        public Port() {
            request(Request.class);
            indication(Response.class);
        }
    }

    private static class Acknowledge extends BaseMessage {
        private static final byte SUCCESS  = 0;
        private static final byte BAD_SEQ  = 1;
        private static final byte NOT_MINE = 2;

        private Acknowledge(Address source, Address destination, byte code) {
            super(source, destination, code);
        }
    }

    static Component create(Creator creator, Connector connector, Init init) {
        final Component writer = creator.create(SimpleWriter.class, init);
        connector.connect(writer.getNegative(Network.class),
                init.networkComponent.getPositive(Network.class), Channel.TWO_WAY);

        final Component ub = UniformBroadcast.create(creator, connector,
                new UniformBroadcast.Init(init.myAddress, init.addresses, init.networkComponent));
        connector.connect(writer.getNegative(UniformBroadcast.Port.class),
                ub.getPositive(UniformBroadcast.Port.class), Channel.TWO_WAY);

        return writer;
    }

//    ------   interface ports   ------
    private final Negative<Port> port = provides(Port.class);

//    ------   implementation ports   ------
    private final Positive<UniformBroadcast.Port> ubPort = requires(UniformBroadcast.Port.class);
    private final Positive<Network> networkPort = requires(Network.class);

    private final Address myAddress;
    private final Collection<Address> addresses;

    private final Map<String, Data> memory;
    private final ReplicationPolicy policy;

    private final Map<Address, Byte> ackAddresses = new HashMap<>();

    private final Handler<Request> requestHandler = new Handler<Request>() {
        @Override
        public void handle(Request request) {
            ackAddresses.clear();

            final UniformBroadcast.Broadcast ubBroadcast
                    = new UniformBroadcast.Broadcast(request.keyData);
            trigger(ubBroadcast, ubPort);

            //  FIXME: start timer
        }
    };

    private final Handler<Acknowledge> acknowledgeHandler = new Handler<Acknowledge>() {
        @Override
        public void handle(Acknowledge acknowledge) {
            ackAddresses.put(acknowledge.getSource(), (Byte) acknowledge.getData());
            if (ackAddresses.keySet().containsAll(addresses)) { //  FIXME: do on timeout
                int ackCount = (int) ackAddresses.values().stream()
                        .filter(code -> code == Acknowledge.SUCCESS)
                        .count();

                Response response;
                if (ackAddresses.values().contains(Acknowledge.BAD_SEQ))
                    response = new Response(InnerCode.RETRY);
                else if (ackCount < ReplicationPolicy.REPLICATION_DEGREE)
                    response = new Response(InnerCode.NOT_ENOUGH_NODES);
                else
                    response = new Response(InnerCode.SUCCESS);
                trigger(response, port);
            }
        }
    };

    private final Handler<UniformBroadcast.Deliver> ubDeliverHandler =
            new Handler<UniformBroadcast.Deliver>() {
                @Override
                public void handle(UniformBroadcast.Deliver ubDeliver) {
                    final KeyData keyData = (KeyData) ubDeliver.data;

                    final Data myData = memory.get(keyData.getKey());
                    final Data otherData = keyData.getData();

                    Acknowledge acknowledge;
                    if (myData == null) {
                        if (policy.canSave(myAddress, keyData.getKey())) {
                            memory.put(keyData.getKey(), otherData);
                            acknowledge = new Acknowledge(myAddress, ubDeliver.source,
                                    Acknowledge.SUCCESS);
                        } else {
                            acknowledge = new Acknowledge(myAddress, ubDeliver.source,
                                    Acknowledge.NOT_MINE);
                        }
                    } else if (myData.getSequenceNumber() >= otherData.getSequenceNumber()) {
                        acknowledge = new Acknowledge(myAddress, ubDeliver.source,
                                Acknowledge.BAD_SEQ);    //  TODO: think about equals case
                    } else {
                        memory.put(keyData.getKey(), otherData);
                        acknowledge = new Acknowledge(myAddress, ubDeliver.source,
                                Acknowledge.SUCCESS);
                    }
                    trigger(acknowledge, networkPort);
                }
            };

    public SimpleWriter(Init init) {
        myAddress = init.myAddress;
        addresses = init.addresses;
        memory = init.memory;
        policy = init.policy;

        subscribe(requestHandler, port);
        subscribe(acknowledgeHandler, networkPort);
        subscribe(ubDeliverHandler, ubPort);
    }
}
