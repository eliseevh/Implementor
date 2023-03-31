package info.kgeorgiy.java.advanced.implementor.generic;

import info.kgeorgiy.java.advanced.implementor.generic.subpackage.Subpackage;

import java.io.Serializable;
import java.util.List;

public class DeclaringClass {

    public abstract static class Parent {
        protected abstract String getValue();
    }

    public interface InterfaceParent<T> {
        default <R extends Serializable> T getValue() {
            return null;
        }
    }

    public interface OtherInterface {
        <T extends String, Q, S> Serializable getValue();
    }

    public abstract static class StringChild extends Parent implements InterfaceParent<String>, OtherInterface {
//        @Override
//        public String getValue() {
//            return InterfaceParent.super.getValue();
//        }
    }

    public interface A {
        String get();
    }
    public interface B {
        <T extends String, Q extends List<T>> T get();
    }
    public abstract static class C implements A, B {
    }

    public interface X {
        <T extends C & Serializable> C a();
    }
    public interface Y {
        <T extends C & Serializable> Serializable a();
    }
    public interface Z {
        <T extends C & Serializable> T a();
    }
    public abstract static class U implements X, Y, Z {

        @Override
        public <T extends C & Serializable> T a() {
            return null;
        }
//        @Override
//        public  a() {
//            return null;
//        }
    }


    public interface XX {
        <T> Object a();
    }

    public interface YY {
        <T> T[] a();
    }

    public static abstract class ZZ implements XX, YY {
//        public abstract <T> T[] a();
    }

    public static abstract class ExtendsPackagePrivate extends Subpackage.A {}
}