package info.kgeorgiy.java.advanced.implementor.generic;

import info.kgeorgiy.java.advanced.implementor.Impler;
import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.InterfaceJarImplementorTest;

import java.nio.file.Path;

/**
 * @author Aleksandr Eliseev
 */
public class GenericJarImplementorTest extends GenericImplementorTest {
    @Override
    protected void implement(final Path root, final Impler implementor, final Class<?> clazz) throws ImplerException {
        super.implement(root, implementor, clazz);
        InterfaceJarImplementorTest.implementJar(root, implementor, clazz);
    }
}
