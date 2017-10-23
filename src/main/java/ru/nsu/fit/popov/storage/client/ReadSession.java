package ru.nsu.fit.popov.storage.client;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.charset.Charset;

class ReadSession extends Session {

    ReadSession(Session session, int keyLength) {
        super(session);
        buffer = ByteBuffer.allocate(keyLength);
    }

    @Override
    Session readProcess() {
        final byte[] bytes = buffer.array();
        final String key = new String(bytes, Charset.forName("UTF8"));
        final int value;
        try {
            value = memory.read(key);
        } catch (MemoryException e) {
            return errorProcess(e.getCode());
        }

        buffer = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES);
        buffer.put(Memory.SUCCESS);
        buffer.putInt(value);
        buffer.flip();

        selectionKey.interestOps(SelectionKey.OP_WRITE);

        return this;
    }

    private Session errorProcess(byte code) {
        buffer = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES);
        buffer.put(code);
        buffer.putInt(-1);
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
