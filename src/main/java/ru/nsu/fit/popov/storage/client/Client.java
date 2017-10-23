package ru.nsu.fit.popov.storage.client;

import ru.nsu.fit.popov.storage.memory.Code;
import ru.nsu.fit.popov.storage.memory.SharedMemory;
import ru.nsu.fit.popov.storage.util.StartPort;
import se.sics.kompics.*;

import java.io.IOException;
import java.net.SocketAddress;

public class Client extends ComponentDefinition implements Memory {

    public static class Init extends se.sics.kompics.Init<Client> {
        private final SocketAddress address;

        public Init(SocketAddress address) {
            this.address = address;
        }
    }

//    ------   interface ports   ------
    private final Positive<StartPort> startPort = requires(StartPort.class);
    private final Positive<SharedMemory.Port> smPort = requires(SharedMemory.Port.class);

    private NetServer netServer;

    private final Object lock = new Object();

    private Code responseCode;
    private int responseValue;

    private final Handler<Start> startHandler = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            System.out.println("Client has started");
            new Thread(Client.this::startServer).start();
        }
    };

    private final Handler<SharedMemory.WriteResponse> writeResponseHandler =
            new Handler<SharedMemory.WriteResponse>() {
                @Override
                public void handle(SharedMemory.WriteResponse response) {
                    responseCode = response.code;

                    synchronized (lock) {
                        lock.notify();
                    }
                }
            };

    private final Handler<SharedMemory.ReadResponse> readResponseHandler =
            new Handler<SharedMemory.ReadResponse>() {
                @Override
                public void handle(SharedMemory.ReadResponse response) {
                    responseCode = response.code;
                    responseValue = response.value;

                    synchronized (lock) {
                        lock.notify();
                    }
                }
            };

    public Client(Init init) throws IOException {
        if (init.address == null) {
            suicide();
            return;
        }

        netServer = new NetServer(init.address, this);

        subscribe(startHandler, startPort);
        subscribe(writeResponseHandler, smPort);
        subscribe(readResponseHandler, smPort);
    }

    private void startServer() {
        if (netServer == null)
            return;

        try {
            netServer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Kompics.shutdown();
    }

    @Override
    public int read(String key) throws MemoryException {
        readRequest(key);

        switch (responseCode) {
            case BAD_KEY:
                throw new MemoryException(BAD_KEY);
            case NOT_ENOUGH_NODES:
                throw new MemoryException(NOT_ENOUGH_NODES);
        }

        return responseValue;
    }

    private void readRequest(String key) {
        final SharedMemory.ReadRequest request = new SharedMemory.ReadRequest(key);

        synchronized (lock) {
            trigger(request, smPort);
            System.out.printf("\treading... %s\n", key);

            try {
                lock.wait();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void write(String key, int value) throws MemoryException {
        writeRequest(key, value);

        switch (responseCode) {
            case BAD_KEY:
                throw new MemoryException(BAD_KEY);
            case NOT_ENOUGH_NODES:
                throw new MemoryException(NOT_ENOUGH_NODES);
        }
    }

    private void writeRequest(String key, int value) {
        final SharedMemory.WriteRequest request = new SharedMemory.WriteRequest(key, value);

        synchronized (lock) {
            trigger(request, smPort);
            System.out.printf("\twriting... %s:%d\n", key, value);

            try {
                lock.wait();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
