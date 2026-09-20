# RESULTADO 10 — FASE H, item H.2 (só H.2; H.1/H.3/H.4/H.5 não foram feitos)

## O que mudou

- `MarkdownLivePreviewTransformation.kt`: `MarkdownScanner.scan(source)` — função pura —
  era chamado do zero em todo `filter()`, mesmo quando só o cursor anda entre linhas (o
  `MarkDown.kt`, fora do escopo desta sessão, cria uma `MarkdownLivePreviewTransformation`
  **nova** a cada mudança de seleção via `remember(textContent.text, textContent.selection, …)`).
  Um cache por instância nunca seria reaproveitado nesse desenho — por isso o cache de uma
  entrada só (`cachedSource`/`cachedSpans`) foi posto no **companion object** (compartilhado
  entre instâncias), não na instância. `scanCached(source)` compara por igualdade de conteúdo
  (`==`) e só rechama `MarkdownScanner.scan` quando o texto muda.
- Nenhum outro arquivo do produto foi tocado — nem `MarkDown.kt` (isso é H.1/Fase G, fora do
  pedido desta rodada), nem `MarkdownScanner.kt`.
- Exposto `internal fun scanCachedForTest` só para o teste de identidade de lista poder chamar
  o cache diretamente (sem isso não dá para provar reaproveitamento sem depender de tempo).

## Por que isso importa além da velocidade

Confirmado o texto do handoff: a Fase J (migração para `TextFieldState`) chama o
`OutputTransformation` novo *toda vez que um texto precisa ser exibido* e o framework descarta
o resultado — sem memória própria. Sem H.2, a migração trocaria de API e manteria o custo do
`scan`. H.2 é o que sobrevive à troca de API: o cache não depende de `remember`/Compose, é só
Kotlin puro guardando o último par (texto, spans).

**Import­ante para quem for medir o resultado final:** H.2 sozinho, **sem H.1**, só corta o
custo da *varredura* (`MarkdownScanner.scan`). O resto do `filter()` — construir `hiddenRanges`,
alocar os dois `IntArray(source.length + 1)`, reconstruir o `AnnotatedString` inteiro — continua
rodando em toda chamada, porque isso depende de `activeLines` (que muda a cada seleção) e
`MarkDown.kt` continua recriando a instância a cada seleção. A régua de 27,4 ms por tecla só cai
para poucos milissegundos **com H.1 também** (texto do handoff, seção H, item H.3), que não foi
tocado nesta sessão por instrução explícita.

## Medição (PERF_H2_SCAN_CACHE)

