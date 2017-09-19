package ru.nsu.fit.popov.storage.broadcast;

import ru.nsu.fit.popov.storage.net.Address;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

class BroadcastData implements Serializable {

    private final UUID id = UUID.randomUUID();

    private final Address pureSource;
    private final Serializable data;

    BroadcastData(Address pureSource, Serializable data) {
        this.pureSource = pureSource;
        this.data = data;
    }

    Address getPureSource() {
        return pureSource;
    }

    Serializable getData() {
        return data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        final BroadcastData broadcastData = (BroadcastData) o;
        return Objects.equals(id, broadcastData.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
