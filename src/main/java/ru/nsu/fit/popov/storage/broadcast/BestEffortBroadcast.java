package ru.nsu.fit.popov.storage.broadcast;

import ru.nsu.fit.popov.storage.net.Address;
import ru.nsu.fit.popov.storage.net.BaseMessage;
import se.sics.kompics.*;
import se.sics.kompics.network.Network;

import java.io.Serializable;
import java.util.*;

public class BestEffortBroadcast extends ComponentDefinition {

    public static class Init extends se.sics.kompics.Init<BestEffortBroadcast> {
        private final Address myAddress;
        private final Collection<Address> addresses;    //  FIXME: change to FD correct list

        public Init(Address myAddress, Collection<Address> addresses) {
            this.myAddress = myAddress;
            this.addresses = addresses;
        }
    }

    public static class Broadcast implements KompicsEvent {
        private final Serializable data;

        Broadcast(Serializable data) {
            this.data = data;
        }
    }

    public static class Deliver implements KompicsEvent {
        final Address source;
        final Serializable data;

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

    private static class Message extends BaseMessage {
        private final UUID handlerId;

        private Message(Address source, Address destination, Serializable data,
                        UUID handlerId) {
            super(source, destination, data);
            this.handlerId = handlerId;
        }
    }

    private final Negative<Port> port = provides(Port.class);
    private final Positive<Network> networkPort = requires(Network.class);

    private final UUID myId = UUID.randomUUID();

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
            if (Objects.equals(message.handlerId, myId)) {
                final Deliver deliver = new Deliver(message.getSource(), message.getData());
                trigger(deliver, port);
            }
        }
    };

    public BestEffortBroadcast(Init init) {
        myAddress = init.myAddress;
        addresses = init.addresses;

        subscribe(requestHandler, port);
        subscribe(messageHandler, networkPort);
    }
}
