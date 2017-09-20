package ru.nsu.fit.popov.storage.memory;

import ru.nsu.fit.popov.storage.broadcast.UniformBroadcast;
import ru.nsu.fit.popov.storage.net.Address;
import se.sics.kompics.*;
import se.sics.kompics.network.Network;

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

//    ------   interface ports   ------
    private final Negative<Port> port = provides(Port.class);

//    ------   implementation ports   ------
//    private final Positive<UniformBroadcast.Port> ubPort = requires(UniformBroadcast.Port.class);
//    private final Positive<Network> networkPort = requires(Network.class);

    private final Address myAddress;
    private final Collection<Address> addresses;

    private final Map<String, Data> memory;
    private final ReplicationPolicy policy;

    private KeyData pendingData;
    private final Set<Address> valueAddresses = new HashSet<>();

    private final Handler<Request> requestHandler = new Handler<Request>() {
        @Override
        public void handle(Request request) {
            final Data data = memory.get(request.key);

            Response response;
            if (data == null)
                response = new Response(Code.BAD_KEY, -1);
            else
                response = new Response(Code.SUCCESS, data.getValue());
            trigger(response, port);
        }
    };

    public SMReader(Init init) {
        myAddress = init.myAddress;
        addresses = init.addresses;
        memory = init.memory;
        policy = init.policy;

        subscribe(requestHandler, port);
    }
}
