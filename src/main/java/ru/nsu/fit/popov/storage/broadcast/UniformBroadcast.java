package ru.nsu.fit.popov.storage.broadcast;

import ru.nsu.fit.popov.storage.net.Address;
import ru.nsu.fit.popov.storage.util.Connector;
import ru.nsu.fit.popov.storage.util.Creator;
import se.sics.kompics.*;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.CancelPeriodicTimeout;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;
import se.sics.kompics.timer.java.JavaTimer;

import java.io.Serializable;
import java.util.*;

public class UniformBroadcast extends ComponentDefinition {

    public static class Init extends se.sics.kompics.Init<UniformBroadcast> {
        private final Address myAddress;
        private final Collection<Address> addresses;    //  FIXME: change to FD correct list
        private final Component network;

        public Init(Address myAddress, Collection<Address> addresses, Component network) {
            this.myAddress = myAddress;
            this.addresses = addresses;
            this.network = network;
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
        {
            request(Broadcast.class);
            indication(Deliver.class);
        }
    }

    private static class UpdateTimeout extends Timeout {
        private UpdateTimeout(SchedulePeriodicTimeout request) {
            super(request);
        }
    }

    public static Component create(Creator creator, Connector connector, Init init) {
        final Component ub = creator.create(UniformBroadcast.class, init);

        final Component beb = creator.create(BestEffortBroadcast.class,
                new BestEffortBroadcast.Init(init.myAddress, init.addresses));
        connector.connect(ub.getNegative(BestEffortBroadcast.Port.class),
                beb.getPositive(BestEffortBroadcast.Port.class), Channel.TWO_WAY);
        connector.connect(beb.getNegative(Network.class),
                init.network.getPositive(Network.class), Channel.TWO_WAY);

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
            final BroadcastData data = new BroadcastData(broadcast.data);
            pending.put(data, new ArrayList<>());

            final BestEffortBroadcast.Broadcast bebBroadcast =
                    new BestEffortBroadcast.Broadcast(data);
            trigger(bebBroadcast, bebPort);

            if (timerId == null)
                startTimer();
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

    private final Handler<UpdateTimeout> timeoutHandler = new Handler<UpdateTimeout>() {
        @Override
        public void handle(UpdateTimeout timeout) {
            for (Map.Entry<BroadcastData, List<Address>> entry : pending.entrySet()) {
                final BroadcastData data = entry.getKey();
                final List<Address> sources = entry.getValue();

                if (delivered.contains(data))
                    continue;   //  TODO: think about removing from pending+delivered

                if (sources.containsAll(addresses)) {
                    delivered.add(entry.getKey());

                    final Deliver deliver = new Deliver(sources.get(0), data.getData());
                    trigger(deliver, port);
                }
            }
        }
    };

    public UniformBroadcast(Init init) {
        myAddress = init.myAddress;
        addresses = init.addresses;

        subscribe(broadcastHandler, port);
        subscribe(bebDeliverHandler, bebPort);
        subscribe(timeoutHandler, timerPort);
    }

    private void startTimer() {
        final SchedulePeriodicTimeout schedule = new SchedulePeriodicTimeout(0, 1000);   //  TODO: move to constant
        final UpdateTimeout timeout = new UpdateTimeout(schedule);
        schedule.setTimeoutEvent(timeout);

        trigger(schedule, timerPort);
        timerId = timeout.getTimeoutId();
    }

    @Override
    public void tearDown() {
        trigger(new CancelPeriodicTimeout(timerId), timerPort);
    }
}
