package ru.nsu.fit.popov.storage.memory;

import java.io.Serializable;

class KeyData implements Serializable {

    private final String key;
    private final Data data;

    KeyData(String key, Data data) {
        this.key = key;
        this.data = data;
    }

    String getKey() {
        return key;
    }

    Data getData() {
        return data;
    }
}
