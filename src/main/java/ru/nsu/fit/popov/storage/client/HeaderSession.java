package ru.nsu.fit.popov.storage.client;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

class HeaderSession extends Session {

    private static final int HEADER_SIZE = Byte.BYTES + Integer.BYTES;

    private static final byte OP_READ   = 0;
    private static final byte OP_WRITE  = 1;

    HeaderSession(SelectionKey key, Memory memory) {
        super(key, memory);
    }

    HeaderSession(Session session) {
        super(session);
    }

    {
        buffer = ByteBuffer.allocate(HEADER_SIZE);
    }

    @Override
    Session readProcess() {
        final byte op = buffer.get();
        final int keyLength = buffer.getInt();
        switch (op) {
            case OP_READ:
                return new ReadSession(this, keyLength);
            case OP_WRITE:
                return new WriteSession(this, keyLength);
            default:
                throw new RuntimeException("Bad operation code: " + op);
        }
    }

    @Override
    Session writeProcess() {
        throw new RuntimeException("Never should be called");
    }
}
