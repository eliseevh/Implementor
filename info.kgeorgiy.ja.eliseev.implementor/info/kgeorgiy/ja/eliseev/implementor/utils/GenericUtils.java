package info.kgeorgiy.ja.eliseev.implementor.utils;

import info.kgeorgiy.ja.eliseev.implementor.GenericContexts;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility class for working with generic types and method signatures.
 *
 * @author Aleksandr Eliseev
 */
public final class GenericUtils {
    /**
     * Private constructor to ensure no instances of utility class would be created.
     */
    private GenericUtils() {
    }

    /**
     * Converts {@code type} to {@link String}, but using {@code context} to substitute types' names.
     *
     * @param context context used to substitute types' names
     * @param type    type to be converted
     * @param ignore  names of types that should not be converted using {@code context}, but left as is
     * @return {@link String} representation of {@code type} in {@code context}
     */
    public static String typeToStringInContext(
            final Map<String, Type> context, final Type type, final Set<String> ignore) {
        final Function<Type, String> recursiveCall = type_ -> typeToStringInContext(context, type_, ignore);

        if (ignore.contains(type.getTypeName())) {
            return type.getTypeName();
        }

        if (type instanceof final ParameterizedType parameterizedType) {
            final StringBuilder result = new StringBuilder(
                    ((Class<?>) parameterizedType.getRawType()).getCanonicalName());
            final Type[] arguments = parameterizedType.getActualTypeArguments();
            if (arguments.length != 0) {
                result.append(Arrays.stream(arguments)
                        .map(recursiveCall)
                        .collect(Collectors.joining(", ", "<", ">")));
            }
            return result.toString();
        } else if (type instanceof final TypeVariable<?> typeVariable) {
            if (context.containsKey(typeVariable.getName())) {
                final Type replacement = context.get(typeVariable.getName());
                // If replacement is TypeVariable shouldn't make recursive call,
                // because no need to replace it second time
                if (replacement instanceof final TypeVariable<?> variable) {
                    return variable.getName();
                }
                return recursiveCall.apply(replacement);
            }
            return typeVariable.getName();
        } else if (type instanceof final WildcardType wildcard) {
            final StringBuilder result = new StringBuilder("?");
            final Type[] lowerBounds = wildcard.getLowerBounds();
            final Type[] upperBounds = wildcard.getUpperBounds();
            if (lowerBounds.length != 0) {
                result.append(Arrays.stream(lowerBounds)
                        .map(recursiveCall)
                        .collect(Collectors.joining(" & ", " super ", "")));
            } else if (upperBounds.length != 0 && !(upperBounds.length == 1 && upperBounds[0] == Object.class)) {
                result.append(Arrays.stream(upperBounds)
                        .map(recursiveCall)
                        .collect(Collectors.joining(" & ", " extends ", "")));
            }
            return result.toString();
        } else if (type instanceof final GenericArrayType arrayType) {
            return (recursiveCall.apply(arrayType.getGenericComponentType()) + "[]");
        } else if (type instanceof final Class<?> clazz) {
            return clazz.getCanonicalName();
        } else {
            throw new AssertionError("Unreachable");
        }
    }

    /**
     * Gets <a href="https://docs.oracle.com/javase/specs/jls/se19/html/jls-4.html#jls-4.6">type erasure</a>
     * of given {@code type} using {@code context} to substitute type variables.
     *
     * @param type        type to be erased
     * @param context     context used to substitute types;
     * @param ignoreTypes names of types that should not be converted using {@code context}, but left as is.
     * @return type erasure of {@code type}
     */
    private static Class<?> getTypeErasure(
            final Type type, final Map<String, Type> context, final Set<String> ignoreTypes) {
        if (type instanceof final ParameterizedType parameterizedType) {
            return getTypeErasure(parameterizedType.getRawType(), context, ignoreTypes);
        } else if (type instanceof final GenericArrayType arrayType) {
            return getTypeErasure(arrayType.getGenericComponentType(), context, ignoreTypes).arrayType();
        } else if (type instanceof final TypeVariable<?> typeVariable) {
            if (context.containsKey(typeVariable.getName()) && !ignoreTypes.contains(typeVariable.getName())) {
                final Type replacement = context.get(typeVariable.getName());
                if (replacement instanceof final TypeVariable<?> tv) {
                    return getTypeErasure(tv.getBounds()[0], Map.of(), Set.of());
                }
                return getTypeErasure(replacement, context, ignoreTypes);
            }
            return getTypeErasure(typeVariable.getBounds()[0], context, ignoreTypes);
        } else if (type instanceof final Class<?> clazz) {
            return clazz;
        } else {
            throw new AssertionError("Unreachable");
        }

    }

