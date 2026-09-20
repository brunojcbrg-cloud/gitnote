# RESULTADO 10 — FASE D

## O que mudou

- `HistoricoDatabase` guarda `Aberturas(relativePath, abertaEmMillis)` em um banco Room separado. `GridViewModel.abrirNota` grava a abertura nele e atualiza `Notes.lastOpenedTimeMillis` antes de navegar.
- `Notes` ganhou `lastOpenedTimeMillis`; `RepoDatabase` passou da versão 2 para a 3. Na reindexação, `StorageManager` lê as aberturas duráveis e `clearAndInit` recompõe a coluna. Sem abertura, ela recebe a data de modificação. O banco principal continua com migração destrutiva: a primeira abertura após a atualização exige reindexação completa.
- A ordem `UltimaVisualizacao` usa `MAX(lastOpenedTimeMillis, lastModifiedTimeMillis) DESC` e é o novo padrão de `sortOrder`. Uma escolha antiga gravada explicitamente nas preferências continua respeitada.
- A pasta abre com `LIMIT 10`. O rodapé mostra `Mostrar todas (N)` quando há mais notas; a ação remove o teto nesta visita. Trocar de pasta repõe 10. A busca mantém todos os resultados.
- A abertura explícita do banco principal antes da comparação de `databaseCommit` garante que o callback da migração destrutiva zere a preferência a tempo de iniciar a reindexação, mesmo quando o commit Git não mudou.

Commit de implementação: `33576e1` (`handoff 10 D: historico de aberturas e limite inicial de 10 notas`). Nenhuma alteração de segurança não commitada entrou no commit; em `AppPreferences.kt`, somente a linha do padrão de ordenação foi incluída.

## Números PERF_ antes e depois

A Fase D não pede um teste `PERF_` nem define uma medição temporal antes/depois. Portanto **não há número `PERF_` da Fase D para comparar**; não atribuí a esta fase medições de outras funcionalidades. O critério numérico funcional exercitado foi **40 → 10 → 40**, com retorno a **10** ao trocar de pasta. O cenário de ordenação foi `(3, sem abertura), (1, 5), (2, sem abertura)` → **2ª, 1ª, 3ª**.

O log do mesmo CI ainda registra testes `PERF_` anteriores, sem relação causal com a Fase D: `PERF_NOTES_CONTAINING_TAG` mediana **207,959 ms**, `PERF_MARKDOWN_LIVE_PREVIEW` mediana **18,502 ms**, `PERF_TABLE_REGION_AT` mediana **1,119 ms** e `PERF_TABLE_REGION_AT_SEM_TABELA` mediana **0,689 ms**.

## Testes escritos e o que provam

1. `HistoricoDurabilidadeTest.aberturaSobreviveAReindexacaoEOrdenaComDataDeModificacao` — Robolectric com `HistoricoDatabase` em arquivo e `RepoDatabase` em memória: grava a abertura, fecha e reabre o banco durável, roda `clearAndInit` duas vezes, executa `ORDER BY MAX(...)` no SQLite e confirma a ordem 2ª, 1ª, 3ª antes e depois. `clearAndInit` recebe no teste um predicado de extensão para não carregar a função Rust nativa indisponível na JVM.
2. `TetoDeNotasTest.quarentaNotasComecamEmDezEVoltandoAPastaRecomecamEmDez` — exercita o limite inicial, a remoção do limite nesta visita e o retorno a 10 ao trocar de pasta.
3. `TetoDeNotasTest.buscaNaoAplicaTetoEDevolveMaisDeDezResultados` — confirma que o limite e o rodapé não se aplicam a buscas.
4. `GradeFaseDSqlTest.listagemDaPastaOrdenaPelaDataMaisRecenteEAplicaLimite` — confere no SQL de produção a ordenação por abertura/modificação, o `LIMIT` e o filtro de pasta exata, sem janela.
5. `GradeFaseDSqlTest.buscaNaoTemLimiteEFluxoDaGradeReiniciaOTetoAoTrocarPasta` — confere que o SQL da busca não contém `LIMIT` e que o fluxo do VM liga o reset, a ação “mostrar todas”, a consulta de contagem e a busca sem teto.

No job [Unit tests + APK](https://github.com/brunojcbrg-cloud/gitnote/actions/runs/35519029186/job/106099772950), os **cinco testes novos aparecem como `PASSED`**, os **326 testes listados passaram**, `testDebugUnitTest` e o build do APK terminaram com `BUILD SUCCESSFUL`.

## O que não foi feito e por quê

- Não foi construído `GridViewModel` real em teste: o caminho passa por `StorageManager`/`GitManager` e tenta carregar `git_wrapper` na JVM. É a limitação já registrada nas Fases A e C; as transições de teto foram testadas no módulo puro e a ligação do VM foi inspecionada no código de produção.
- Não foi executada a consulta completa `gridNotes`/`gridNotesWithQuery` com `parentPath`, `fullName` e `rank` no Robolectric: o `RequerySQLiteOpenHelperFactory` carrega uma `.so` Android ausente no runner JVM. É a lacuna aceita pelo Bruno na Fase B. A expressão de ordenação foi executada no SQLite do teste de durabilidade, e o SQL de produção foi verificado como texto.
- Nenhum `PERF_` novo foi escrito porque o critério de aceitação da Fase D não o pede. Não houve teste em aparelho nem compilação local, conforme a seção 0 do handoff.
- Nenhuma fase após D foi iniciada; I, Parte V e J.2+ continuam fora da lista desta sessão.

## Divergência entre handoff e código real

`onDestructiveMigration` só zera `databaseCommit` quando o banco é efetivamente aberto. No caminho real de `StorageManager.updateDatabaseWithoutLocker`, a comparação com o commit Git acontecia antes da primeira consulta ao banco; se o commit não mudasse, a rotina podia retornar antes de disparar a migração. A Fase D abriu o banco explicitamente antes dessa comparação para cumprir a reindexação descrita no handoff. Isto foi constatado por leitura do fluxo e corrigido; não houve medição em aparelho.

## Release do CI

Push do commit `33576e1`: run [35519029186](https://github.com/brunojcbrg-cloud/gitnote/actions/runs/35519029186) **verde**, release [b56 — 26.08.1.56](https://github.com/brunojcbrg-cloud/gitnote/releases/tag/b56), publicada em 2026-09-20T15:20:01Z.
