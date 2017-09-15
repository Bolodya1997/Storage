package ru.nsu.fit.popov.storage.util;

import se.sics.kompics.PortType;
import se.sics.kompics.Start;

public class StartPort extends PortType {

    //  TODO: think about implementing custom Control port

    public StartPort() {
        indication(Start.class);
    }
}
