# br.commons.debug — cronometragem condicional

`Execution` mede tempo de um bloco e loga em DEBUG. Se DEBUG estiver desligado
para o chamador, roda o bloco direto, sem custo de medição.

## 1. Uso

```java
List<Item> itens = Execution.millis(() -> repo.listAll());   // Supplier → retorna valor
Execution.nanos(() -> cache.warmUp());                        // Runnable
```

Saída (DEBUG): `<metodo> time: 12 ms`. O rótulo vem de `Meta.method(lambda)`.

## 2. Caminho quente

Sem `caller`, o gate de log pode precisar descobrir o chamador pela pilha.
Em código chamado com frequência, passe a classe como constante:

```java
private static final String CALLER = Meta.fqn(MinhaClasse.class);
...
return Execution.nanos(CALLER, () -> calcular());
```

## 3. Relógio próprio

```java
Execution.time(() -> tarefa(), Time::millis, "ms");
Execution.time(CALLER, () -> tarefa(), meuRelogio::ler, "ticks");
```

## 4. Regras rápidas para agentes

1. Nunca deixe `System.nanoTime()` + log manual: use `Execution`.
2. Em hot path, sempre variante com `caller` constante.
3. Exceção do bloco propaga normalmente (o tempo é logado no `finally`).
4. Não use para métricas de produção: só existe em DEBUG.

## 5. Checklist de revisão

- [ ] Hot path usa `caller` constante.
- [ ] Bloco medido não depende de efeito colateral do log.

---

<!-- Fim da seção de uso (até a linha 100). Abaixo, implementação avançada. -->

# Implementação avançada

## 6. Funcionamento

- `Logger.enabled(LogLevel.DEBUG, caller)` decide. `caller` vazio
  (`Strings.EMPTY`) faz o logger resolver o chamador — custo de stack walk
  quando há override de nível por pacote.
- Com DEBUG ligado: `Meta.method(supplier)` resolve nome do lambda (custo de
  reflexão) **antes** do relógio — não entra na medida.
- Tempo logado mesmo se o bloco lançar (bloco `finally`), sem indicar falha.

## 7. Sobrecargas

Pares `Supplier<T>` / `Runnable` para `nanos`, `millis`, `time`. Lambda de
expressão com valor (`() -> list.add(x)`) é compatível com os dois; o
compilador escolhe `Supplier` (mais específico). Para forçar a variante
`Runnable`, use bloco: `() -> { list.add(x); }`.

## 8. Extensão

Precisa de outro nível (TRACE/VERBOSE) ou de histograma? Crie sobrecarga com
`LogLevel` em vez de duplicar a lógica; mantenha o atalho sem medição quando o
nível está desligado.
