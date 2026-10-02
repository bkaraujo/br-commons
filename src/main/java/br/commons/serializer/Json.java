package br.commons.serializer;

import br.commons.serializer.json.JsonRoot;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Documento JSON já parseado, na mesma forma do {@link Yaml}: a interface é a API pública, a
 * implementação vive em {@code br.commons.serializer.json} e só se alcança por estas fábricas.
 *
 * <p>Codec à mão, sem databind de terceiros — o formato do fio fica sob controle do produto. Consulta,
 * escalares e binding vêm de {@link Serializer}, comuns ao YAML; aqui fica o que é só do JSON: escrever um valor
 * e ler um array de topo.
 */
@NullMarked
public interface Json extends Serializer {

    /** Serializa qualquer valor: primitivos, {@code String}, {@code UUID}, enums, {@code Map}, {@code List} e {@code record}s. */
    static String write(@Nullable Object value) { return JsonRoot.write(value); }

    static Json from(String text) { return JsonRoot.from(text); }

    /** Materializa um documento que é um array de topo; lista vazia quando não é. */
    <T> List<T> asList(Class<T> type);
}
