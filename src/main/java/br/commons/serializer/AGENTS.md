# br.commons.serializer — ler, criar e persistir documentos

Documentos JSON, YAML e XML (formatos em `br.commons.lang`, ver §8) lidos para uma
representação interna comum e consultados por uma única API. Formatos só leem/validam (YAML e XML também persistem).

| Tipo                               | Papel                                                          |
|------------------------------------|----------------------------------------------------------------|
| `Serializer`                       | **Única API de leitura**; também `builder(...)` e `overlay(...)` |
| `lang.Json`                        | Fábricas: `from(String)`, `write(Object)`                      |
| `lang.Yaml`                        | Fábricas: `from(Path)`, `fromClasspath`, `from(Map)`, `from()`, `save` |
| `lang.Xml`                         | Fábricas: `from(String)`, `from(Path)`, `fromClasspath`, `write`, `save` |
| `document.builder.DocumentBuilder` | Cria/edita documento: `put`, `append`, `clear`, `build`        |
| `document.builder.Entry`           | Par chave/valor para `append`                                  |
| `document.Document`                | Representação interna imutável; uso interno (ver §8)           |

## 1. Ler

```java
Serializer body = Json.from(text);                      // IllegalArgumentException se inválido
Serializer cfg  = Yaml.from(Path.of("app.yaml"));       // vazio se o arquivo falta/ilegível
Serializer res  = Yaml.fromClasspath("defaults.yaml");  // vazio se o recurso falta
Serializer mem  = Yaml.from(Map.of("port", 8080));      // cópia do map; Yaml.from() = vazio
Serializer xml  = Xml.from(Path.of("nf.xml"));          // atributo/filho = chave; mapeamento §14
```
Declare sempre a variável como `Serializer`: `Json`/`Yaml`/`Xml` não são tipos de documento.

## 2. Consultar

Chave = caminho pontuado; segmento numérico indexa lista (`server.ports.0.number`).

```java
cfg.contains("server.port");                  // existe? (valor null conta)
cfg.asString("server.host");                  // null se ausente
cfg.asInt("server.port", 8080);               // com default
cfg.asEnum("log.level", LogLevel.class);      // null se ausente ou nome desconhecido
cfg.asInts("ports");                          // "1, 2, 3" -> int[]
cfg.asTuples("props");                        // lista de {name, value} -> Tuple[]
Serializer db = cfg.subtree("db");            // subdocumento ou null
List<Serializer> users = cfg.list("users");   // itens; vazio (+warn) se não for lista
cfg.folder();                                 // pasta do arquivo de origem ou null
```

Escalares: `asString/asByte/asShort/asInt/asLong/asFloat/asDouble/asBoolean`;
`isEmpty()` = documento ausente, map vazio ou lista vazia.

## 3. Binding para tipos

```java
record Server(String host, int port) {}
Server s = Yaml.from(path).as(Server.class);              // documento inteiro
List<Item> items = Json.from("[...]").asList(Item.class); // lista de topo
```

Suporta `record` (construtor canônico), `List<T>` declarada, escalares, `UUID`, enums.

## 4. Criar, editar e persistir

```java
Serializer doc = Serializer.builder()
        .put("server.port", 8080)                   // cria maps intermediários
        .append("users", "name", "ana")             // item {name: ana} na lista users
        .append(new Entry("env", "prod"))           // chave de topo
        .build();
Yaml.save(doc, Path.of("app.yaml"));

Serializer edited = Serializer.builder(cfg).put("server.port", 9090).build();
Yaml.save(edited);                                  // grava na origem de cfg
String json = Json.write(edited);                   // documentos também serializam
```

Documento **imutável** (`builder(base)` copia). `Yaml.save` sem origem: warning; falha de escrita: log de erro.

## 5. Cache (caminho quente)

| System property                            | Default | Efeito                          |
|--------------------------------------------|---------|---------------------------------|
| `br.commons.serializer.cache.ttl.seconds`  | `60`    | Janela de validade de cada entrada |
| `br.commons.serializer.cache.size`         | `256`   | Entradas por cache (LRU)        |

Lidas uma vez (`CacheSettings`): mudar exige **restart**; `<= 0` desliga. Mesma
origem dentro da janela devolve a **mesma instância** de documento.

## 6. Regras rápidas para agentes

1. Leia só por `Serializer` (nunca `Document`); edite com `Serializer.builder(doc)`, nunca mute.
2. Grave só por `Yaml.save`/`Xml.save` (invalidam o cache, §10.3); releia com `from(path)`.
3. Escalar inválido em JSON/XML lança `NumberFormatException`; em YAML é `Logger.fatal`.
4. `asInts`/`asBytes`… em chave ausente é `Logger.fatal`: confira `contains`.
5. Formato novo = leitor que produz `Document.of(...)` (§13).

