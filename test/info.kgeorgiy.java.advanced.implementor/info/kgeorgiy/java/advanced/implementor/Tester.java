package info.kgeorgiy.java.advanced.implementor;

import info.kgeorgiy.java.advanced.base.BaseTester;
import info.kgeorgiy.java.advanced.implementor.generic.GenericImplementorTest;
import info.kgeorgiy.java.advanced.implementor.generic.GenericJarImplementorTest;

/**
 * @author Georgiy Korneev (kgeorgiy@kgeorgiy.info)
 */
public final class Tester {
    private Tester() {
    }

    public static void main(final String... args) {
        new BaseTester()
                .add("interface", InterfaceImplementorTest.class)
                .add("class", ClassImplementorTest.class)
                .add("advanced", AdvancedImplementorTest.class)
                .add("covariant", CovariantImplementorTest.class)
                .add("generic", GenericImplementorTest.class) // Added by Aleksandr Eliseev
                .add("jar-interface", InterfaceJarImplementorTest.class)
                .add("jar-class", ClassJarImplementorTest.class)
                .add("jar-advanced", AdvancedJarImplementorTest.class)
                .add("jar-generic", GenericJarImplementorTest.class) // Added by Aleksandr Eliseev
                .run(args);
    }
}
