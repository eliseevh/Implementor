package info.kgeorgiy.ja.eliseev.implementor;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents map from classes to their generic contexts.
 * More precisely: context is map from {@link String} name of type parameter defined in class source code to
 * {@link Type} type passed as argument. For example, for {@code java.util.List<T>} map would contain one entry:
 * {@code "E" -> T}, where T is {@link java.lang.reflect.TypeVariable} passed to
 * {@code java.util.List} as type argument.
 * This class lets you add such contexts associated with raw types(in example above, such type would be
 * {@link java.util.List}), and get them by raw type {@link Class} token.
 *
 * @author Aleksandr Eliseev
 */
public class GenericContexts {
    /**
     * Maps raw type to contexts
     */
    private final Map<Class<?>, Map<String, Type>> rawTypeToContext;

    /**
     * Creates map with no contexts
     */
    public GenericContexts() {
        rawTypeToContext = new HashMap<>();
    }

    /**
     * Adds new context to map
     *
     * @param rawType raw type which context to add
     * @param context context of {@code rawType}
     */
    public void addContext(final Class<?> rawType, final Map<String, Type> context) {
        this.rawTypeToContext.put(rawType, context);
    }

    /**
     * Gets context of {@code rawType}.
     * If its context was not added to map, then result is empty, modifiable {@link Map}.
     *
     * @param rawType type to get context
     * @return context of {@code rawType}
     */
    public Map<String, Type> getContext(final Class<?> rawType) {
        return this.rawTypeToContext.getOrDefault(rawType, new HashMap<>());
    }
}
