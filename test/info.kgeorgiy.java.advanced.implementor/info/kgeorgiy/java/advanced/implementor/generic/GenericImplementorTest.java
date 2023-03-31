package info.kgeorgiy.java.advanced.implementor.generic;

import info.kgeorgiy.java.advanced.implementor.CovariantImplementorTest;
import info.kgeorgiy.java.advanced.implementor.generic.subpackage.Subpackage;
import org.junit.Test;

/**
 * My tests for bonus version of
 * <a href="https://www.kgeorgiy.info/courses/java-advanced/homeworks.html#homework-implementor">Implementor</a>
 * homework. Official tests is kept under secret, but here are tests I used in process of doing this homework.
 *
 * @author Aleksandr Eliseev
 */
public class GenericImplementorTest extends CovariantImplementorTest {
    @Test
    public void myGenericTest() {
        test(true, DeclaringClass.C.class);
        test(true, Subpackage.AC.class);
        test(true, DeclaringClass.ExtendsPackagePrivate.class);
        test(false, ChangeNamesAB.class, ChangeNameBA.class, Dependency.class, Dependent.class, DollarsIn$Name$$.class,
             DollarsIn$Name$$.$Inner$Cla$$.class, DollarsIn$Name$$.SSSS.class, GenericArrays.class,
             GenericInterface.class, GenericInterfaceWithGenericMethods.class, GenericsWithBounds.class,
             GenericThrowsConstructor.class, InterfaceWithGenericMethods.class, RealTypeInGenericArgument.class,
             DeclaringClass.StringChild.class, DeclaringClass.ZZ.class, DeclaringClass.U.class);
    }
}
