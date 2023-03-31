package info.kgeorgiy.java.advanced.implementor.generic;

public class GenericThrowsConstructor<T extends Exception> {
    public <E extends Throwable> GenericThrowsConstructor() throws E, T {

    }
}
