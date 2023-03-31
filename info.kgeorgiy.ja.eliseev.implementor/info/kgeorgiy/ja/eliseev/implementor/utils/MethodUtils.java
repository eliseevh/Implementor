package info.kgeorgiy.ja.eliseev.implementor.utils;

import info.kgeorgiy.ja.eliseev.implementor.GenericContexts;
import info.kgeorgiy.ja.eliseev.implementor.ImplementationMethodSignature;
import info.kgeorgiy.java.advanced.implementor.ImplerException;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.TypeVariable;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility class for working with methods inheritance/overriding.
 *
 * @author Aleksandr Eliseev
 */
public final class MethodUtils {
    /**
     * Private constructor to ensure no instances of utility class would be created.
     */
    private MethodUtils() {
    }

    /**
     * Gets signatures of methods needed to be implemented in non-abstract subclass of {@code token}.
     *
     * @param token class/interface to be implemented
     * @return list of signatures of implementation methods
     * @throws ImplerException if {@code token} cannot have non-abstract subclass or
     *                         its subclass' code must have raw types or/and unchecked casts
     */
    public static List<ImplementationMethodSignature> getMethodSignatures(final Class<?> token) throws ImplerException {
        final List<ImplementationMethodSignature> methodSignatures = new ArrayList<>();
        final GenericContexts contexts = GenericUtils.getContexts(token);
        final Set<Set<Method>> quotientSetOfMemberMethods = MethodUtils.getQuotientSetOfMemberMethods(token);
        for (final Set<Method> equivalent : quotientSetOfMemberMethods) {
            if (equivalent.stream().anyMatch(method -> Modifier.isAbstract(method.getModifiers()))) {
                methodSignatures.add(ImplementationMethodSignature.getCommonMethodSignature(equivalent, Arrays.stream(
                        token.getTypeParameters()).map(TypeVariable::getName).collect(Collectors.toSet()), contexts));
            }
        }
        for (final Method method : quotientSetOfMemberMethods.stream().flatMap(Set::stream).toList()) {
            for (final ImplementationMethodSignature methodSignature : methodSignatures) {
                if (methodSignature.getName().equals(method.getName()) && !GenericUtils.isSubSignature(
                        methodSignature.getReferenceMethod(), method, contexts) && GenericUtils.hasSameErasure(
                        methodSignature.getReferenceMethod(), method,
                        contexts.getContext(methodSignature.getReferenceMethod().getDeclaringClass()))) {
                    throw new ImplerException("Cannot implement: subtype cannot be non-abstract");
                }
            }
        }
        Class<?> superClass = token;
        List<Class<?>> superClasses = new ArrayList<>();
        Set<Method> allMethods = getAllMethods(token);
        Map<Class<?>, Set<Method>> inheritedMethods = getInheritedMethods(token, allMethods, contexts);
        while (superClass != null) {
            superClasses.add(superClass);
            if (!superClass.getPackageName().equals(token.getPackageName())) {
                for (Method declaredMethod : superClass.getDeclaredMethods()) {
                    int modifiers = declaredMethod.getModifiers();
                    if (isPackagePrivate(modifiers) && Modifier.isAbstract(modifiers)) {
                        if (allMethods.stream().noneMatch(method -> superClasses.stream().anyMatch(
                                superclass -> isOverridingDeclarationFrom(method, declaredMethod, superclass,
                                                                          inheritedMethods, allMethods, contexts)))) {
                            throw new ImplerException(String.format(
                                    "Cannot generate non-abstract implementation: method %s.%s is abstract and " +
                                            "cannot be overridden.",
                                    superClass.getCanonicalName(), declaredMethod.getName()));
                        }
                    }
                }
            }
            superClass = superClass.getSuperclass();
        }
        return methodSignatures;
    }

    /**
     * Gets quotient set of methods that are members in {@code rootToken} class/interface.
     * Equivalence used for partition is
     * <a href="https://docs.oracle.com/javase/specs/jls/se19/html/jls-8.html#jls-8.4.2">override equivalent</a>
     * (It is equivalence for methods that are members of the same class/interface).
     *
     * @param rootToken class/interface to get methods from
     * @return quotient set by
     * <a href="https://docs.oracle.com/javase/specs/jls/se19/html/jls-8.html#jls-8.4.2">override equivalent</a>
     * equivalence.
     * @see #makeQuotientSet(Set, GenericContexts)
     */
    private static Set<Set<Method>> getQuotientSetOfMemberMethods(final Class<?> rootToken) {
        final GenericContexts contexts = GenericUtils.getContexts(rootToken);
        return makeQuotientSet(
                getMemberMethods(rootToken, getInheritedMethods(rootToken, getAllMethods(rootToken), contexts)),
                contexts);
    }

