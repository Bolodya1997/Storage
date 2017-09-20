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
        private final Address myAddress;
        private final Collection<Address> addresses;
        private final Component starter;
        private final Component networkComponent;

        private final Map<String, Data> memory = new HashMap<>();
        private final ReplicationPolicy policy;

        public Init(Address myAddress, Collection<Address> addresses,
                    Component starter, Component networkComponent) {
            this.myAddress = myAddress;
            this.addresses = addresses;
            this.starter = starter;
            this.networkComponent = networkComponent;

            policy = new ReplicationPolicy(addresses);
        }
    }

    public static class WriteRequest implements KompicsEvent {
        private final String key;
        private final int value;

        public WriteRequest(String key, int value) {
            this.key = key;
            this.value = value;
        }
    }

    public static class WriteResponse implements KompicsEvent {
        public final Code code;

        private WriteResponse(Code code) {
            this.code = code;
        }
    }

    public static class ReadRequest implements KompicsEvent {
        private final String key;

        public ReadRequest(String key) {
            this.key = key;
        }
    }

    public static class ReadResponse implements KompicsEvent {
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
        final Component memory = creator.create(SharedMemory.class, init);

        final Component writer = SMWriter.create(creator, connector,
                new SMWriter.Init(init.myAddress, init.addresses, init.networkComponent,
                        init.memory, init.policy));
        connector.connect(memory.getNegative(SMWriter.Port.class),
                writer.getPositive(SMWriter.Port.class), Channel.TWO_WAY);

        final Component reader = SMReader.create(creator, connector,
                new SMReader.Init(init.myAddress, init.addresses, init.networkComponent,
                        init.memory, init.policy));
        connector.connect(memory.getNegative(SMReader.Port.class),
                reader.getPositive(SMReader.Port.class), Channel.TWO_WAY);

        final Component fd = FailureDetector.create(creator, connector,
                new FailureDetector.Init(init.myAddress, init.addresses, init.networkComponent));
        connector.connect(fd.getNegative(StartPort.class),
                init.starter.getPositive(StartPort.class), Channel.TWO_WAY);
        connector.connect(memory.getNegative(FailureDetector.Port.class),
                fd.getPositive(FailureDetector.Port.class), Channel.TWO_WAY);

        return memory;
    }

//    ------   interface ports   ------
    private final Negative<Port> port = provides(Port.class);

//    ------   implementation ports   ------
    private final Positive<SMWriter.Port> writerPort = requires(SMWriter.Port.class);
    private final Positive<SMReader.Port> readerPort = requires(SMReader.Port.class);
    private final Positive<FailureDetector.Port> fdPort = requires(FailureDetector.Port.class);

    private final Collection<Address> addresses;
    private final ReplicationPolicy policy;

    private final Handler<WriteRequest> writeRequestHandler = new Handler<WriteRequest>() {
        @Override
        public void handle(WriteRequest writeRequest) {
            final SMWriter.Request request
                    = new SMWriter.Request(writeRequest.key, writeRequest.value);
            trigger(request, writerPort);
        }
    };

    private final Handler<ReadRequest> readRequestHandler = new Handler<ReadRequest>() {
        @Override
        public void handle(ReadRequest readRequest) {
            final SMReader.Request request = new SMReader.Request(readRequest.key);
            trigger(request, readerPort);
        }
    };

    private final Handler<SMWriter.Response> writerResponseHandler =
            new Handler<SMWriter.Response>() {
                @Override
                public void handle(SMWriter.Response response) {
                    final WriteResponse writeResponse =
                            new WriteResponse(response.code);
                    trigger(writeResponse, port);
                }
            };

    private final Handler<SMReader.Response> readerResponseHandler =
            new Handler<SMReader.Response>() {
                @Override
                public void handle(SMReader.Response response) {
                    final ReadResponse readResponse =
                            new ReadResponse(response.code, response.value);
                    trigger(readResponse, port);
                }
            };

    private final Handler<FailureDetector.Fail> failHandler = new Handler<FailureDetector.Fail>() {
        @Override
        public void handle(FailureDetector.Fail fail) {
            System.err.printf("[%s] FAILED\n", fail.address);

            addresses.remove(fail.address);

            //  FIXME: lost data replication

            policy.fail(fail.address);
        }
    };

    public SharedMemory(Init init) {
        addresses = init.addresses;
        policy = init.policy;

        subscribe(writeRequestHandler, port);
        subscribe(readRequestHandler, port);
        subscribe(writerResponseHandler, writerPort);
        subscribe(readerResponseHandler, readerPort);
        subscribe(failHandler, fdPort);
    }
}
