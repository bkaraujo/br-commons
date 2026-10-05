# br.commons.serializer — reading, creating and persisting documents

JSON and YAML documents read into one common internal representation and queried
through a single API. Formats only read/validate (and YAML persists).

| Type                               | Role                                                          |
|------------------------------------|---------------------------------------------------------------|
| `Serializer`                       | **The only read API**; also `builder(...)` and `overlay(...)` |
| `Json`                             | Factories: `from(String)`, `write(Object)`                    |
| `Yaml`                             | Factories: `from(Path)`, `fromClasspath`, `from(Map)`, `save` |
| `document.builder.DocumentBuilder` | Creates/edits a document: `put`, `append`, `clear`, `build`   |
| `document.builder.Entry`           | Key/value pair for `append`                                   |
| `document.Document`                | Immutable internal representation; internal use (see §8)      |

## 1. Read

```java
Serializer body = Json.from(text);                      // IllegalArgumentException if invalid
Serializer cfg  = Yaml.from(Path.of("app.yaml"));       // empty if the file is missing
Serializer res  = Yaml.fromClasspath("defaults.yaml");  // empty if the resource is missing
Serializer mem  = Yaml.from(Map.of("port", 8080));      // copy of the map
```
Always declare the variable as `Serializer`: `Json`/`Yaml` are not document types.

## 2. Query

Key = dotted path; a numeric segment indexes a list (`server.ports.0.number`).

```java
cfg.contains("server.port");                  // exists? (a null value counts)
cfg.asString("server.host");                  // null when missing
cfg.asInt("server.port", 8080);               // with default
cfg.asEnum("log.level", LogLevel.class);      // null if missing or unknown name
cfg.asInts("ports");                          // "1, 2, 3" -> int[]
cfg.asTuples("props");                        // list of {name, value} -> Tuple[]
Serializer db = cfg.subtree("db");            // sub-document or null
List<Serializer> users = cfg.list("users");   // list items; empty if not a list
cfg.folder();                                 // folder of the source file or null
```

Scalars: `asString/asByte/asShort/asInt/asLong/asFloat/asDouble/asBoolean`.

## 3. Binding to types

```java
record Server(String host, int port) {}
Server s = Yaml.from(path).as(Server.class);              // whole document
List<Item> items = Json.from("[...]").asList(Item.class); // top-level list
```

Supports `record` (canonical constructor), declared `List<T>`, scalars, `UUID`, enums.

## 4. Create, edit and persist

```java
Serializer doc = Serializer.builder()
        .put("server.port", 8080)                   // creates intermediate maps
        .append("users", "name", "ana")             // item {name: ana} in list users
        .append(new Entry("env", "prod"))           // top-level key
        .build();
Yaml.save(doc, Path.of("app.yaml"));

Serializer edited = Serializer.builder(cfg).put("server.port", 9090).build();
Yaml.save(edited);                                  // writes to cfg's source
String json = Json.write(edited);                   // documents serialize too
```

- A document is **immutable**: `builder(base)` copies; `base` does not change.
- `Yaml.save(doc)` without a source (not loaded from a file) only logs a warning.

## 5. Cache (hot path)

| System property                            | Default | Effect                          |
|--------------------------------------------|---------|---------------------------------|
| `br.commons.serializer.cache.ttl.seconds`  | `60`    | Validity window of each entry   |
| `br.commons.serializer.cache.size`         | `256`   | Entries per cache (LRU)         |

Read once at startup: changing them requires a **restart**. `<= 0` disables.
The same source within the window returns the **same document instance**.

## 6. Quick rules for agents

1. Read only through `Serializer` methods; never use `Document` directly.
2. Never try to mutate a document; edit with `Serializer.builder(doc)`.
3. After writing a file, re-read with `Yaml.from(path)` (cache already invalidated).
4. Never write a YAML file outside `Yaml.save`: the cache won't know (§10.3).
5. A bad scalar in JSON throws `NumberFormatException`; in YAML it is `Logger.fatal`.
6. `asInts`/`asBytes`… on a missing key is `Logger.fatal`: check `contains`.
7. New format = a reader that produces `Document.of(...)` (§13).

## 7. Review checklist

- [ ] Variables typed as `Serializer`, not `Document`.
- [ ] Explicit defaults (`asInt(key, def)`) for optional configuration.
- [ ] Writes go through `Serializer.builder` + `Yaml.save`.
- [ ] New tests use `@TempDir` for files.

---

<!-- End of the usage section (up to line 100). Below: advanced implementation. -->

# Advanced implementation

## 8. Architecture

```
Serializer (read API)  <—  document.Document (only implementation) / document.Overlay
          ^                              ^
Json ——> json.JsonReader ——(validates)——> Document.of(tree, null, WIRE)
Json.write ——> json.JsonWriter
Yaml ——> yaml.YamlReader ——(validates)——> Document.of(tree, path, CONFIG)
Yaml.save ——> yaml.YamlWriter ——> file + DocumentCache.invalidate(path)
```