    /**
     * Gets parameters' types of
     * <a href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-4.html#jls-4.6">erasure</a>
     * of {@code method}'s signature.
     *
     * @param method method to get erased signature
     * @return {@code method}'s signature erasure
     * @see #getMethodErasure(Method, Map, Set)
     */
    private static Class<?>[] getMethodErasure(final Method method) {
        return getMethodErasure(method, Map.of(), Set.of());
    }

    /**
     * Gets parameters' types of
     * <a href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-4.html#jls-4.6">erasure</a>
     * of {@code method}'s signature using {@code context} to substitute type variables.
     *
     * @param method      method to get erased signature
     * @param context     context used to substitute types
     * @param ignoreTypes names of types that should not be converted using {@code context}, but left as is
     * @return {@code method}'s signature erasure
     * @see #getMethodErasure(Method)
     * @see #getTypeErasure(Type, Map, Set)
     */
    private static Class<?>[] getMethodErasure(
            final Method method, final Map<String, Type> context, final Set<String> ignoreTypes) {
        return Arrays.stream(method.getGenericParameterTypes())
                .map(type -> getTypeErasure(type, context, ignoreTypes))
                .toArray(Class[]::new);
    }

    /**
     * Checks if {@code method1}'s
     * <a href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-4.html#jls-4.6">signature erasure</a>
     * is same as {@code method2}'s.
     * {@code method1}'s parameters types are substituted using {@code method1Context}.
     *
     * @param method1        first method
     * @param method2        second method
     * @param method1Context context used to substitute types in first method
     * @return {@code true} if and only if methods' signatures have same erasures
     * @see #getMethodErasure(Method, Map, Set)
     */
    public static boolean hasSameErasure(
            final Method method1, final Method method2, final Map<String, Type> method1Context) {
        return method1.getName().equals(method2.getName()) &&
                Arrays.equals(getMethodErasure(method1,
                                method1Context,
                                Arrays.stream(method1.getTypeParameters())
                                        .map(TypeVariable::getName)
                                        .collect(Collectors.toSet())),
                        getMethodErasure(method2)
                );
    }

