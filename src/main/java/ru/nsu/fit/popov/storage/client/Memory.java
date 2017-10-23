package ru.nsu.fit.popov.storage.client;

interface Memory {

    byte SUCCESS            = 0;
    byte BAD_KEY            = 1;
    byte NOT_ENOUGH_NODES   = 2;

    int read(String key) throws MemoryException;
    void write(String key, int value) throws MemoryException;
}