    /**
     * Makes quotient set from set of methods. Given methods must be members of the same class.
     * Equivalence used for partition is
     * <a href="https://docs.oracle.com/javase/specs/jls/se19/html/jls-8.html#jls-8.4.2">override equivalent</a>
     * (It is equivalence for methods that are members of the same class/interface).
     *
     * @param methods  methods to divide to equivalence classes
     * @param contexts {@link GenericContexts} from which we can get contexts for methods
     * @return quotient set by
     * <a href="https://docs.oracle.com/javase/specs/jls/se19/html/jls-8.html#jls-8.4.2">override equivalent</a>
     * equivalence.
     * @see #getQuotientSetOfMemberMethods(Class)
     */
    private static Set<Set<Method>> makeQuotientSet(final Set<Method> methods, final GenericContexts contexts) {
        final Set<Set<Method>> result = new HashSet<>();
        methodLoop:
        for (final Method method : methods) {
            for (final Set<Method> set : result) {
                if (set.stream().anyMatch(other -> GenericUtils.isOverrideEquivalent(method, other, contexts))) {
                    set.add(method);
                    continue methodLoop;
                }
            }
            final Set<Method> newSet = new HashSet<>();
            newSet.add(method);
            result.add(newSet);
        }
        return result;
    }

    /**
     * Gets all methods declared in {@code token} class/interface or its superclasses/superinterfaces.
     *
     * @param token root class/interface to get methods
     * @return all methods from {@code token} and all its supertypes
     */
    private static Set<Method> getAllMethods(final Class<?> token) {
        if (token == null) {
            return Set.of();
        }
        final Set<Method> result = new HashSet<>(Arrays.asList(token.getDeclaredMethods()));
        for (final Class<?> iface : token.getInterfaces()) {
            result.addAll(getAllMethods(iface));
        }
        result.addAll(getAllMethods(token.getSuperclass()));
        return result;
    }