## 7. Checklist de revisão

- [ ] Variáveis tipadas como `Serializer`, não `Document`.
- [ ] Defaults explícitos (`asInt(key, def)`) para configuração opcional.
- [ ] Escritas passam por `Serializer.builder` + `Yaml.save`/`Xml.save`.
- [ ] Testes novos usam `@TempDir` para arquivos.

---

<!-- Fim da seção de uso (até a linha 100). Abaixo, implementação avançada. -->

# Implementação avançada

## 8. Arquitetura

```
Serializer (API de leitura)  <—  document.Document (única implementação) / document.Overlay
          ^                              ^
lang.Json ——> lang.json.JsonReader ——(JsonParser valida)——> Document.of(tree, null, WIRE)
lang.Json.write ——> lang.json.JsonWriter
lang.Yaml ——> lang.yaml.YamlReader ——(valida)——> Document.of(tree, path, CONFIG)
lang.Yaml.save ——> lang.yaml.YamlWriter ——> arquivo + DocumentCache.invalidate(path)
lang.Xml ——> lang.xml.XmlReader ——(StAX valida)——> Document.of(tree, path|null, WIRE)
lang.Xml.write/save ——> lang.xml.XmlWriter ——> texto | arquivo + DocumentCache.invalidate(path)
```

```
serializer/            Serializer                             (a API)
└── document/          Document (+unwrap), DocumentCache, Overlay, Policy
    ├── builder/       DocumentBuilder, Entry
    ├── cache/         LruCache, CacheSettings
    ├── navigation/    Trees                                  (caminhos pontuados, índice plano)
    └── binding/       TreeBinder, Scalars                    (records, coerção de escalar)
lang/                  Json, Yaml, Xml                        (fachadas dos formatos)
├── json/              JsonReader, JsonWriter, JsonParser
├── yaml/              YamlReader, YamlWriter
└── xml/               XmlReader, XmlWriter
```

- `document`: a representação e tudo que é compartilhado; não conhece formato.
- `json`/`yaml`/`xml`: só sintaxe, validação e escrita; um não vê o outro.
- `Json`/`Yaml`/`Xml` são classes `final` de fábrica; não estendem `Serializer`.
- Subpacotes são `public` só porque Java não tem acesso por subpacote.
  `SerializerArchitectureTest` (ArchUnit, sobre `br.commons`) garante: fora de
  `br.commons.serializer..`, só `lang.json`/`lang.yaml`/`lang.xml` veem `document`;
  formatos só são alcançados pelas fachadas `lang.Json`/`lang.Yaml`/`lang.Xml`; formatos
  independentes; `serializer` inteiro ignora `br.commons.lang`.
- Erros de validação/binding vêm localizados de `br.commons.locale.i18n.Errors`
  (`jsonUnexpectedContent`, `yamlRootNotMap`, `xmlMalformed`, `xmlInvalidName`, `keyMustBeText`,
  `unsupportedTargetType`, `cannotBindNull`, `buildFailed`…).

## 9. `Document` — o invariante

`Document.of` copia profundamente e valida:

- Map → `Collections.unmodifiableMap(LinkedHashMap)` (mantém ordem, aceita `null`).
- List → `Collections.unmodifiableList(ArrayList)`.
- Escalar: `String`, `Number`, `Boolean`, `null` passam; enum vira `name()`;
  o resto vira `toString()` (ex.: `UUID`).
- Chave de map não `String` → `IllegalArgumentException`.

Subdocumentos (`subtree`, `list`) compartilham a subárvore sem copiar e herdam
`source` e `policy`. `subtree` de uma lista devolve `{últimoSegmento: lista}`.

Busca: índice plano (`Trees.flatten`) criado no primeiro uso (campo `volatile`,
corrida benigna); lista é indexada inteira e por elemento. Caminho que é map
intermediário cai em `Trees.find`. Índice de lista não numérico →
`Logger.fatal`; índice além do fim → warning + `null`.

`contains` = chave no índice (mesmo com valor `null`) ou achada na árvore.

YAML: raiz que não é map → `IllegalArgumentException` (`yamlRootNotMap`); em
`Yaml.from(Path)` toda falha de leitura é capturada → log de erro + vazio; em
`fromClasspath` só recurso ausente vira vazio (raiz inválida/IO lança).
`asList` em documento que não é lista → warning + lista vazia.

## 10. Cache em dois níveis

### 10.1 `LruCache<K,V>`

