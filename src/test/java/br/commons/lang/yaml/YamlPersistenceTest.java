package br.commons.lang.yaml;

import br.commons.lang.Yaml;
import br.commons.serializer.Serializer;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class YamlPersistenceTest {

    @TempDir
    Path dir;

    @Test
    void createSaveAndReadBack() {
        val file = dir.resolve("app.yaml");
        Yaml.save(Serializer.builder().put("server.port", 8080).append("users", "name", "ana").build(), file);

        val read = Yaml.from(file);
        assertEquals(8080, read.asInt("server.port"));
        assertEquals("ana", read.asString("users.0.name"));
        assertEquals(dir, read.folder());
    }

    @Test
    void sameFileComesFromTheCacheUntilSaved() {
        val file = dir.resolve("app.yaml");
        Yaml.save(Serializer.builder().put("port", 1).build(), file);

        val first = Yaml.from(file);
        assertSame(first, Yaml.from(file));

        Yaml.save(Serializer.builder(first).put("port", 2).build());
        assertEquals(2, Yaml.from(file).asInt("port"));
        assertEquals(1, first.asInt("port"));
    }
}
