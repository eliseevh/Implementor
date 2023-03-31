package info.kgeorgiy.ja.eliseev.implementor;

import info.kgeorgiy.ja.eliseev.implementor.utils.*;
import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.reflect.*;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

/**
 * Implementation of {@link JarImpler} interface.
 *
 * @author Aleksandr Eliseev
 */
public class Implementor implements JarImpler {
    /**
     * Implements given class, creates jar if needed.
     * Usage: java info.kgeorgiy.ja.eliseev.implementor.Implementor [-jar &lt;jar-name&gt;] &lt;class/interface name&gt;
     *
     * @param args command-line arguments.
     */
    public static void main(final String[] args) {
        if (args == null) {
            System.err.println("Error: Args array must be non-null");
            return;
        }
        try {
            if (args.length == 3) {
                if ("-jar".equals(args[0])) {
                    if (args[1] == null) {
                        System.err.println("Error: Jar file name must be non-null");
                        return;
                    }
                    if (args[2] == null) {
                        System.err.println("Error: Class/interface name must be non-null");
                        return;
                    }

                    final Path jarFile;
                    try {
                        jarFile = Path.of(args[1]);
                    } catch (final InvalidPathException e) {
                        System.err.println("Error: Jar file name is not a valid path: " + e.getMessage());
                        return;
                    }
                    final Class<?> token = ClassLoader.getSystemClassLoader().loadClass(args[2]);
                    new Implementor().implementJar(token, jarFile);
                } else {
                    System.err.println("Usage: \"Implementor -jar <jar-name> <class/interface name>\" " +
                            "or \"Implementor <class/interface name>\"");
                }
            } else if (args.length == 1) {
                if (args[0] == null) {
                    System.err.println("Error: Class/interface name must be non-null");
                    return;
                }
                final Class<?> token = ClassLoader.getSystemClassLoader().loadClass(args[0]);
                final Path root = Path.of(".");
                new Implementor().implement(token, root);
            } else {
                System.err.println("Usage: \"Implementor -jar <jar-name> <class/interface name>\" " +
                        "or \"Implementor <class/interface name>\"");
            }
        } catch (final ClassNotFoundException e) {
            System.err.println("Error: Class not found: " + e.getMessage());
        } catch (final ImplerException e) {
            System.err.println("Error: Cannot generate class: " + e.getMessage());
        }
    }


    /**
     * @throws ImplerException if
     *                         <ul>
     *                         <li>{@code token} class cannot be extended/implemented by non-abstract class or/and
     *                         without using raw types or/and without unchecked casts</li>
     *                         <li>{@code token} class cannot have subclass because of java language rules</li>
     *                         <li>{@code token} class' subclass cannot have same name as <var>.java</var>
     *                         file that contains it</li>
     *                         <li>an I/O error occurs</li>
     *                         </ul>
     */
    @Override
    public void implement(final Class<?> token, final Path root) throws ImplerException {
        final int modifiers = token.getModifiers();
        if (Modifier.isPrivate(modifiers)) {
            throw new ImplerException("Cannot implement private interface or extends private class");
        }
        if (Modifier.isFinal(modifiers)) {
            throw new ImplerException("Cannot extend final class");
        }
        if (token.equals(Enum.class)) {
            throw new ImplerException("Cannot extend java.lang.Enum");
        }

        if (token.getDeclaringClass() != null && !Modifier.isStatic(modifiers)) {
            throw new ImplerException("Cannot implement non-static inner class");
        }

        final Constructor<?> constructor;
        if (!token.isInterface()) {
            constructor = Arrays.stream(token.getDeclaredConstructors())
                    .filter(constr -> !Modifier.isPrivate(constr.getModifiers()))
                    .findAny()
                    .orElseThrow(() -> new ImplerException("Cannot implement class with only private constructors"));
        } else {
            constructor = null;
        }

        final List<ImplementationMethodSignature> methodSignatures = MethodUtils.getMethodSignatures(token);

        final String packageName = token.getPackageName();
        try {
            IOUtils.createOutputDirectories(packageName, root);
        } catch (final IOException e) {
            System.err.println("Error: Cannot create output directory: " + e.getMessage()
                    + IOUtils.LINE_SEPARATOR + "Trying to open output file.");
        }

        final Path output = IOUtils.getFilePath(token, root, IOUtils.JAVA_EXTENSION);
        try (final Writer writer = Files.newBufferedWriter(output)) {
            IOUtils.writePackage(writer, packageName);
            IOUtils.writeClassDeclaration(writer, token);

            if (constructor != null) {
                IOUtils.writeConstructorImplementation(writer, constructor);
            }
            methodSignatures.forEach(IOConsumer.makeUnchecked(
                    method -> IOUtils.writeMethodImplementation(writer, method)
            ));

            writer.write("}" + IOUtils.LINE_SEPARATOR);
        } catch (final IOException | UncheckedIOException e) {
            throw new ImplerException("Cannot write to output file", e);
        }
    }

    /**
     * @throws ImplerException if
     *                         <ul>
     *                         <li>error occurs when trying to create temporary directory</li>
     *                         <li>{@code token} class cannot be extended/implemented with
     *                         {@link #implement(Class, Path)} method</li>
     *                         <li>an I/O error occurs during implementation</li>
     *                         <li>{@code jdk.compiler} module is not available</li>
     *                         <li>error occurs when compiling generated implementation</li>
     *                         <li>an I/O error occurs during <var>.jar</var> file creation</li>
     *                         </ul>
     */
    @Override
    public void implementJar(final Class<?> token, final Path jarFile) throws ImplerException {
        final Path compilationDir = jarFile.getParent().resolve("compiled");
        try {
            Files.createDirectory(compilationDir);
        } catch (final FileAlreadyExistsException ignored) {
        } catch (final IOException e) {
            throw new ImplerException("Cannot create directory for compilation", e);
        }
        try {
            implement(token, compilationDir);

            IOUtils.compile(IOUtils.getFilePath(token, compilationDir, IOUtils.JAVA_EXTENSION).toString(),
                    compilationDir + File.pathSeparator + IOUtils.getClassPath(token));

            final Path classFilePath = IOUtils.getFilePath(token, compilationDir, IOUtils.CLASS_EXTENSION);

            try (final JarOutputStream jarOutputStream =
                         new JarOutputStream(Files.newOutputStream(jarFile), IOUtils.MANIFEST)) {
                jarOutputStream.putNextEntry(
                        new ZipEntry(IOUtils.convertToJarPath(compilationDir.relativize(classFilePath))));
                Files.copy(classFilePath, jarOutputStream);
            } catch (final IOException e) {
                throw new ImplerException("Cannot write jar file", e);
            }

        } finally {
            try {
                IOUtils.clean(compilationDir);
            } catch (final IOException e) {
                System.err.println("Cannot clean compilation directory: " + e.getMessage());
            }
        }
    }
}
