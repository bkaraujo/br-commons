package br.bundled.judas;

import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Each layer imports only the ones allowed by the dependency rule (ARCH-02), read from the import lines of the sources. */
@NullMarked
class ArchitectureTest {

    private static final String ROOT = "br.bundled.judas";
    private static final Path SOURCES = Path.of("src/main/java/br/bundled/judas");
    private static final Pattern IMPORT = Pattern.compile("^import\\s+(?:static\\s+)?(br\\.bundled\\.judas\\.[\\w.]+)", Pattern.MULTILINE);

    /** The layers each layer must never import, by package prefix. */
    private static final Map<String, List<String>> FORBIDDEN = Map.of(
            ROOT + "._0_domain", List.of(ROOT + "._1_application", ROOT + "._2_infrastructure"),
            ROOT + "._1_application", List.of(ROOT + "._2_infrastructure"),
            ROOT + "._2_infrastructure.persistence", List.of(ROOT + "._2_infrastructure.web"),
            ROOT + "._2_infrastructure.web", List.of(ROOT + "._2_infrastructure.persistence"));

    /** ARCH-02 */
    @Test
    void layersImportOnlyWhatTheRuleAllows() throws IOException {
        val violations = new ArrayList<String>();
        try (val files = Files.walk(SOURCES)) {
            for (val file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                val owner = packageOf(file);
                val matcher = IMPORT.matcher(Files.readString(file));
                while (matcher.find()) {
                    val imported = matcher.group(1);
                    if (forbidden(owner, imported)) violations.add(file + " -> " + imported);
                }
            }
        }
        assertEquals(List.of(), violations);
    }

    private static String packageOf(Path file) {
        val relative = SOURCES.relativize(file.getParent()).toString().replace('/', '.');
        return relative.isEmpty() ? ROOT : ROOT + "." + relative;
    }

    private static boolean forbidden(String owner, String imported) {
        return FORBIDDEN.entrySet().stream()
                .filter(rule -> owner.startsWith(rule.getKey()))
                .flatMap(rule -> rule.getValue().stream())
                .anyMatch(imported::startsWith);
    }
}
