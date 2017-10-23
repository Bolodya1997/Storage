package ru.nsu.fit.popov.storage.client;

class MemoryException extends Exception {

    private final byte code;

    MemoryException(byte code) {
        this.code = code;
    }

    byte getCode() {
        return code;
    }
}
