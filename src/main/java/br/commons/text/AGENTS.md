# br.commons.text — normalização de texto

`TextNormalizer.normalize(String)` gera chave de comparação: remove acentos,
caixa alta, espaços colapsados e aparados.

## 1. Uso

```java
TextNormalizer.normalize("  São   Paulo ");   // "SAO PAULO"
TextNormalizer.normalize("ﬁm");               // "FIM" (NFKD decompõe ligaduras)
```

Use para busca, deduplicação e casamento de nomes vindos de fontes diferentes
(extrato bancário, planilhas, input do usuário).

## 2. Regras rápidas para agentes

1. Use só para **comparar/indexar**; nunca exiba nem persista o normalizado
   no lugar do original.
2. Normalize os dois lados da comparação.
3. Para meses de `Dates.MONTHS_PTBR` não use: remove o cedilha (`MARÇO` →
   `MARCO`).

## 3. Checklist de revisão

- [ ] Valor original preservado para exibição.
- [ ] Ambos os lados normalizados antes do `equals`.

---

<!-- Fim da seção de uso (até a linha 100). Abaixo, implementação avançada. -->

# Implementação avançada

## 4. Algoritmo

1. `Normalizer.Form.NFKD`: decomposição de compatibilidade (acentos viram
   marcas combinantes; ligaduras, superscritos e larguras viram forma base —
   `²` vira `2`).
2. Remove `\p{M}+` (marcas).
3. `toUpperCase(Locale.ROOT)` — independente do locale (evita o `i` turco).
4. `\s+` → espaço único; `strip()`.

## 5. Limites

- Pontuação e símbolos são mantidos (`"A-B"` ≠ `"A B"`).
- `ß` vira `SS` no upper case (tamanho muda).
- Caracteres sem decomposição (ex.: `ø`, `đ`) não são simplificados.
- Patterns pré-compilados: chamada é thread-safe e sem alocação de regex.

Para ignorar pontuação, crie método novo (`normalizeLoose`) em vez de mudar
`normalize` — chaves já persistidas/indexadas dependem do resultado atual.
