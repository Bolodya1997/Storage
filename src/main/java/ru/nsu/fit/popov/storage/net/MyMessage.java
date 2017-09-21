package ru.nsu.fit.popov.storage.net;

import java.io.Serializable;

public class MyMessage extends BaseMessage {

    private static class MyData implements Serializable {
        private final int myId;
        private final Serializable data;

        private MyData(int myId, Serializable data) {
            this.myId = myId;
            this.data = data;
        }
    }

    public MyMessage(Address source, Address destination, Serializable data,
                     int myId) {
        super(source, destination, new MyData(myId, data));
    }

    public boolean canHandle(int myId) {
        final MyData myData = (MyData) super.getData();
        return myData.myId == myId;
    }

    @Override
    public Serializable getData() {
        final MyData myData = (MyData) super.getData();
        return myData.data;
    }
}
