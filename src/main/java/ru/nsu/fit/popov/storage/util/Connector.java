package ru.nsu.fit.popov.storage.util;

import se.sics.kompics.*;

@FunctionalInterface
public interface Connector {

    <T extends PortType> Channel<T> connect(Negative<T> negative,
                                            Positive<T> positive, ChannelFactory factory);
}
