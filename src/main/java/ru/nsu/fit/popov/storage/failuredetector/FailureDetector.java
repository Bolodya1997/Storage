package ru.nsu.fit.popov.storage.failuredetector;

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

public class FailureDetector extends ComponentDefinition {

    public static class Init extends se.sics.kompics.Init<FailureDetector> {
        private final Address myAddress;
        private Collection<Address> addresses;
        private final Component networkComponent;

        public Init(Address myAddress, Collection<Address> addresses, Component networkComponent) {
            this.myAddress = myAddress;
            this.addresses = addresses;
            this.networkComponent = networkComponent;
        }
    }

    public static class Fail implements KompicsEvent {
        public final Address address;

        private Fail(Address address) {
            this.address = address;
        }
    }

    public static class Port extends PortType {
        public Port() {
            indication(Fail.class);
        }
    }

    private static class Timeout extends se.sics.kompics.timer.Timeout {
        private Timeout(SchedulePeriodicTimeout request) {
            super(request);
        }
    }

    private final static long DELAY = 1000L;

    public static Component create(Creator creator, Connector connector, Init init) {
        final Collection<Address> bebAddresses = new HashSet<>();
        bebAddresses.addAll(init.addresses);
        init.addresses = bebAddresses;

        final Component fd = creator.create(FailureDetector.class, init);

        final Component timer = creator.create(JavaTimer.class, null);
        connector.connect(fd.getNegative(Timer.class),
                timer.getPositive(Timer.class), Channel.TWO_WAY);

        final Component beb = BestEffortBroadcast.create(creator, connector,
                new BestEffortBroadcast.Init(init.myAddress, init.addresses,
                        init.networkComponent));
        connector.connect(fd.getNegative(BestEffortBroadcast.Port.class),
                beb.getPositive(BestEffortBroadcast.Port.class), Channel.TWO_WAY);

        return fd;
    }

//    ------   interface ports   ------
    private final Positive<StartPort> startPort = requires(StartPort.class);
    private final Negative<Port> port = provides(Port.class);

//    ------   implementation ports   ------
    private final Positive<BestEffortBroadcast.Port> bebPort =
            requires(BestEffortBroadcast.Port.class);
    private final Positive<Timer> timerPort = requires(Timer.class);

    private final Map<Address, Long> addresses = new HashMap<>();
    private final Collection<Address> bebAddresses; //  need for crash-stop model

    private UUID timerId;

    private final Handler<Start> startHandler = new Handler<Start>() {
        @Override
        public void handle(Start start) {
            heartBeat();

            final SchedulePeriodicTimeout schedule = new SchedulePeriodicTimeout(DELAY, DELAY);
            final Timeout timeout = new Timeout(schedule);
            schedule.setTimeoutEvent(timeout);

            trigger(schedule, timerPort);
            timerId = timeout.getTimeoutId();
        }
    };

    private final Handler<Timeout> timeoutHandler = new Handler<Timeout>() {
        @Override
        public void handle(Timeout timeout) {
            heartBeat();

            final long current = System.currentTimeMillis();
            for (Map.Entry<Address, Long> entry : addresses.entrySet()) {
                final Address address = entry.getKey();
                final long lastTime = entry.getValue();
                if (lastTime == -1L)
                    continue;

                if (current - lastTime > 2 * DELAY) {
                    entry.setValue(-1L);
                    bebAddresses.remove(address);
                    trigger(new Fail(address), port);
                }
            }
        }
    };

    private final Handler<BestEffortBroadcast.Deliver> bebDeliverHandler =
            new Handler<BestEffortBroadcast.Deliver>() {
                @Override
                public void handle(BestEffortBroadcast.Deliver bebDeliver) {
                    final Long lastTime = addresses.get(bebDeliver.source);
                    if (lastTime == null || lastTime == -1L)
                        return;

                    final long current = System.currentTimeMillis();
                    addresses.put(bebDeliver.source, current);
                }
            };

    public FailureDetector(Init init) {
        this.bebAddresses = init.addresses;

        for (Address address : init.addresses) {
            addresses.put(address, 0L);
        }

        subscribe(startHandler, startPort);
        subscribe(timeoutHandler, timerPort);
        subscribe(bebDeliverHandler, bebPort);
    }

    private void heartBeat() {
        final BestEffortBroadcast.Broadcast bebBroadcast =
                new BestEffortBroadcast.Broadcast(null);
        trigger(bebBroadcast, bebPort);
    }

    @Override
    public void tearDown() {
        trigger(new CancelPeriodicTimeout(timerId), timerPort);
    }
}
