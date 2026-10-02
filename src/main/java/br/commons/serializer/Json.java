package br.commons.serializer;

import br.commons.serializer.json.JsonRoot;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Documento JSON já parseado, na mesma forma do {@link Yaml}: a interface é a API pública, a
 * implementação vive em {@code br.commons.serializer.json} e só se alcança por estas fábricas.
 *
 * <p>Codec à mão, sem databind de terceiros — o formato do fio fica sob controle do produto. A
 * navegação da árvore, a coerção de escalares e o binding para {@code record} são de
 * {@code br.commons.serializer.core}, compartilhados com o YAML.
 */
@NullMarked
public interface Json {

    /** Serializa qualquer valor: primitivos, {@code String}, {@code UUID}, enums, {@code Map}, {@code List} e {@code record}s. */
    static String write(@Nullable Object value) { return JsonRoot.write(value); }

    static Json from(String text) { return JsonRoot.from(text); }

    boolean isEmpty();

    boolean contains(String key);

    @Nullable String  asString  (String key);
    @Nullable Integer asInt     (String key);
    @Nullable Long    asLong    (String key);
    @Nullable Double  asDouble  (String key);
    @Nullable Boolean asBoolean (String key);

    /** Materializa o documento inteiro no tipo pedido — tipicamente um {@code record}. */
    <T> T as(Class<T> type);

    /** Materializa um documento que é um array de topo; lista vazia quando não é. */
    <T> List<T> asList(Class<T> type);
}
