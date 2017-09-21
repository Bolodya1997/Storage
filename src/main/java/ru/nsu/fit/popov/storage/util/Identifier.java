package ru.nsu.fit.popov.storage.util;

public final class Identifier {

    private static int nextId = 0;

    public static int getId() {
        return nextId++;
    }
}
