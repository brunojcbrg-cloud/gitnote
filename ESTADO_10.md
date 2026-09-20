# ESTADO_10 — controle de fases do HANDOFF_10

Ordem: A, B, C, D, E, F, G, H, J.1
(I e Parte V bloqueadas em decisão do Bruno; J.2+ exige aval dele e outro modelo — fora desta lista)

Detalhe de cada fase entregue: `RESULTADO_10_<FASE>.md`.

| Fase | Status | Data | Commit | Release |
|---|---|---|---|---|
| A | entregue | 2026-09-20 | fe69d70 | b52 (26.08.1.52) |
| B | entregue | 2026-09-20 | 1b74388 | b53 (26.08.1.53) |
| C | pendente | | | |
| D | pendente | | | |
| E | pendente | | | |
| F | pendente | | | |
| G | pendente | | | |
| H | pendente | | | |
| J.1 | pendente | | | |

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
