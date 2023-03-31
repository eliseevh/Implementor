package info.kgeorgiy.java.advanced.implementor.generic;

public abstract class Dependent<A, B> implements Dependency<A, B> {
    abstract A y(B b);
}
