package ru.nsu.fit.popov.storage.client;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.charset.Charset;

class WriteSession extends Session {

    private final int keyLength;

    WriteSession(Session session, int keyLength) {
        super(session);

        this.keyLength = keyLength;

        buffer = ByteBuffer.allocate(keyLength + Integer.BYTES);
    }

    @Override
    Session readProcess() {
        final byte[] bytes = new byte[keyLength];
        buffer.get(bytes);

        final String key = new String(bytes, Charset.forName("UTF8"));
        final int value = buffer.getInt();
        try {
            memory.write(key, value);
        } catch (MemoryException e) {
            return errorProcess(e.getCode());
        }

        buffer = ByteBuffer.allocate(Byte.BYTES);
        buffer.put(Memory.SUCCESS);
        buffer.flip();

        selectionKey.interestOps(SelectionKey.OP_WRITE);

        return this;
    }

    private Session errorProcess(byte code) {
        buffer = ByteBuffer.allocate(Byte.BYTES);
        buffer.put(code);
        buffer.flip();

        selectionKey.interestOps(SelectionKey.OP_WRITE);

        return this;
    }

    @Override
    Session writeProcess() {
        selectionKey.interestOps(SelectionKey.OP_READ);

        return new HeaderSession(this);
    }
}
