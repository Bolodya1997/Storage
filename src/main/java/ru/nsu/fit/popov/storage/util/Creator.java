package ru.nsu.fit.popov.storage.util;

import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Init;

@FunctionalInterface
public interface Creator {

    <T extends ComponentDefinition> Component create(Class<T> componentClass, Init<T> init);
}
