package ru.nsu.fit.popov.storage.memory;

import ru.nsu.fit.popov.storage.net.Address;
import ru.nsu.fit.popov.storage.util.Connector;
import ru.nsu.fit.popov.storage.util.Creator;
import se.sics.kompics.*;

import java.util.*;

public class SMUpdater extends ComponentDefinition {

    static class Init extends se.sics.kompics.Init<SMUpdater> {
        private final Address myAddress;
        private final Collection<Address> addresses;
        private final Component networkComponent;

        private final Map<String, Data> memory;
        private final ReplicationPolicy policy;

        Init(Address myAddress, Collection<Address> addresses, Component networkComponent,
             Map<String, Data> memory, ReplicationPolicy policy) {
            this.myAddress = myAddress;
            this.addresses = addresses;
            this.networkComponent = networkComponent;
            this.memory = memory;
            this.policy = policy;
        }
    }

    static class Update implements KompicsEvent {
        private final Address failed;

        Update(Address failed) {
            this.failed = failed;
        }
    }

    public static class Port extends PortType {
        public Port() {
            request(Update.class);
        }
    }

    static Component create(Creator creator, Connector connector, Init init) {
        final Component updater = creator.create(SMUpdater.class, init);

        final Component sw = SimpleWriter.create(creator, connector,
                new SimpleWriter.Init(init.myAddress, init.addresses, init.networkComponent,
                        init.memory, init.policy));
        connector.connect(updater.getNegative(SimpleWriter.Port.class),
                sw.getPositive(SimpleWriter.Port.class), Channel.TWO_WAY);

        return updater;
    }

//    ------   interface ports   ------
    private final Negative<Port> port = provides(Port.class);

//    ------   implementation ports   ------
    private final Positive<SimpleWriter.Port> swPort = requires(SimpleWriter.Port.class);

    private final Map<String, Data> memory;
    private final ReplicationPolicy policy;

    private final Set<KeyData> toUpdate = new HashSet<>();

    private final Handler<Update> updateHandler = new Handler<Update>() {
        @Override
        public void handle(Update update) {
            toUpdate.clear();
            for (Map.Entry<String, Data> entry : memory.entrySet()) {
                final KeyData keyData = new KeyData(entry.getKey(), entry.getValue());
                if (policy.canSave(update.failed, keyData.getKey()))
                    toUpdate.add(keyData);
            }

            policy.fail(update.failed);
            updateNext();
        }
    };

    private final Handler<SimpleWriter.Response> swResponseHandler =
            new Handler<SimpleWriter.Response>() {
                @Override
                public void handle(SimpleWriter.Response swResponse) {
                    updateNext();
                }
            };

    public SMUpdater(Init init) {
        memory = init.memory;
        policy = init.policy;

        subscribe(updateHandler, port);
        subscribe(swResponseHandler, swPort);
    }

    private void updateNext() {
        final Iterator<KeyData> iterator = toUpdate.iterator();
        if (!iterator.hasNext())
            return;

        final KeyData keyData = iterator.next();
        final SimpleWriter.Request swRequest = new SimpleWriter.Request(keyData);
        trigger(swRequest, swPort);

        iterator.remove();
    }
}
