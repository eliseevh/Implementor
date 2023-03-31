package info.kgeorgiy.java.advanced.implementor.generic;

import java.util.Collection;

public interface GenericInterfaceWithGenericMethods<T, V extends T, S extends Comparable<? super V>> {
    <Q> T method(S first);
    <T extends V> T method2();
    void method3(java.util.List<?> first, Collection<S> second);
}
