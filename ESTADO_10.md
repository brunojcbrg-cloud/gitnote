# ESTADO_10 — controle de fases do HANDOFF_10

Ordem: A, B, C, D, E, F, G, H, J.1
(I e Parte V bloqueadas em decisão do Bruno; J.2+ exige aval dele e outro modelo — fora desta lista)

| Fase | Status | Data | Commit | Release |
|---|---|---|---|---|
| A | pendente | | | |
| B | pendente | | | |
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
2. **`AndroidChaveMestra.kt` também está modificado e sem menção no handoff.**
   O handoff (seção 0, item 9) lista 7 arquivos modificados pelo trabalho de segurança
   não commitado; o `git status` real mostra 8 — `AndroidChaveMestra.kt` também aparece
   como `M`. Não toquei nele (só li, para confirmar que construir `AppPreferences` em
   teste não dispara Keystore). Registrando para o Bruno confirmar que é esperado.
