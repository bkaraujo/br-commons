package br.commons.serializer.core;

import br.commons.Logger;
import br.commons.Strings;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Navegação sobre a árvore genérica que todo formato produz depois de parseado:
 * {@link Map} de {@link String} para {@link Object}, {@link List} de {@link Object} e escalares.
 * É a estrutura comum entre YAML e JSON — nada aqui conhece a sintaxe de nenhum dos dois.
 *
 * <p>Chaves são caminhos pontuados ({@code servidor.portas.0.numero}), onde cada segmento indexa um
 * mapa pela chave ou uma lista pela posição.
 */
@NullMarked
public abstract class Trees {

    private static final Pattern DOT = Pattern.compile("\\.");

    private Trees() {}

    /** Quebra o caminho pontuado em segmentos. */
    public static String[] splitKey(String key) {
        return DOT.split(key);
    }

    /** Resolve o caminho pontuado a partir da raiz; {@code null} quando algum segmento não existe. */
    @Nullable
    public static Object find(Map<String, Object> root, String key) {
        val tokens = DOT.split(key, -1);

        Object container = root;
        for (val token : tokens) {
            if (container instanceof Map<?, ?> map) {
                val next = map.get(token);
                if (next == null) return null;
                container = next;
                continue;
            }

            if (container instanceof List<?> list) {
                container = element(list, token);
                if (container == null) return null;
            }
        }

        return container;
    }

    @Nullable
    private static Object element(List<?> list, String token) {
        try {
            val index = Integer.parseInt(token);
            if (index >= list.size()) {
                Logger.warn("Element index (%d) beyond bounds (%d)", index, list.size() - 1);
                return null;
            }
            return list.get(index);
        } catch (NumberFormatException e) {
            Logger.fatal("Expected a number, got %s", token);
            return null;
        }
    }

    /**
     * Achata a árvore em chaves pontuadas, para lookup O(1). Uma lista entra duas vezes: inteira,
     * sob a própria chave, e elemento a elemento, sob {@code chave.<indice>}.
     */
    public static Map<String, Object> flatten(Map<String, Object> root) {
        val flat = new LinkedHashMap<String, Object>();
        flatten(Strings.EMPTY, root, flat);
        return flat;
    }

    @SuppressWarnings("unchecked")
    private static void flatten(String prefix, Map<String, Object> map, Map<String, Object> target) {
        for (val entry : map.entrySet()) {
            val key = prefix.isEmpty() ? entry.getKey() : prefix + Strings.DOT + entry.getKey();
            val value = entry.getValue();

            if (value instanceof Map<?, ?> nested) {
                flatten(key, (Map<String, Object>) nested, target);
            } else if (value instanceof List<?> list) {
                flatten(key, list, target);
            } else {
                target.put(key, value);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void flatten(String key, List<?> list, Map<String, Object> target) {
        target.put(key, list);
        for (int i = 0; i < list.size(); i++) {
            val item = list.get(i);
            if (item instanceof Map<?, ?> nested) {
                flatten(key + Strings.DOT + i, (Map<String, Object>) nested, target);
            } else {
                target.put(key + Strings.DOT + i, item);
            }
        }
    }
}
