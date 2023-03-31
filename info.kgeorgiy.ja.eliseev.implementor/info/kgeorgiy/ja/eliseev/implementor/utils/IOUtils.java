package info.kgeorgiy.ja.eliseev.implementor.utils;

import info.kgeorgiy.ja.eliseev.implementor.ImplementationMethodSignature;
import info.kgeorgiy.ja.eliseev.implementor.Implementor;
import info.kgeorgiy.java.advanced.implementor.ImplerException;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.*;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Utility class for working with I/O and compilation.
 *
 * @author Aleksandr Eliseev
 */
public final class IOUtils {
    /**
     * {@link FileVisitor} implementation that recursively deletes directory.
     * Copy of {@code info.kgeorgiy.java.advanced.implementor.BaseImplementorTest.DELETE_VISITOR}.
     */
    public static final FileVisitor<Path> DELETE_VISITOR = new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
        }
    };
    // @code instead of @link,
    // because info.kgeorgiy.java.advanced.implementor.BaseImplementorTest.DELETE_VISITOR is private
    /**
     * Platform-independent path separator used in <var>.jar</var>-files.
     */
    public static final String JAR_PATH_SEPARATOR = "/";
    /**
     * Suffix added to class/interface name by {@link Implementor} to generate implementation.
     */
    public static final String CLASS_NAME_SUFFIX = "Impl";
    /**
     * System line separator used to separate lines in output files.
     */
    public static final String LINE_SEPARATOR = System.lineSeparator();
    /**
     * Java source files extension.
     */
    public static final String JAVA_EXTENSION = ".java";
    /**
     * Java compiled class-files extension.
     */
    public static final String CLASS_EXTENSION = ".class";
    /**
     * Simple manifest used in {@link Implementor#implementJar(Class, Path)} to create <var>.jar</var>-file.
     */
    public static final Manifest MANIFEST;

    static {
        MANIFEST = new Manifest();
        MANIFEST.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        MANIFEST.getMainAttributes().putValue("Created-By", Implementor.class.getCanonicalName());
    }

    /**
     * Private constructor to ensure no instances of utility class would be created.
     */
    private IOUtils() {
    }

    /**
     * Replaces path delimiters with {@value JAR_PATH_SEPARATOR}.
     *
     * @param path path to be converted
     * @return {@link String} representation of converted path
     */
    public static String convertToJarPath(final Path path) {
        final List<String> result = new ArrayList<>();
        path.iterator()
                .forEachRemaining(pathSegment -> result.add(pathSegment.toString()));
        return String.join(JAR_PATH_SEPARATOR, result);
    }

    /**
     * Gets directory for package {@code packageName} in directory {@code root}
     *
     * @param packageName name of the package
     * @param root        root directory
     * @return {@link Path} that represents directory for given package in given root
     */
    private static Path getDirectory(final String packageName, final Path root) {
        final String[] path = packageName.split("\\.");
        return root.resolve(Path.of(".", path));
    }

    /**
     * Creates a directory for package {@code packageName} in directory {@code root}
     * by creating all nonexistent parent directories first.
     *
     * @param packageName name of the package
     * @param root        root directory
     * @throws IOException if directory cannot be created because of I/O error.
     */
    public static void createOutputDirectories(final String packageName, final Path root) throws IOException {
        Files.createDirectories(getDirectory(packageName, root));
    }

    /**
     * Gets path to file, that is in the directory corresponds to {@code token} package in directory {@code root},
     * has same name as {@code token} class and given extension.
     *
     * @param token         class to get package and filename
     * @param root          root directory
     * @param fileExtension extension of result file
     * @return {@link Path} that represents described file
     * @see #getDirectory(String, Path)
     */
    public static Path getFilePath(final Class<?> token, final Path root, final String fileExtension) {
        return getDirectory(token.getPackageName(), root).resolve(
                String.format("%s%s%s", token.getSimpleName(), CLASS_NAME_SUFFIX, fileExtension));
    }

    // @code instead of @link, because it is private

    /**
     * Gets classpath of given class {@code token}.
     * Almost a copy of {@code info.kgeorgiy.java.advanced.implementor.BaseImplementorTest.getClassPath()}.
     *
     * @param token class token to get classpath of
     * @return classpath
     */
    public static String getClassPath(final Class<?> token) {
        try {
            return Path.of(token.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        } catch (final URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Compiles java source file.
     * Almost a copy of {@link info.kgeorgiy.java.advanced.implementor.BaseImplementorTest#compileFiles(Path, List)}.
     *
     * @param file      path to source file
     * @param classPath classpath to be used in compilation
     * @throws ImplerException if {@code jdk.compiler} module is not available, or compilation error occurs
     */
    public static void compile(final String file, final String classPath) throws ImplerException {
        final String[] args = Stream.concat(Stream.of(file), Stream.of(
                "-encoding",
                StandardCharsets.UTF_8.toString(),
                "-cp",
                classPath)
        ).toArray(String[]::new);
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null || compiler.run(null, null, null, args) != 0) {
            throw new ImplerException("Cannot compile generated file");
        }
    }

    /**
     * Recursively deletes directory with all subdirectories.
     * Copy of {@link info.kgeorgiy.java.advanced.implementor.BaseImplementorTest#clean(Path)}
     *
     * @param root directory to be deleted
     * @throws IOException if an I/O error occurs
     */
    public static void clean(final Path root) throws IOException {
        if (Files.exists(root)) {
            Files.walkFileTree(root, DELETE_VISITOR);
        }
    }

    /**
     * Writes package name with {@code package} keyword and line separators.
     * If {@code packageName} is empty, doesn't write anything.
     *
     * @param writer      used to write output text
     * @param packageName package name to be written
     * @throws IOException if an I/O error occurs
     */
    public static void writePackage(final Writer writer, final String packageName) throws IOException {
        if (!packageName.isEmpty()) {
            writer.write("package " + packageName + ";" + LINE_SEPARATOR.repeat(2));
        }
    }

    /**
     * Writes implementation class declaration, including generic parameters and implemented/extended type.
     * Implementation class name would be {@code token.getSimpleName()} with
     * {@value IOUtils#CLASS_NAME_SUFFIX} suffix
     *
     * @param writer used to write output text
     * @param token  class/interface to be implemented
     * @throws IOException if an I/O error occurs
     */
    public static void writeClassDeclaration(final Writer writer, final Class<?> token) throws IOException {
        final TypeVariable<?>[] typeParameters = token.getTypeParameters();
        writer.write("public class " +
                token.getSimpleName() +
                CLASS_NAME_SUFFIX +
                GenericUtils.typeParametersToString(
                        Arrays.asList((TypeVariable<?>[]) token.getTypeParameters()), Map.of()) +
                " " +
                (token.isInterface() ? "implements" : "extends") +
                " " +
                token.getCanonicalName() +
                (typeParameters.length == 0 ? " " : Arrays.stream(typeParameters)
                        .map(TypeVariable::getName)
                        .collect(Collectors.joining(", ", "<", "> "))) +
                "{" +
                LINE_SEPARATOR
        );
    }

    /**
     * Writes implementation of constructor matching given {@code constructor}.
     * Implemented constructor arguments would have names var0, var1, var2 and so on.
     * Implemented constructor throws list would be same as {@code constructor}'s.
     * Implemented constructor would simply call {@code super} constructor with given arguments.
     * Code would be indented for 4 whitespaces from the lines' start and properly formatted.
     *
     * @param writer      used to write output text
     * @param constructor the constructor implementation would match
     * @throws IOException if an I/O error occurs
     */
    public static void writeConstructorImplementation(final Writer writer, final Constructor<?> constructor)
            throws IOException {
        indent(writer);
        final String modifiers = Modifier.toString(constructor.getModifiers() & Modifier.constructorModifiers());
        writer.write(modifiers +
                (modifiers.isEmpty() ? "" : " ") +
                GenericUtils.typeParametersToString(Arrays.asList(constructor.getTypeParameters()), Map.of()) +
                constructor.getDeclaringClass().getSimpleName() +
                "Impl"
        );
        final Set<String> typeParameterNames = Arrays.stream(constructor.getTypeParameters())
                .map(TypeVariable::getName)
                .collect(Collectors.toSet());
        final Type[] types = constructor.getGenericParameterTypes();
        writer.write(IntStream.range(0, types.length).mapToObj(idx -> {
            final Type type = types[idx];
            if (constructor.isVarArgs() && idx == types.length - 1) {
                if (type instanceof final GenericArrayType genericArrayType) {
                    return GenericUtils.typeToStringInContext(Map.of(),
                            genericArrayType.getGenericComponentType(), typeParameterNames) + "... var" + idx;
                } else {
                    return GenericUtils.typeToStringInContext(Map.of(),
                            ((Class<?>) type).getComponentType(), typeParameterNames) + "... var" + idx;
                }
            }
            return GenericUtils.typeToStringInContext(Map.of(), type, typeParameterNames) + " var" + idx;
        }).collect(Collectors.joining(", ", "(", ") ")));
        final Type[] exceptionTypes = constructor.getGenericExceptionTypes();
        if (exceptionTypes.length > 0) {
            writer.write(Arrays.stream(exceptionTypes)
                    .map(exceptionType -> GenericUtils.typeToStringInContext(
                            Map.of(), exceptionType, typeParameterNames))
                    .collect(Collectors.joining(", ", "throws ", " "))
            );
        }
        writer.write("{" + LINE_SEPARATOR);

        indent(writer, 2);
        writer.write(IntStream.range(0, constructor.getParameterCount()).mapToObj(idx -> "var" + idx)
                .collect(Collectors.joining(", ", "super(", ");" + LINE_SEPARATOR)));

        indent(writer);
        writer.write("}" + LINE_SEPARATOR.repeat(2));
    }

    /**
     * Writes implementation of method with given {@code methodSignature}.
     * Implemented method arguments would have names var0, var1, var2 and so on.
     * Implemented method ignores all its arguments and returns default value.
     * Implemented method would have the {@link Override} annotation.
     * Code would be indented for 4 whitespaces from the lines' start and properly formatted.
     *
     * @param writer          used to write output text
     * @param methodSignature signature of method to be implemented
     * @throws IOException if an I/O error occurs
     */
    public static void writeMethodImplementation(
            final Writer writer, final ImplementationMethodSignature methodSignature) throws IOException {
        indent(writer);
        writer.write("@Override" + LINE_SEPARATOR);
        indent(writer);
        writer.write(methodSignature.toString() + LINE_SEPARATOR);
        indent(writer, 2);
        final Class<?> clazz = methodSignature.getReferenceMethod().getReturnType();
        final String returnValue;
        if (clazz.equals(void.class)) {
            returnValue = "";
        } else if (clazz.equals(boolean.class)) {
            returnValue = "false";
        } else if (clazz.isPrimitive()) {
            returnValue = "0";
        } else {
            returnValue = "null";
        }
        if (!returnValue.isEmpty()) {
            writer.write("return " + returnValue + ";" + LINE_SEPARATOR);
            indent(writer);
        }
        writer.write("}" + LINE_SEPARATOR.repeat(2));
    }

    /**
     * Writes indentation for {@code count} level.
     * One indentation level is considered to be 4 whitespaces.
     *
     * @param writer used to write output text
     * @param count  level of indentation
     * @throws IOException if an I/O error occurs
     */
    private static void indent(final Writer writer, final int count) throws IOException {
        writer.write(" ".repeat(4 * count));
    }

    /**
     * Write indentation for one level.
     * Method behaviour is identical as call to {@link #indent(Writer, int)} with arguments {@code writer, 1}
     *
     * @param writer used to write output text
     * @throws IOException if an I/O error occurs
     */
    private static void indent(final Writer writer) throws IOException {
        indent(writer, 1);
    }
}
