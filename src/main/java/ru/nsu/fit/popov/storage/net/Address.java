package ru.nsu.fit.popov.storage.net;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class Address implements se.sics.kompics.network.Address, Serializable {

    private final InetSocketAddress socketAddress;

    public Address(String ipPort) {
        final String[] parsed = ipPort.split(":");
        final String hostname = parsed[0];
        final int port = Integer.decode(parsed[1]);

        socketAddress = new InetSocketAddress(hostname, port);
    }

    public Address(InetAddress address, int port) {
        socketAddress = new InetSocketAddress(address, port);
    }

    @Override
    public InetAddress getIp() {
        return socketAddress.getAddress();
    }

    @Override
    public int getPort() {
        return socketAddress.getPort();
    }

    @Override
    public InetSocketAddress asSocket() {
        return socketAddress;
    }

    @Override
    public boolean sameHostAs(se.sics.kompics.network.Address address) {
        return socketAddress.equals(address.asSocket());
    }

//    ------   util   ------

    @Override
    public String toString() {
        return socketAddress.toString();
    }

    @Override
    public int hashCode() {
        return socketAddress.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || obj.getClass() != Address.class)
            return false;

        final Address address = (Address) obj;
        return socketAddress.equals(address.socketAddress);
    }
}
