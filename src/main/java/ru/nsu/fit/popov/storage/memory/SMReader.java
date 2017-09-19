package ru.nsu.fit.popov.storage.memory;

import ru.nsu.fit.popov.storage.net.Address;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Direct;
import se.sics.kompics.PortType;

import java.util.Collection;
import java.util.Map;

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

    static class Request extends Direct.Request<Response> {
        private final String key;

        Request(String key) {
            this.key = key;
        }
    }

    static class Response implements Direct.Response {
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
}
