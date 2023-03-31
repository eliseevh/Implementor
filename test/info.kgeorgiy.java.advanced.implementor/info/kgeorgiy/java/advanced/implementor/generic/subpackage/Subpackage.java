package info.kgeorgiy.java.advanced.implementor.generic.subpackage;

public class Subpackage {
    public abstract static class A {
        abstract int x(int a);
    }
    interface C<T> {
        T id(T x);
    }


    interface I<T extends Number> {
        T id(T x);
    }

    public abstract static class AC implements C<Number>, I<Integer> {}

}
