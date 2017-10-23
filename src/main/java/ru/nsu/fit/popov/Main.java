package ru.nsu.fit.popov;

import ru.nsu.fit.popov.storage.Application;
import se.sics.kompics.Kompics;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class Main {

    public static void main(String[] args) {
        final int number = Integer.decode(args[0]);

        SocketAddress serverAddress = null;
        if (args.length > 1) {
            int port = Integer.decode(args[1]);
            serverAddress = new InetSocketAddress("localhost", port);
        }

        final Application.Init init = new Application.Init(number, serverAddress);
        Kompics.createAndStart(Application.class, init);
    }
}
