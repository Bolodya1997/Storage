package ru.nsu.fit.popov.storage.broadcast;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

class BroadcastData implements Serializable {

    private final UUID id = UUID.randomUUID();

    private final Serializable data;

    BroadcastData(Serializable data) {
        this.data = data;
    }

    UUID getId() {
        return id;
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

        BroadcastData broadcastData = (BroadcastData) o;
        return Objects.equals(id, broadcastData.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
