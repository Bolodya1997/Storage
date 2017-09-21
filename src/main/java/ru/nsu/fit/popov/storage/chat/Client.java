package ru.nsu.fit.popov.storage.chat;

import ru.nsu.fit.popov.storage.memory.SharedMemory;
import ru.nsu.fit.popov.storage.util.StartPort;
import se.sics.kompics.*;

import java.util.Objects;
import java.util.Scanner;

public class Client extends ComponentDefinition {

//    ------   interface ports   ------
    private final Positive<StartPort> startPort = requires(StartPort.class);
    private final Positive<SharedMemory.Port> smPort = requires(SharedMemory.Port.class);

    private final Object lock = new Object();

    private final Handler<Start> startHandler = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            System.out.println("Client has started");
            new Thread(Client.this::loop).start();
        }
    };

    private final Handler<SharedMemory.WriteResponse> writeResponseHandler =
            new Handler<SharedMemory.WriteResponse>() {
                @Override
                public void handle(SharedMemory.WriteResponse response) {
                    switch (response.code) {
                        case SUCCESS:
                            System.out.println("success");
                            break;
                        case LOST_DATA:
                            System.out.println("not enough nodes");
                    }
                    synchronized (lock) {
                        lock.notify();
                    }
                }
            };

    private final Handler<SharedMemory.ReadResponse> readResponseHandler =
            new Handler<SharedMemory.ReadResponse>() {
                @Override
                public void handle(SharedMemory.ReadResponse response) {
                    switch (response.code) {
                        case SUCCESS:
                            System.out.printf("success: %d\n", response.value);
                            break;
                        case BAD_KEY:
                            System.out.println("bad key");
                            break;
                        case LOST_DATA:
                            System.out.println("not enough nodes");
                    }
                    synchronized (lock) {
                        lock.notify();
                    }
                }
            };

    public Client() {
        subscribe(startHandler, startPort);
        subscribe(writeResponseHandler, smPort);
        subscribe(readResponseHandler, smPort);
    }

    private void loop() {
        final Scanner scanner = new Scanner(System.in);
        while (true) {
            final String input = scanner.nextLine();
            if (Objects.equals("exit", input))
                break;

            final String[] parsed = input.split(" ");
            if (parsed.length < 2) {
                error();
                continue;
            }

            final String command = parsed[0];
            final String key = parsed[1];
            switch (command) {
                case "write":
                    if (parsed.length == 3)
                        writeRequest(key, Integer.decode(parsed[2]));
                    else
                        error();
                    break;
                case "read":
                    if (parsed.length == 2) {
                        readRequest(key);
                        break;
                    }
                default:
                    error();
            }
        }

        System.out.println("Client has stopped");
        Kompics.shutdown();
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

    private void error() {
        System.out.println("write key value\nread key\n");
    }
}
