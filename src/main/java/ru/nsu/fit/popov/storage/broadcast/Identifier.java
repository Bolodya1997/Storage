package ru.nsu.fit.popov.storage.broadcast;

final class Identifier {

    private static int nextId = 0;

    static int getId() {
        return nextId++;
    }
}
