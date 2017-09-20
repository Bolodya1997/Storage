package ru.nsu.fit.popov.storage;

import ru.nsu.fit.popov.storage.chat.Client;
import ru.nsu.fit.popov.storage.memory.SharedMemory;
import ru.nsu.fit.popov.storage.net.Address;
import ru.nsu.fit.popov.storage.util.StartPort;
import se.sics.kompics.Channel;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.network.netty.NettyInit;
import se.sics.kompics.network.netty.NettyNetwork;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public class Application extends ComponentDefinition {

    public static class Init extends se.sics.kompics.Init<Application> {
        private final int number;

        public Init(int number) {
            this.number = number;
        }
    }

    private List<Address> addresses;

    public Application(Init init) {
        try {
            readProperties();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final Address myAddress = addresses.get(init.number);

        final NettyInit nettyInit = new NettyInit(myAddress);
        final Component networkComponent = create(NettyNetwork.class, nettyInit);
        final Component client = create(Client.class, se.sics.kompics.Init.NONE);

        final Component starter = Starter.create(this::create, this::connect,
                new Starter.Init(myAddress, addresses, networkComponent));
        connect(client.getNegative(StartPort.class),
                starter.getPositive(StartPort.class), Channel.TWO_WAY);

        final Component sm = SharedMemory.create(this::create, this::connect,
                new SharedMemory.Init(myAddress, addresses, starter, networkComponent));
        connect(client.getNegative(SharedMemory.Port.class),
                sm.getPositive(SharedMemory.Port.class), Channel.TWO_WAY);
    }

    private void readProperties() throws IOException {
        final Properties properties = new Properties();
        properties.load(Application.class.getResourceAsStream("config.properties"));

        addresses = Arrays.stream(properties.getProperty("addresses")
                    .replaceAll(" ", "")
                    .split(","))
                .map(Address::new)
                .collect(Collectors.toList());
    }
}
