# ESTADO_10 — controle de fases do HANDOFF_10

Ordem: A, B, C, D, E, F, **K**, G, H, J.1
(I e Parte V bloqueadas em decisão do Bruno; J.2+ exige aval dele e outro modelo — fora desta lista)

~~**A Fase K entrou em 20/09** e tem prioridade sobre G e H~~ — **entregue em 20/09**
(`382aaf7`, release b67). A regressão da Fase B está consertada: pasta que só tem subpastas
volta a mostrar as subpastas na grade, e a leva de 10 voltou a ser recursiva. Detalhe em
`RESULTADO_10_K.md`. **A próxima da fila é a G.**

Detalhe de cada fase entregue: `RESULTADO_10_<FASE>.md`.

| Fase | Status | Data | Commit | Release |
|---|---|---|---|---|
| A | entregue | 2026-09-20 | fe69d70 | b52 (26.08.1.52) |
| B | entregue | 2026-09-20 | 1b74388 | b53 (26.08.1.53) |
| C | entregue | 2026-09-20 | 3b41b45 | b55 (26.08.1.55) |
| D | entregue | 2026-09-20 | 33576e1 | b56 (26.08.1.56) |
| E | entregue | 2026-09-20 | afa2580 | b58 (26.08.1.58) |
| F | entregue (F.1+F.2; F.3 adiada para H.4) | 2026-09-20 | 829456a | b64 (26.08.1.64) |
| K | entregue | 2026-09-20 | 382aaf7 | b67 (26.08.1.67) |
| G | pendente | | | |
| H.1 | pendente | | | |
| H.2 | entregue e mesclada | 2026-09-20 | 3591284 (squash de `handoff10-h2`, PR #3) | b65 (26.08.1.65) |
| H.3/H.4/H.5 | pendente | | | |
| J.1 | entregue e mesclada | 2026-09-20 | df8d403 (squash de `handoff10-j1`, PR #2, mesclada pelo Bruno) | build próprio cancelado (ver nota) |

### Números medidos na H.2 (e a ressalva que vale para a H.3)

Cache de uma entrada no `companion object` de `MarkdownLivePreviewTransformation`, memorizando
`MarkdownScanner.scan(source)`. No `companion` e não na instância porque `MarkDown.kt` cria uma
transformação nova a cada mudança de seleção — cache por instância nunca seria reaproveitado.

`PERF_H2_SCAN_CACHE`, nota de 1.946 linhas / 180.046 caracteres, 5 amostras, run 35524589778:

| Cenário | Amostras (ms) | Mediana |
|---|---|---|
| Texto muda a cada chamada (pior caso) | 32,597 / 21,07 / 15,593 / 7,919 / 7,847 | **15,593 ms** |
| Texto igual, só a seleção muda (o que o cache resolve) | 25,509 / 7,883 / 16,313 / 10,997 / 7,861 | **10,997 ms** |

**Ler esses números com desconfiança.** A dispersão vai de 7,8 a 32,6 ms nos *dois* cenários —
o runner do CI é ruidoso, e as medianas se sobrepõem. A direção é a esperada, mas **isto não
prova a economia**. Quem provar de verdade é a prova funcional que a sessão escreveu, que não
depende de tempo: `scanCacheReusesTheSameSpanListWhenTheTextDidNotChange` verifica **por
identidade de referência** que texto igual reaproveita a lista e texto diferente revarre.

**Para a H.3:** a H.2 sozinha corta o custo do `scan`, **não** a reconstrução inteira do
`filter()` (o `AnnotatedString` e os dois `IntArray` continuam sendo refeitos). A medição que
decide sobre a Fase J é depois da H.1, não agora — e precisa de mais amostras para vencer o
ruído do runner.

### Sessões abertas em 2026-09-20 (atualizar ao fechar)

| Onde | Branch | Fase | Arquivos que ela detém |
|---|---|---|---|
| `E:/Projetos/gitnote` | `master` | — | livre (Fase K fechada em 20/09; próxima é a **G**) |

**Nenhuma sessão de código aberta.** A próxima é a **Fase G**, na master.

Os worktrees de H.2 e J.1 já não existem no disco nem em `git worktree list`; as PRs #3 e #2
estão mescladas. O worktree `gitnote-pos` permanece registrado e não faz parte desta limpeza.

**O handoff 10 não está versionado.** Os handoffs 07, 08 e 09 estão no git; o
`HANDOFF_10_...md` só existe em disco, em três cópias soltas. Commitá-lo não dispara CI
(o `paths-ignore` de `**.md` cobre o `fork-release.yml`).

**Regra de convivência:** quem está num worktree entrega por pull request e **não** edita este
arquivo — deixa a linha pronta no fim do seu `RESULTADO_10_*.md`. Quem está na master edita
aqui normalmente, mas **preserva as linhas das outras fases**.

**Pendências que não são fase e dependem só do Bruno:**
- **F.3** (recolher no modo de edição) — adiado por decisão do handoff para a sessão de H.4.
- **I.0** — pasta de anexos e exceção no `.gitignore` do vault. Bloqueia a Fase I inteira.
- **I.7** — renderizar ou não imagem remota (`![](https://…)`).
- **Parte V** — a renomeação `06_Conhecimento` → `NOTAS`. Sem impedimento técnico desde a Fase C.
- **J.2** — exige Opus 5 com esforço máximo e aval dele; prompt pronto em `PROXIMO_PROMPT_J2.md`.

## Divergências entre handoff e código real

### Fase A (2026-09-20)

1. **`GitManager` bloqueia qualquer teste que construa `GridViewModel`.**
   `StorageManager` (usado por `GridViewModel`) inicializa
   `private val gitManager: GitManager = MyApp.appModule.gitManager` no corpo da classe,
   e o companion de `GitManager` roda `System.loadLibrary("git_wrapper")` no `init`.
   Essa lib nativa só existe dentro do APK — não carrega em teste JVM/Robolectric.
   Como `StorageManager` e `GitManager` são classes concretas (não abertas, não
   interfaces), não há como fornecer um fake sem passar por esse `init`. Ou seja: **hoje,
   nenhum teste deste repositório consegue construir `GridViewModel` de verdade nem
   compor `GridScreen()` via `viewModel()`** — isso é anterior à Fase A, não foi causado
   por ela. O critério de aceitação da Fase A ("teste Robolectric que compõe a tela
   contra um DAO falso que falha se `notesContainingTag` for chamado") não é executável
   como está escrito. Implementei em vez disso:
   - `NotesContainingTagPerfTest` (Robolectric, Room de verdade, sem passar por
     `MyApp.appModule`) para o `PERF_NOTES_CONTAINING_TAG`.
   - `GridScreenFlashcardsRemovedTest` (JUnit puro, lê o código-fonte de `GridScreen.kt`,
     `BottomGrid.kt` e `AppNav.kt`) para garantir que a varredura não pode voltar a ser
     disparada por essas telas.
   Bruno: preciso que confirme se essa saída serve, ou se quer que eu abra uma fase
   separada para dar ao `AppModule` um seam testável (ex.: `GitManager`/`StorageManager`
   injetáveis) antes de continuar — não fiz isso aqui porque mexeria em arquitetura fora
   do pedido da Fase A.
2. **`RepoDatabase.buildFactory` (SQLite do requery) não carrega na JVM de teste.**
   Medido direto no CI: `UnsatisfiedLinkError` ao tentar usar
   `RequerySQLiteOpenHelperFactory` num teste Robolectric (o `.so` é específico de
   Android). `NotesContainingTagPerfTest` passou a usar o SQLite padrão do Room
   (Robolectric o sombreia com uma libsqlite nativa do host), que serve porque
   `notesContainingTag` não usa nenhuma das funções customizadas (`fullName`,
   `parentPath`, `rank`, `caseFold`) que só o factory do requery registra. **Isto importa
   para a Fase B**: os testes de SQL que ela pede exercitam `gridNotes`/
   `gridNotesWithQuery`, que USAM essas funções — investigar isso no início da Fase B
   antes de escrever os testes, porque pode ser preciso outro caminho (talvez registrar
   as mesmas funções como um `SQLiteFunction` diferente compatível com o SQLite padrão,
   ou aceitar que esses testes específicos não rodam no CI Linux como estão).
3. **`AndroidChaveMestra.kt` também está modificado e sem menção no handoff.**
   O handoff (seção 0, item 9) lista 7 arquivos modificados pelo trabalho de segurança
   não commitado; o `git status` real mostra 8 — `AndroidChaveMestra.kt` também aparece
   como `M`. Não toquei nele (só li, para confirmar que construir `AppPreferences` em
   teste não dispara Keystore). Registrando para o Bruno confirmar que é esperado.

### Fase B (2026-09-20) — decisão do Bruno sobre os testes de SQL

Confirmado no início da Fase B, exatamente como a divergência 2 da Fase A antecipava:
`gridNotes`, `gridNotesWithQuery` e `drawerFolders` só funcionam com 4 funções SQLite
customizadas (`fullName`, `parentPath`, `rank`, `caseFold`), registradas hoje só por
`RepoDatabase.buildFactory` (via `RequerySQLiteOpenHelperFactory`). Essa lib nativa não
carrega na JVM de teste (`UnsatisfiedLinkError`, já medido na Fase A). Não há API pública
do Android para registrar função SQLite customizada — é por isso que o projeto depende do
requery. Levantei duas alternativas: (a) SQLite puro em JVM via `org.xerial:sqlite-jdbc`,
testando o texto literal do SQL fora do Android/Robolectric — dependência nova, primeira
vez no repo; (b) API interna não documentada do Robolectric
(`nativeRegisterCustomFunction`/`SQLiteCustomFunction`) — mais arriscada, não dá para
confirmar sem compilar localmente. Perguntei ao Bruno; ele escolheu **aceitar a lacuna**:
implementar a Fase B inteira em produção, testar isoladamente as 3 funções puras
(`ParentPath`/`FullName`/`CaseFold`, já são Kotlin sem SQL) e documentar no
`RESULTADO_10_B.md` que os testes de SQL ponta a ponta de `gridNotes`/`gridNotesWithQuery`
(contagem de linhas por pasta, isolamento entre `Medicina`/`Medicina2`) **não são
executáveis neste CI hoje**. Sem dependência nova, sem risco de CI vermelho por causa disso.

### Fase C (2026-09-20)

1. **Mesmo bloqueio de `GridViewModel` das Fases A/B.** Não dá para construir o VM real
   em teste para provar `currentNoteFolderRelativePath.value` ponta a ponta. Segui o
   padrão já aceito: extraí a decisão em `PastaInicial.escolher` (função pura) e testei
   ela sozinha — é o mesmo código que o `GridViewModel` chama de verdade
   (`GridViewModel.kt:63-68`), só que fora do caminho que trava o teste JVM. Não perguntei
   de novo ao Bruno porque já é o padrão das duas fases anteriores.
2. **`AppPreferences.kt` e `SettingsScreen.kt` são dois dos arquivos com trabalho de
   segurança não commitado (regra 9 da seção 0).** Isolei minhas duas mudanças (uma
   linha em `AppPreferences.kt`, um bloco de UI em `SettingsScreen.kt`) das linhas de
   segurança e commitei separadamente, sem tocar nem reverter nada do trabalho não
   commitado — método descrito em `RESULTADO_10_C.md`, seção "Como o commit foi feito".
   Depois do commit, `git diff` desses dois arquivos mostra exatamente as mesmas linhas
   de segurança de antes, intactas.
3. **Erro meu, não do handoff:** o primeiro push (`f72eb8e`) quebrou o
   `compileDebugKotlin` por um import inválido (`androidx.compose.foundation.lazy.item`
   não é importável — `item` é método de `LazyListScope`, disponível sem import dentro
   do `LazyColumn`). Consertado no commit seguinte (`3b41b45`); detalhe e evidência do
   run vermelho em `RESULTADO_10_C.md`.

### Fase D (2026-09-20)

1. **A migração destrutiva só dispara quando o Room abre o banco.** O caminho real de
   `StorageManager.updateDatabaseWithoutLocker` comparava `fsCommit` com
   `databaseCommit` antes da primeira consulta ao banco. Sem mudança no Git, podia
   retornar antes de `onDestructiveMigration` zerar a preferência, contrariando a
   reindexação imediata prevista no handoff. A fase D passou a abrir o banco antes
   dessa comparação. Descoberto por leitura do fluxo, sem medição em aparelho.
2. **Os testes completos do VM e do SQL da grade seguem com as limitações já
   registradas em A/B/C.** `GitManager` carrega `git_wrapper` na JVM do VM;
   `RequerySQLiteOpenHelperFactory` não roda no Robolectric. A Fase D testou a
   durabilidade e a expressão `ORDER BY MAX(...)` em SQLite real, o limite em
   função pura e a ligação do SQL/VM por leitura do código. Para executar
   `clearAndInit` no Robolectric, o predicado de extensão foi injetado no teste,
   evitando a função Rust nativa sem alterar a execução de produção.

### Fase E (2026-09-20)

1. **Contagem do vault mudou durante a edição das notas.** A primeira medição
   encontrou 2.486 títulos fora de cercas, contra 2.465 no handoff. Bruno explicou
   que estava editando uma nota e pediu para prosseguir sem interação. A medição
   final, no mesmo conjunto de 140 notas, encontrou **2.487 títulos** e **269**
   na maior nota. A contagem é um retrato do momento, não uma constante do app.
   O teste de UI usa 269 títulos; o módulo usa uma lista dinâmica.
2. **O teste de UI não instancia `MarkDownVM` real.** O bloqueio de
   `GitManager`/`git_wrapper` na JVM segue igual ao das Fases A/C. O Robolectric
   compôs `SumarioLateral` e verificou seleção, callback de navegação e largura
   em 320/375 dp; a seleção exata da linha foi calculada com
   `offsetOfLineStart`, a mesma função chamada pelo VM.
3. **A primeira execução do CI falhou na compilação** por uma expressão `Int * Dp`
   na indentação. Corrigido em `afa2580`; o CI seguinte passou em testes e APK,
   e publicou b58. Detalhe em `RESULTADO_10_E.md`.

### Fase K (2026-09-20)

1. **A base de comparação de PERF que o handoff pede para a Fase K não existe.** O
   critério 6 manda comparar os três números novos com o `PERF_GRID_QUERY` registrado no
   `RESULTADO_10_B.md` — mas aquele relatório diz, com todas as letras, que **não escreveu
   esse número**: a consulta da Fase B usava `parentPath()`, e a anterior a ela usava
   `fullName()` mais função de janela; nenhuma das duas roda na JVM do CI (é a divergência
   das Fases A/B, o requery não carrega). Perguntei ao Bruno antes de codar; ele escolheu
   **medir os três cenários de verdade e registrar a ausência da base**, sem emular a
   consulta antiga para fabricar um quarto número. **A medição só passou a ser possível
   agora**: o filtro recursivo da K.2 dispensou `parentPath()`, e na ordem padrão a
   consulta da grade não usa mais nenhuma função customizada — por isso `GradeSqlTest` é o
   primeiro teste deste repositório que executa a consulta da grade de verdade, em vez de
   conferir o texto do SQL.
2. **Duas consequências da listagem recursiva que o handoff não previa**, as duas
   decididas pelo Bruno antes do código:
   - **Título do card.** Com recursão, duas notas de mesmo nome em subpastas diferentes
     apareceriam idênticas, e o desambiguador (função de janela) não pode voltar. Escolha:
     o título passa a ser o caminho a partir da pasta aberta (`GridRow.tituloRelativoA`);
     nota da própria pasta continua só com o nome.
   - **Contagem por pasta.** O `JOIN` de `drawerFolders` usava `LIKE f.relativePath || '%'`
     **sem barra** — `Medicina` contava as notas de `Medicina2/` e de um `Medicina.md` ao
     lado. É o mesmo defeito que a K.2 proíbe de voltar, e a K.1 levou esse número para a
     grade. Escolha: corrigir junto, na mesma fase.
3. **A contagem de pastas do vault mudou entre a manhã e a noite de 20/09.** O handoff
   registra 1.598 pastas e 513 abrindo em branco; a varredura desta sessão deu **1.952 e
   548**. É retrato do momento (mesma natureza da contagem de títulos na Fase E), e as três
   pastas do critério de aceitação (`06_Conhecimento` com 6 subpastas e 0 notas diretas,
   `Medicina` com 5, `Matérias Básicas` com 10) batem exatamente.
4. **Lição repetida sobre teste com banco:** o primeiro push ficou vermelho não pelo SQL,
   mas por `assertNotMainThread` do Room — `query()` direto na thread principal do
   Robolectric. `allowMainThreadQueries()` no banco em memória resolve, e só o teste muda.
   O artefato `test-report-<n>` (que só existe quando a rodada falha) trouxe a exceção
   completa; o log do job mostrava apenas `FAILED`.

**Lista de fases do Sonnet:** A, B, C, D, E, F e J.1 entregues (J.1 feita fora de ordem, o que
o handoff permite explicitamente: "J.1 pode ser feita a qualquer momento, inclusive já: não
muda comportamento"). Faltam G, H — e a **K**, entregue em 20/09 fora dessa lista original, já está fechada. F.3 (recolher no modo de edição) fica para a sessão de H.4,
por instrução explícita desta rodada — não é dívida técnica, é escopo adiado de propósito.

### Fase F (2026-09-20) — F.1 e F.2 só; F.3 não foi feita (por instrução)

1. **Divergência real entre dois critérios de aceitação, perguntada ao Bruno antes de
   codar "Recolher tudo".** A regra "não pode regredir" exige que recolher uma seção
   também recolha (esconda) os títulos aninhados de nível maior dentro dela — testado e
   medido. O critério de aceitação separado ("nota com 257 títulos, recolher tudo → o
   texto visível tem 257 linhas") só é possível numa nota **sem aninhamento** (todos os
   títulos no mesmo nível): numa nota real com títulos aninhados, marcar todos os
   títulos e aplicar a cascata deixa visível só os títulos que não estão dentro de
   nenhuma seção recolhida — ou seja, só os de nível mais alto (o de nível mínimo
   presente). Medido na própria máquina, na maior nota real de `06_Conhecimento`
   (`Aula Introdução à micro.md`, agora com **269 títulos**, não mais 257 — contagem
   mudou de novo desde a Fase E): níveis 1/2/3/4 = 117/25/79/48. "Recolher tudo" em
   cascata nessa nota deixa **117** títulos visíveis (os de nível 1), não 269.
   Perguntei ao Bruno qual comportamento valia; ele escolheu **cascata** (a opção
   recomendada — reusa `dobrar` sem mecanismo novo, consistente com a regra de não
   regressão). Os testes refletem isso: `DobraTest.recolherTudoNumaNotaSemAninhamentoDeixaSoAsLinhasDeTitulo`
   prova a frase literal do handoff numa nota sintética sem aninhamento (mesmo padrão
   flat de `SumarioTest`), e `DobraTest.recolherTudoNaNotaRealMaisAninhadaDeixaSoOsTitulosDeNivel1`
   prova o número real (117 de 269) que uma nota aninhada de verdade produz.
2. **`MapaDeDobra.paraOriginal` tinha um bug real na fronteira de um corte**, achado
   rodando o algoritmo (reimplementado em Python) sobre as 140 notas reais com seções
   aleatórias recolhidas: o título que vem **logo depois** de uma seção recolhida (o
   que encerrou a seção) mapeia para o mesmo ponto visível que o **início** do trecho
   escondido — os dois colapsam no mesmo offset visível, porque o trecho removido tem
   largura zero no texto visível. A primeira versão desempatava para o início do corte
   (escondido); a correta é desempatar para o fim do corte (o título seguinte, que é o
   que está de fato visível ali). Sem essa correção, `paraOriginal(paraVisivel(x)) == x`
   falhava sempre que um título ficava logo após uma seção recolhida — bem comum, não é
   caso de borda raro. Corrigido em `Dobra.kt`, revalidado: 0 falhas de identidade e 0
   falhas de round-trip em 2.270 offsets de título testados nas 140 notas reais
   (script local, não faz parte do CI — mesma limitação de sempre, ver item 3).
3. **Verificação nas 140 notas reais é local, não roda no CI**, pelo mesmo motivo de
   sempre (o vault não está no repositório do app). Repeti o método da Fase E: reescrevi
   `sumarioDe`/`secoesDe`/`dobrar`/`MapaDeDobra` em Python, rodei contra os arquivos de
   verdade, e só os casos sintéticos entram como teste Kotlin no CI.
4. **Nenhum teste de UI constrói `MarkDownVM`/`GridViewModel` reais** — mesmo bloqueio
   de `GitManager`/`git_wrapper` das Fases A/C/E. O chevron de recolher foi testado
   compondo `MarkdownCustomInner` direto (como a Fase E fez com `SumarioLateral`); a
   integração do estado `recolhidas` dentro de `MarkDownContent` (que fica em
   `MarkDown.kt`, arquivo liberado para a Fase F) foi revisada no código e provada por
   um teste estrutural (`RecolhimentoNaoAlteraTextoSalvoTest`) que lê o código-fonte e
   garante que o ramo do modo de edição nunca referencia o texto dobrado.
5. **F.3 não foi feita nesta sessão, por instrução explícita do prompt desta rodada**
   (não da minha decisão): o handoff pede medir F.2 antes de decidir F.3, e o arquivo
   que F.3 precisaria tocar (`MarkDownVM.kt`, view model do editor) estava sendo
   alterado em paralelo pela sessão da branch `handoff10-j1`. Fica pendente para a
   sessão de H.4, como o próprio handoff já previa ("Se F.2 sair antes de H, deixar F.3
   para a sessão de H.4").

6. **Os testes Robolectric da Fase F seguraram a master por quatro rodadas, e a causa não
   era o recolher — era a fronteira entre o composable e a árvore semântica.** Fica aqui
   porque vale para toda fase que testar UI neste repositório:
   - **O markdown não está na árvore quando o teste olha.** `com.mikepenz` v0.43.0 parseia
     em `withContext(Dispatchers.Default)` dentro de um `LaunchedEffect`; só parseia dentro
     do `remember { }` quando `immediate = true`, cujo default é `LocalInspectionMode.current`
     (falso em teste). `waitForIdle()`/`runOnIdle` **não** esperam thread de fora do Compose,
     então o teste media `State.Loading`, que desenha o `loading = { Box(modifier) }` vazio —
     e o erro que aparece é "could not find any node", que parece defeito do composable.
     Quem for compor markdown em teste: envolver em
     `CompositionLocalProvider(LocalInspectionMode provides true)`.
   - **Nó que existe mas não recebe toque.** Dentro de um contêiner que rola e recorta
     (`horizontalScroll`, `LazyColumn`), `assertExists()` passa para um nó fora da viewport,
     e `performClick()` injeta o toque sem erro — ele só cai fora da área clipada e não
     atinge nada. O sintoma é o callback não ser chamado, que parece defeito de produção.
     Usar `performScrollTo()` antes do clique.
   - Consequência prática registrada: o relatório HTML de teste **só vira artefato quando a
     rodada falha** (`gh run download <id> -n test-report-<n>`), e é ele que traz a mensagem
     de erro completa — o log do job mostra só `AssertionError at Arquivo.kt:linha`. Ler o
     artefato antes de tentar conserto economizou a quinta rodada.
   - Um teste que a rodada anterior tinha trocado por leitura de código-fonte
     (`semCallbackDeToggle...`) voltou a ser prova de comportamento com o modo de inspeção
     ligado. Detalhe completo em `RESULTADO_10_F.md`.

### Fase J.1 (2026-09-20)

1. **CI da PR: 330/331 testes verdes nas 3 tentativas** (run
   [35520521548](https://github.com/brunojcbrg-cloud/gitnote/actions/runs/35520521548)).
   A única falha, sempre a mesma, foi `SumarioTest.perfSumarioNaNotaDe1946Linhas` (Fase E,
   fora do escopo de J.1): mediana 7,636 ms, depois 3,198 ms, depois 8,235 ms, contra
   limite de 3 ms — o mesmo teste tinha medido 2,005 ms no `master` na própria Fase E.
   Instabilidade do runner, não regressão: J.1 não toca nenhum arquivo `Sumario*`. Detalhe
   em `RESULTADO_10_J1.md`.
2. **PR #2 mesclada pelo Bruno (squash, `df8d403`) após revisão desta sessão.** Esta
   sessão não mesclou por conta própria — o modo automático do Claude Code bloqueia
   merge de PR sem confirmação explícita do Bruno no chat, mesmo com autorização dada.
   O build de release próprio do commit de merge foi **cancelado** (run
   [35522118827](https://github.com/brunojcbrg-cloud/gitnote/actions/runs/35522118827)):
   a sessão da Fase F empurrou `38a27b8` quase em seguida, e o `fork-release.yml` cancela
   o run anterior do mesmo branch por concorrência. O build de F.1+F.2 que sucedeu já
   contém o código de J.1, então valida os dois juntos.
3. **Não prosseguiu para J.2.** Por instrução do handoff (seção "Ao terminar J.1"), J.2
   exige Opus 5 com esforço máximo e aval do Bruno. H.2 foi entregue e medida na release
   b65; falta H.1 para liberar a Fase J. Prompt pronto salvo em `PROXIMO_PROMPT_J2.md`,
   mas **não deve ser usado ainda**: falta H.1.
