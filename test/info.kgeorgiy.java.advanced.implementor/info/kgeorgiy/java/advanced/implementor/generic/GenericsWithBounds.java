package info.kgeorgiy.java.advanced.implementor.generic;

public interface GenericsWithBounds<T extends Number & Comparable<? super T>, V extends T> {
    T max(T first, T second, V third);
}
