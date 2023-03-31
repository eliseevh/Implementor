package info.kgeorgiy.java.advanced.implementor.generic;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public abstract class GenericClass<T> implements List<T>, Collection<T> {
    public <Q extends T> GenericClass(Q arg) {

    }
    public GenericClass(Collection<? super T> coll) {

    }
    abstract<Q extends List<T> & Comparable<? super Q>> Map.Entry<T, Q> method(Q first);
}
