package info.kgeorgiy.ja.eliseev.implementor;

import info.kgeorgiy.ja.eliseev.implementor.utils.GenericUtils;
import info.kgeorgiy.java.advanced.implementor.ImplerException;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Represents signature of non-existing method, that to be implemented.
 *
 * @author Aleksandr Eliseev
 */
public class ImplementationMethodSignature {
    /**
     * The Java source access modifiers.
     *
     * @see Modifier#PUBLIC
     * @see Modifier#PROTECTED
     * @see Modifier#PRIVATE
     */
    private static final int ACCESS_MODIFIERS = Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE;
    /**
     * List of four access modifiers in order of decreasing provided access.
     * Specifically, order is {@code public, protected, package-private, private}
     *
     * @see #ACCESS_MODIFIER_COMPARATOR
     */
    private static final List<Integer> ORDERED_ACCESS_MODIFIERS = List.of(Modifier.PUBLIC, Modifier.PROTECTED, 0,
                                                                          Modifier.PRIVATE);
    /**
     * Compares methods based on their access modifiers.
     * Method, which modifiers provides more access considered less than the other.
     *
     * @see #ACCESS_MODIFIERS
     * @see #ORDERED_ACCESS_MODIFIERS
     */
    private static final Comparator<Method> ACCESS_MODIFIER_COMPARATOR = Comparator.comparingInt(
            method -> ORDERED_ACCESS_MODIFIERS.indexOf(method.getModifiers() & ACCESS_MODIFIERS));

    /**
     * Method's access modifier.
     *
     * @see #ACCESS_MODIFIERS
     */
    private final int accessModifier;
    /**
     * Method's type parameters.
     */
    private final List<GeneratedTypeVariable> typeParameters;
    /**
     * {@link String} representation of method's return type.
     */
    private final String returnType;
    /**
     * Method's name.
     */
    private final String name;
    /**
     * Real method, that has same return type, name, type parameters and types of parameters as this.
     *
     * @see <a href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-8.html#jls-8.4.4">
     * "Same type parameters" definition</a>.
     */
    private final Method referenceMethod;
    /**
     * {@link String} representations of method's parameter types.
     */
    private final List<String> parameterTypes;
    /**
     * Context used to substitute types in {@link #referenceMethod}.
     */
    private final Map<String, Type> context;

    /**
     * Constructor, that assign its arguments to class' fields.
     *
     * @param accessModifier  value of {@link #accessModifier}
     * @param typeParameters  value of {@link #typeParameters}
     * @param returnType      value of {@link #returnType}
     * @param name            value of {@link #name}
     * @param referenceMethod value of {@link #referenceMethod}
     * @param parameterTypes  value of {@link #parameterTypes}
     * @param context         value of {@link #context}
     */
    private ImplementationMethodSignature(final int accessModifier, final List<GeneratedTypeVariable> typeParameters,
                                          final String returnType, final String name, final Method referenceMethod,
                                          final List<String> parameterTypes, final Map<String, Type> context) {
        this.accessModifier = accessModifier;
        this.typeParameters = typeParameters;
        this.returnType = returnType;
        this.name = name;
        this.referenceMethod = referenceMethod;
        this.parameterTypes = parameterTypes;
        this.context = context;
    }