    /**
     * Gets all methods inherited by {@code token} and all its superclasses/superinterfaces.
     *
     * @param token      type to get inherited methods for
     * @param allMethods set, that contains at least all methods declared in {@code token} and all its supertypes
     * @param contexts   {@link GenericContexts} from which we can get contexts for methods
     * @return mapping from types to set of all methods inherited by type.
     * Contains at least {@code token} and all of its supertypes
     * @see #getAllMethods(Class)
     */
    private static Map<Class<?>, Set<Method>> getInheritedMethods(final Class<?> token, final Set<Method> allMethods,
                                                                  final GenericContexts contexts) {
        // Object and primitives don't have inherited methods
        // All classes have superclasses so later token.getSuperclass() is not null if token.isInterface() is false
        if (token == null || token == Object.class || token.isPrimitive()) {
            final Map<Class<?>, Set<Method>> result = new HashMap<>();
            result.put(token, new HashSet<>());
            return result;
        }
        final Map<Class<?>, Set<Method>> result = getInheritedMethods(token.getSuperclass(), allMethods, contexts);
        for (final Class<?> iface : token.getInterfaces()) {
            for (final Map.Entry<Class<?>, Set<Method>> entry : getInheritedMethods(iface, allMethods,
                                                                                    contexts).entrySet()) {
                result.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        final Set<Method> methods = new HashSet<>();
        final List<Method> tokenDeclaredMethods = Arrays.asList(token.getDeclaredMethods());
        if (token.isInterface()) {
            for (final Class<?> directInterface : token.getInterfaces()) {
                for (final Method superinterfaceMember : getMemberMethods(directInterface, result)) {
                    if (!superinterfaceMember.isDefault() && !Modifier.isAbstract(
                            superinterfaceMember.getModifiers())) {
                        continue;
                    }
                    if (tokenDeclaredMethods.stream().noneMatch(
                            method -> GenericUtils.isSubSignature(method, superinterfaceMember, contexts)) &&
                            // There exists no method m' that is a member of a direct superinterface,
                            // J', of I (m distinct from m', J distinct from J'), such that m' overrides from J'
                            // the declaration of the method m.

                            // In our case, I = token, J = directInterface,
                            // m = superinterfaceMember we are searching for J'
                            // over direct superinterfaces of I, excluding J, searching if any of their
                            // member method overrides m from J'
                            Arrays.stream(token.getInterfaces())
                                  .filter(Predicate.not(Predicate.isEqual(directInterface))).allMatch(
                                          anotherDirectInterface -> getMemberMethods(anotherDirectInterface, result).stream()
                                                                                                                    .noneMatch(
                                                                                                                            method -> isOverridingDeclarationFrom(
                                                                                                                                    method,
                                                                                                                                    superinterfaceMember,
                                                                                                                                    anotherDirectInterface,
                                                                                                                                    result,
                                                                                                                                    allMethods,
                                                                                                                                    contexts)))) {
                        methods.add(superinterfaceMember);
                    }
                }
            }
        } else {
            final Set<Method> concreteInherited = new HashSet<>();
            final Set<Method> superclassMembers = getMemberMethods(token.getSuperclass(), result);
            // first, add only concrete methods of superclass
            for (final Method superclassMember : superclassMembers) {
                if (!Modifier.isAbstract(superclassMember.getModifiers())) {
                    if (isAccessibleFrom(superclassMember, token) && tokenDeclaredMethods.stream().noneMatch(
                            method -> GenericUtils.isSubSignature(method, superclassMember, contexts))) {
                        concreteInherited.add(superclassMember);
                    }
                }
            }
            methods.addAll(concreteInherited);
            // then add abstract methods from superclass
            for (final Method superclassMember : superclassMembers) {
                if (Modifier.isAbstract(superclassMember.getModifiers())) {
                    if (isAccessibleFrom(superclassMember, token) && tokenDeclaredMethods.stream().noneMatch(
                            method -> GenericUtils.isSubSignature(method, superclassMember,
                                                                  contexts)) && concreteInherited.stream().noneMatch(
                            method -> GenericUtils.isSubSignature(method, superclassMember, contexts))) {
                        methods.add(superclassMember);
                    }
                }
            }
            // then add abstract and default methods from superinterfaces
            for (final Class<?> directInterface : token.getInterfaces()) {
                for (final Method superinterfaceMember : getMemberMethods(directInterface, result)) {
                    if (!superinterfaceMember.isDefault() && !Modifier.isAbstract(
                            superinterfaceMember.getModifiers())) {
                        continue;
                    }
                    if (tokenDeclaredMethods.stream().noneMatch(
                            method -> GenericUtils.isSubSignature(method, superinterfaceMember,
                                                                  contexts)) && concreteInherited.stream().noneMatch(
                            method -> GenericUtils.isSubSignature(method, superinterfaceMember, contexts)) &&
                            // There exists no method m' that is a member of the direct superclass
                            // or a direct superinterface, D', of C (m distinct from m', D distinct from D'),
                            // such that m' from D' overrides the declaration of the method m.

                            // In our case, C = token, D = directInterface, m = superinterfaceMember,
                            // we are searching for D' over direct superinterfaces and superclass of C,
                            // excluding D, searching if any of their member method overrides m from D'
                            Stream.concat(Stream.of(token.getSuperclass()), Arrays.stream(token.getInterfaces()))
                                  .filter(Predicate.not(Predicate.isEqual(directInterface)))
                                  .allMatch(anotherDirectInterface -> {
                                      final Set<Method> thisMemberMethods = getMemberMethods(anotherDirectInterface,
                                                                                             result);
                                      return thisMemberMethods.stream().noneMatch(
                                              method -> isOverridingDeclarationFrom(method, superinterfaceMember,
                                                                                    anotherDirectInterface, result,
                                                                                    allMethods, contexts));
                                  })) {
                        methods.add(superinterfaceMember);
                    }
                }
            }
        }
        result.put(token, methods);
        return result;
    }

    /**
     * Checks if {@code method} is accessible from {@code token}.
     *
     * @param method method to check accessibility of
     * @param token  type to check accessibility from
     * @return {@code true} if and only if {@code method} is accessible from {@code token}
     */
    private static boolean isAccessibleFrom(final Method method, final Class<?> token) {
        final int modifiers = method.getModifiers();
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers) || (isPackagePrivate(
                modifiers) && method.getDeclaringClass().getPackageName()
                                    .equals(token.getPackageName())) || (Modifier.isPrivate(
                modifiers) && method.getDeclaringClass() == token);
    }

    /**
     * Gets all member methods of {@code token}.
     * Member methods are either methods inherited or declared in class/interface.
     *
     * @param token               type to get methods from
     * @param inheritedMethodsMap mapping from types to set of methods inherited by that type.
     *                            Must contain {@code token} as a key
     * @return all methods that are members of {@code token} class/interface
     * @see #getInheritedMethods(Class, Set, GenericContexts)
     */
    private static Set<Method> getMemberMethods(final Class<?> token,
                                                final Map<Class<?>, Set<Method>> inheritedMethodsMap) {
        final Set<Method> result = new HashSet<>(Arrays.asList(token.getDeclaredMethods()));
        result.addAll(inheritedMethodsMap.get(token));
        return result;
    }

    /**
     * Checks if method {@code overriding} overrides method {@code overridden} from class/interface {@code token}.
     *
     * @param overriding       method to check if overrides
     * @param overridden       method to check if overridden
     * @param from             class/interface to check point of overriding
     * @param inheritedMethods mapping from types to set of methods inherited by that type.
     *                         Must contain {@code from} and all of its supertypes as a keys
     * @param allMethods       set, that contains at least all methods declared in {@code from} and all its supertypes
     * @param contexts         {@link GenericContexts} from which we can get contexts for methods
     * @return {@code true} if and only if {@code overriding} overrides the {@code overridden} method from {@code token}
     * @see #getInheritedMethods(Class, Set, GenericContexts)
     * @see #getAllMethods(Class)
     * @see <a href="https://docs.oracle.com/javase/specs/jls/se19/html/jls-8.html#jls-8.4.8.1">Overriding
     * from class</a>
     * @see <a href="https://docs.oracle.com/javase/specs/jls/se19/html/jls-9.html#jls-9.4.1.1">
     * Overriding from interface</a>
     */
    private static boolean isOverridingDeclarationFrom(final Method overriding, final Method overridden, final Class<
            ?> from, final Map<Class<?>, Set<Method>> inheritedMethods, final Set<Method> allMethods,
                                                       final GenericContexts contexts) {
        // Object and primitives has no superclasses and superinterfaces,
        // so method cannot override something from Object.
        // All classes have superclasses so later from.getSuperclass() is not null if from.isInterface() is false
        if (from == Object.class || from.isPrimitive()) {
            return false;
        }
        final Class<?> overriddenDeclaringClass = overridden.getDeclaringClass();
        if (from == overriddenDeclaringClass) {
            return false;
        }
        if (!getMemberMethods(from, inheritedMethods).contains(overriding) || Modifier.isStatic(
                overriding.getModifiers()) || !overriddenDeclaringClass.isAssignableFrom(
                from) || !GenericUtils.isSubSignature(overriding, overridden, contexts)) {
            return false;
        } else if (overriddenDeclaringClass.isInterface()) {
            if (from.isInterface()) {
                return true;
            } else {
                return overridden.isDefault() || Modifier.isAbstract(overridden.getModifiers());
            }
        } else {
            if (from.isInterface()) {
                // Method declared in class cannot be overridden from interface
                return false;
            } else {
                if (inheritedMethods.get(from).contains(overridden)) {
                    return false;
                } else {
                    final int overriddenModifiers = overridden.getModifiers();
                    if (Modifier.isPublic(overriddenModifiers) || Modifier.isProtected(overriddenModifiers)) {
                        return true;
                    } else if (Modifier.isPrivate(overriddenModifiers)) {
                        return false;
                    } else {
                        // package access

                        if (overridden.getDeclaringClass().getPackageName()
                                      .equals(from.getPackageName()) && (overriding.getDeclaringClass() == from || getMemberMethods(
                                from.getSuperclass(), inheritedMethods).contains(overridden))) {
                            return true;
                        } else {
                            Class<?> superclass = from.getSuperclass();
                            while (superclass != null) {
                                if (isOverridingDeclarationFrom(overriding, overridden, superclass, inheritedMethods,
                                                                allMethods, contexts)) {
                                    return true;
                                }
                                for (final Method method : allMethods) {
                                    if (!method.equals(overridden) && !method.equals(
                                            overriding) && isOverridingDeclarationFrom(overriding, method, from,
                                                                                       inheritedMethods, allMethods,
                                                                                       contexts) && isOverridingDeclarationFrom(
                                            method, overridden, superclass, inheritedMethods, allMethods, contexts)) {
                                        return true;
                                    }
                                }
                                superclass = superclass.getSuperclass();
                            }
                            return false;
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks if {@code modifier} represents package-private access.
     *
     * @param modifiers - a set of modifiers
     * @return {@code true} if and only if {@code modifiers} don't include {@code private, public, protected} modifiers
     * @see Modifier#isPrivate(int)
     * @see Modifier#isPublic(int)
     * @see Modifier#isProtected(int)
     */
    private static boolean isPackagePrivate(final int modifiers) {
        return !(Modifier.isPrivate(modifiers) || Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers));
    }
}