Nota sintética de 1.946 linhas / 180.046 caracteres, mesmo padrão de `MarkdownTablePerfTest.kt`:
5 amostras, mediana, `println` com prefixo `PERF`. Números do CI (run
[35524589778](https://github.com/brunojcbrg-cloud/gitnote/actions/runs/35524589778), verde):

| Cenário | Amostras (ms) | Mediana (ms) |
|---|---|---|
| Texto muda a cada chamada (pior caso — cache nunca acerta) | `[32.597, 21.07, 15.593, 7.919, 7.847]` | **15.593** |
| Texto igual, só a seleção muda (caso que o cache resolve) | `[25.509, 7.883, 16.313, 10.997, 7.861]` | **10.997** |

Para contexto, o teste que já existia (`transformsAFixtureWithTheDimensionsOfARealLargeVaultNote`,
Fase H ainda pendente formalmente, mas o teste já estava no repo) mediu nesta mesma rodada
`PERF_MARKDOWN_LIVE_PREVIEW median_ms=14.072` (7 amostras) — mesma ordem de grandeza do
cenário "texto muda", como esperado (esse teste também gera texto novo a cada chamada).

**Runner ruidoso** (mesma observação já registrada nas Fases E/F/J.1 para outros `PERF_*`):
as amostras variam por um fator de ~4× dentro do mesmo cenário. Por isso o teste **não faz
`assertTrue` sobre tempo** — só imprime e deixa a decisão para quem lê o relatório. A mediana do
cenário "só seleção muda" (10,997 ms) ficou abaixo da de "texto muda" (15,593 ms), na direção
esperada, mas a proporção não é grande porque, como explicado acima, o resto do pipeline de
`filter()` continua sendo refeito nos dois cenários — só a fração do `scan` em si foi cortada.

## Prova funcional (não depende de tempo)

`scanCacheReusesTheSameSpanListWhenTheTextDidNotChange` prova por **identidade de referência**
de `List<MdSpan>` (não por tempo, que é ruidoso em CI): texto de conteúdo igual (duas
`String` diferentes na memória, mesmo conteúdo) devolve a mesma lista; texto diferente força
nova varredura; voltar ao texto anterior revarre (o cache tem uma entrada só, não um mapa).

## Testes tocados

Só em `MarkdownLivePreviewTransformationTest.kt` (arquivo autorizado):

1. `scanCacheReusesTheSameSpanListWhenTheTextDidNotChange` (novo) — prova funcional do cache.
2. `scanCacheHelpsOnlyWhenTheTextIsUnchanged` (novo) — `PERF_H2_SCAN_CACHE`, os dois cenários
   pedidos.

Nenhum teste existente de `MarkdownLivePreviewTransformationTest` ou `MathRenderingTest` foi
alterado. Todas as asserções de marcadores escondidos, conversão de fórmula e mapeamento de
offset continuam as mesmas de antes — o cache é transparente para o resultado.

## CI

- PR: [brunojcbrg-cloud/gitnote#3](https://github.com/brunojcbrg-cloud/gitnote/pull/3)
- Resultado final: **verde** — `Build-Debug` (assembleDebug + lintDebug + testDebugUnitTest) em
  5m14s e `Rust tests` em 19s, run
  [35524589778](https://github.com/brunojcbrg-cloud/gitnote/actions/runs/35524589778).
- `gh pr view 3 --json files` confirma que o diff da PR contra a `master` mostra **só** os dois
  arquivos autorizados (`MarkdownLivePreviewTransformation.kt` e seu teste) — as duas mesclagens
  abaixo não aparecem no diff porque já estão publicadas na `master`.

### Duas rodadas vermelhas antes desta, sem relação com H.2 — registrado para transparência

A branch foi criada a partir de `43b8863` (Fase F), e a instrução avisava que outra sessão
podia estar fechando a Fase F na `master` ao mesmo tempo. Foi exatamente isso: as duas primeiras
rodadas de CI desta PR falharam em testes Robolectric de Fase F (`DobraTest`,
`SumarioLateralTest`, `MarkdownCustomInnerRecolherTest`) — arquivos fora do escopo desta sessão
(só podia tocar `MarkdownLivePreviewTransformation.kt` e seus testes) e que a outra sessão
corrigiu na `master` enquanto isso, em dois commits:

- `27b8bfc` — corrigiu `DobraTest` e tentou corrigir `SumarioLateralTest`/
  `MarkdownCustomInnerRecolherTest`.
- `829456a` — corrigiu de fato os dois restantes (bug de timing do parse assíncrono da lib de
  markdown em `LaunchedEffect`, e um nó fora da área clipada por `horizontalScroll` sem
  `performScrollTo`).

Em vez de tocar esses arquivos (proibido pelo escopo desta tarefa, e arriscaria colisão com a
sessão que já estava consertando exatamente isso), esta sessão deu **merge da `master` para
dentro de `handoff10-h2`** nos dois momentos em que a correção apareceu lá — confirmado antes de
cada merge, por `git show --stat`, que nenhum commit tocava `MarkdownLivePreviewTransformation.kt`
nem seus testes. As três tentativas de CI, para registro:

| Run | Resultado | Motivo |
|---|---|---|
| [35523356902](https://github.com/brunojcbrg-cloud/gitnote/actions/runs/35523356902) | vermelho | 3 testes de Fase F (`DobraTest`, `SumarioLateralTest`, `MarkdownCustomInnerRecolherTest`) — corrigido por `27b8bfc` na master |
| [35523919039](https://github.com/brunojcbrg-cloud/gitnote/actions/runs/35523919039) | vermelho | 2 testes de Fase F ainda instáveis (`SumarioLateralTest`, outro caso de `MarkdownCustomInnerRecolherTest`) — corrigido por `829456a` na master |
| [35524589778](https://github.com/brunojcbrg-cloud/gitnote/actions/runs/35524589778) | **verde** | — |

Em nenhuma das três rodadas um teste de `MarkdownLivePreviewTransformationTest` ou
`MathRenderingTest` falhou.

## O que não foi feito (por instrução, não por esquecimento)

- **H.1** (parar de refazer tudo quando só o cursor anda, em `MarkDown.kt`) — fora de escopo,
  é o item que faltava para o cache de H.2 se traduzir em ganho maior na régua de 27,4 ms/tecla.
- **H.3** (medir de novo depois de H.1+H.2 e decidir se H.4 é necessário), **H.4** (edição por
  seção) e **H.5** (teto no histórico de desfazer + Parcelable) — não pedidos nesta rodada.
- `ESTADO_10.md` não foi editado, por instrução explícita (a master está em uso). Linha pronta
  para o Bruno colar:

```
| H.2 | entregue | 2026-09-20 | 7543327 (branch `handoff10-h2`, PR #3) | — |
```
