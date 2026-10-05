package br.commons.serializer.document.binding;

import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * Materializes the generic tree into typed objects: {@code record}s via the canonical constructor,
 * {@link List}s via the declared type argument, and scalars via {@link Scalars}.
 *
 * <p>Format-agnostic — receives the same tree whether from JSON or YAML, which is why
 * it lives here and not with a parser.
 */
@NullMarked
public abstract class TreeBinder {

    private TreeBinder() {}

    @SuppressWarnings("unchecked")
    public static <T> T bind(@Nullable Object tree, Class<T> type) {
        val bound = bindValue(tree, type, type);
        if (bound == null) {
            throw new IllegalArgumentException("Cannot bind null to " + type);
        }
        return (T) bound;
    }

    @Nullable
    private static Object bindValue(@Nullable Object tree, Class<?> type, Type generic) {
        if (tree == null) return null;
        if (List.class.isAssignableFrom(type)) return bindList((List<?>) tree, generic);
        if (type.isRecord()) return bindRecord((Map<?, ?>) tree, type);

        val scalar = Scalars.coerce(tree, type);
        if (scalar == null) {
            throw new IllegalArgumentException("Unsupported target type: " + type);
        }
        return scalar;
    }

    private static List<Object> bindList(List<?> tree, Type generic) {
        Class<?> elementType = Object.class;
        if (generic instanceof ParameterizedType parameterized
                && parameterized.getActualTypeArguments()[0] instanceof Class<?> declared) {
            elementType = declared;
        }

        val element = elementType;
        return tree.stream().map(item -> bindValue(item, element, element)).toList();
    }

    private static Object bindRecord(Map<?, ?> tree, Class<?> type) {
        val components = type.getRecordComponents();
        val args = new Object[components.length];
        val paramTypes = new Class<?>[components.length];

        for (int i = 0; i < components.length; i++) {
            val component = components[i];
            paramTypes[i] = component.getType();
            args[i] = bindValue(tree.get(component.getName()), component.getType(), component.getGenericType());
        }

        try {
            val constructor = type.getDeclaredConstructor(paramTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to build " + type, e);
        }
    }
}
