package ru.nsu.fit.popov.storage.broadcast;

import ru.nsu.fit.popov.storage.net.Address;
import ru.nsu.fit.popov.storage.util.Connector;
import ru.nsu.fit.popov.storage.util.Creator;
import se.sics.kompics.*;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.CancelPeriodicTimeout;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.Timer;
import se.sics.kompics.timer.java.JavaTimer;

import java.io.Serializable;
import java.util.*;

public class UniformBroadcast extends ComponentDefinition {

    public static class Init extends se.sics.kompics.Init<UniformBroadcast> {
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
        private Serializable data;

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

    private static class Timeout extends se.sics.kompics.timer.Timeout {
        private Timeout(SchedulePeriodicTimeout request) {
            super(request);
        }
    }

    private static long DELAY = 10L;

    public static Component create(Creator creator, Connector connector, Init init) {
        final Component ub = creator.create(UniformBroadcast.class, init);

        final Component beb = creator.create(BestEffortBroadcast.class,
                new BestEffortBroadcast.Init(init.myAddress, init.addresses));
        connector.connect(ub.getNegative(BestEffortBroadcast.Port.class),
                beb.getPositive(BestEffortBroadcast.Port.class), Channel.TWO_WAY);
        connector.connect(beb.getNegative(Network.class),
                init.networkComponent.getPositive(Network.class), Channel.TWO_WAY);

        final Component timer = creator.create(JavaTimer.class, null);
        connector.connect(ub.getNegative(Timer.class),
                timer.getPositive(Timer.class), Channel.TWO_WAY);

        return ub;
    }

    private final Negative<Port> port = provides(Port.class);
    private final Positive<BestEffortBroadcast.Port> bebPort =
            requires(BestEffortBroadcast.Port.class);
    private final Positive<Timer> timerPort = requires(Timer.class);

    private final Address myAddress;
    private final Collection<Address> addresses;

    private final Map<BroadcastData, List<Address>> pending = new HashMap<>();
    private final Set<BroadcastData> delivered = new HashSet<>();

    private UUID timerId;

    private final Handler<Broadcast> broadcastHandler = new Handler<Broadcast>() {
        @Override
        public void handle(Broadcast broadcast) {
            final BroadcastData data = new BroadcastData(myAddress, broadcast.data);
            pending.put(data, new ArrayList<>());

            final BestEffortBroadcast.Broadcast bebBroadcast =
                    new BestEffortBroadcast.Broadcast(data);
            trigger(bebBroadcast, bebPort);
        }
    };

    private final Handler<Start> startHandler = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            final SchedulePeriodicTimeout schedule = new SchedulePeriodicTimeout(0, DELAY);
            final Timeout timeout = new Timeout(schedule);
            schedule.setTimeoutEvent(timeout);

            trigger(schedule, timerPort);
            timerId = timeout.getTimeoutId();
        }
    };

    private final Handler<BestEffortBroadcast.Deliver> bebDeliverHandler =
            new Handler<BestEffortBroadcast.Deliver>() {
                @Override
                public void handle(BestEffortBroadcast.Deliver bebDeliver) {
                    final BroadcastData data = (BroadcastData) bebDeliver.data;
                    if (!pending.containsKey(data)) {
                        pending.put(data, new ArrayList<>());

                        final BestEffortBroadcast.Broadcast bebBroadcast =
                                new BestEffortBroadcast.Broadcast(data);
                        trigger(bebBroadcast, bebPort);
                    }

                    pending.get(data).add(bebDeliver.source);
                }
            };

    private final Handler<Timeout> timeoutHandler = new Handler<Timeout>() {
        @Override
        public void handle(Timeout timeout) {
            for (Map.Entry<BroadcastData, List<Address>> entry : pending.entrySet()) {
                final BroadcastData data = entry.getKey();
                final List<Address> sources = entry.getValue();

                if (delivered.contains(data))
                    continue;   //  TODO: think about removing from pending+delivered

                if (sources.containsAll(addresses)) {
                    delivered.add(entry.getKey());

                    final Deliver deliver = new Deliver(data.getPureSource(), data.getData());
                    trigger(deliver, port);
                }
            }
        }
    };

    public UniformBroadcast(Init init) {
        myAddress = init.myAddress;
        addresses = init.addresses;

        subscribe(startHandler, control);
        subscribe(broadcastHandler, port);
        subscribe(bebDeliverHandler, bebPort);
        subscribe(timeoutHandler, timerPort);
    }

    @Override
    public void tearDown() {
        trigger(new CancelPeriodicTimeout(timerId), timerPort);
    }
}
