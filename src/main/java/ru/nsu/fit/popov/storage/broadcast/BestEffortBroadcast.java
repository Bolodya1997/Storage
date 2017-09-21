package ru.nsu.fit.popov.storage.broadcast;

import ru.nsu.fit.popov.storage.net.Address;
import ru.nsu.fit.popov.storage.net.MyMessage;
import ru.nsu.fit.popov.storage.util.Connector;
import ru.nsu.fit.popov.storage.util.Creator;
import ru.nsu.fit.popov.storage.util.Identifier;
import se.sics.kompics.*;
import se.sics.kompics.network.Network;

import java.io.Serializable;
import java.util.*;

public class BestEffortBroadcast extends ComponentDefinition {

    public static class Init extends se.sics.kompics.Init<BestEffortBroadcast> {
        private final Address myAddress;
        private final Collection<Address> addresses;
        private final Component networkComponent;

        public Init(Address myAddress, Collection<Address> addresses, Component networkComponent) {
            this.myAddress = myAddress;
            this.addresses = addresses;
            this.networkComponent = networkComponent;
        }
    }

    public static class Broadcast implements KompicsEvent {
        private final Serializable data;

        public Broadcast(Serializable data) {
            this.data = data;
        }
    }

    public static class Deliver implements KompicsEvent {
        public final Address source;
        public final Serializable data;

        private Deliver(Address source, Serializable data) {
            this.source = source;
            this.data = data;
        }
    }

    public static class Port extends PortType {
        public Port() {
            request(Broadcast.class);
            indication(Deliver.class);
        }
    }

    private static class Message extends MyMessage {
        private Message(Address source, Address destination, Serializable data, int myId) {
            super(source, destination, data, myId);
        }
    }

    public static Component create(Creator creator, Connector connector, Init init) {
        final Component beb = creator.create(BestEffortBroadcast.class, init);
        connector.connect(beb.getNegative(Network.class),
                init.networkComponent.getPositive(Network.class), Channel.TWO_WAY);

        return beb;
    }

//    ------   interface ports   ------
    private final Negative<Port> port = provides(Port.class);

//    ------   implementation ports   ------
    private final Positive<Network> networkPort = requires(Network.class);

    private final int myId = Identifier.getId();

    private final Address myAddress;
    private final Collection<Address> addresses;

    private final Handler<Broadcast> requestHandler = new Handler<Broadcast>() {
        @Override
        public void handle(Broadcast broadcast) {
            for (Address destination : addresses) {
                final Message message = new Message(myAddress, destination, broadcast.data, myId);
                trigger(message, networkPort);
            }
        }
    };

    private final Handler<Message> messageHandler = new Handler<Message>() {
        @Override
        public void handle(Message message) {
            if (!message.canHandle(myId))
                return;

            final Deliver deliver = new Deliver(message.getSource(), message.getData());
            trigger(deliver, port);
        }
    };

    public BestEffortBroadcast(Init init) {
        myAddress = init.myAddress;
        addresses = init.addresses;

        subscribe(requestHandler, port);
        subscribe(messageHandler, networkPort);
    }
}