- `LinkedHashMap` em ordem de acesso + `removeEldestEntry`; cada entrada guarda `expiresAt`.
- `lookup` devolve o valor (pode ser `null`, via sentinela interno) ou
  `LruCache.MISS`; sem lambda, sem alocação. Quem chama calcula e faz `put`.
- `ReentrantLock` simples (`get` em map de ordem de acesso o altera).
- Relógio injetável (`LongSupplier`) — testes usam `AtomicLong` (`LruCacheTest`).
  `LruCache.standard()` usa `CacheSettings` + `System::nanoTime`.
- Duas threads podem calcular o mesmo valor; a segunda sobrescreve. Seguro
  porque tudo que é guardado é imutável.

### 10.2 Valores por documento

Cada `Document` tem um `LruCache<Key(path, type), Object>`: escalares
convertidos (ausência = `null` incluída), `subtree` (`Serializer.class`) e `list`
(`List.class`). Como o documento é imutável nunca há invalidação, só
expiração/despejo. `as`/`asList` **não** são cacheados (binding novo a cada chamada).

### 10.3 Documentos por origem — `DocumentCache`

Chaves: `Path` absoluto normalizado, `"classpath:" + recurso`, texto JSON/XML.
`Yaml.from(Map)`/`Yaml.from()` e `Yaml.from((Path) null)` não passam pelo cache.

- Arquivo alterado por fora fica velho até a janela expirar.
- Arquivo ausente/ilegível também é cacheado (documento vazio) pela janela.
- `YamlWriter.save`/`XmlWriter.save` invalidam a origem no `finally`, mesmo se a escrita falhar.
- Corpos JSON são cacheados: até `size` textos retidos por `ttl`. Se pesar
  (corpos grandes), reduza `size` ou desligue.

## 11. `Policy`

| Policy   | Quem    | Texto      | Número inválido                          |
|----------|---------|------------|------------------------------------------|
| `WIRE`   | JSON, XML | cru      | `NumberFormatException` propaga          |
| `CONFIG` | YAML    | `trim()`   | `Logger.fatal` (encerra o processo)      |

Em `CONFIG`, tipo não suportado por `Scalars` cai em `type.cast` (falha em quem
chama). Builder sem base usa `CONFIG`; com base herda a policy dela.

## 12. `DocumentBuilder`

- Trabalha numa cópia mutável; `build()` sempre gera `Document` novo (builder
  reutilizável). `Serializer.builder()` = `DocumentBuilder.create()`;
  `builder(base)` = `DocumentBuilder.edit(base)` (mesma origem e policy).
- `put("a.b.c", v)`: cria maps faltantes; valor `Map`/`List` é copiado.
- `append(key, Entry...)`/`append(key, name, value)`: adiciona à lista
  existente ou a cria.
- `append(Entry...)`/`append(key, String)`: chaves de topo.
- Segmento intermediário que é lista: um map novo é anexado a ela.

## 13. `Overlay` e formatos novos

`Serializer.overlay(base, overrides)`: escalares consultam `overrides` primeiro
(a cada chamada; `trim`); número inválido no override → `Logger.fatal`; enum/UUID
inválido → `null`. `asInts`… herdam via default sobre `asString`; estrutura e
binding vão para `base`. `Document.unwrap` atravessa o overlay (persistir um
overlay grava `base`, sem os overrides).

Formato novo (ex.: TOML): pacote `br.commons.lang.toml` com leitor que
valida a sintaxe e chama `DocumentCache.cached(source, () -> Document.of(tree, path, policy))`;
nova fachada `br.commons.lang.Toml` devolvendo `Serializer`; inclua o pacote nas
regras de `SerializerArchitectureTest`. Não adicione métodos de leitura fora de
`Serializer`.

Testes espelham o main: `src/test/java/br/commons/serializer/` (`document/…`,
`SerializerArchitectureTest`) e `src/test/java/br/commons/lang/` (`json/JsonTest`,
`yaml/`, `xml/XmlTest`).

## 14. Mapeamento XML

Raiz = documento (nome descartado na leitura); atributo e
elemento filho viram chave (prefixo de namespace some); irmãos repetidos viram
lista — **um só item não é lista** (`list("x")` avisa; use `x.0` só com 2+);
texto ao lado de atributos/filhos vira chave `value`; elemento vazio = `null`;
todo escalar é texto (coerção por `Scalars`, policy `WIRE`). DTD/entidades
externas desligadas (XXE). Escrita: chave → elemento (nunca atributo), lista sob
chave repete o elemento, lista solta vira `<item>`; `Xml.write(v)` usa raiz
`root`, `Xml.write(v, "nome")` escolhe; `Xml.save` mantém a raiz do arquivo
existente, grava indentado com declaração UTF-8 e invalida o cache.
