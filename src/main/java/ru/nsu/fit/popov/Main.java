package ru.nsu.fit.popov;

import ru.nsu.fit.popov.storage.Application;
import se.sics.kompics.Kompics;

public class Main {

    public static void main(String[] args) {
        final int number = Integer.decode(args[0]);

        final Application.Init init = new Application.Init(number);
        Kompics.createAndStart(Application.class, init);
    }
}
