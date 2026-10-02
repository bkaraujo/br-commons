package br.commons.serializer;

import br.commons.Tuple;
import br.commons.serializer.yaml.YamlEntry;
import br.commons.serializer.yaml.YamlRoot;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Documento YAML — tipicamente configuração. Consulta, escalares, {@code asEnum} e binding vêm de
 * {@link Serializer}, comuns ao JSON; aqui fica o que é só do YAML: listas de escalares em texto
 * ({@code "1, 2, 3"}), sub-documentos e escrita de volta no arquivo.
 */
@NullMarked
public interface Yaml extends Serializer {

    static Yaml from(Path path) { return YamlRoot.from(path); }
    static Yaml fromClasspath(String resource) { return YamlRoot.fromClasspath(resource); }
    static Yaml from(Map<String, Object> map) { return YamlRoot.from(map); }
    static Yaml from() { return from(new LinkedHashMap<>()); }

    byte[]      asBytes   (String key);
    short[]     asShorts  (String key);
    int[]       asInts    (String key);
    long[]      asLongs   (String key);
    float[]     asFloats  (String key);
    double[]    asDoubles (String key);
    Tuple[]     asTuples  (String key);

    @Nullable Yaml subtree(String key);

    @Nullable Path folder();
    List<Yaml> list(String key);

    void clear();
    void save();
    void save(Path path);
    void put(String key, Object value);
    void append(String key, String value);
    void append(YamlEntry... values);
    void append(String key, String name, Object value);
    void append(String key, YamlEntry... values);

}
