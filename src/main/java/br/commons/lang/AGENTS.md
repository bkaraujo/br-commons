# br.commons.lang — linguagens: HTML, htmx, JSON, YAML

"lang" = linguagens que a biblioteca lê/escreve (não confundir com `java.lang`).
Fachadas de uso na raiz; implementação nos subpacotes. Cada linguagem tem um
único ponto de entrada.

| Fachada          | Subpacote                                | Documentação              |
|------------------|------------------------------------------|---------------------------|
| `Html5`, `Html4` | `html/` (`core`, `form`, `html4`, `html5`) | `html/AGENTS.md`          |
| `Htmx`           | `htmx/` (enums, `SwapSpec`, headers `HX-*`) | `htmx/AGENTS.md`          |
| `Json`           | `json/` (`JsonReader`, `JsonWriter`, `JsonParser`) | `serializer/AGENTS.md` |
| `Yaml`           | `yaml/` (`YamlReader`, `YamlWriter`)     | `serializer/AGENTS.md`    |

```java
import static br.commons.lang.Html5.*;      // DSL HTML 5 inteira (estáticos herdados de Html)
import static br.commons.lang.Htmx.*;       // atributos hx-*
Serializer cfg = Yaml.from(Path.of("app.yaml"));
Serializer body = Json.from(texto);
```

## 1. Dependências (ArchUnit)

- Subpacotes nunca veem as fachadas `Html4`/`Html5`/`Htmx`
  (`HtmlArchitectureTest`); `htmx` pode usar `Json` (eventos `HX-Trigger`).
- `htmx` depende de `html` e de `br.commons.protocol.http`; nada depende de
  `htmx`; `lang` não conhece `br.commons.web` (`HtmlArchitectureTest`).
- `json`/`yaml` só são alcançados pelas fachadas, um não vê o outro e são os
  únicos fora de `br.commons.serializer` que constroem `serializer.document`
  (`SerializerArchitectureTest`).
- `br.commons.protocol` não conhece `lang` (`HTTPArchitectureTest`).

## 2. Regras rápidas para agentes

1. Código de aplicação importa só a fachada; nunca classes de `json/`/`yaml/`.
2. Linguagem nova: fachada na raiz (`final`, ou abstrata se a DSL é herdada
   como `Html5`) + subpacote; inclua o subpacote nas regras ArchUnit.
3. Testes espelham o main: `src/test/java/br/commons/lang/{html,htmx,json,yaml}`.
