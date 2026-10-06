package br.commons.locale.i18n;

import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Typed message bundles over the JDK {@link ResourceBundle} (spec §2.5). An interface declares one method
 * per key, each returning {@link Text}; a dynamic proxy resolves it. Parameters enter by name
 * ({@code {confirmedOn}}, which needs {@code -parameters}) and are formatted by type in the bundle's locale.
 * The substitution is ours, not {@code MessageFormat}'s, so the Portuguese apostrophe needs no escaping.
 * A method whose only parameter is an enum reads the key {@code <method>_<CONSTANT>}.
 *
 * <p>Creating a bundle checks it ({@link #check}); an incomplete bundle stops the application.
 */
@NullMarked
public final class Bundles {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_]+)}");

    private Bundles() {}

    public static <T> T of(Class<T> type, String baseName, Locale locale) {
        val bundle = load(baseName, locale);
        val problems = check(type, bundle);
        if (!problems.isEmpty()) {
            // Literal on purpose: Errors itself is a Bundle — translating this would recurse when its bundle is broken.
            throw new IllegalStateException("Bundle " + baseName + " for " + locale + " is incomplete: " + problems);
        }
        val handler = new Handler(bundle, new Formats(locale));
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    /** Problems of a bundle: missing keys, placeholders without a parameter, orphan keys (BundleCompletenessTest). */
    public static List<String> check(Class<?> type, String baseName, Locale locale) {
        return check(type, load(baseName, locale));
    }

    private static ResourceBundle load(String baseName, Locale locale) {
        val control = ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);
        return ResourceBundle.getBundle(baseName, locale, Bundles.class.getClassLoader(), control);
    }

    private static List<String> check(Class<?> type, ResourceBundle bundle) {
        val problems = new ArrayList<String>();
        val used = new HashSet<String>();
        for (val method : type.getMethods()) {
            if (method.getDeclaringClass() == Object.class || method.isDefault()) {
                continue;
            }
            checkMethod(method, bundle, used, problems);
        }
        for (val key : bundle.keySet()) {
            if (!used.contains(key)) {
                problems.add("orphan key " + key);
            }
        }
        return problems;
    }

    private static void checkMethod(Method method, ResourceBundle bundle, Set<String> used, List<String> problems) {
        val name = method.getName();
        if (method.getReturnType() != Text.class) {
            problems.add(name + " must return Text");
        } else if (enumParameter(method) != null) {
            for (val constant : constants(method)) {
                val key = name + "_" + constant;
                used.add(key);
                if (!bundle.containsKey(key)) {
                    problems.add("missing key " + key);
                }
            }
        } else if (bundle.containsKey(name)) {
            used.add(name);
            checkPlaceholders(method, bundle.getString(name), problems);
        } else {
            problems.add("missing key " + name);
        }
    }

    private static void checkPlaceholders(Method method, String template, List<String> problems) {
        val names = new HashSet<String>();
        for (val parameter : method.getParameters()) {
            if (!parameter.isNamePresent()) {
                problems.add(method.getName() + ": compile with -parameters");
                return;
            }
            names.add(parameter.getName());
        }
        val matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            if (!names.contains(matcher.group(1))) {
                problems.add(method.getName() + ": {" + matcher.group(1) + "} is not a parameter");
            }
        }
    }

    private static @Nullable Class<?> enumParameter(Method method) {
        val types = method.getParameterTypes();
        return types.length == 1 && types[0].isEnum() ? types[0] : null;
    }

    private static List<String> constants(Method method) {
        val names = new ArrayList<String>();
        for (val constant : method.getParameterTypes()[0].getEnumConstants()) {
            names.add(((Enum<?>) constant).name());
        }
        return names;
    }

    @NullMarked
    private record Handler(ResourceBundle bundle, Formats formats) implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, @Nullable Object @Nullable [] args) {
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, method, args);
            }
            val values = args == null ? new Object[0] : args;
            if (enumParameter(method) != null && values[0] instanceof Enum<?> constant) {
                return Text.message(bundle.getString(method.getName() + "_" + constant.name()));
            }
            return Text.message(substitute(bundle.getString(method.getName()), method, values));
        }

        /**
         * One pass over the template: a value that itself contains {@code {other}} is inserted as is, never
         * substituted again. A placeholder with no parameter of that name stays in the text.
         */
        private String substitute(String template, Method method, @Nullable Object[] values) {
            val parameters = method.getParameters();
            val byName = new HashMap<String, String>(parameters.length * 2);
            for (var i = 0; i < parameters.length; i++) {
                val value = values[i];
                byName.put(parameters[i].getName(), value == null ? "" : formats.argument(value));
            }
            val matcher = PLACEHOLDER.matcher(template);
            val result = new StringBuilder(template.length() + 16);
            while (matcher.find()) {
                val formatted = byName.get(matcher.group(1));
                matcher.appendReplacement(result, Matcher.quoteReplacement(formatted == null ? matcher.group() : formatted));
            }
            matcher.appendTail(result);
            return result.toString();
        }

        private Object objectMethod(Object proxy, Method method, @Nullable Object @Nullable [] args) {
            return switch (method.getName()) {
                case "equals" -> args != null && proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "Bundle(" + bundle.getBaseBundleName() + ", " + formats.locale() + ")";
                default -> throw new MissingResourceException("Unsupported method", method.getName(), "");
            };
        }
    }
}
