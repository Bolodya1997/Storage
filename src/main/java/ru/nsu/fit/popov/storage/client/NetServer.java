package ru.nsu.fit.popov.storage.client;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

class NetServer {

    private final Selector selector = Selector.open();

    private final ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
    private final Memory memory;

    private final Map<SelectionKey, Session> sessions = new HashMap<>();

    NetServer(SocketAddress address, Memory memory) throws IOException {
        serverSocketChannel.bind(address);

        this.memory = memory;

        final SelectionKey selectionKey = serverSocketChannel.keyFor(selector);
        selectionKey.interestOps(SelectionKey.OP_ACCEPT);
        selector.keys().add(selectionKey);
    }

    void start() throws IOException {
        while (true) {
            selector.select();

            final Iterator<SelectionKey> i = selector.selectedKeys().iterator();
            while (i.hasNext()) {
                final SelectionKey key = i.next();
                if (key.isAcceptable())
                    accept();
                else if (key.isReadable())
                    read(key);
                else if (key.isWritable())
                    write(key);

                i.remove();
            }
        }
    }

    private void accept() throws IOException {
        final SocketChannel socketChannel = serverSocketChannel.accept();

        final SelectionKey selectionKey = socketChannel.keyFor(selector);
        final Session session = new HeaderSession(selectionKey, memory);
        sessions.put(selectionKey, session);
    }

    private void read(SelectionKey selectionKey) {
        final Session session = sessions.get(selectionKey).readAction();
        if (session.isClosed()) {
            sessions.remove(selectionKey);
            selectionKey.cancel();
        } else {
            sessions.put(selectionKey, session);
        }
    }

    private void write(SelectionKey selectionKey) {
        final Session session = sessions.get(selectionKey).writeAction();
        if (session.isClosed()) {
            sessions.remove(selectionKey);
            selectionKey.cancel();
        } else {
            sessions.put(selectionKey, session);
        }
    }
}
