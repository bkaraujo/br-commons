package br.commons.persistence.jdbc.migration;

import br.commons.Result;
import br.commons.Strings;
import br.commons.locale.i18n.Errors;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finds {@code V<n>__<description>.sql} under a classpath directory, both on the file system (IDE) and inside
 * jars (packaged application), in every classpath entry that has the directory.
 */
@NullMarked
final class MigrationScanner {

    private static final Pattern FILE = Pattern.compile("V(\\d+)__(.+)\\.sql");

    private final ClassLoader loader;
    private final String location;

    MigrationScanner(ClassLoader loader, String location) {
        this.loader = loader;
        this.location = location.endsWith("/") ? location.substring(0, location.length() - 1) : location;
    }

    Result<List<Migration>, String> scan() {
        try {
            val names = new ArrayList<String>();
            val roots = loader.getResources(location);
            while (roots.hasMoreElements()) {
                names.addAll(list(roots.nextElement()));
            }
            return build(names);
        } catch (IOException | URISyntaxException e) {
            return Result.failure(Errors.get().listMigrationsFailed(location, String.valueOf(e.getMessage())).value());
        }
    }

    private List<String> list(URL root) throws IOException, URISyntaxException {
        if ("jar".equals(root.getProtocol())) {
            val connection = (JarURLConnection) root.openConnection();
            connection.setUseCaches(false);
            try (var jar = connection.getJarFile()) {
                val prefix = location + "/";
                return jar.stream()
                        .map(entry -> entry.getName())
                        .filter(name -> name.startsWith(prefix) && name.indexOf('/', prefix.length()) < 0)
                        .map(name -> name.substring(prefix.length()))
                        .toList();
            }
        }
        try (Stream<Path> files = Files.list(Path.of(root.toURI()))) {
            return files.map(path -> path.getFileName().toString()).toList();
        }
    }

    private Result<List<Migration>, String> build(List<String> names) throws IOException {
        val migrations = new ArrayList<Migration>();
        val versions = new HashSet<Integer>();
        for (val name : names) {
            val match = FILE.matcher(name);
            if (!match.matches()) {
                continue;
            }
            val version = Integer.parseInt(match.group(1));
            if (!versions.add(version)) {
                return Result.failure(Errors.get().duplicateMigrationVersion(String.valueOf(version)).value());
            }
            val sql = read(location + "/" + name);
            migrations.add(new Migration(version, match.group(2).replace('_', ' '), sql, Strings.sha256(sql)));
        }
        migrations.sort(Comparator.comparingInt(Migration::version));
        return Result.success(migrations);
    }

    private String read(String resource) throws IOException {
        try (InputStream input = loader.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException(Errors.get().migrationNotReadable(resource).value());
            }
            return Strings.read(input);
        }
    }
}