```
serializer/            Serializer, Json, Yaml                 (the API)
├── document/          Document (+unwrap), DocumentCache, Overlay, Policy
│   ├── builder/       DocumentBuilder, Entry
│   ├── cache/         LruCache, CacheSettings
│   ├── navigation/    Trees                                  (dotted paths, flat index)
│   └── binding/       TreeBinder, Scalars                    (records, scalar coercion)
├── json/              JsonReader, JsonWriter, JsonParser
└── yaml/              YamlReader, YamlWriter
```

- `document`: the representation and everything shared; knows no format.
- `json`/`yaml`: syntax, validation and writing only; they do not see each other.
- `Json`/`Yaml` are `final` factory classes; they do not extend `Serializer`.
- Sub-packages are `public` only because Java has no sub-package access.
  `SerializerArchitectureTest` (ArchUnit) enforces: outside `br.commons.serializer..`
  only the root package is used; formats are independent; `document` ignores formats.

## 9. `Document` — the invariant

`Document.of` deep-copies and validates:

- Map → `Collections.unmodifiableMap(LinkedHashMap)` (keeps order, allows `null`).
- List → `Collections.unmodifiableList(ArrayList)`.
- Scalar: `String`, `Number`, `Boolean`, `null` pass; an enum becomes `name()`;
  anything else becomes `toString()` (e.g. `UUID`).
- Non-`String` map key → `IllegalArgumentException`.

Sub-documents (`subtree`, `list`) share the subtree without copying and inherit
`source` and `policy`. `subtree` of a list returns `{lastSegment: list}`.

Lookup: flat index (`Trees.flatten`) built on first use (`volatile` field,
benign race); a list is indexed whole and per element. A path that is an
intermediate map falls back to `Trees.find`. Non-numeric list index →
`Logger.fatal`; index past the end → warning + `null`.

`contains` = key in the index (even with a `null` value) or found in the tree.

## 10. Two-level cache

### 10.1 `LruCache<K,V>`

- Access-order `LinkedHashMap` + `removeEldestEntry`; each entry keeps `expiresAt`.
- `lookup` returns the value (may be `null`, via an internal sentinel) or
  `LruCache.MISS`; no lambda, no allocation. The caller computes and calls `put`.
- Plain `ReentrantLock` (`get` on an access-order map mutates it).
- Injectable clock (`LongSupplier`) — tests use `AtomicLong` (`LruCacheTest`).
- Two threads may compute the same value; the second overwrites. Safe
  because everything stored is immutable.

### 10.2 Values per document

Each `Document` owns an `LruCache<Key(path, type), Object>`: converted scalars
(absence = `null` included), `subtree` (`Serializer.class`) and `list`
(`List.class`). Since the document is immutable there is never invalidation, only
expiry/eviction. `as`/`asList` are **not** cached (fresh binding on every call).

### 10.3 Documents per source — `DocumentCache`

Keys: normalized absolute `Path`, `"classpath:" + resource`, JSON text.
`Yaml.from(Map)` and `Yaml.from((Path) null)` bypass the cache.

- A file changed externally stays stale until the window expires.
- A missing/unreadable file is cached too (empty document) for the window.
- `YamlWriter.save` invalidates the source in `finally`, even if writing fails.
- JSON bodies are cached: up to `size` texts retained for `ttl`. If that is heavy
  (large bodies), reduce `size` or disable.

## 11. `Policy`

| Policy   | Who     | Text       | Invalid number                          |
|----------|---------|------------|-----------------------------------------|
| `WIRE`   | JSON    | raw        | `NumberFormatException` propagates      |
| `CONFIG` | YAML    | `trim()`   | `Logger.fatal` (terminates the process) |

In `CONFIG`, a type not supported by `Scalars` falls to `type.cast` (fails at the
caller). A builder without a base uses `CONFIG`; with a base it inherits its policy.

## 12. `DocumentBuilder`

- Works on a mutable copy; `build()` always yields a new `Document` (the builder
  is reusable).
- `put("a.b.c", v)`: creates missing maps; a `Map`/`List` value is copied.
- `append(key, Entry...)`: adds to the existing list or creates it.
- `append(Entry...)`/`append(key, String)`: top-level keys.
- An intermediate segment that is a list: a new map is appended to it.

## 13. `Overlay` and new formats

`Serializer.overlay(base, overrides)`: scalars check `overrides` first
(on every call; `trim`); `asInts`… inherit it through the default over `asString`;
structure and binding go to `base`. `Document.unwrap` goes through the overlay
(persisting an overlay writes `base`, without the overrides).

New format (e.g. TOML): package `br.commons.serializer.toml` with a reader that
validates the syntax and calls `DocumentCache.cached(source, () -> Document.of(tree, path, policy))`;
a new static factory (`Toml`) returning `Serializer`. Do not add read methods
outside `Serializer`.

Tests mirror main: `src/test/java/br/commons/serializer/` (`document/…`, `yaml/`, `JsonTest`,
`SerializerArchitectureTest`).