    /**
     * Gets signature of method, that can override all of {@code equivalentMethods}.
     * Result signature would have access modifier which provides the least access, but enough to override all methods.
     *
     * @param equivalentMethods           methods, should be non-empty, all methods must be members of same class and
     *                                    be <a href=
     *                                    "https://docs.oracle.com/javase/specs/jls/se19/html/jls-8.html#jls-8.4.2">
     *                                    override-equivalent</a>
     * @param forbiddenTypeParameterNames values forbidden to be names of type parameters of the result method
     * @param contexts                    {@link GenericContexts} from which we can get contexts for methods
     * @return signature of method, overriding all of {@code equivalentMethods}
     * @throws ImplerException        if it is not possible to make such a method without using raw types or/and
     *                                unchecked casts
     * @throws NoSuchElementException if {@code equivalentMethods} is empty.
     */
    public static ImplementationMethodSignature getCommonMethodSignature(final Collection<Method> equivalentMethods,
                                                                         final Set<String> forbiddenTypeParameterNames, final GenericContexts contexts)
            throws ImplerException {
        final int accessModifier = getCommonAccessModifier(equivalentMethods);
        final int typeParameterCount = getCommonTypeParameters(equivalentMethods).length;
        final List<String> typeParameterNames = new ArrayList<>();
        int idx = 0;
        for (int i = 0; i < typeParameterCount; i++) {
            while (forbiddenTypeParameterNames.contains("Var" + idx)) {
                idx++;
            }
            typeParameterNames.add("Var" + idx);
            idx++;
        }
        if (equivalentMethods.stream().anyMatch(GenericUtils::hasRawParameterType)) {
            throw new ImplerException("Cannot generate implementation without using raw type in method parameters");
        }
        final Method firstMethod = equivalentMethods.stream().findAny().orElseThrow();
        final List<GeneratedTypeVariable> typeParameters = new ArrayList<>();
        final TypeVariable<?>[] referenceTypeParameters = firstMethod.getTypeParameters();
        for (int i = 0; i < typeParameterNames.size(); i++) {
            typeParameters.add(
                    new GeneratedTypeVariable(referenceTypeParameters[i].getBounds(), typeParameterNames.get(i)));
        }
        final Map<String, Type> context = contexts.getContext(firstMethod.getDeclaringClass());
        for (final GeneratedTypeVariable typeVariable : typeParameters) {
            final Type[] bounds = typeVariable.getBounds();
            for (int j = 0; j < bounds.length; j++) {
                for (int k = 0; k < referenceTypeParameters.length; k++) {
                    if (bounds[j].equals(referenceTypeParameters[k])) {
                        typeVariable.setBound(j, typeParameters.get(k));
                        break;
                    }
                }
            }
        }
        final Map<String, Type> referenceMethodContext = new HashMap<>(context);
        for (int i = 0; i < typeParameters.size(); i++) {
            referenceMethodContext.put(referenceTypeParameters[i].getName(), typeParameters.get(i));
        }
        Type returnType = null;
        for (final Method method : equivalentMethods) {
            if (returnType == null) {
                returnType = method.getGenericReturnType();
            } else if (isAssignable(returnType, method.getGenericReturnType())) {
                returnType = method.getGenericReturnType();
            }
        }
        if (GenericUtils.isRawType(returnType)) {
            throw new ImplerException("Cannot generate implementation without raw method return types");
        }
        Method referenceMethod = null;
        for (final Method method : equivalentMethods) {
            if (method.getGenericReturnType()
                      .equals(returnType) && typeParameters.size() == method.getTypeParameters().length) {
                referenceMethod = method;
                break;
            }
        }
        if (referenceMethod == null) {
            throw new ImplerException("Cannot generate implementation without unchecked casts");
        }
        return new ImplementationMethodSignature(accessModifier, typeParameters,
                                                 GenericUtils.typeToStringInContext(referenceMethodContext, returnType,
                                                                                    Set.of()), firstMethod.getName(),
                                                 referenceMethod, Arrays.stream(firstMethod.getGenericParameterTypes())
                                                                        .map(type -> GenericUtils.typeToStringInContext(
                                                                                referenceMethodContext, type, Set.of()))
                                                                        .toList(), context);
    }

    /**
     * Gets type parameters that must have the method, which overrides all of the {@code methods}.
     * If at least one of methods is not generic, result array is empty. Else, all the methods must have same
     * type parameters, and the method, which overrides all of them would have the same.
     *
     * @param methods methods, should be non-empty, all methods must be members of same class and
     *                be <a href=
     *                "https://docs.oracle.com/javase/specs/jls/se19/html/jls-8.html#jls-8.4.2">
     *                override-equivalent</a>
     * @return type parameters of method, overriding all of the {@code method}
     * @see #getCommonMethodSignature(Collection, Set, GenericContexts)
     * @see <a href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-8.html#jls-8.4.4">
     * "Same type parameters" definition</a>.
     */
    private static TypeVariable<?>[] getCommonTypeParameters(final Collection<Method> methods) {
        final List<TypeVariable<Method>[]> methodsTypeParameters = methods.stream().map(Method::getTypeParameters)
                                                                          .toList();
        if (methodsTypeParameters.stream().anyMatch(parameters -> parameters.length == 0)) {
            return new TypeVariable[0];
        }
        return methodsTypeParameters.get(0);
    }

    /**
     * Gets access modifier which provides the least access, but enough to override all {@code methods}.
     *
     * @param methods methods to get access modifiers, must be non-empty
     * @return least common access modifier of all {@code methods}
     * @see #getCommonMethodSignature(Collection, Set, GenericContexts)
     * @see #ACCESS_MODIFIER_COMPARATOR
     */
    private static int getCommonAccessModifier(final Collection<Method> methods) {
        return methods.stream().min(ACCESS_MODIFIER_COMPARATOR).map(method -> method.getModifiers() & ACCESS_MODIFIERS)
                      .orElseThrow();
    }

