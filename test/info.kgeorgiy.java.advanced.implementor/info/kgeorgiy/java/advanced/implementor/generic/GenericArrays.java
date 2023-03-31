package info.kgeorgiy.java.advanced.implementor.generic;

import java.io.IOException;
import java.util.List;

public abstract class GenericArrays<X> implements List<X[]> {
    public abstract <T> void f(X x, T[] y);

    protected GenericArrays(X[] arr) throws IOException {}

    protected abstract <S extends List<X>> S[] get(X[] a);
}