    /**
     * Checks if {@code method1} and {@code method2} have the
     * <a href="https://docs.oracle.com/javase/specs/jls/se19/html/jls-8.html#jls-8.4.2">same signature</a>
     *
     * @param method1  first method
     * @param method2  second method
     * @param contexts {@link GenericContexts} from which we can get contexts for methods
     * @return true if and only if methods have the same signature
     */
    private static boolean isSameSignature(final Method method1, final Method method2, final GenericContexts contexts) {
        if (!method1.getName().equals(method2.getName())) {
            return false;
        }
        final Type[] method1ParametersTypes = method1.getGenericParameterTypes();
        final Type[] method2ParametersTypes = method2.getGenericParameterTypes();
        if (method1ParametersTypes.length != method2ParametersTypes.length) {
            return false;
        }

        final TypeVariable<?>[] method1TypeParameters = method1.getTypeParameters();
        final TypeVariable<?>[] method2TypeParameters = method2.getTypeParameters();
        if (method1TypeParameters.length != method2TypeParameters.length) {
            return false;
        }

        final Map<String, Type> method1Context = contexts.getContext(method1.getDeclaringClass());
        // need copy because map would be changed
        final Map<String, Type> method2Context = new HashMap<>(contexts.getContext(method2.getDeclaringClass()));

        final Set<String> method1Ignore = Arrays.stream(method1TypeParameters)
                .map(TypeVariable::getName)
                .collect(Collectors.toSet());

        for (int i = 0; i < method1TypeParameters.length; i++) {
            method2Context.put(method2TypeParameters[i].getName(), method1TypeParameters[i]);
        }

        for (int i = 0; i < method1TypeParameters.length; i++) {
            final TypeVariable<?> method1TypeParameter = method1TypeParameters[i];
            final TypeVariable<?> method2TypeParameter = method2TypeParameters[i];
            final Type[] method1TypeParameterBounds = method1TypeParameter.getBounds();
            final Type[] method2TypeParameterBounds = method2TypeParameter.getBounds();

            if (method1TypeParameterBounds.length != method2TypeParameterBounds.length) {
                return false;
            }

            for (int j = 0; j < method1TypeParameterBounds.length; j++) {
                final String method1TypeBound = typeToStringInContext(method1Context, method1TypeParameterBounds[j],
                        method1Ignore);
                final String method2TypeBound = typeToStringInContext(method2Context, method2TypeParameterBounds[j],
                        Set.of());

                if (!method1TypeBound.equals(method2TypeBound)) {
                    return false;
                }
            }
        }

        for (int i = 0; i < method1ParametersTypes.length; i++) {
            final Type method1Parameter = method1ParametersTypes[i];
            final Type method2Parameter = method2ParametersTypes[i];

            final String method1ParameterName = typeToStringInContext(method1Context, method1Parameter, method1Ignore);
            final String method2ParameterName = typeToStringInContext(method2Context, method2Parameter, Set.of());

            if (!method1ParameterName.equals(method2ParameterName)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Checks if {@code method1}'s signature is
     * <a href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-8.html#jls-8.4.2">subsignature</a>
     * of {@code method2}'s signature.
     *
     * @param method1  first method
     * @param method2  second method
     * @param contexts {@link GenericContexts} from which we can get contexts for methods
     * @return {@code true} is and only if method1's signature is subsignature of method2's signature
     * @see #isSameSignature(Method, Method, GenericContexts)
     */
    public static boolean isSubSignature(final Method method1, final Method method2, final GenericContexts contexts) {
        if (!method1.getName().equals(method2.getName())) {
            return false;
        }
        if (isSameSignature(method1, method2, contexts)) {
            return true;
        }
        final Type[] method1ParameterTypes = method1.getGenericParameterTypes();
        final Type[] method2ErasedParameterTypes = getMethodErasure(method2);
        return Arrays.equals(method1ParameterTypes, method2ErasedParameterTypes) &&
                method1.getTypeParameters().length == 0;
    }

    /**
     * Checks if {@code method1} and {@code method2} are
     * <a href=https://docs.oracle.com/javase/specs/jls/se19/html/jls-8.html#jls-8.4.2>override-equivalent</a>.
     *
     * @param method1  first method
     * @param method2  second method
     * @param contexts {@link GenericContexts} from which we can get contexts for methods
     * @return {@code true} if and only if methods are override-equivalent
     * @see #isSameSignature(Method, Method, GenericContexts)
     * @see #isSubSignature(Method, Method, GenericContexts)
     */
    public static boolean isOverrideEquivalent(
            final Method method1, final Method method2, final GenericContexts contexts) {
        return isSubSignature(method1, method2, contexts) ||
                isSubSignature(method2, method1, contexts);
    }

    /**
     * Checks if {@code type} is a <a href="https://docs.oracle.com/javase/specs/jls/se19/html/jls-4.html#jls-4.8">
     * raw type</a>.
     *
     * @param type type to check
     * @return {@code true} if and only of {@code type} is a raw type
     */
    public static boolean isRawType(final Type type) {
        return type instanceof final Class<?> clazz &&
                (clazz.getTypeParameters().length > 0 ||
                        (clazz.isArray() && isRawType(clazz.getComponentType())) ||
                        (!Modifier.isStatic(clazz.getModifiers()) &&
                                clazz.getDeclaringClass() != null &&
                                isRawType(clazz.getDeclaringClass()))
                );
    }

    /**
     * Checks if method has parameter with raw type.
     *
     * @param method method to check
     * @return {@code true} if and only if method has parameter which type is raw.
     * @see #isRawType(Type)
     */
    public static boolean hasRawParameterType(final Method method) {
        return Arrays.stream(method.getGenericParameterTypes()).anyMatch(GenericUtils::isRawType);
    }

    /**
     * Gets {@link GenericContexts} of given class and all of its superinterfaces and superclasses.
     *
     * @param root root to get contexts from
     * @return contexts of given class and all of its superinterfaces and superclasses
     * @see #fillContexts(Class, GenericContexts, Map)
     */
    public static GenericContexts getContexts(final Class<?> root) {
        final GenericContexts contexts = new GenericContexts();
        fillContexts(root, contexts, Map.of());
        return contexts;
    }

    /**
     * Adds contexts of given class and all of its superinterfaces and superclasses to {@code contexts}
     *
     * @param root          root to get contexts from
     * @param contexts      map to add new contexts
     * @param parentContext context used to substitute types' names.
     * @see #getContexts(Class)
     */
    private static void fillContexts(
            final Class<?> root, final GenericContexts contexts, final Map<String, Type> parentContext) {
        Stream.concat(
                Arrays.stream(root.getGenericInterfaces()),
                Stream.of(root.getGenericSuperclass())
        ).forEach(extendedType -> {
            if (extendedType instanceof final ParameterizedType type) {
                final Map<String, Type> typeParameterToActualType = new HashMap<>();
                // getRawType is always instanceof Class, so cast is safe
                final Class<?> rawType = (Class<?>) type.getRawType();
                final TypeVariable<?>[] parameters = rawType.getTypeParameters();
                final Type[] arguments = type.getActualTypeArguments();

                for (int i = 0; i < parameters.length; i++) {
                    typeParameterToActualType.put(
                            parameters[i].getName(),
                            parentContext.getOrDefault(
                                    arguments[i].getTypeName(), arguments[i]
                            )
                    );
                }
                contexts.addContext(rawType, typeParameterToActualType);
                fillContexts(rawType, contexts, typeParameterToActualType);
            } else if (extendedType instanceof final Class<?> clazz) {
                fillContexts(clazz, contexts, Map.of());
            }
            // nothing except ParameterizedType and Class can be, because it's extended type
        });
    }

    /**
     * Get {@link String} representation of given type variable.
     * Representation includes type bounds separated by <var>&amp;</var>.
     *
     * @param typeVariable   type variable to get representation
     * @param context        context used to substitute types' names.
     * @param typeParameters names of type parameters in the {@code typeVariable}'s generic declaration.
     * @return representation of {@code typeVariable}
     */
    private static String getTypeVariableRepresentation(
            final TypeVariable<?> typeVariable, final Map<String, Type> context, final Set<String> typeParameters) {
        final String bounds = Arrays.stream(typeVariable.getBounds())
                .filter(Predicate.not(Predicate.isEqual(Object.class)))
                .map(type -> typeToStringInContext(context, type, typeParameters))
                .collect(Collectors.joining(" & "));
        if (bounds.length() != 0) {
            return typeVariable.getName() + " extends " + bounds;
        }
        return typeVariable.getName();
    }

    /**
     * Gets {@link String} representation of the list of type parameters.
     * If there are no type parameters, result would be empty string.
     *
     * @param typeParameters type parameters to get representation of
     * @param context        context used to substitute types' names.
     * @param <T>            type of type variables in {@code typeParameters} {@link List}
     * @return {@link String} representing {@code typeParameters}
     */
    public static <T extends TypeVariable<?>> String typeParametersToString(
            final List<T> typeParameters, final Map<String, Type> context) {
        final Set<String> ignore = typeParameters.stream().map(TypeVariable::getName).collect(Collectors.toSet());
        return typeParameters.size() == 0 ? "" :
                typeParameters.stream()
                        .map(typeParameter -> getTypeVariableRepresentation(
                                typeParameter, context, ignore))
                        .collect(Collectors.joining(", ", "<", ">"));
    }
}
