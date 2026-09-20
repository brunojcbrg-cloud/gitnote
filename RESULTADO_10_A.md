# RESULTADO 10 — FASE A

## O que mudou

- `GridScreen.kt`: removido `onFlashcardsClick`, o `FlashcardViewModel` instanciado só
  para pintar o número no botão, e o `collectAsStateWithLifecycle` de
  `availableCount`. `GridScreen` agora só recebe `onSettingsClick` e `onEditClick`.
- `BottomGrid.kt`: `FloatingActionButtons` perdeu o `ExtendedFloatingActionButton` de
  flashcards e os parâmetros `onFlashcardsClick`/`availableFlashcards`. Fica só o FAB de
  criar nota.
- `AppNav.kt`: o ramo `AppDestination.Grid` não passa mais `onFlashcardsClick` para
  `GridScreen`. A entrada pelos flashcards continua intacta na Home
  (`AppDestination.Home`, que já tinha seu próprio botão).

Nenhum arquivo do trabalho de segurança não commitado (`PortaoDeSeguranca.kt` e os
outros 7 listados na seção 0 do handoff, mais `AndroidChaveMestra.kt` — ver
divergência 2 abaixo) foi tocado.

## Números PERF_

Não há "antes/depois" de velocidade de consulta aqui — a Fase A **remove o chamador**
(`FlashcardViewModel.init` → `dao.notesContainingTag`), não otimiza a consulta. O número
registrado é o custo que deixa de rodar toda vez que a tela de Notas abre:

```
PERF_NOTES_CONTAINING_TAG notas=4309 bytes=28311552
amostras_ms=[325.859, 335.395, 324.468, 325.239, 325.51]
mediana_ms=325.51
```

Banco sintético: 4.309 notas somando exatamente 27 MiB (28.311.552 bytes), uma delas
com `#flashcards` no conteúdo — mesmo formato do vault real (`06_Conhecimento` tem 140
notas, mas o vault inteiro tem 4.309 notas indexáveis e 1 única com `#flashcards`,
conforme a seção 1 do handoff). Mediana de 5 amostras após 2 de aquecimento, mesmo
padrão do `MarkdownTablePerfTest.kt`.

## Testes escritos

1. **`NotesContainingTagPerfTest`** (`app/src/test/.../data/room/`) — Robolectric, Room
   de verdade (SQLite padrão, não o factory do requery — ver divergência 1). Prova o
   número acima e que a consulta encontra exatamente 1 nota entre 4.309.
2. **`GridScreenFlashcardsRemovedTest`** (`app/src/test/.../ui/screen/app/grid/`) — JUnit
   puro, lê o código-fonte de `GridScreen.kt`, `BottomGrid.kt` e `AppNav.kt`. Prova que:
   - `GridScreen.kt` não menciona mais `FlashcardViewModel`, `onFlashcardsClick` nem
     `notesContainingTag`;
   - `BottomGrid.kt` não tem nada de flashcards;
   - `AppNav.kt` tem exatamente 1 ocorrência de `onFlashcardsClick` (a da Home).

   Isto **não é** o "teste Robolectric que compõe a tela" pedido pelo critério de
   aceitação original — ver divergência 1, é a limitação que me fez escolher este
   desenho.

## O que não foi feito e por quê

- **Não compus `GridScreen()` de verdade em teste nenhum.** Ver divergência 1 abaixo:
  é uma limitação de todo o repositório, não desta fase, e pedir para eu resolvê-la
  seria mexer em arquitetura fora do escopo da Fase A.
- Não toquei nos ajustes `showFullNoteHeight`/`noteMinWidth` nem em nada da Fase B — não
  pertence a esta fase.

## Divergências (também em `ESTADO_10.md`)

1. **`GitManager` impede compor `GridScreen`/construir `GridViewModel` em qualquer
   teste deste repositório**, hoje, independente da Fase A: `StorageManager` (usado por
   `GridViewModel`) inicializa `gitManager = MyApp.appModule.gitManager` no corpo da
   classe, e o companion de `GitManager` roda `System.loadLibrary("git_wrapper")` no
   `init` — lib nativa que só existe dentro do APK. Como `StorageManager` e
   `GitManager` são classes concretas (não interfaces, não abertas), não há como
   fornecer um fake sem passar por esse `init`. O critério de aceitação pedia um "teste
   Robolectric que compõe a tela contra um DAO falso" — troquei pelos dois testes
   descritos acima. **Preciso que confirme se serve, ou se quer uma fase separada para
   dar ao `AppModule` um seam testável antes de eu continuar.**
2. `RepoDatabase.buildFactory` (o SQLite do requery) **não carrega na JVM do teste**
   (`UnsatisfiedLinkError`, medido no primeiro push desta fase, corrigido no segundo).
   O `PERF_NOTES_CONTAINING_TAG` usa o SQLite padrão do Room em vez disso — funciona
   porque `notesContainingTag` é um `LIKE` puro, sem as funções customizadas
   (`fullName`, `parentPath`, `rank`, `caseFold`) que só o factory do requery registra.
   **Isto importa para a Fase B**: os testes de SQL que a Fase B pede (`gridNotes`,
   `gridNotesWithQuery`, que usam `fullName`/`parentPath`) vão precisar do factory do
   requery de propósito — e esse factory não roda no CI Linux como está. Vale investigar
   isso no início da Fase B antes de escrever os testes dela.
3. `AndroidChaveMestra.kt` também aparece modificado no `git status`, sem estar na lista
   de 7 arquivos da seção 0 do handoff (que lista `AppPreferences.kt`, `MainActivity.kt`,
   `SettingsScreen.kt`, `app/build.gradle.kts`, `AndroidManifest.xml`,
   `libs.versions.toml`, `fork-release.yml`). Não toquei nele — só li para confirmar que
   construir `AppPreferences` de verdade em teste não dispara Keystore. Registrando para
   você confirmar que é esperado.

## Release do CI

- Push 1 (`6231f68`): CI vermelho — `UnsatisfiedLinkError` no `NotesContainingTagPerfTest`
  (divergência 2). Corrigido no mesmo commit de sessão, sem pular o teste.
- Push 2 (`fe69d70`): **CI verde** — run
  [35513415471](https://github.com/brunojcbrg-cloud/gitnote/actions/runs/35513415471),
  release **b52 (26.08.1.52)**, publicada em 2026-09-20T13:29:52Z.
