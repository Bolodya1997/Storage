package ru.nsu.fit.popov.storage.memory;

import ru.nsu.fit.popov.storage.failuredetector.FailureDetector;
import ru.nsu.fit.popov.storage.net.Address;
import ru.nsu.fit.popov.storage.util.Connector;
import ru.nsu.fit.popov.storage.util.Creator;
import ru.nsu.fit.popov.storage.util.StartPort;
import se.sics.kompics.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class SharedMemory extends ComponentDefinition {

    public static class Init extends se.sics.kompics.Init<SharedMemory> {
        private final int number;
        private final Address myAddress;
        private final Collection<Address> addresses;
        private final Component starter;
        private final Component networkComponent;

        public Init(int number, Address myAddress, Collection<Address> addresses,
                    Component starter, Component networkComponent) {
            this.number = number;
            this.myAddress = myAddress;
            this.addresses = addresses;
            this.starter = starter;
            this.networkComponent = networkComponent;
        }
    }

    public enum Code {
        SUCCESS,
        BAD_KEY,
        LOST_DATA
    }

    public static class WriteRequest extends Direct.Request<WriteResponse> {
        private final String key;

        public WriteRequest(String key) {
            this.key = key;
        }
    }

    public static class WriteResponse implements Direct.Response {
        public final Code code;
        public final int value;

        private WriteResponse(Code code, int value) {
            this.code = code;
            this.value = value;
        }
    }

    public static class ReadRequest extends Direct.Request<ReadResponse> {
        private final String key;

        public ReadRequest(String key) {
            this.key = key;
        }
    }

    public static class ReadResponse implements Direct.Response {
        public final Code code;
        public final int value;

        private ReadResponse(Code code, int value) {
            this.code = code;
            this.value = value;
        }
    }

    public static class Port extends PortType {
        public Port() {
            request(WriteRequest.class);
            indication(WriteResponse.class);
            request(ReadRequest.class);
            indication(ReadResponse.class);
        }
    }

    public static Component create(Creator creator, Connector connector, Init init) {
        final Component chat = creator.create(SharedMemory.class, init);

        //  FIXME: SMWriter

        //  FIXME: SMReader

        final Component fd = FailureDetector.create(creator, connector,
                new FailureDetector.Init(init.myAddress, init.addresses, init.networkComponent));
        connector.connect(fd.getNegative(StartPort.class),
                init.starter.getPositive(StartPort.class), Channel.TWO_WAY);
        connector.connect(chat.getNegative(FailureDetector.Port.class),
                fd.getPositive(FailureDetector.Port.class), Channel.TWO_WAY);

        return chat;
    }

    private final Negative<Port> port = provides(Port.class);
    private final Positive<FailureDetector.Port> fdPort =
            requires(FailureDetector.Port.class);

    private final Map<String, Data> memory = new HashMap<>();

    private final int myNumber;
    private final Map<Address, Boolean> addresses = new HashMap<>();
    private final Collection<Address> broadcastAddresses;

    public SharedMemory(Init init) {
        myNumber = init.number;
        for (Address address : init.addresses) {
            addresses.put(address, true);
        }
        broadcastAddresses = init.addresses;
    }
}
