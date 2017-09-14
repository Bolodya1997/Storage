package ru.nsu.fit.popov.storage.net;

import se.sics.kompics.network.Msg;
import se.sics.kompics.network.Transport;

import java.io.Serializable;

public class BaseMessage implements Msg<Address, Header>, Serializable {

    private final Header header;
    private final Serializable data;

    public BaseMessage(Address source, Address destination, Serializable data) {
        header = new Header(source, destination);
        this.data = data;
    }

    public Serializable getData() {
        return data;
    }

    @Override
    public Header getHeader() {
        return header;
    }

    @Override
    public Address getSource() {
        return header.getSource();
    }

    @Override
    public Address getDestination() {
        return header.getDestination();
    }

    @Override
    public Transport getProtocol() {
        return header.getProtocol();
    }
}
