package ru.nsu.fit.popov.storage.net;

import se.sics.kompics.network.Transport;

import java.io.Serializable;

class Header implements se.sics.kompics.network.Header<Address>, Serializable {

    private final Address source;
    private final Address destination;

    Header(Address source, Address destination) {
        this.source = source;
        this.destination = destination;
    }

    @Override
    public Address getSource() {
        return source;
    }

    @Override
    public Address getDestination() {
        return destination;
    }

    @Override
    public Transport getProtocol() {
        return Transport.TCP;
    }
}
