package ru.nsu.fit.popov.storage.memory;

import java.io.Serializable;

class Data implements Serializable {

    private int value;
    private int sequenceNumber;

    Data(int value, int sequenceNumber) {
        this.value = value;
        this.sequenceNumber = sequenceNumber;
    }

    Data(int value) {
        this(value, 0);
    }

    int getValue() {
        return value;
    }

    void setValue(int value) {
        this.value = value;
    }

    int getSequenceNumber() {
        return sequenceNumber;
    }

    void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }
}
