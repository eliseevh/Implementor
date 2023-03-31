/**
 * Implementation of
 * <a href="https://www.kgeorgiy.info/courses/java-advanced/homeworks.html#implementor">implementor</a> homework of
 * <a href="https://www.kgeorgiy.info/courses/java-advanced/">Java Advanced</a> course.
 *
 * @author Aleksandr Eliseev
 */
open module info.kgeorgiy.ja.eliseev.implementor {
    requires transitive info.kgeorgiy.java.advanced.implementor;
    requires java.compiler;
    exports info.kgeorgiy.ja.eliseev.implementor;
}