    /**
     * Checks if value of type {@code type2} is assignable to type {@code type1}.
     *
     * @param type1 type to be assigned to
     * @param type2 type to check if can be assigned
     * @return if {@code type2} is assignable to {@code type1}, {@code true} is returned,
     * if {@code type1} is assignable to {@code type2}, {@code false} is returned,
     * else, there are no guarantee on return value
     */
    private static boolean isAssignable(final Type type1, final Type type2) {
        if (type1.equals(type2)) {
            return true;
        }
        if (type2 instanceof final Class<?> clazz2) {
            if (type1 instanceof final Class<?> clazz1) {
                return clazz1.isAssignableFrom(clazz2);
            } else if (type1 instanceof final GenericArrayType genericArrayType) {
                return clazz2.isArray() && isAssignable(genericArrayType.getGenericComponentType(),
                                                        clazz2.getComponentType());
            } else if (type1 instanceof final ParameterizedType parameterizedType) {
                return ((Class<?>) parameterizedType.getRawType()).isAssignableFrom(clazz2);
            } else if (type1 instanceof TypeVariable) {
                return false;
            } else if (type1 instanceof final WildcardType wildcardType) {
                return Arrays.stream(wildcardType.getLowerBounds()).anyMatch(bound -> isAssignable(bound, clazz2));
            } else {
                throw new AssertionError("Unreachable");
            }
        } else if (type2 instanceof final GenericArrayType genericArrayType2) {
            if (type1 instanceof final Class<?> clazz) {
                return clazz == Object.class || (clazz.isArray() && isAssignable(clazz.arrayType(),
                                                                                 genericArrayType2.getGenericComponentType()));
            } else if (type1 instanceof final GenericArrayType genericArrayType1) {
                return isAssignable(genericArrayType1.getGenericComponentType(),
                                    genericArrayType2.getGenericComponentType());
            } else {
                return false;
            }
        } else if (type2 instanceof final ParameterizedType parameterizedType2) {
            return isAssignable(type1, parameterizedType2.getRawType());
        } else if (type2 instanceof final TypeVariable<?> typeVariable2) {
            return Arrays.stream(typeVariable2.getBounds()).anyMatch(bound -> isAssignable(type1, bound));
        } else if (type2 instanceof final WildcardType wildcardType2) {
            return Arrays.stream(wildcardType2.getUpperBounds()).anyMatch(bound -> isAssignable(type1, bound));
        } else {
            throw new AssertionError("Unreachable");
        }
    }

    /**
     * Gets {@link #name}.
     *
     * @return name of the method
     */
    public String getName() {
        return name;
    }

    /**
     * Gets {@link #referenceMethod}.
     *
     * @return reference method.
     */
    public Method getReferenceMethod() {
        return referenceMethod;
    }

    /**
     * Generates {@link String} representation of the signature of the method.
     * Specifically, it is access modifier, followed by type parameters, if any,
     * followed by name, followed by parameters list in parentheses, followed by left curly bracket.
     *
     * @return method's signature.
     */
    @Override
    public String toString() {
        return String.format("%s%s %s %s%s{", Modifier.toString(accessModifier),
                             GenericUtils.typeParametersToString(typeParameters, context), returnType, name,
                             IntStream.range(0, parameterTypes.size()).mapToObj(idx -> {
                                 final String typeName = parameterTypes.get(idx);
                                 return typeName + " var" + idx;
                             }).collect(Collectors.joining(", ", "(", ") ")));
    }

    /**
     * Wrapper for non-existing type variables.
     *
     * @author Aleksandr Eliseev
     */
    private static class GeneratedTypeVariable implements TypeVariable<Method> {
        /**
         * Array of {@link Type} objects representing the
         * upper bound(s) of this type variable.  If no upper bound is
         * explicitly declared, the upper bound is {@link Object}.
         */
        private final Type[] bounds;
        /**
         * Name of the type variable
         */
        private final String name;

        /**
         * Constructor, that assign its arguments to class' fields.
         *
         * @param bounds value of {@link #bounds}
         * @param name   value of {@link #name}
         */
        public GeneratedTypeVariable(final Type[] bounds, final String name) {
            this.bounds = bounds;
            this.name = name;
        }

        /**
         * Sets bound with index {@code idx} to {@code bound}.
         *
         * @param idx   index of new bound
         * @param bound new bound
         * @throws ArrayIndexOutOfBoundsException if {@code idx < 0} or {@code idx >= } number of bounds.
         */
        public void setBound(final int idx, final Type bound) {
            bounds[idx] = bound;
        }

        @Override
        public Type[] getBounds() {
            return bounds;
        }

        @Override
        public String getName() {
            return name;
        }

        /**
         * It is not an actual type variable, so there is no generic declaration that declares it.
         *
         * @return {@code null}
         */
        @Override
        public Method getGenericDeclaration() {
            return null;
        }

        @Override
        public AnnotatedType[] getAnnotatedBounds() {
            return new AnnotatedType[0];
        }

        @Override
        public <T extends Annotation> T getAnnotation(final Class<T> annotationClass) {
            return null;
        }

        @Override
        public Annotation[] getAnnotations() {
            return new Annotation[0];
        }

        @Override
        public Annotation[] getDeclaredAnnotations() {
            return new Annotation[0];
        }
    }
}
