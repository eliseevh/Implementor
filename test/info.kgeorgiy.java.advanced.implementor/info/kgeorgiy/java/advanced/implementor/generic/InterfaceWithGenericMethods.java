package info.kgeorgiy.java.advanced.implementor.generic;

public interface InterfaceWithGenericMethods {
    <T> int method1(T first, T second);
    <T1, T2, T3> T1 method2(T2 first, T3 second);
}
