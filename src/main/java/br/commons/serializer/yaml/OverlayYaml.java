package br.commons.serializer.yaml;

import br.commons.Logger;
import br.commons.Strings;
import br.commons.Tuple;
import br.commons.serializer.Yaml;
import br.commons.serializer.core.Scalars;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

/**
 * {@link Yaml} com uma camada de sobrescrita por chave: toda leitura de escalar consulta
 * {@code overrides} primeiro e só cai no {@code base} quando ela não tem valor para a chave. A fonte
 * é de quem monta — system properties, um mapa de teste — e é consultada a cada chamada, então valor
 * definido depois da montagem já vale. Estrutura ({@code list}, {@code subtree}, {@code asTuples}) e
 * escrita delegam ao {@code base}, sem sobrescrita.
 *
 * <p>Sobrescrita presente nunca cai silenciosamente no valor do arquivo. Texto que deveria ser número
 * segue a mesma política do {@link YamlReader}: falha catastrófica de configuração
 * ({@link Logger#fatal}, que encerra o processo). Enum ou UUID inválido devolve {@code null} (e o
 * default, nas variantes com default).
 */
@NullMarked
public final class OverlayYaml implements Yaml {

    private final Yaml base;
    private final Function<String, @Nullable String> overrides;

    public OverlayYaml(Yaml base, Function<String, @Nullable String> overrides) {
        this.base = base;
        this.overrides = overrides;
    }

    private <T> @Nullable T pick(String key, Class<T> type, Function<String, @Nullable T> fallback) {
        val raw = overrides.apply(key);
        if (raw == null) return fallback.apply(key);
        try {
            return Scalars.coerce(raw.trim(), type);
        } catch (NumberFormatException e) {
            Logger.fatal("Expected a number in override of %s", key);
            return null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override @Nullable public Byte    asByte    (String key) { return pick(key, Byte.class, base::asByte); }
    @Override @Nullable public Short   asShort   (String key) { return pick(key, Short.class, base::asShort); }
    @Override @Nullable public Integer asInt     (String key) { return pick(key, Integer.class, base::asInt); }
    @Override @Nullable public Long    asLong    (String key) { return pick(key, Long.class, base::asLong); }
    @Override @Nullable public Float   asFloat   (String key) { return pick(key, Float.class, base::asFloat); }
    @Override @Nullable public Double  asDouble  (String key) { return pick(key, Double.class, base::asDouble); }
    @Override @Nullable public Boolean asBoolean (String key) { return pick(key, Boolean.class, base::asBoolean); }
    @Override @Nullable public String  asString  (String key) { return pick(key, String.class, base::asString); }

    @Override public byte[]   asBytes   (String key) { val raw = overrides.apply(key); return raw == null ? base.asBytes(key) : Strings.bytes(raw.trim()); }
    @Override public short[]  asShorts  (String key) { val raw = overrides.apply(key); return raw == null ? base.asShorts(key) : Strings.shorts(raw.trim()); }
    @Override public int[]    asInts    (String key) { val raw = overrides.apply(key); return raw == null ? base.asInts(key) : Strings.ints(raw.trim()); }
    @Override public long[]   asLongs   (String key) { val raw = overrides.apply(key); return raw == null ? base.asLongs(key) : Strings.longs(raw.trim()); }
    @Override public float[]  asFloats  (String key) { val raw = overrides.apply(key); return raw == null ? base.asFloats(key) : Strings.floats(raw.trim()); }
    @Override public double[] asDoubles (String key) { val raw = overrides.apply(key); return raw == null ? base.asDoubles(key) : Strings.doubles(raw.trim()); }

    @Override
    @Nullable
    public <T extends Enum<T>> T asEnum(String key, Class<T> klass) {
        val raw = overrides.apply(key);
        if (raw == null) return base.asEnum(key, klass);
        for (val constant : klass.getEnumConstants()) {
            if (constant.name().equals(raw.trim())) return constant;
        }
        return null;
    }

    @Override public boolean contains(String key) { return overrides.apply(key) != null || base.contains(key); }

    @Override public Tuple[] asTuples(String key) { return base.asTuples(key); }
    @Override public List<Yaml> list(String key) { return base.list(key); }
    @Override @Nullable public Yaml subtree(String key) { return base.subtree(key); }
    @Override @Nullable public Path folder() { return base.folder(); }
    @Override public boolean isEmpty() { return base.isEmpty(); }

    @Override public void clear() { base.clear(); }
    @Override public void save() { base.save(); }
    @Override public void save(Path path) { base.save(path); }
    @Override public void put(String key, Object value) { base.put(key, value); }
    @Override public void append(String key, String value) { base.append(key, value); }
    @Override public void append(YamlEntry... values) { base.append(values); }
    @Override public void append(String key, String name, Object value) { base.append(key, name, value); }
    @Override public void append(String key, YamlEntry... values) { base.append(key, values); }

    @Override
    public String toString() {
        return base.toString();
    }
}
