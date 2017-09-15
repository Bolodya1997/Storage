package ru.nsu.fit.popov.storage.chat;

import ru.nsu.fit.popov.storage.broadcast.UniformBroadcast;
import ru.nsu.fit.popov.storage.net.Address;
import ru.nsu.fit.popov.storage.util.Creator;
import ru.nsu.fit.popov.storage.util.Connector;
import ru.nsu.fit.popov.storage.util.StartPort;
import se.sics.kompics.*;

import java.util.Collection;
import java.util.Objects;
import java.util.Scanner;

public class Chat extends ComponentDefinition {

    public static class Init extends se.sics.kompics.Init<Chat> {
        private final Address myAddress;
        private final Collection<Address> addresses;    //  FIXME: change to FD correct list
        private final Component networkComponent;

        public Init(Address myAddress, Collection<Address> addresses, Component networkComponent) {
            this.myAddress = myAddress;
            this.addresses = addresses;
            this.networkComponent = networkComponent;
        }
    }

    public static Component create(Creator creator, Connector connector, Init init) {
        final Component chat = creator.create(Chat.class, init);

        final Component ub = UniformBroadcast.create(creator, connector,
                new UniformBroadcast.Init(init.myAddress, init.addresses, init.networkComponent));
        connector.connect(chat.getNegative(UniformBroadcast.Port.class),
                ub.getPositive(UniformBroadcast.Port.class), Channel.TWO_WAY);

        return chat;
    }

    private final Positive<StartPort> startPort = requires(StartPort.class);
    private final Positive<UniformBroadcast.Port> ubPort = requires(UniformBroadcast.Port.class);

    private final Address myAddress;

    private final Handler<Start> startHandler = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            System.out.println("Wake up on " + myAddress);
            new Thread(Chat.this::loop).start();
        }
    };

    private final Handler<UniformBroadcast.Deliver> ubDeliverHandler =
            new Handler<UniformBroadcast.Deliver>() {
                @Override
                public void handle(UniformBroadcast.Deliver deliver) {
                    final String message = (String) deliver.data;
                    System.out.println("[" + deliver.source + "] : " + message);
                }
            };

    public Chat(Init init) {
        myAddress = init.myAddress;

        subscribe(startHandler, startPort);
        subscribe(ubDeliverHandler, ubPort);
    }

    private void loop() {
        final Scanner scanner = new Scanner(System.in);
        while (true) {
            final String input = scanner.nextLine();
            if (Objects.equals("exit", input))
                break;

            final UniformBroadcast.Broadcast ubBroadcast = new UniformBroadcast.Broadcast(input);
            trigger(ubBroadcast, ubPort);
        }

        System.out.println("Tear down on " + myAddress);
        Kompics.shutdown();
    }
}
