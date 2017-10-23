package ru.nsu.fit.popov.storage.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

abstract class Session {

    protected final SelectionKey selectionKey;
    protected final SocketChannel channel;

    protected final Memory memory;

    protected ByteBuffer buffer;

    private boolean closed = false;

    protected Session(SelectionKey selectionKey, Memory memory) {
        this.selectionKey = selectionKey;
        this.channel = (SocketChannel) selectionKey.channel();

        this.memory = memory;
    }

    protected Session(Session session) {
        this.selectionKey = session.selectionKey;
        this.channel = session.channel;
        this.memory = session.memory;
    }

    private void close() {
        closed = true;
    }

    boolean isClosed() {
        return closed;
    }

    Session readAction() {
        int read;
        try {
            read = channel.read(buffer);
        } catch (IOException e) {
            read = -1;
        }

        if (read <= 0) {
            close();
            return this;
        }

        if (buffer.hasRemaining())
            return this;
        buffer.flip();

        return readProcess();
    }

    Session writeAction() {
        int written;
        try {
            written = channel.write(buffer);
        } catch (IOException e) {
            written = -1;
        }

        if (written <= 0) {
            close();
            return this;
        }

        if (buffer.hasRemaining())
            return this;

        return writeProcess();
    }

    abstract Session readProcess();
    abstract Session writeProcess();
}
