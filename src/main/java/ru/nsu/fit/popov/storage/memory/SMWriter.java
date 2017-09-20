package ru.nsu.fit.popov.storage.memory;

import ru.nsu.fit.popov.storage.broadcast.UniformBroadcast;
import ru.nsu.fit.popov.storage.net.Address;
import ru.nsu.fit.popov.storage.net.BaseMessage;
import ru.nsu.fit.popov.storage.util.Connector;
import ru.nsu.fit.popov.storage.util.Creator;
import se.sics.kompics.*;
import se.sics.kompics.network.Network;

import java.util.*;

public class SMWriter extends ComponentDefinition {

    static class Init extends se.sics.kompics.Init<SMWriter> {
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
        private final String key;
        private final int value;

        Request(String key, int value) {
            this.key = key;
            this.value = value;
        }
    }

    static class Response implements KompicsEvent {
        final Code code;

        private Response(Code code) {
            this.code = code;
        }
    }

    public static class Port extends PortType {
        public Port() {
            request(Request.class);
            indication(Response.class);
        }
    }

    private static class SequenceResponse extends BaseMessage {
        private SequenceResponse(Address source, Address destination, int sequenceNumber) {
            super(source, destination, sequenceNumber);
        }
    }

    private static final byte SUCCESS  = 0;
    private static final byte BAD_SEQ  = 1;
    private static final byte NOT_MINE = 2;

    private static class WriteAcknowledge extends BaseMessage {
        private WriteAcknowledge(Address source, Address destination, byte code) {
            super(source, destination, code);
        }
    }

    static Component create(Creator creator, Connector connector, Init init) {
        final Component writer = creator.create(SMWriter.class, init);
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

    private KeyData pendingData;
    private final Set<Address> seqAddresses = new HashSet<>();
    private final Map<Address, Byte> ackAddresses = new HashMap<>();

    private final Handler<Request> requestHandler = new Handler<Request>() {
        @Override
        public void handle(Request request) {
            final Data data = new Data(request.value);
            pendingData = new KeyData(request.key, data);
            seqAddresses.clear();

            final UniformBroadcast.Broadcast ubBroadcast =
                    new UniformBroadcast.Broadcast(request.key);
            trigger(ubBroadcast, ubPort);
        }
    };

    private final Handler<SequenceResponse> sequenceResponseHandler =
            new Handler<SequenceResponse>() {
                @Override
                public void handle(SequenceResponse sequenceResponse) {
                    final Data data = pendingData.getData();

                    final int sequenceNumber = (int) sequenceResponse.getData();
                    if (data.getSequenceNumber() < sequenceNumber)
                        data.setSequenceNumber(sequenceNumber);

                    seqAddresses.add(sequenceResponse.getSource());
                    if (seqAddresses.containsAll(addresses)) {  //  FIXME: do on timeout
                        seqAddresses.clear();
                        ackRequest();
                    }
                }
            };

    private final Handler<WriteAcknowledge> writeAcknowledgeHandler =
            new Handler<WriteAcknowledge>() {
                @Override
                public void handle(WriteAcknowledge acknowledge) {
                    ackAddresses.put(acknowledge.getSource(), (Byte) acknowledge.getData());
                    if (ackAddresses.keySet().containsAll(addresses)) { //  FIXME: do on timeout
                        if (ackAddresses.values().contains(BAD_SEQ)) {
                            retryPending();
                            return;
                        }

                        int ackCount = (int) ackAddresses.values().stream()
                                .filter(code -> code == SUCCESS)
                                .count();

                        Response response;
                        if (ackCount < ReplicationPolicy.REPLICATION_DEGREE)
                            response = new Response(Code.LOST_DATA);
                        else
                            response = new Response(Code.SUCCESS);
                        trigger(response, port);
                    }
                }
            };

    private final Handler<UniformBroadcast.Deliver> ubDeliverHandler =
            new Handler<UniformBroadcast.Deliver>() {
                @Override
                public void handle(UniformBroadcast.Deliver ubDeliver) {
                    if (ubDeliver.data instanceof String)
                        seqRequestHandler(ubDeliver.source, (String) ubDeliver.data);
                    else if (ubDeliver.data instanceof KeyData)
                        ackRequestHandler(ubDeliver.source, (KeyData) ubDeliver.data);
                    else
                        throw new RuntimeException("Invalid request");
                }
            };

    public SMWriter(Init init) {
        myAddress = init.myAddress;
        addresses = init.addresses;
        memory = init.memory;
        policy = init.policy;

        subscribe(requestHandler, port);
        subscribe(sequenceResponseHandler, networkPort);
        subscribe(writeAcknowledgeHandler, networkPort);
        subscribe(ubDeliverHandler, ubPort);
    }

    private void retryPending() {
        final Request request =
                new Request(pendingData.getKey(), pendingData.getData().getValue());
        trigger(request, port);
    }

    private void seqRequestHandler(Address source, String key) {
        final Data data = memory.get(key);
        final int sequenceNumber = (data != null) ? data.getSequenceNumber() : 0;

        final SequenceResponse response = new SequenceResponse(myAddress, source, sequenceNumber);
        trigger(response, networkPort);
    }

    private void ackRequest() {
        final int sequenceNumber = pendingData.getData().getSequenceNumber() + 1;
        pendingData.getData().setSequenceNumber(sequenceNumber);

        ackAddresses.clear();

        final UniformBroadcast.Broadcast ubBroadcast = new UniformBroadcast.Broadcast(pendingData);
        trigger(ubBroadcast, ubPort);

        //  FIXME: add timeout
    }

    private void ackRequestHandler(Address source, KeyData keyData) {
        final Data myData = memory.get(keyData.getKey());
        final Data otherData = keyData.getData();

        WriteAcknowledge acknowledge;
        if (myData == null) {
            if (policy.canSave(myAddress, keyData.getKey())) {
                memory.put(keyData.getKey(), otherData);
                acknowledge = new WriteAcknowledge(myAddress, source, SUCCESS);
            } else {
                acknowledge = new WriteAcknowledge(myAddress, source, NOT_MINE);
            }
        } else if (myData.getSequenceNumber() >= otherData.getSequenceNumber()) {
            acknowledge = new WriteAcknowledge(myAddress, source, BAD_SEQ);    //  TODO: think about equals case
        } else {
            memory.put(keyData.getKey(), otherData);
            acknowledge = new WriteAcknowledge(myAddress, source, SUCCESS);
        }
        trigger(acknowledge, networkPort);
    }
}
