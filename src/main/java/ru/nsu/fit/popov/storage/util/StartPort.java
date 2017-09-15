package ru.nsu.fit.popov.storage.util;

import se.sics.kompics.PortType;
import se.sics.kompics.Start;

public class StartPort extends PortType {

    public StartPort() {
        indication(Start.class);
    }
}
