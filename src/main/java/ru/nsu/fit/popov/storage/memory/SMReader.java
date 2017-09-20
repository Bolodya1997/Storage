package ru.nsu.fit.popov.storage.memory;

import ru.nsu.fit.popov.storage.broadcast.UniformBroadcast;
import ru.nsu.fit.popov.storage.net.Address;
import ru.nsu.fit.popov.storage.net.BaseMessage;
import ru.nsu.fit.popov.storage.util.Connector;
import ru.nsu.fit.popov.storage.util.Creator;
import se.sics.kompics.*;
import se.sics.kompics.network.Network;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SMReader extends ComponentDefinition {

    static class Init extends se.sics.kompics.Init<SMReader> {
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

        Request(String key) {
            this.key = key;
        }
    }

    static class Response implements KompicsEvent {
        final Code code;
        final int value;

        private Response(Code code, int value) {
            this.code = code;
            this.value = value;
        }
    }

    public static class Port extends PortType {
        public Port() {
            request(Request.class);
            indication(Response.class);
        }
    }

    private static class DataResponse extends BaseMessage {
        private DataResponse(Address source, Address destination, Data data) {
            super(source, destination, data);
        }
    }

    static Component create(Creator creator, Connector connector, Init init) {
        final Component reader = creator.create(SMReader.class, init);
        connector.connect(reader.getNegative(Network.class),
                init.networkComponent.getPositive(Network.class), Channel.TWO_WAY);

        final Component ub = UniformBroadcast.create(creator, connector,
                new UniformBroadcast.Init(init.myAddress, init.addresses, init.networkComponent));
        connector.connect(reader.getNegative(UniformBroadcast.Port.class),
                ub.getPositive(UniformBroadcast.Port.class), Channel.TWO_WAY);

        return reader;
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
    private final Set<Address> valueAddresses = new HashSet<>();

    private final Handler<Request> requestHandler = new Handler<Request>() {
        @Override
        public void handle(Request request) {
            final Data data = new Data(-1, -1);
            pendingData = new KeyData(request.key, data);
            valueAddresses.clear();

            final UniformBroadcast.Broadcast ubBroadcast =
                    new UniformBroadcast.Broadcast(request.key);
            trigger(ubBroadcast, ubPort);
        }
    };

    private final Handler<DataResponse> dataResponseHandler = new Handler<DataResponse>() {
        @Override
        public void handle(DataResponse dataResponse) {
            final Data myData = pendingData.getData();
            final Data otherData = (Data) dataResponse.getData();
            if (myData.getSequenceNumber() < otherData.getSequenceNumber()) {
                myData.setValue(otherData.getValue());
                myData.setSequenceNumber(otherData.getSequenceNumber());
            }

            valueAddresses.add(dataResponse.getSource());
            if (valueAddresses.containsAll(addresses)) {    //  FIXME: do on timeout
                valueAddresses.clear();

                Response response;
                if (myData.getSequenceNumber() < 0)
                    response = new Response(Code.BAD_KEY, -1);
                else
                    response = new Response(Code.SUCCESS, myData.getValue());
                trigger(response, port);
            }
        }
    };

    private final Handler<UniformBroadcast.Deliver> ubDeliverHandler =
            new Handler<UniformBroadcast.Deliver>() {
                @Override
                public void handle(UniformBroadcast.Deliver ubDeliver) {
                    final String key = (String) ubDeliver.data;
                    Data data = memory.get(key);
                    if (data == null)
                        data = new Data(-1, -1);

                    final DataResponse dataResponse
                            = new DataResponse(myAddress, ubDeliver.source, data);
                    trigger(dataResponse, networkPort);
                }
            };

    public SMReader(Init init) {
        myAddress = init.myAddress;
        addresses = init.addresses;
        memory = init.memory;
        policy = init.policy;

        subscribe(requestHandler, port);
        subscribe(dataResponseHandler, networkPort);
        subscribe(ubDeliverHandler, ubPort);
    }
}
