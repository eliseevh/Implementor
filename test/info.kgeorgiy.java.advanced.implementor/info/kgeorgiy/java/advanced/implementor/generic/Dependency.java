package info.kgeorgiy.java.advanced.implementor.generic;

public interface Dependency<T, R> {
    <R> R get(T a, R b);
    <A extends R> A x();
}
