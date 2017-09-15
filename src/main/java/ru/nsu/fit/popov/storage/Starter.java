package ru.nsu.fit.popov.storage;

import ru.nsu.fit.popov.storage.broadcast.BestEffortBroadcast;
import ru.nsu.fit.popov.storage.net.Address;
import ru.nsu.fit.popov.storage.util.Connector;
import ru.nsu.fit.popov.storage.util.Creator;
import ru.nsu.fit.popov.storage.util.StartPort;
import se.sics.kompics.*;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.CancelPeriodicTimeout;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.Timer;
import se.sics.kompics.timer.java.JavaTimer;

import java.util.*;

public class Starter extends ComponentDefinition {

    static class Init extends se.sics.kompics.Init<Starter> {
        private final Address myAddress;
        private final Collection<Address> addresses;
        private final Component networkComponent;

        Init(Address myAddress, Collection<Address> addresses, Component networkComponent) {
            this.myAddress = myAddress;
            this.addresses = addresses;
            this.networkComponent = networkComponent;
        }
    }

    private static class HeartbeatTimeout extends se.sics.kompics.timer.Timeout {
        private HeartbeatTimeout(SchedulePeriodicTimeout request) {
            super(request);
        }
    }

    private final static byte WAITING   = 0;
    private final static byte READY     = 1;

    static Component create(Creator creator, Connector connector, Init init) {
        final Component starter = creator.create(Starter.class, init);

        final Component timer = creator.create(JavaTimer.class, null);
        connector.connect(starter.getNegative(Timer.class),
                timer.getPositive(Timer.class), Channel.TWO_WAY);

        final Component beb = creator.create(BestEffortBroadcast.class,
                new BestEffortBroadcast.Init(init.myAddress, init.addresses));
        connector.connect(starter.getNegative(BestEffortBroadcast.Port.class),
                beb.getPositive(BestEffortBroadcast.Port.class), Channel.TWO_WAY);
        connector.connect(beb.getNegative(Network.class),
                init.networkComponent.getPositive(Network.class), Channel.TWO_WAY);

        return starter;
    }

    private final Positive<Timer> timerPort = requires(Timer.class);
    private final Positive<BestEffortBroadcast.Port> bebPort
            = requires(BestEffortBroadcast.Port.class);
    private final Negative<StartPort> startPort = provides(StartPort.class);

    private final Map<Address, Boolean> addresses = new HashMap<>();
    private boolean woken = false;

    private UUID timerId;

    private final Handler<Start> startHandler = new Handler<Start>() {
        @Override
        public void handle(Start start) {
            final SchedulePeriodicTimeout schedule = new SchedulePeriodicTimeout(0, 10);   //  TODO: move to constant
            final HeartbeatTimeout timeout = new HeartbeatTimeout(schedule);
            schedule.setTimeoutEvent(timeout);

            trigger(schedule, timerPort);
            timerId = timeout.getTimeoutId();
        }
    };

    private final Handler<HeartbeatTimeout> timeoutHandler = new Handler<HeartbeatTimeout>() {
        @Override
        public void handle(HeartbeatTimeout timeout) {
            if (addresses.isEmpty()) {
                suicide();
                return;
            }

            if (!woken) {
                woken = addresses.values().stream().allMatch(Boolean::booleanValue);
                if (woken)
                    trigger(Start.event, startPort);
            }

            final BestEffortBroadcast.Broadcast bebBroadcast = (woken)
                    ? new BestEffortBroadcast.Broadcast(READY)
                    : new BestEffortBroadcast.Broadcast(WAITING);
            trigger(bebBroadcast, bebPort);
        }
    };

    private final Handler<BestEffortBroadcast.Deliver> bebDeliverHandler =
            new Handler<BestEffortBroadcast.Deliver>() {
                @Override
                public void handle(BestEffortBroadcast.Deliver bebDeliver) {
                    final byte state = (byte) bebDeliver.data;
                    switch (state) {
                        case WAITING:
                            addresses.put(bebDeliver.source, true);
                            break;
                        case READY:
                            addresses.remove(bebDeliver.source);
                            break;
                        default:
                            throw new RuntimeException("Incorrect state");
                    }
                }
            };

    public Starter(Init init) {
        for (Address address : init.addresses) {
            addresses.put(address, false);
        }

        subscribe(startHandler, control);
        subscribe(timeoutHandler, timerPort);
        subscribe(bebDeliverHandler, bebPort);
    }

    @Override
    public void tearDown() {
        trigger(new CancelPeriodicTimeout(timerId), timerPort);
    }
}
