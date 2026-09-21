# HANDOFF 10 — Desempenho da lista, navegação por títulos, busca na nota e imagens

App Android **Life SO** (`E:\Projetos\gitnote`, fork de `wiiznokes/gitnote`), mais o
`notas-web` (`E:\Projetos\notas-web`) e o vault (`E:\Obsidian\CONHECIMENTO`).
Escrito em 2026-09-20 a partir de leitura do código e medição. Nada aqui foi implementado ainda.

> **Este documento é grande de propósito, mas NÃO é uma sessão só.**
> São 9 fases no app, 1 fase que atravessa os três clientes (imagens) e 1 parte fora do app
> (a renomeação). Cada uma é entregável sozinha, tem critério de aceitação próprio e deve ser
> **uma sessão por fase** — e a Fase J, várias. Fazer tudo de uma vez estoura o contexto e
> impede medir o que cada mudança causou.
> Ordem recomendada: **A → B → C → D → E → F → K → G → H → J**. A **Fase K** conserta uma
> regressão da B relatada em 20/09 (pasta só com subpastas abre em branco — 513 pastas do
> vault, inclusive a pasta padrão dele) e **tem prioridade sobre G e H**. A **Fase J** (migração do editor
> para `TextFieldState`, decidida em 20/09) **exige H.2 entregue e medido**; suas sub-fases
> J.1 a J.5 são sessões separadas. A **Parte V** (renomeação) em sessão isolada,
> preferencialmente depois de C; e a **Fase I** (imagens) pode entrar em paralelo a qualquer
> momento **depois que o Bruno decidir o item I.0**, porque não encosta nas outras.
> **J.1 pode ser feita a qualquer momento**, inclusive já: não muda comportamento.
>
> **Modelo por fase:** A a H e J.1 rodam em **Sonnet 5** (`claude-sonnet-5`), esforço alto.
> **J.2 em diante é Opus 5** (`claude-opus-5`), esforço máximo, e só com o aval do Bruno —
> quem terminar J.1 tem de parar, avisar e entregar o prompt pronto da J.2 (ver o bloco
> "Ao terminar J.1" dentro da Fase J).

---

## 0. Regras deste repositório (ler antes de escrever código)

1. **Não há SDK Android nem JDK nesta máquina.** Não tente compilar localmente, não instale
   Gradle, não configure `ANDROID_HOME`. Isso já custou uma rodada inteira em 09/09.
2. **O CI compila sozinho.** `fork-release.yml` dispara em `push` na `master`
   (com `paths-ignore` de `**.md`) e roda: testes Rust → `./gradlew testDebugUnitTest` →
   lib Rust com NDK → APK → release. O fluxo é: escrever testes, commitar, empurrar, ler o
   log do job **"Unit tests + APK"**. Desde 18/09 o log **lista cada teste executado**
   (`testLogging` em `app/build.gradle.kts`), então dá para provar que um teste novo rodou.
3. **Teste de UI aqui é Robolectric**, nunca aparelho. Padrão em
   `app/src/test/java/io/github/wiiznokes/gitnote/ui/screen/app/edit/MarkdownEditorUiTest.kt`
   (`@Config(sdk = [35])`, `@GraphicsMode(NATIVE)`, `createComposeRule()`).
4. **Teste de custo por tecla** tem padrão pronto em
   `app/src/test/java/io/github/wiiznokes/gitnote/ui/viewmodel/edit/MarkdownTablePerfTest.kt`:
   nota sintética de 1.946 linhas / 180.046 caracteres, 5 amostras, mediana, `println` com
   prefixo `PERF_`. **Toda fase que mexe no editor tem de deixar um teste desses.**
5. **A migração para `TextFieldState` / `BasicTextField2` foi decidida pelo Bruno em 20/09** e
   é a **Fase J**. Ela **não** pode ser antecipada para dentro de outra fase: fora da Fase J,
   continua valendo não encostar na arquitetura do editor. A Fase J só começa com **H.2
   entregue e medido**.
6. Kotlin escrito aqui é **revisado, não compilado**: evitar API experimental e ícones que o
   projeto ainda não usa (`material-icons-extended` está no build, mas prefira ícones já
   presentes no código). Todo `when` sobre `AppDestination` é exaustivo — cobrir ao somar destino.
7. **`applicationId` continua `io.github.wiiznokes.gitnote`** de propósito. Não mexer.
8. Strings novas vão em `app/src/main/res/values/strings.xml` **e** em
   `app/src/main/res/values-pt-rBR/strings.xml`. As outras cinco línguas podem ficar sem.
9. Há **trabalho de segurança não commitado** na árvore agora (`PortaoDeSeguranca.kt`,
   `PortaoDeSegurancaTest.kt` sem rastreio; `AppPreferences.kt`, `MainActivity.kt`,
   `SettingsScreen.kt`, `app/build.gradle.kts`, `AndroidManifest.xml`, `libs.versions.toml`
   e `fork-release.yml` modificados). **Confirmar com o Bruno o que fazer com isso antes de
   commitar qualquer coisa** — não engolir essas mudanças num commit desta fase.

---

## 1. O que foi medido (é daqui que cada fase sai)

### Vault (`E:\Obsidian\CONHECIMENTO`)

| Medida | Valor |
|---|---|
| Notas indexáveis (`.md`/`.txt`, abaixo do teto de 2 MB) | **4.309** |
| Conteúdo total dessas notas | **27,0 MB** |
| Notas acima de 100 KB | 31 |
| Notas acima de 50 KB | 97 |
| Notas em `06_Conhecimento` | **140** (2,0 MB) |
| Maior nota de `06_Conhecimento` | `Microbiologia/Aula Introdução à micro.md` — **336 KB, 257 títulos** |
| Títulos (`#`..`######`) em `06_Conhecimento` | **2.465** em 140 notas |
| Distribuição de títulos por nota | 107 com menos de 10; 6 com 10–19; 13 com 20–29; e a cauda 100, 170, 210, 230, 257 |
| Notas do vault inteiro contendo `#flashcards` | **1** |

Duas consequências diretas:

- O painel de títulos (Fase E) e o recolhimento (Fase F) têm de aguentar **257 títulos numa
  nota só** — nada de construir a lista inteira fora de `LazyColumn`.
- O banco do app carrega **27 MB de texto** e a tela de notas encosta nisso de várias formas.

### Código do app — as cinco causas medidas

**(1) A consulta da grade puxa o conteúdo inteiro e é recursiva.**
`Dao.kt:185-214` (`gridNotes`) monta:

```sql
WITH notes_with_filename AS (
    SELECT *, fullName(relativePath) AS fileName
    FROM Notes
    WHERE relativePath LIKE :currentNoteFolderRelativePath || '%'
)
SELECT *, CASE WHEN COUNT(*) OVER (PARTITION BY fileName) = 1 THEN 1 ELSE 0 END AS isUnique
FROM notes_with_filename
ORDER BY ...
```

- `SELECT *` traz a coluna `content` (`Schema.kt:69-77`: a entidade `Note` guarda o arquivo
  inteiro no banco).
- `LIKE :path || '%'` é **prefixo, não pasta**: na raiz (`path = ""`) casa com **as 4.309
  notas**; e em `Medicina` casaria também com `Medicina2/...` — defeito latente à parte.
- `COUNT(*) OVER (PARTITION BY fileName)` é função de janela: o Room de paginação embrulha a
  consulta em `SELECT * FROM (<consulta>) LIMIT ? OFFSET ?` e `SELECT COUNT(*) FROM (<consulta>)`,
  ou seja **o conjunto inteiro é materializado a cada página e a cada recontagem**.

**(2) O card renderiza o markdown inteiro.** `GridScreen.kt:417` chama
`MarkdownCustom(content = gridNote.note.content, …)` — parser GFM completo, por card, para
até 50 cards por página. A lista alternativa (`ListView.kt`) **já** mostra só título + data;
quem está com `NoteViewType.Grid` (que é o padrão, `AppPreferences.kt`) paga o parser.

**(3) Abrir Notas dispara uma varredura de flashcards nos 27 MB.**
`GridScreen.kt:99-100` instancia `FlashcardViewModel` só para pintar o número no botão
flutuante. O `init` desse VM (`FlashcardViewModel.kt:93-95`) chama `loadCards()`, que em
`FlashcardViewModel.kt:226-229` faz `dao.notesContainingTag("#flashcards")` →
`SELECT * FROM Notes WHERE content LIKE '%#flashcards%'` — **varredura completa da coluna de
conteúdo, sem índice possível** (curinga à esquerda) — mais `dao.allNoteFolders()`.
**No vault inteiro existe 1 nota com `#flashcards`.** Os 27 MB são lidos para achar 1 nota,
toda vez que ele toca em "Notas".

**(4) A pré-visualização ao vivo refaz a nota inteira a cada tecla.**
`MarkdownLivePreviewTransformation.filter` roda `MarkdownScanner.scan(source)` sobre o texto
todo e aloca `IntArray(source.length + 1)` duas vezes, reconstruindo o `AnnotatedString`
inteiro. Já medido no CI: **27,4 ms por tecla** numa nota de 1.946 linhas / 180 mil caracteres.
Pior: `MarkDown.kt:362-377` faz `remember(textContent.text, textContent.selection, …)`, então
**mover o cursor sem digitar também refaz tudo**.

**(5) A nota inteira viaja como Parcelable.** `AppDestination.Edit(EditParams.Idle(note, …))`
carrega `Note`, que é `@Parcelize` **com o conteúdo**; o backstack fica em `rememberSaveable`
(`AppNav.kt:44`). Numa nota de 336 KB isso é um `Bundle` de 336 KB a cada navegação e a cada
salvamento de estado. Além disso `TextVM.history` (`TextVM.kt:67`) guarda cópias inteiras do
texto por passo de desfazer.

### O que já existe e NÃO precisa ser criado

- **Âncoras de título no modo leitura**: `MarkDown.kt:129` mantém
  `headingPositions: Map<Int, HeadingAnchor>` e `WikilinkSupport.kt:39` define
  `HeadingAnchor(text, y, sourceOffset)`. É a fonte de dados pronta do sumário (Fase E).
- **Faixa de rolagem rápida na borda direita**: `FastScrollGutter` em `MarkDown.kt:519+`,
  com aparecer/sumir em 1,5 s e captura só depois do toque longo. É onde o sumário
  lateral se pendura (Fase E).
- **Mapeamento linha ⇄ offset**: `MarkdownLineAnchors.kt` (`lineStartOffsets`,
  `lineOfOffset`, `offsetOfLineStart`, `nearestLineAtOrBefore`, `firstLineAtOrAfter`).
- **Diálogo de escolher pasta**: `ui/component/PickFolderDialog.kt`, já usado em
  `SettingsScreen.kt:160` para `defaultPathForNewNote`. É o molde exato da Fase C.
- **Navegação para pastas acima**: `DrawerScreen.kt` já tem botão Início (`openFolder("")`),
  FAB de subir um nível e trilha de migalhas clicável (`RowNFoldersNavigation`).

---

## FASE A — Tirar os flashcards da tela de Notas

**Pedido do Bruno:** "tire o botão de flash cards da parte de notas, quando eu clico em notas
e deixe só no painel inicial do programa, para não gerar carregamento desnecessário."

**Por que é a primeira:** é a causa (3) acima — varredura de 27 MB disparada por abrir Notas —
e sai com remoção de código, sem desenho novo. É o maior ganho pelo menor risco do handoff.

### O que fazer

1. `GridScreen.kt`
   - apagar o parâmetro `onFlashcardsClick` da assinatura (`:95`);
   - apagar `val flashcardVm: FlashcardViewModel = viewModel()` (`:99`) e
     `val availableFlashcards by …` (`:100`);
   - apagar os dois argumentos na chamada de `FloatingActionButtons` (`:141-142`);
   - apagar o import de `FlashcardViewModel` (`:80`). Atenção: `collectAsStateWithLifecycle`
     continua em uso (`isRefreshing`), não apagar.
2. `BottomGrid.kt` — apagar o `ExtendedFloatingActionButton` dos flashcards e os parâmetros
   `onFlashcardsClick` / `availableFlashcards`. Fica só o FAB de criar nota.
3. `AppNav.kt` — no ramo `is AppDestination.Grid`, remover o argumento `onFlashcardsClick`.
   A entrada pelos flashcards continua na Home (`HomeScreen.kt`, já existe e não muda).

### Critério de aceitação

- Abrir **Notas** não instancia `FlashcardViewModel`, logo não chama `notesContainingTag`.
  Prova: teste Robolectric que compõe a tela contra um DAO falso que **falha** se
  `notesContainingTag` for chamado.
- Um `PERF_NOTES_CONTAINING_TAG` medindo essa consulta sobre um banco sintético de 4.309 notas
  somando 27 MB, só para o número ficar registrado no relatório (é o custo que some).
- Os flashcards continuam abrindo pela Home, com a mesma contagem de antes.
- `testDebugUnitTest` verde, com os testes novos aparecendo no log.

---

## FASE B — Card sem conteúdo e consulta só da pasta

**Pedidos do Bruno (dois, que são o mesmo defeito):**
- "no card, ao invés de carregar o conteúdo inteiro, apareça somente o título do documento md,
  a data da última visualização/modificação";
- "quero que ele carregue na Ram dele somente os cards dos documentos daquela determinada pasta".

### B.1 — Projeção leve (tirar `content` da consulta da grade)

Criar um modelo de linha **sem conteúdo**:

```kotlin
// ui/model/GridRow.kt
data class GridRow(
    val relativePath: String,
    val id: Int,
    val lastModifiedTimeMillis: Long,
    val selected: Boolean = false,
)
```

- `gridNotesRaw` passa a devolver `PagingSource<Int, GridRow>` e o SQL passa a selecionar
  **apenas** `relativePath, id, lastModifiedTimeMillis` (mais o que a Fase D exigir).
- Quem precisa do `Note` inteiro busca sob demanda: já existe
  `dao.noteByRelativePath(relativePath)` (`Dao.kt:146`). Vale para abrir o editor, apagar e
  selecionar. Ver B.4.

### B.2 — Consulta por pasta, não por prefixo

Trocar, em `gridNotes` (`Dao.kt:185-214`):

```sql
WHERE relativePath LIKE :currentNoteFolderRelativePath || '%'
```

por

```sql
WHERE parentPath(relativePath) = :currentNoteFolderRelativePath
```

`parentPath` já é função registrada no SQLite do app (objeto `ParentPath` em `Dao.kt`,
registrada em `RepoDatabase.buildFactory`), e `drawerFolders` já usa exatamente esse padrão.

**Mudança de comportamento, intencional e a ser dita ao Bruno:** hoje abrir
`06_Conhecimento` lista as notas de todas as subpastas juntas; depois disso lista só as notas
soltas dessa pasta, e as subpastas se abrem pela gaveta. É o que ele pediu ("somente os cards
dos documentos daquela determinada pasta"). Se ele quiser o modo antigo de volta, isso vira um
ajuste "incluir subpastas" — **não implementar por conta própria.**

**A busca continua recursiva.** Em `gridNotesWithQuery` (`Dao.kt:216-262`) manter o `LIKE`,
mas corrigir o defeito latente: usar
`(:path = '' OR Notes.relativePath LIKE :path || '/%')` para `Medicina` não casar com
`Medicina2/…`.

### B.3 — Tirar a função de janela do caminho quente

Com a consulta por pasta, `isUnique` perde sentido: dois arquivos na **mesma** pasta não podem
ter o mesmo nome. Então:

- em `gridNotes`: **remover** o `COUNT(*) OVER (PARTITION BY fileName)` e o `CASE`;
- o card passa a mostrar sempre o nome sem extensão (ou o caminho inteiro, se
  `showFullPathOfNotes` estiver ligado);
- em `gridNotesWithQuery` (recursiva, onde nomes repetidos existem de verdade) **manter** o
  desambiguador, mas medir: se a janela custar caro na busca, trocar por "mostrar sempre o
  caminho nos resultados de busca", que é o que o Obsidian faz.

### B.4 — Card só com título e data

Em `GridScreen.kt`, no `NoteCard`:

- apagar o bloco `if (… is FileExtension.Md) { MarkdownCustom(…) } else { Text(content) }`
  (`:415` em diante);
- deixar título (`titleMedium`, negrito) + data formatada (`bodySmall`, cor
  `onSurfaceVariant`), exatamente como `ListView.kt:NoteListRow` já faz — inclusive o
  `remember(lastModifiedTimeMillis) { DateFormat.getDateTimeInstance(MEDIUM, SHORT).format(…) }`;
- `MarkdownCustom` em `markdownHelper.kt` fica **sem nenhum chamador** depois disso.
  Verificar com grep e apagar se for o caso (`MarkdownCustomInner` continua — é o do modo
  leitura, não mexer).
- Consequência a registrar: os ajustes `showFullNoteHeight` e `noteMinWidth` deixam de
  influenciar o conteúdo (o card vira uma altura só). `noteMinWidth` ainda controla a largura
  da coluna. **Não apagar esses ajustes nesta fase** — só anotar no relatório.

O clique no card precisa do `Note` inteiro. Trocar o caminho:
`onEditClick(gridNote.note, …)` → `vm.abrirNota(relativePath) { note -> onEditClick(note, EditType.Update) }`,
com `abrirNota` no `GridViewModel` fazendo `dao.noteByRelativePath` em `Dispatchers.IO`.
O mesmo para `deleteNote`. A seleção múltipla pode passar a guardar `Set<String>` de caminhos
em vez de `List<Note>` — isso simplifica `refreshSelectedNotes` (`GridViewModel.kt:77-84`),
que hoje consulta o banco nota a nota.

### Critério de aceitação da Fase B

1. Teste de SQL (Robolectric + banco em memória com as funções `parentPath`/`fullName`
   registradas — ver `RepoDatabase.buildFactory`) com uma árvore sintética:
   raiz com 3 notas, `A/` com 5, `A/B/` com 7, mais `A2/` com 2.
   - abrir `""` devolve **3** linhas, não 17;
   - abrir `A` devolve **5**, não 12, e **não** inclui nada de `A2`;
   - a busca por termo em `A` continua achando o que está em `A/B`.
2. Teste que prova que a projeção não traz conteúdo: a consulta da grade sobre uma nota de
   336 KB devolve linha cujo tamanho somado dos campos é menor que 1 KB.
3. `PERF_GRID_QUERY`: tempo da primeira página na raiz, com 4.309 notas sintéticas somando
   27 MB, **antes e depois**. Registrar os dois números no relatório.
4. Teste Robolectric do card: compõe o card com conteúdo de 336 KB e verifica que o texto do
   conteúdo **não** aparece na árvore semântica, e que título e data aparecem.

---

## FASE C — Pasta padrão de carregamento

**Pedido do Bruno:** "eu quero poder selecionar uma pasta padrão, que no caso será a pasta
notas, que, ao iniciar, vai aparecer pastas e arquivos só dela, mas que eu consiga navegar nas
pastas anteriores do repositório também… tenha uma opção de voltar, para que eu possa ir para a
raiz do diretório… ao clicar em notas na tela inicial, deve carregar já na pasta notas."

### O que fazer

1. `AppPreferences.kt` — nova preferência ao lado de `defaultPathForNewNote`:

```kotlin
val pastaPadrao = stringPreference("pastaPadrao", "")
```

2. `GridViewModel.kt:60-68` — a inicialização de `_currentNoteFolderRelativePath` passa a ser:

```
se rememberLastOpenedFolder e lastOpenedFolder != ""  -> lastOpenedFolder
senão se pastaPadrao != ""                            -> pastaPadrao
senão                                                  -> ""
```

**Guarda obrigatória:** se a pasta escolhida **não existe** no banco (caso da Parte V, quando
`06_Conhecimento` virar `NOTAS` e a preferência no celular ainda apontar para o nome velho),
cair na raiz e avisar por toast. Sem isso a tela abre vazia e parece que o app perdeu o vault.
A checagem é barata: criar `isFolderExist` no DAO, espelhando `isNoteExist` (`Dao.kt:138`).
Como a construção do VM é síncrona, fazer a queda para a raiz num `viewModelScope.launch`
logo depois, sem bloquear a abertura.

3. `SettingsScreen.kt` — entrada nova copiando o bloco de `defaultPathForNewNote` (`:152-166`):
título "Pasta padrão ao abrir as notas", subtítulo com o valor atual, `PickFolderDialog`,
e um jeito de **limpar** (voltar a "" = raiz).

4. Navegação para cima: conferir que continua funcionando com a pasta padrão em uso —
   `DrawerScreen.kt` já tem o botão Início (`openFolder("")`), o FAB de subir um nível e a
   trilha clicável. **Não** travar a navegação na pasta padrão: ele pediu explicitamente para
   poder subir até a raiz.

5. Acrescentar ao `DrawerScreen` um item "Voltar à pasta padrão", visível quando `pastaPadrao`
   não é vazia e o caminho atual é diferente dela.

### Critério de aceitação

- Com `pastaPadrao = "06_Conhecimento"` (ou `NOTAS`, depois da Parte V) e
  `rememberLastOpenedFolder` desligado, abrir o app frio leva a grade direto a essa pasta —
  provado por teste do VM (sem UI): construir `GridViewModel` com preferências falsas e
  verificar `currentNoteFolderRelativePath.value`.
- Com `pastaPadrao` apontando para pasta inexistente, o VM cai para `""` e emite o toast.
- A gaveta continua conseguindo chegar à raiz a partir da pasta padrão.

---

## FASE D — Ordem por última visualização e teto de 10

**Pedidos do Bruno:**
- "no carregamento inicial, quero que os cards apareçam em ordem de visualização/modificação,
  para que em caso de o app travar e eu precisar reiniciar, eu consiga retomar o mais rápido possível";
- "ao abrir a pasta do repositório… preciso que ele mostre somente os 10 últimos
  visualizados/modificados e nada mais além disso."

### D.1 — "Última visualização" não existe hoje. Onde guardar.

Hoje só existe `lastModifiedTimeMillis` (`Schema.kt:72`). Precisa de um carimbo de
**abertura**. **Duas armadilhas medidas:**

- `dao.clearAndInit` (`Dao.kt:41`) **apaga a tabela `Notes` inteira** a cada reindexação, que
  acontece sempre que o commit do repositório muda (`StorageManager.kt:122-146`). Uma coluna
  nova em `Notes` seria zerada a cada sincronização.
- `RepoDatabase.kt` usa **`.fallbackToDestructiveMigration(true)`**. Subir a versão do banco
  **apaga tudo**. Uma tabela nova dentro do `RepoDatabase` morre na primeira mudança de versão.

**Portanto: banco Room separado**, pequeno, próprio, que não é tocado por `clearAndInit` nem
pela migração destrutiva do outro:

```kotlin
@Entity(tableName = "Aberturas", primaryKeys = ["relativePath"])
data class Abertura(val relativePath: String, val abertaEmMillis: Long)

@Database(entities = [Abertura::class], version = 1)
abstract class HistoricoDatabase : RoomDatabase() { abstract val dao: HistoricoDao }
```

Registrar em `MyApp.appModule` junto de `repoDatabase`. Gravação: no `GridViewModel.abrirNota`
(criado na Fase B), antes de navegar, `upsert(Abertura(path, agora))` em `Dispatchers.IO`.

### D.2 — A ordenação

Nova entrada em `SortOrder` (`ui/model/SortOrder.kt`): `UltimaVisualizacao`, e torná-la o
**padrão** (`AppPreferences.sortOrder`, hoje `MostRecent`).

Como os dois bancos são separados, não dá `JOIN` em SQL. Duas saídas — **implementar a (a)**:

- **(a) Carimbar na reindexação.** `clearAndInit` já recebe `timestamps: HashMap<String, Long>`
  para preservar datas (`Dao.kt:43`) — seguir o mesmo caminho: `StorageManager` lê as aberturas
  do `HistoricoDatabase` antes de reindexar e preenche uma coluna nova `lastOpenedTimeMillis`
  em `Notes` (default = `lastModifiedTimeMillis`). A ordenação vira
  `ORDER BY MAX(lastOpenedTimeMillis, lastModifiedTimeMillis) DESC`, tudo em SQL, e o
  `HistoricoDatabase` é só a cópia durável que sobrevive ao `clearAndInit`. Ao gravar uma
  abertura, atualizar **as duas** (a tabela durável e a coluna em `Notes`).
- (b) Ordenar em memória — **não fazer**: quebra a paginação.

Isso exige subir a versão do `RepoDatabase` para 3. Como a migração é destrutiva, o efeito é
uma reindexação completa na primeira abertura depois da atualização (o `onDestructiveMigration`
já zera `databaseCommit`, `RepoDatabase.kt:34-40`). É aceitável **porque** as aberturas estão
no banco separado. Deixar isso escrito no relatório.

### D.3 — Teto de 10 na abertura da pasta

- `GridViewModel`: `private val _teto = MutableStateFlow(PRIMEIRA_LEVA)` com
  `PRIMEIRA_LEVA = 10`; entra no `combine` que monta `gridNotes` (`:209-227`) e vira
  `LIMIT` no SQL.
- `openFolder` (`:115`) **volta o teto para 10** a cada troca de pasta.
- Buscar (`query` não vazia) **ignora o teto**.
- Rodapé da grade/lista: quando o teto está cortando, mostrar "Mostrar todas (N)" — `N` vem de
  um `SELECT COUNT(*)` barato na pasta (sem conteúdo, sem janela). Tocar remove o teto **só
  para esta visita**.
- `PRIMEIRA_LEVA` fica como constante nomeada num lugar só; se ele pedir para ajustar, vira
  preferência depois.

### Critério de aceitação

- Teste do VM: pasta com 40 notas → o fluxo emite 10; após "mostrar todas" emite 40;
  `openFolder` para outra pasta volta a emitir 10.
- Teste de ordenação: três notas com (modificada, aberta) = (3, —), (1, 5), (2, —) saem na
  ordem 2ª, 1ª, 3ª.
- **Teste de durabilidade (é o que prova o desenho de dois bancos):** gravar abertura →
  rodar `clearAndInit` → a ordem por visualização continua valendo.
- Buscar dentro da pasta devolve mais de 10 resultados quando existem.

---

## FASE E — Painel de títulos (sumário estilo Google Docs)

**Pedido do Bruno:** "quero a visualização com um mecanismo semelhante ao do google docs, que
aparece uma guia ao lado e eu consiga navegar pelos títulos, todos os títulos e subtítulos
disponíveis… e na rolagem pela lateral, quando ela for ativada pelo lado direito, os tópicos e
subtópicos apareçam no canto esquerdo, enquanto o documento é rolado ao fundo, para que eu
consiga visualizar tanto o lugar que está rolando do documento… mas essas coisas devem ser
analisadas para não ocupar a tela inteira."

### E.1 — Módulo puro de títulos (testável sem UI)

Arquivo novo `ui/component/markdown/Sumario.kt`:

```kotlin
data class ItemDeSumario(
    val nivel: Int,        // 1..6
    val texto: String,     // sem os '#', sem marcadores de ênfase
    val linha: Int,
    val offset: Int,       // offset do '#' no texto de origem
)

fun sumarioDe(texto: String): List<ItemDeSumario>
```

Regras que o vault exige:

- ATX (`# `..`###### `) no começo da linha, com pelo menos um espaço depois dos `#`;
- **`#` sem espaço é tag do Obsidian, não título** (`#flashcards`) — não entra;
- linha dentro de cerca de código (``` ou `~~~`) **não** é título, mesmo começando com `#`;
- tirar `**`, `_`, `` ` `` e `==` do texto exibido, mantendo o texto de origem para busca;
- **CRLF**: o vault tem 134 arquivos com CRLF — **testar LF e CRLF**. É exatamente onde a
  lista automática quebrou no handoff 07 (17 de 22 casos falhavam em CRLF).

### E.2 — O painel

`ui/screen/app/edit/SumarioLateral.kt`, um overlay:

- ancorado à **esquerda**, `widthIn(max = 280.dp)` e no máximo **62% da largura da tela** — o
  documento tem de continuar visível ao lado/atrás (ele pediu isso explicitamente);
- fundo `surface.copy(alpha = 0.94f)` com sombra; **sem scrim** que escureça o documento;
- **`LazyColumn`** obrigatoriamente (257 títulos na maior nota);
- indentação por nível: `padding(start = (nivel - 1) * 12.dp)`, fonte decrescendo de
  `titleSmall` (nível 1) a `bodySmall` (níveis 4-6);
- item do título **atual** destacado (cor `primary` + fundo `primaryContainer`), e a lista
  rolada até ele ao abrir (`LazyListState.scrollToItem`);
- tocar num item fecha o painel e salta.

### E.3 — Ligação nos dois modos

**Modo leitura** (`MarkDown.kt:126-345`): a fonte já existe —
`headingPositions: Map<Int, HeadingAnchor>` com `y` medido. Saltar é
`scrollState.animateScrollTo(heading.y.coerceIn(0, scrollState.maxValue))`, igual ao que
`resolveSectionHeading` já faz para wikilink de seção (`:221`). O título atual é o último com
`y <= scrollState.value`.

*Cuidado:* `headingPositions` só tem os títulos **já compostos**. Numa nota longa os de baixo
ainda não foram medidos. Solução: montar a lista a partir de `sumarioDe(renderedContent)`
(todos os títulos, sempre) e usar `headingPositions` apenas para o `y`; título ainda não medido
→ rolar pelo bloco conhecido mais próximo (`nearestAnchorAtOrBefore` já existe) e deixar o
resto para o próximo quadro.

**Modo edição** (`MarkDown.kt:346-407`): a lista vem de `sumarioDe(textContent.text)`,
memorizada em `remember(textContent.text)` — **cuidado, isto é o caminho de cada tecla**:
`sumarioDe` tem de ser varredura linear simples, e é obrigatório um teste `PERF_SUMARIO` na
nota de 1.946 linhas. Alvo: **abaixo de 3 ms**; se passar disso, recalcular só quando o número
de `\n` mudar. Saltar é `vm.moveCursorToLine(item.linha)` (já existe em `MarkDownVM`).

### E.4 — Abrir o painel

Dois gatilhos, os dois pedidos por ele:

1. **Botão**: ícone de lista na barra superior do `EditScreen.kt` (ao lado do cadeado), nos
   dois modos.
2. **Durante o arrasto na faixa direita**: em `FastScrollGutter` (`MarkDown.kt:519+`), quando
   `dragging == true`, mostrar o painel à esquerda com o título correspondente à posição atual
   destacado, e escondê-lo ao soltar. Aproveitar o `visible`/`hideGeneration` que já existem.
   **Não** consumir o gesto: quem rola continua sendo o `TextField`/`ScrollState`, como o
   handoff 07 deixou — içar a rolagem para fora do `TextField` **já foi tentado e reprovou**.

### Critério de aceitação

- `sumarioDe` acerta os **2.465 títulos das 140 notas de `06_Conhecimento`**: rodar o módulo
  sobre os arquivos reais numa medição e registrar o número no relatório. Nos testes, casos
  sintéticos cobrindo ATX 1-6, `#tag`, cerca de código, CRLF, título com ênfase, `#` solto e
  `#` dentro de tabela.
- `PERF_SUMARIO` medido e registrado na nota de 1.946 linhas.
- Teste Robolectric: painel aberto numa nota com 257 títulos compõe sem estourar, mostra os
  primeiros itens, e tocar num item move o cursor para a linha certa.
- O painel nunca passa de 62% da largura; verificar em 375 dp e em 320 dp.

---

## FASE F — Recolher títulos

**Pedido do Bruno:** "implemente, nas notas, a função de recolher os títulos, os tópicos com
'#' dentro das notas, para que fique mais fácil a leitura, caso eu não queira ler um
determinado conteúdo e consiga passar mais facilmente para o próximo."

### F.1 — Motor puro (é o mesmo da Fase H — fazer uma vez só)

`ui/component/markdown/Dobra.kt`:

```kotlin
data class Secao(val titulo: ItemDeSumario, val inicioCorpo: Int, val fimCorpo: Int)

/** Uma seção vai do fim da linha do título até o próximo título de nível <= ao dele. */
fun secoesDe(texto: String, sumario: List<ItemDeSumario>): List<Secao>

data class TextoDobrado(val visivel: String, val mapa: MapaDeDobra)

/** Remove o corpo das seções recolhidas e devolve o mapeamento de offsets. */
fun dobrar(texto: String, recolhidas: Set<Int> /* offsets de título */): TextoDobrado

class MapaDeDobra {
    fun paraVisivel(offsetOriginal: Int): Int
    fun paraOriginal(offsetVisivel: Int): Int
}
```

Kotlin puro, sem Compose. É onde vai **toda** a bateria de testes. Serve para o recolhimento
(esta fase) e para o editor por seção (Fase H.4).

### F.2 — Modo leitura primeiro (baixo risco)

- Estado: `var recolhidas by rememberSaveable { mutableStateOf(setOf<Int>()) }`.
- Antes de renderizar, aplicar `dobrar(renderedContent, recolhidas).visivel` — o markdown que
  chega ao `MarkdownCustomInner` já vem sem os corpos recolhidos.
- Marcador visual: em `positionedHeading` (`markdownHelper.kt`, já embrulha o título num `Box`)
  acrescentar um chevron e tornar o título clicável para alternar. Usar ícone já presente no
  pacote base (`Icons.Rounded.KeyboardArrowDown` / `KeyboardArrowUp`), não o
  `material-icons-extended`.
- Ações no painel de sumário (Fase E): "Recolher tudo" / "Expandir tudo" / "Recolher até nível N".
- **O texto salvo nunca muda.** O recolhimento é de exibição. Garantir por teste.

### F.3 — Modo edição depois, e só depois de F.2 estar medido

Recolher dentro do `TextField` significa **tirar texto do campo**, o que exige que:
- `vm.save()` grave sempre o texto **completo** (`dobrar` é reversível pelo mapa);
- `markdownSmartEditor`, as ações de tabela/lista e o desfazer operem em offsets convertidos;
- a âncora (`rememberAnchor` / `moveCursorToLine`) use linhas do texto original.

É o mesmo maquinário da Fase H.4. Se F.2 sair antes de H, deixar F.3 para a sessão de H.4.

### Critério de aceitação

- Teste sobre as **140 notas reais** de `06_Conhecimento`: `dobrar` com um conjunto aleatório
  de seções e `paraOriginal(paraVisivel(x)) == x` para todo offset de título; e
  `dobrar(texto, emptySet()).visivel == texto`.
- Teste: recolher uma seção de nível 2 recolhe os níveis 3-6 dentro dela e **não** recolhe a
  próxima de nível 2.
- Teste: nota com 257 títulos, recolher tudo → o texto visível tem 257 linhas.
- Teste de não-regressão: salvar com seções recolhidas grava o arquivo idêntico ao original.
- `PERF_DOBRAR` na nota de 1.946 linhas.

---

## FASE G — Busca dentro da nota (o Ctrl+F)

**Pedido do Bruno:** "quero um mecanismo de pesquisa, do tipo que tem em docs pdf, ctrl + F,
que eu consiga pesquisar conteúdos dentro de determinado arquivo."

### G.1 — Casador puro

`ui/component/markdown/BuscaNaNota.kt`:

```kotlin
fun ocorrencias(texto: String, termo: String, diferenciarMaiusculas: Boolean = false): List<IntRange>
```

**Sem acento importa aqui.** As notas são em português; procurar "cranio" tem de achar
"crânio". Normalizar com `java.text.Normalizer.normalize(s, NFD)` removendo as marcas
combinantes (`\p{Mn}`) **dos dois lados**, e mapear de volta ao offset original — a
normalização pode mudar o comprimento, então construir um vetor de índices na primeira
passada. Termo vazio → lista vazia. **Nada de regex vinda do usuário.**

### G.2 — UI

- Ícone de lupa na barra superior do `EditScreen.kt`; abre uma linha embaixo da barra:
  campo + "3 / 17" + `‹` `›` + fechar.
- `rememberSaveable` para o termo e o índice atual, para sobreviver à rotação.
- **Modo edição**: saltar = `updateSelection(TextRange(inicio, fim))`. O `TextField` já traz o
  cursor para a tela sozinho — é o mesmo mecanismo de que a rolagem rápida depende (handoff 07).
- **Modo leitura**: o alvo é o **bloco** que contém a ocorrência. `blockCoordinates`
  (`MarkDown.kt:~145`) mapeia linha → coordenada; converter offset → linha com
  `lineOfOffset(renderedLineStarts, offset)` e rolar com `nearestAnchorAtOrBefore`. Destacar:
  reaproveitar o `annotator` (`missingWikilinkAnnotator`, `WikilinkSupport.kt`) e as cores
  `colors.highlight` / `colors.highlightBackground`, que já existem no tema (são as do `==`).
- Buscar **não** pode rodar a cada tecla do termo sobre uma nota de 336 KB: debounce de
  ~150 ms e cálculo fora do quadro de composição.

### Critério de aceitação

- Testes: "cranio" acha "crânio" e "CRÂNIO"; "a" numa nota de 180 mil caracteres devolve a
  contagem certa; termo vazio devolve vazio; ocorrências sobrepostas ("aa" em "aaa") seguem a
  regra escolhida **e a regra está escrita no teste**.
- `PERF_BUSCA` na nota de 1.946 linhas: alvo abaixo de 10 ms por consulta.
- Teste Robolectric: digitar o termo, tocar `›` três vezes, verificar a seleção no `TextField`.

---

## FASE H — O custo por tecla no editor

**Pedidos do Bruno:** "quando o arquivo vai ficando grande, começa dar uns travamentos…
preciso que veja um caminho para otimizar, carregando uma parte do conteúdo"; e "sempre que eu
vou modificar uma nota… ela começa a dar umas travadas no modo edição".

Régua atual, já medida no CI: **27,4 ms por tecla** (nota de 1.946 linhas / 180.046 caracteres).
A meta é derrubar esse número **com medição antes e depois na mesma nota sintética**.

Fazer na ordem, medindo cada passo — os dois primeiros são baratos e podem já resolver:

**H.1 — Parar de refazer tudo quando só o cursor anda.**
`MarkDown.kt:362-377` tem `remember(textContent.text, textContent.selection, …)`. A
transformação só depende da seleção através de `activeMarkdownLines(...)`. Calcular esse
conjunto antes e usar **ele** como chave. Mover o cursor dentro da mesma linha deixa de
reconstruir a nota inteira.

**H.2 — Memorizar a varredura.** `MarkdownScanner.scan(source)` é pura. Guardar o último par
(texto, spans) num cache de uma entrada só, dentro da transformação. Com H.1, andar com o
cursor entre linhas passa a reaproveitar a varredura.

**H.3 — Medir de novo.** Se H.1 + H.2 trouxerem o custo para a faixa de poucos milissegundos,
**parar aqui** e escrever o número no relatório. Não seguir para H.4 sem necessidade medida.

**H.4 — Edição por seção (só se H.3 não resolver, e agora provavelmente nem isso).** É o que
o Bruno chamou de "carregar uma parte do conteúdo". Usa o motor de `Dobra.kt` (Fase F): o
`TextField` recebe apenas a seção atual; o VM guarda o texto inteiro; salvar recompõe. Custa:
desfazer, ações de formatação, tabela e âncora passam a precisar de conversão de offset.
**Fase própria, handoff próprio** — não tentar junto com H.1-H.3.
**Desde 20/09, com a Fase J decidida, H.4 caiu para último recurso**: medir depois de J.3
antes de cogitá-la (ver J.6). O motor de dobra continua sendo feito na Fase F de qualquer
jeito, porque recolher título é pedido dele, não otimização.

**H.5 — Dois itens à parte, baratos, que valem a mesma sessão de H.1-H.3:**
- `TextVM.history` guarda cópias inteiras do texto (`TextVM.kt:67` e `:199`). Numa nota de
  336 KB, 40 passos de desfazer são ~13 MB. Pôr **teto no número de passos** (ex.: 100),
  descartando os mais antigos, e registrar o teto. **Paliativo com prazo:** a Fase J.4 troca
  essa lista pelo desfazer embutido do `TextFieldState`. Fazer o teto agora mesmo assim — J
  vem depois e a nota grande trava hoje.
- A nota inteira viaja como Parcelable no backstack (`AppDestination.Edit` →
  `EditParams.Idle(note)` → `rememberSaveable` em `AppNav.kt:44`). Trocar por
  `relativePath: String` e buscar com `dao.noteByRelativePath` na abertura do editor. Tira até
  336 KB de cada `Bundle` e é o mesmo caminho que a Fase B já cria.
  **Atenção**: `EditParams.Saved` (recuperação de edição não salva, via `NoteSaver`) precisa
  continuar carregando o conteúdo — é justamente o texto que ainda não está no disco.

### Critério de aceitação

- `PERF_LIVE_PREVIEW` antes e depois, na nota de 1.946 linhas / 180.046 caracteres, no mesmo
  runner, com mediana de 5 amostras. Os dois números no relatório.
- Teste que prova H.1: mover a seleção dentro da mesma linha **não** reconstrói a transformação
  (contador de invocações).
- Todos os 244+ testes atuais continuam verdes — em especial os de `MarkdownSmartEditor`
  (23 casos × LF/CRLF) e os de tabela.

---

## FASE J — Migrar o editor para `TextFieldState`

**Decidido pelo Bruno em 20/09/2026.** Deixou de ser item de decisão e virou trabalho.

**Pré-requisito rígido: H.1 e H.2 entregues, medidos e verdes.** O `transformOutput` do modelo
novo é chamado *"toda vez que um texto novo precisa ser exibido"* e o resultado é **descartado
depois de desenhar** — o framework não memoriza nada. Sem a varredura memorizada (H.2), a
migração troca de API e mantém o custo. **Não começar a Fase J antes de H.2.**

### J.0 — O que a documentação oficial garante (levantado em 20/09)

- `TextFieldBuffer.addStyle(SpanStyle | ParagraphStyle, início, fim)` existe **desde o Compose
  1.9 (agosto/2025)** e é chamável dentro do `OutputTransformation`. O projeto usa o BOM
  `2026.06.01` (`gradle/libs.versions.toml:12`) — **a API está disponível, sem subir nada**.
- `SpanStyle` cobre cor, peso, tamanho e `baselineShift` — exatamente o que
  `MarkdownLivePreviewTransformation` aplica hoje (cor por elemento do tema, tamanho de título,
  índice sub/sobrescrito da fórmula).
- **Mapeamento de offset deixa de ser código nosso.** A documentação diz que a diferença-chave
  para a `VisualTransformation` é que *"você não precisa calcular os mapeamentos de offset"*.
- Esconder marcador vira `delete()` no buffer; converter fórmula para Unicode vira `replace()`.
- `BasicTextField(state = …)` **expõe `scrollState`**.
- Restrição: **mudar seleção/cursor dentro do `transformOutput` não tem efeito**. O código
  atual não faz isso, então não afeta — mas quem migrar precisa saber.

Fontes: blog do Android "What's new in Jetpack Compose August '25", a página
*Configure text fields* do developer.android.com e a referência de `OutputTransformation`.

### J.0.1 — O raio de alcance, medido (não migrar o que não precisa)

`TextFieldValue` aparece em **18 arquivos**. **Só 6 são do editor.** Os outros são campos
simples que usam `TextFieldValue` apenas para posicionar o cursor e **devem ficar como estão**:

| Migra (núcleo do editor) | Ocorrências |
|---|---|
| `ui/viewmodel/edit/MarkdownSmartEditor.kt` | 19 |
| `ui/viewmodel/edit/TextVM.kt` | 10 |
| `ui/viewmodel/edit/MarkdownTable.kt` | 4 |
| `ui/viewmodel/edit/MarkDownVM.kt` | 2 |
| `ui/screen/app/edit/MarkDown.kt` | 2 |
| `ui/screen/app/edit/EditScreen.kt` | 2 |
| `ui/component/markdown/MarkdownLivePreviewTransformation.kt` | a classe inteira + `ArrayOffsetMapping` (`:248`) |

| **NÃO migra** | Por quê |
|---|---|
| `ui/component/GetStringDialog.kt` | campo de diálogo; usa `PasswordVisualTransformation`, que continua válida |
| `ui/screen/app/grid/TopGrid.kt` | campo de busca da grade |
| `ui/screen/app/edit/TableDialog.kt` | dois campos numéricos |
| `setup/remote/CredentialsScreen.kt`, `EnterUrlScreen.kt`, `LoadKeysFromDeviceScreen.kt`, `PickRepoScreen.kt` | telas de configuração inicial |

**Testes que serão tocados:** `MarkdownSmartEditorTest` (8), `MarkdownTableInsertionTest` (8),
`MarkdownEditorUiTest` (5), `MarkdownTableDetectionTest` (3), `ObsidianLineBreaksTest` (3),
`MarkdownLivePreviewTransformationTest`, `MathRenderingTest`.

### J.1 — Soltar as funções puras do `TextFieldValue` (sem migrar nada ainda)

**Esta sub-fase é entregável sozinha, não muda comportamento e não depende da decisão.**

`MarkdownSmartEditor` e `MarkdownTable` são funções puras `(TextFieldValue) -> TextFieldValue`.
Elas não precisam do `TextFieldValue` — precisam de **texto + seleção**. Trocar por um tipo
neutro:

```kotlin
// ui/viewmodel/edit/EdicaoDeTexto.kt
data class EdicaoDeTexto(val texto: String, val selecao: TextRange)
```

Adaptar no limite (`TextVM`/`MarkDownVM`) com duas funções de conversão. Os 23 casos × LF/CRLF
do `markdownSmartEditor` e os de tabela passam a exercitar o tipo neutro, **mecanicamente, sem
mudar uma asserção de comportamento**.

Ganho: quando J.3 chegar, **31 das 39 ocorrências do núcleo já estão fora do caminho**, e a
migração encosta só na UI e no VM. É o que transforma um salto num degrau.

#### Ao terminar J.1 — parada obrigatória e troca de modelo

**J.1 é a última fase que roda no Sonnet.** Quem terminar J.1 **não** começa J.2. Em vez disso:

1. Avisar o Bruno, em destaque, que **a Fase J.2 exige Opus 5 (`claude-opus-5`) com esforço no
   máximo, e o aval dele**. O motivo, sem rodeio: J.2 reescreve o live preview com portão de
   "sai igual ou aborta", em cima de nove funcionalidades já entregues (handoffs 01 a 08) que
   não podem regredir. Não é trabalho de modelo de execução.
2. Lembrá-lo de **conferir se H.2 foi entregue e medido** — J.2 sem a varredura memorizada
   troca de API e mantém o custo (ver o pré-requisito no topo da Fase J).
3. Registrar em `ESTADO_10.md` que a lista de fases do Sonnet acabou.
4. **Imprimir na conversa, em bloco de código, o prompt abaixo, pronto para colar**, e também
   gravá-lo em `E:\Projetos\gitnote\PROXIMO_PROMPT_J2.md`:

~~~
MODELO: Opus 5 (claude-opus-5). ESFORÇO: máximo. Confira os dois antes de começar.

Leia E:\Projetos\gitnote\HANDOFF_10_DESEMPENHO_E_NAVEGACAO.md inteiro, com atenção
à seção 0 (regras do repositório) e à Fase J completa. Leia também ESTADO_10.md e
RESULTADO_10_H.md — o custo por tecla medido na Fase H é a régua desta sessão.

TAREFA: executar SOMENTE a FASE J.2 (o OutputTransformation novo ao lado do antigo).
Não toque em J.3. Não troque o TextField. Nada do app muda de comportamento nesta
sessão: no fim, as duas implementações coexistem e a antiga continua sendo a que roda.

ANTES DE ESCREVER CÓDIGO
1. Confirme que H.2 foi entregue e medido. Se não foi, PARE e me diga.
2. Crie a etiqueta de retorno: git tag handoff10-antes-de-J2 na master verde, e
   anote-a no relatório. É para onde voltamos se esta fase reprovar.

O PORTÃO DESTA FASE
J.2 só é aprovada se a nova implementação produzir o MESMO resultado visível da
antiga. Adapte MarkdownLivePreviewTransformationTest e MathRenderingTest para uma
bateria parametrizada que roda contra as DUAS e exige igualdade: texto renderizado,
intervalos de estilo, e o mapeamento de offset dentro e fora da linha ativa.
Se não sair igual, NÃO force, NÃO afrouxe o teste e NÃO siga para J.3: pare,
escreva o que divergiu e me chame. O app segue intacto porque nada foi trocado.

MEDIÇÃO
PERF_OUTPUT_TRANSFORMATION na nota sintética de 1.946 linhas / 180.046 caracteres,
no padrão de MarkdownTablePerfTest.kt, comparado com o PERF_LIVE_PREVIEW da Fase H.
Os dois números vão no relatório.

REGRAS DO REPOSITÓRIO
- Não compile localmente; quem compila é o GitHub Actions no push da master. Leia o
  log do job "Unit tests + APK".
- Não encoste no trabalho de segurança não commitado (PortaoDeSeguranca.kt e os
  outros 7 arquivos modificados).
- Não migre nenhum arquivo da coluna "NÃO migra" de J.0.1.

AO TERMINAR
Escreva E:\Projetos\gitnote\RESULTADO_10_J2.md com: o que mudou, a tabela de
conversão que você de fato aplicou, os dois números PERF_, o veredito do portão
(aprovado ou reprovado, com evidência), a etiqueta de retorno criada, e o que ficou
pendente. Atualize ESTADO_10.md. Depois pare e me chame antes de qualquer J.3.
~~~

### J.2 — O `OutputTransformation` novo, ao lado do antigo

> **Não executar no Sonnet.** Esta sub-fase e as seguintes (J.3, J.4, J.5) são de
> **Opus 5, esforço máximo**, e só começam com o aval do Bruno. Ver o bloco acima.

Escrever `MarkdownLivePreviewOutput : OutputTransformation` **sem apagar** a
`MarkdownLivePreviewTransformation`. Mapeamento direto do que existe hoje:

| Hoje | No buffer |
|---|---|
| esconder marcador (`hiddenRanges`) | `delete(início, fim)` |
| trocar `$fórmula$` pelo Unicode | `replace(início, fim, convertido)` |
| pintar cor/peso/tamanho do tema | `addStyle(SpanStyle(...), início, fim)` |
| sub/sobrescrito da fórmula | `addStyle(SpanStyle(baselineShift = …, fontSize = …), …)` |
| `IntArray` + `ArrayOffsetMapping` (`:248`) | **apagar** — o framework faz |
| `activeLines` vindo de fora | ler a seleção do próprio buffer |

**Rodar os testes existentes contra as duas implementações.** `MarkdownLivePreviewTransformationTest`
e `MathRenderingTest` já cobrem o que importa, inclusive as formas de wikilink e o mapeamento
de offset dentro e fora da linha ativa; adaptar para uma bateria parametrizada que exercita a
antiga e a nova e **exige o mesmo resultado visível**. Onde a nova não puder devolver
`TransformedText`, comparar o texto renderizado e os intervalos de estilo.

`PERF_OUTPUT_TRANSFORMATION` na nota de 1.946 linhas, comparando com o `PERF_LIVE_PREVIEW` de H.

**Portão:** se aqui a coloração não sair igual, **parar a Fase J** e relatar. O resto do app
segue funcionando, porque nada foi trocado ainda.

### J.3 — Trocar o campo e o estado

1. `TextVM`: `_content: MutableState<TextFieldValue>` → `TextFieldState`. `save()` lê
   `state.text`.
2. `MarkDownContent` (`MarkDown.kt:346-407`) e `GenericTextField` (`EditScreen.kt:320-345`):
   `TextField(value=…, onValueChange=…)` → `BasicTextField(state = …, outputTransformation = …,
   scrollState = …)`, com a decoração que devolve a aparência atual do Material 3.
3. `markdownSmartEditor` sai do `onValueChange` e vira **`InputTransformation`** — é o lugar
   dele: filtra a entrada na faixa que mudou, em vez de reprocessar o documento.
4. Os dois `todo: report this to google` do `TextVM.kt` (valor desatualizado ao segurar Enter;
   `onValueChange` que para de ser chamado ao segurar Backspace) **são defeitos do modelo
   antigo**. Escrever um teste para cada antes de migrar, vê-los vermelhos, e verificar que
   passam depois. **É a prova mais forte de que a migração valeu.**

### J.4 — Desfazer embutido

Trocar a lista `history` (`TextVM.kt:67`, cópias inteiras do texto) pelo `undoState` do
`TextFieldState`. Isso **substitui H.5**, que era só pôr teto no crescimento. Conferir o
agrupamento: hoje há regras à mão (`isSimilar`, `flagDoNotRemove`) para agrupar digitação; se o
agrupamento embutido for pior para ele, manter o comportamento com o que a API oferecer e
registrar a diferença.

### J.5 — Colher o `scrollState`

Fecha a pendência do handoff 07: a faixa de rolagem passa a **rolar de verdade**, em vez de
mover o cursor e o campo correr atrás. Refazer, sobre o `scrollState`:

- `FastScrollLineOverlay` (`MarkDown.kt:455`) — vira irmã da `FastScrollOverlay` do modo leitura;
- o salto do sumário no modo edição (Fase E.3);
- o salto da busca no modo edição (Fase G.2).

**Estas três passam a ser mais simples do que foram escritas nas fases E e G.** Se J vier antes
delas, escrever já na forma nova; se vier depois, este é o item que as reescreve.

### J.6 — O que pode deixar de ser necessário

**H.4 (edição por seção)** existia para cortar o custo por tecla. Se J.2 e J.3 derrubarem o
número, H.4 perde a razão de ser como otimização. O **motor de dobra da Fase F continua**, porque
recolher título é pedido dele, não otimização. Medir e decidir; não fazer H.4 por inércia.

### Critério de aceitação da Fase J

- **Marco de retorno antes de começar:** `git tag` na `master` verde, anotado no relatório.
  Critério de abandono declarado por escrito: se J.2 reprovar a coloração, ou se J.3 quebrar
  algo dos handoffs 02-08 que não se conserte na mesma sessão, **voltar para a etiqueta**.
- **Os 244+ testes atuais verdes**, com os nomes aparecendo no log do CI.
- **Lista de conferência das funcionalidades entregues, uma a uma, no aparelho:** live preview
  temático (handoff 01), modo leitura + wikilinks (02), wikilink de alias e seção (02B), quebra
  de linha do Obsidian (03), flashcards (04/05), destaque de seção e rolagem (06), rolagem no
  editor e lista automática (07), tabelas (08), fórmula LaTeX. **Nenhuma pode regredir.**
- `PERF_LIVE_PREVIEW` de H **versus** `PERF_OUTPUT_TRANSFORMATION` de J, na mesma nota de
  1.946 linhas / 180.046 caracteres, no mesmo runner. Os dois números no relatório.
- Os dois testes dos defeitos do modelo antigo (J.3.4): vermelhos antes, verdes depois.
- Nenhum arquivo da coluna "NÃO migra" de J.0.1 aparece no diff.

---

## FASE I — Imagens nas notas, iguais nos três lugares

**Pedido do Bruno:** "Precisamos implementar a visualização de imagens e colocar imagens pelo
próprio documento, tal como é feito no obsidian… quero que o celular e a web tenham o mesmo
mecanismo do obsidian, que eu possa colocar ou colar qualquer imagem e eu consiga visualizar em
qualquer lugar, tanto pelo pc, quanto pelo celular. E deixe a web, o celular e o obsidian
alinhados, porque eu abro as notas nos três. Aí no caso, deve funcionar tanto no modo leitura,
quanto edição. E que eu consiga fazer o redirecionamento, tal como no obsidian."

**Fase que atravessa três projetos:** vault (`E:\Obsidian\CONHECIMENTO`), app
(`E:\Projetos\gitnote`), web (`E:\Projetos\notas-web`). Mesmo assim continua sendo
**uma sessão por sub-fase** — I.0 primeiro, sempre.

---

### I.0 — O bloqueio que vem antes de qualquer código: **as imagens não estão no git**

Isto é o que ele viu na segunda captura, e **não é falha de renderizador**.

| Medida | Valor |
|---|---|
| Imagens no disco do vault | **404** arquivos, **79,6 MB** |
| Imagens **rastreadas pelo git** (`git ls-files`) | **0** |
| `.gitignore` do vault, bloco "# Imagens" (linhas 64-72) | exclui `*.png *.jpg *.jpeg *.gif *.webp *.bmp *.tif *.tiff *.svg` |

O arquivo que ele colou hoje, `Pasted image 20260920093042.png`, está em
`E:\Obsidian\CONHECIMENTO\` (raiz) e **nunca foi enviado**. O celular e a web não conseguem
mostrar um arquivo que não existe no repositório deles. **Um renderizador perfeito continuaria
mostrando imagem quebrada.**

Segundo achado, da mesma família: **`.obsidian/app.json` não define `attachmentFolderPath`**.
O padrão do Obsidian é colar na **raiz do vault** — por isso a imagem da nota de Microbiologia
foi parar na raiz, e não junto da nota. No Obsidian isso funciona porque ele resolve `![[nome]]`
**procurando o nome no vault inteiro**; qualquer cliente que resolva por caminho relativo falha.

**Onde as 404 imagens estão hoje:**

| Pasta | Imagens | Tamanho |
|---|---|---|
| `05_Sistema` (plugins, Bruno Kernel) | 229 | 24,3 MB |
| `07_Oraculo` | 100 | 12,1 MB |
| raiz solta (imagens coladas) | 20 | 10,1 MB |
| `export` | 18 | 10,1 MB |
| `.obsidian` | 17 | 0,1 MB |
| `04_IA_Workspace` | 13 | 12,6 MB |
| `Excalidraw` | 7 | 10,0 MB |
| **`06_Conhecimento`** | **1** | **0,2 MB** |

Ou seja: **quase nenhuma imagem de nota** existe hoje. O peso está em material de plugin e de
sistema, que **não** precisa ir para o celular.

#### O que fazer em I.0 (é uma decisão do Bruno, não uma escolha do agente)

1. Definir **uma pasta de anexos dentro da árvore de notas** — proposta:
   `NOTAS/_anexos/` (depois da Parte V; antes dela, `06_Conhecimento/_anexos/`). Fica dentro
   da pasta que já é sincronizada e é carregada junto na renomeação.
2. Configurar o Obsidian para colar ali: em `.obsidian/app.json`, acrescentar
   `"attachmentFolderPath": "NOTAS/_anexos"` e `"newLinkFormat"` compatível. Conferir na
   interface (Opções → Arquivos e links → Local para novos anexos) em vez de confiar só no JSON.
3. **Des-ignorar seletivamente** no `.gitignore` do vault, mantendo o bloco geral e abrindo
   exceção só para os anexos das notas:

```gitignore
# Imagens (mantido: plugins, exports e material de sistema ficam fora do repositório)
*.png
*.jpg
...
# Exceção: anexos das notas, que precisam chegar ao celular e à web
!NOTAS/_anexos/
!NOTAS/_anexos/**
```

   (O git precisa das duas linhas — a da pasta e a do conteúdo — senão a exclusão do diretório
   impede o descer nele.)
4. Mover para `NOTAS/_anexos/` as imagens coladas que estão soltas na raiz (**20 arquivos,
   10,1 MB** — conferir uma a uma: nem toda imagem solta na raiz é anexo de nota) e a única de
   `06_Conhecimento`, corrigindo os embeds que as citam.
5. **Teto de tamanho.** Definir um limite por imagem (proposta: **1 MB**, comprimindo no ato da
   colagem — ver I.3 e I.5). Sem isso o repositório cresce sem freio e o clone no celular
   engorda a cada print. Registrar o limite escolhido.

**Sem I.0 nada do resto funciona.** Qualquer sub-fase de código que seja entregue antes disso
vai passar nos testes e falhar no aparelho.

---

### I.1 — A sintaxe que os três têm de falar (o contrato)

O Obsidian é o que já funciona; ele é a referência. Os outros dois se alinham a ele.

| Forma | Obsidian | App (hoje) | Web (hoje) | Alvo |
|---|---|---|---|---|
| `![[imagem.png]]` | renderiza | texto cru | texto cru (`markdown.ts:135`) | renderiza nos três |
| `![[imagem.png\|496]]` (largura em px) | renderiza a 496 px | — | — | renderiza **e permite redimensionar** — ver I.6 |
| `![[imagem.png\|800x600]]` (largura×altura) | renderiza | — | — | renderiza (gravar só largura — ver I.6) |
| `![alt](caminho/img.png)` | renderiza | texto cru | texto cru (`markdown.ts:147`) | renderiza nos três |
| `![alt\|496](caminho/img.png)` | renderiza a 496 px | — | — | renderiza |
| `![alt](https://…)` | renderiza | — | — | **decisão** — ver I.7 |
| `data:image/…;base64,…` | renderiza | — | — | renderiza (limitado a `image/*`) |

**O `|largura` não é hipótese: já está no vault.** A nota
`06_Conhecimento/Medicina/Matérias Básicas/Microbiologia/Aula Introdução à micro.md` contém hoje
`![[Pasted image 20260920093913.png|496]]`, gravado pelo próprio Obsidian quando o Bruno
arrastou a alça. **Esse é o contrato** — os três clientes gravam e leem exatamente assim.

**Resolução do nome**, que é a parte que costuma passar batido: `![[imagem.png]]` **não é
caminho relativo**. O Obsidian procura o nome no vault inteiro. A regra a implementar nos dois
clientes, na ordem:

1. caminho relativo à nota, se o texto contiver `/`;
2. senão, procurar o nome de arquivo no índice do vault;
3. mais de um resultado → o mais próximo da nota na árvore;
4. nenhum → mostrar marcador de "imagem não encontrada" com o nome, **nunca** falhar em
   silêncio nem sumir com o texto.

Os dois clientes já têm o índice de que isso precisa: no app, `dao.wikilinkCandidates`
(`Dao.kt:160-185`), que já faz exatamente essa busca por nome para os wikilinks de texto; na
web, `ctx.caminhos` e `resolverWikilink` (`markdown.ts:136-138`). **Reaproveitar, não reescrever.**

---

### I.2 — App, modo leitura: mostrar a imagem

Estado medido: `markdownHelper.kt:90` passa `imageTransformer = NoOpImageTransformerImpl()` —
a imagem **nunca foi implementada**. E **não há Coil, Glide ou Picasso** no projeto (grep em
`gradle/libs.versions.toml` e `app/build.gradle.kts`: zero).

Boa notícia medida: `data/platform/FileSystem.kt` usa `java.nio.Paths` (`NodeFs.File.fromPath`),
**não** SAF/`DocumentFile`. Então, onde o app já lê o `.md`, ele lê o `.png` ao lado pelo mesmo
caminho. **Conferir mesmo assim** qual `StorageConfig` a instalação do Bruno usa (`App` = interno,
`Device` = pasta do aparelho, `AppPreferences.storageConfig`) e registrar no relatório: em
`Device` o acesso depende de `StoragePermissionHelper`.

**Caminho recomendado: `ImageTransformer` próprio, sem dependência nova.**

```kotlin
// ui/component/markdown/ImagemDaNota.kt
class TransformadorDeImagemLocal(
    private val raizDoRepo: String,
    private val resolver: (String) -> String?,   // nome/caminho -> caminho absoluto
) : ImageTransformer { /* … */ }
```

Três exigências **não negociáveis**:

1. **Amostragem obrigatória.** Decodificar com `BitmapFactory.Options` em duas passadas
   (`inJustDecodeBounds = true` e depois `inSampleSize`) para o tamanho do contêiner. Uma foto
   de celular de 4 MB descomprime em **dezenas de MB** de bitmap; sem isso o app morre de
   `OutOfMemory` na primeira nota com 3 imagens.
2. **Cache LRU com teto em bytes** (`LruCache` do Android, proposta: 1/8 da memória do
   processo), com chave = caminho + largura alvo.
3. **Decodificação fora da composição** (`Dispatchers.IO`), com espaço reservado do tamanho
   final para a lista não pular enquanto carrega.

Preprocessar antes de renderizar: `![[x.png]]` → `![](arquivo:///caminho/resolvido)`, no mesmo
lugar e do mesmo jeito que `preprocessWikilinksForReading` já faz para os wikilinks de texto
(`WikilinkSupport.kt`). **Não** tentar ensinar o parser a entender `![[…]]`.

**Alternativa**: somar Coil (`io.coil-kt:coil-compose`). Resolve amostragem e cache de graça,
mas é dependência nova num projeto que hoje não tem nenhuma de imagem. **Medir a primeira via
antes**; se o custo de fazer à mão passar de ~150 linhas com casos de borda, trocar por Coil e
dizer isso no relatório.

Cuidado com a Fase B: **o card da lista não volta a renderizar imagem.** O card é título + data.

---

### I.3 — App, modo edição: colar e inserir imagem

Escopo desta sub-fase: **colar da área de transferência** e **escolher da galeria**. Câmera fica
de fora (é outra permissão e outro fluxo).

1. Botão de imagem na barra de formatação (`TextFormatRow`, `MarkDown.kt:~600`), ao lado do de
   tabela. Abre o seletor do sistema:
   `ActivityResultContracts.PickVisualMedia` — **não exige permissão** de armazenamento, é o
   photo picker do Android. Não usar `READ_MEDIA_IMAGES`.
2. Colar: interceptar o conteúdo da área de transferência quando houver imagem
   (`ClipData.Item.uri` com MIME `image/*`).
3. Gravar em `<pasta de anexos>/Pasted image <yyyyMMddHHmmss>.png` — **o mesmo padrão de nome do
   Obsidian**, para os três ficarem indistinguíveis.
4. **Comprimir antes de gravar**, respeitando o teto de I.0: redimensionar o maior lado para
   ~1600 px e gravar PNG (ou WebP com qualidade 85 — decidir e registrar; WebP é bem menor e o
   Obsidian lê).
5. Inserir `![[nome do arquivo.png]]` na posição do cursor, com quebra de linha antes e depois —
   o mesmo cuidado que `insertTable` já toma (`MarkdownTable.kt`).
6. Commitar o binário junto com a nota. `StorageManager` já faz add/commit/push; conferir que o
   arquivo novo entra no `add` (o fluxo hoje é orientado a nota, não a arquivo qualquer).

**A armadilha que vai morder:** se o `.gitignore` do vault ainda excluir a pasta de anexos
(I.0 não feito), o app grava o arquivo, o git ignora, o push sobe só o `.md`, e no PC aparece
embed quebrado — **sem nenhuma mensagem de erro**. Teste obrigatório: gravar anexo em repositório
de teste **com** e **sem** a exceção no `.gitignore`, e no caso "sem" o app tem de **avisar**,
não engolir.

---

### I.4 — Web, modo leitura: mostrar a imagem

Estado medido: os dois caminhos estão **desligados de propósito**:

- `src/markdown.ts:135` — `if (alvo.embed) return escapar('![[' + alvo.bruto + ']]')`
- `src/markdown.ts:147-153` — `md.renderer.rules.image` devolve o texto escapado

O que fazer:

1. Trocar as duas regras por emissão de `<img data-caminho="…" alt="…">` **sem `src`**.
2. Depois da sanitização, uma passada resolve os `data-caminho` e preenche o `src`. Os bytes
   vêm do mesmo caminho que já existe: `GET ${API}/repos/${REPO}/contents/<caminho>?ref=${BRANCH}`
   (`github.ts:137`) devolve base64, e `base64ParaBytes` já está escrito em `bytes.ts:15`.
   Virar `Blob` → `URL.createObjectURL`.
3. **Cache por caminho** num `Map`, e **`URL.revokeObjectURL` ao trocar de nota** — sem isso a
   aba vaza memória a cada navegação.
4. **DOMPurify**: `img` não está em `FORBID_TAGS`, mas confirmar que `blob:` em `src` sobrevive à
   sanitização (pode precisar de `ALLOWED_URI_REGEXP` ou de preencher o `src` **depois** do
   `limparHtml`, que é o caminho mais seguro e é o que está proposto no passo 2).
5. Carregar só o que está à vista (`IntersectionObserver` ou `loading="lazy"`) — uma nota com
   20 prints não pode disparar 20 chamadas à API de uma vez.

---

### I.5 — Web, modo edição: colar imagem

1. `EditorView.domEventHandlers({ paste })` no CodeMirror 6 (o editor já é CM6 — ver
   `src/NotaBytes.ts`), lendo `event.clipboardData.files`.
2. Comprimir no navegador via `canvas` (mesmo teto e mesmo formato escolhidos em I.0/I.3 — os
   três clientes têm de gravar igual).
3. `PUT` na API de conteúdo (o caminho de escrita já existe, `github.ts:162`) com o base64.
4. Inserir `![[nome]]` na posição do cursor.
5. Mensagem clara se o `PUT` falhar (arquivo já existe, token sem permissão, arquivo grande
   demais) — nada de falha silenciosa.

---

### I.6 — Redimensionar a imagem dentro do documento

**Definido pelo Bruno em 20/09:** *"no redirecionamento é eu conseguir alterar o tamanho da
imagem no próprio documento, igual no obsidian"*. Ou seja: **arrastar a alça da imagem muda o
tamanho e o tamanho fica gravado na nota** — não é visualizador de tela cheia.

O mecanismo do Obsidian, que é o contrato: arrastar a alça no canto inferior direito reescreve
o embed para `![[nome.png|<largura em px>]]`. O arquivo de imagem não é tocado; muda só o texto
da nota. **Já há um caso real no vault** (`|496`, gravado por ele hoje), então ler e gravar essa
forma é obrigatório, mesmo onde arrastar não for possível.

#### Web — dá para ficar igual ao Obsidian, e o encanamento já existe

O `notas-web` usa **CodeMirror 6**, o mesmo editor do Obsidian, e o projeto **já usa widget de
decoração**: `src/NotaLivePreview.ts:48` tem `class BolinhaLista extends WidgetType` e `:144`
faz `Decoration.replace({ widget: … })`. O caminho é o mesmo:

1. Um `WidgetType` novo (`ImagemEmbutida`) substitui o trecho `![[nome|N]]` por um `<img>` com
   a largura aplicada e uma alça no canto inferior direito.
2. `pointerdown` / `pointermove` na alça redimensionam **só no DOM** — barato, sem tocar no
   documento a cada pixel.
3. No `pointerup`, **uma** transação `view.dispatch` reescreve o trecho para
   `![[nome|<largura final>]]`, arredondada para inteiro. Uma transação só, para o desfazer
   voltar o redimensionamento inteiro de uma vez, não pixel a pixel.
4. Limites: largura mínima ~80 px, máxima = largura do editor. Sem `|N`, largura natural.
5. Modo leitura (fora do CM6) só **aplica** a largura declarada; não precisa de alça.

#### App — a alça não cabe no editor atual, e isso é um limite real

No app, o modo edição é um `TextField` do Material 3 com `VisualTransformation`
(`MarkDown.kt:346-407`). **`VisualTransformation` não hospeda composables** — ela transforma
`AnnotatedString` em `AnnotatedString`. Não dá para pôr uma imagem de verdade, muito menos uma
alça arrastável, dentro do campo de edição sem trocar a arquitetura do editor (a migração para
`TextFieldState`, que continua proibida sem decisão do Bruno). **Não tentar.**

O que fazer, então:

1. **Modo leitura**: a imagem é renderizada de verdade (I.2) e **respeita o `|N`**.
2. **Redimensionar no modo leitura**: tocar na imagem abre uma folha com **predefinições
   (25% / 50% / 75% / 100% da largura da tela) e um controle deslizante**, mostrando a largura
   em px ao vivo. Confirmar reescreve o embed para `![[nome|N]]` e salva a nota.
   **Em tela de toque isso é mais confiável que arrastar uma alça de 24 px** — e grava
   exatamente o mesmo texto que o Obsidian gravaria.
3. **Alça de arrasto no modo leitura** é opcional e vem depois, **se** a folha não bastar:
   `pointerInput` sobre a imagem, largura em estado durante o arrasto, e uma única escrita no
   fim. Só medir e fazer se ele pedir.
4. **Modo edição do app**: o embed continua como texto (`![[nome|496]]`), como é hoje. Ele
   **vê e pode editar o número à mão**, e o resultado aparece no modo leitura e nos outros dois
   clientes. É a limitação honesta desta arquitetura — deixar escrito no relatório, não esconder.

#### Regras comuns aos três

- Gravar **só a largura** (`|496`), nunca `largura x altura` — é o que o Obsidian grava ao
  arrastar, e evita imagem distorcida. **Ler** `800x600` se aparecer, mas não gerar.
- A largura é em **pixels de CSS / dp**, não porcentagem — é o que o Obsidian grava e é o que
  mantém os três iguais.
- Redimensionar **nunca** toca no arquivo de imagem. Só o texto da nota muda.
- Uma só entrada no histórico de desfazer por redimensionamento.

---

### I.7 — A pergunta que sobrou (confirmar antes de I.2/I.4)

**Imagem remota (`![](https://…)`)**: renderizar dentro da nota faz o cliente buscar num
servidor de terceiro toda vez que a nota abre — entrega o IP dele e permite rastreamento por
quem hospeda. Proposta: **renderizar só caminhos do repositório e `data:image/*`**; URL externa
vira link clicável, não `<img>`. Se ele preferir renderizar, é decisão dele e fica registrada.

*(Fora de escopo até ele pedir: `![[Nota#Seção]]` embutindo o trecho de outra nota, que o
Obsidian faz e os dois clientes mostram como texto cru; e visualizador em tela cheia com zoom.
Nenhum dos dois é o que ele quis dizer com "redirecionamento".)*

---

### Critério de aceitação da Fase I

**I.0 (vault)**
- `git check-ignore -v` sobre um arquivo em `NOTAS/_anexos/` **não** casa com nenhuma regra.
- `git ls-files` passa a contar as imagens de anexo, e **só** elas — nada de `05_Sistema`,
  `07_Oraculo`, `Excalidraw`, `export` ou `.obsidian` entra no diff.
- Colar uma imagem no Obsidian passa a gravá-la em `NOTAS/_anexos/`, não na raiz.

**O teste que vale por todos (fazer nas três pontas):** colar **a mesma imagem** no Obsidian, no
app e na web; sincronizar; e **abrir a mesma nota nos três** — a imagem aparece nos três, no modo
leitura e no de edição, e o arquivo gerado tem o mesmo padrão de nome nos três.

**O segundo teste que vale por todos (redimensionamento, I.6):** redimensionar a mesma imagem
nos três, um de cada vez, sincronizando entre eles — e verificar que **o texto gravado na nota é
idêntico** (`![[nome.png|N]]`, só largura, inteiro) e que os outros dois passam a mostrar aquela
largura. Fazer o caso de partida com a imagem que já está no vault com `|496`.

**Testes de tamanho (módulo puro, nos dois clientes):** `![[x.png]]` → largura natural;
`![[x.png|496]]` → 496; `![[x.png|800x600]]` → lê 800 e **não** gera altura; `![[x.png|abc]]`
→ trata como texto do alias, não como tamanho, e não quebra; `![alt|300](x.png)` → 300.

**App**
- Teste de resolução (Kotlin puro, sem UI) para as 4 regras de I.1, incluindo nome duplicado em
  pastas diferentes e nome inexistente.
- Teste de amostragem: imagem sintética de 4000×3000 decodificada para um contêiner de 400 px
  consome bitmap compatível com 400 px, não com 4000 — medir os bytes e afirmar.
- `PERF_IMAGEM`: abrir nota com 10 imagens; registrar o tempo e o pico de memória.
- Teste do `.gitignore`: gravar anexo em repositório onde a pasta está ignorada **avisa**.
- Não-regressão: notas **sem** imagem renderizam exatamente como antes (os 244+ testes verdes).

**Web**
- Testes em `tests/markdown.test.ts`: `![[x.png]]` e `![](x.png)` passam a emitir `<img>` com
  `data-caminho` certo; URL externa **não** vira `<img>` (conforme I.7); `data:image/png` vira
  `<img>`; `data:text/html` **não**.
- Teste de vazamento: trocar de nota 50 vezes e verificar que os `blob:` foram revogados.
- `npm test` verde.

---

## FASE K — Pastas navegáveis na grade e a leva de 10 recursiva

**Conserto de uma regressão da Fase B, relatada pelo Bruno em 20/09** — tem prioridade sobre
G e H, porque quebra a navegação no uso diário.

### K.0 — O sintoma e a medição

**Relato dele:** *"Quando eu abro uma pasta raiz, que só tem pastas, aí não aparece nada e só
consigo navegar entre as pastas pelo painel lateral."*

**Causa:** a Fase B.2 trocou o filtro recursivo por `parentPath(relativePath) = :path`. Pasta
sem nota direta passou a devolver zero linhas, e as pastas só existem na gaveta
(`DrawerScreen`), nunca na grade.

**Tamanho do estrago, medido no vault:**

| Medida | Valor |
|---|---|
| Pastas no vault | 1.598 |
| **Pastas sem nota direta mas com subpasta — as que abrem em branco hoje** | **513** |
| Maior fan-out de subpastas (irrelevante, é `site-packages`) | 177 |
| Maior fan-out dentro de `06_Conhecimento` | 10 (`Medicina/Matérias Básicas`) |

**As três piores são exatamente o caminho de uso diário dele** (nenhuma tem nota direta):

- `06_Conhecimento` — 6 subpastas, **0 notas diretas**. É a **pasta padrão** da Fase C: hoje
  ele abre o app e vê **tela vazia**.
- `06_Conhecimento/Medicina` — 5 subpastas, 0 notas.
- `06_Conhecimento/Medicina/Matérias Básicas` — 10 subpastas, 0 notas.

### K.1 — Pastas como itens da grade

Tudo de que isto precisa **já existe**: `GridViewModel.drawerFolders` é um
`StateFlow<List<DrawerFolderModel>>` (com `noteCount` por pasta) e **já é coletado dentro do
`GridScreen`** para alimentar a gaveta. Não criar consulta nova.

- Renderizar as pastas como uma **seção acima das notas**, nos **dois** modos de visualização:
  na grade escalonada como itens de linha inteira (`StaggeredGridItemSpan.FullLine`), na lista
  como linhas comuns.
- Conteúdo de cada item: ícone de pasta, nome, e a contagem de notas que `noteCount` já traz.
- Tocar chama `vm.openFolder(caminho)`, o mesmo que a gaveta já faz.
- **O teto de 10 da Fase D não se aplica às pastas.** Ele é das notas.
- **Durante a busca** (`query` não vazia), **não** mostrar pastas: o resultado da busca é de
  notas e é recursivo.
- Quando o caminho atual não é a raiz, oferecer também um item de **subir um nível** no topo,
  para ele não depender da gaveta. `getParentPath` já existe e é o que o FAB da gaveta usa.
- **Estado vazio de verdade:** se não houver nem pasta nem nota, mostrar uma mensagem. Tela
  em branco nunca mais.

### K.2 — A leva inicial de 10 volta a ser recursiva

**Decidido pelo Bruno em 20/09:** *"na visualização dos 10, aí pode sim aparecer todas as notas
daquela pasta em específica e das pastas filhas, mas aí, limitando às últimas 10
visualizadas/modificadas"*.

Ou seja, a consulta da grade volta ao filtro **recursivo**, e quem segura o custo é o `LIMIT`:

- filtro: `(:path = '' OR relativePath LIKE :path || '/%')` — **com a barra**, para `Medicina`
  não casar com `Medicina2` (o defeito latente que a B.2 corrigiu não pode voltar);
- ordem: a da Fase D, por `MAX(lastOpenedTimeMillis, lastModifiedTimeMillis)` desc;
- `LIMIT 10` na abertura da pasta.

**Isto não desfaz o ganho da Fase B**, e é importante entender por quê: o que pesava eram o
`SELECT *` com a coluna de conteúdo (27 MB) e a função de janela sobre o conjunto inteiro.
Os dois saíram na B.1/B.3 e **não voltam**. Uma consulta recursiva com projeção leve e
`LIMIT 10` toca poucas linhas.

**"Mostrar todas"** passa a mostrar todas as notas da pasta **e das descendentes**, paginadas.
Na raiz isso é o vault inteiro — 4.309 linhas leves, paginadas de 50 em 50. **Medir** e
registrar: se a primeira página na raiz sem teto passar de ~100 ms, dizer no relatório e
propor índice, não improvisar.

### Critério de aceitação da Fase K

1. Abrir `06_Conhecimento` mostra **6 pastas** navegáveis **e** as 10 notas mais recentes das
   descendentes — hoje mostra nada. Mesmo teste para `Medicina` (5 pastas) e
   `Medicina/Matérias Básicas` (10 pastas).
2. Teste de SQL com a árvore sintética da Fase B: abrir `A` devolve as notas de `A` **e** de
   `A/B`, no máximo 10, e **nenhuma** de `A2`.
3. Tocar numa pasta da grade navega para ela; o item de subir um nível volta para a mãe.
4. Buscando, as pastas somem e os resultados continuam recursivos.
5. Pasta sem nada mostra a mensagem de vazio, não uma tela em branco.
6. `PERF_GRID_QUERY` em três cenários, na raiz do vault sintético de 4.309 notas: com teto de
   10, sem teto (primeira página), e a contagem de "Mostrar todas". Comparar com o número
   registrado no `RESULTADO_10_B.md`.
7. Os testes das Fases B e D continuam verdes — em especial o que prova que a projeção não
   traz conteúdo e o que prova que o carimbo de abertura sobrevive à reindexação.

---

## PARTE V — Renomear `06_Conhecimento` para `NOTAS` (fora do app)

**Pedido do Bruno:** "renomeie a pasta '06_conhecimento' para 'NOTAS'… quero deixar esse padrão
e todos os lugares que usam essa pasta, como o próprio app do celular já atualize de forma direta."

**Sessão separada.** Mexe no vault (`E:\Obsidian\CONHECIMENTO`) e no `notas-web`, não no app.

### O que foi medido

- **961 a 982 ocorrências de `06_Conhecimento` em 63 arquivos** do vault.
- **A maioria é registro histórico** (logs, saídas, capturas, backups) — **não pode ser
  reescrita**: é o registro do que era verdade na época. Só os `.json` de
  `04_IA_Workspace/Logs/` respondem por cerca de 830 ocorrências.
- **O app Android NÃO tem nenhuma referência** ao nome da pasta (grep em `app/src`: zero).
  O que existe no celular são **preferências** apontando para o caminho.
- **A CENTRAL (`E:\CENTRAL`) não tem referência em código.** Confirmado.
- **O Life SO desktop (`E:\Life SO`) só menciona num relatório**, não em código.
- **`notas-web` tem o nome cravado no código** — é o que mais quebra.

### V.1 — O renomear em si

```bash
git -C "E:/Obsidian/CONHECIMENTO" mv 06_Conhecimento NOTAS
```

`git mv` preserva histórico. Não é renomeação só de caixa, então não há a armadilha do
sistema de arquivos insensível a maiúsculas do Windows.

**Wikilinks `[[Nota]]` não carregam caminho** — não quebram. O que quebra é link markdown com
caminho (`[x](06_Conhecimento/…)`), busca `path:` e configuração.

### V.2 — Arquivos que **devem** ser atualizados (operacionais)

Vault:
- `PROTOCOLO.md` (3 ocorrências, linhas 36, 39, 60) — **é a fonte da verdade das permissões**,
  atualizar primeiro.
- `.obsidian/bookmarks.json` (4: duas buscas `path:`, um caminho de nota, um título)
- `.obsidian/graph.json` (2)
- `07_Oraculo/oraculo.config.json` (1: `"notas_autorais": "06_Conhecimento"`)
- `07_Oraculo/mcp/estudo_server.py` (5)
- `07_Oraculo/README.md` (3), `SYSTEM_TUTOR.md`, `SYSTEM_ORACULO.md`, `schema/SCHEMA.md`,
  `PLANO-INTEGRACAO-CENTRAL.md` (2)
- `04_IA_Workspace/Skills/analise/`: `SKILL.md` (3), `SPEC-eficiencia-incremental.md` (3),
  `scripts/threshold_router.py`, `scripts/prefiltro.py`, `scripts/hash_notas.py`
- `04_IA_Workspace/Skills/oraculo-construcao/SKILL.md`
- `04_IA_Workspace/Skills/coordinate-coding-agents/SKILL.md` e `scripts/acionar-agente.ps1`
- `05_Sistema/Agentes/_global.md` (2), `Claude/INICIO-claude-code.md` (2),
  `Claude/INICIO-claude-cowork.md` (2), `Codex/INICIO-codex-app.md`
- `05_Sistema/segundo-cerebro-arquitetura.md`,
  `05_Sistema/setup/HANDOFF-SONNET-estrutura-vault-aulas.md` (2),
  `05_Sistema/setup/HANDOFF-CODEX-mcp-toggle-app-config.md`,
  `05_Sistema/Bruno Kernel/Workflows/SPEC-fatia-1-infra-mte-oraculo.md`
- `04_IA_Workspace/Memory/contexto-geral.md`, `.claude/napkin.md`
- `09_Trilha-Pessoal/README - Arquitetura da Trilha.md` (7),
  `09_Trilha-Pessoal/REGISTRO - Fontes e Raciocínio do Mapa Macro.md`,
  `09_Trilha-Pessoal/01_Trivium/MOC - Trivium.md`
- `06_Conhecimento/Medicina/Pré-Clínico/Semiologia/Semiologia da Cabeça e Pescoço.md`
  (referência ao próprio caminho, dentro da pasta renomeada)
- `04_IA_Workspace/Estado/tarefas/grafo-bruno-revisao.md`
- Conferir também `C:\Users\bruno\.claude\CLAUDE.md` e `E:\Obsidian\CONHECIMENTO\CLAUDE.md`
  (no grep atual não apareceram, mas confirmar antes de fechar).

`notas-web` (`E:\Projetos\notas-web`):
- `src/github.ts:5` — `export const PASTA = '06_Conhecimento/';` **(a raiz de tudo)**
- `src/github.ts:58` — mensagem de erro
- `src/tree.ts:57` e `:143` — nome da raiz na árvore e na trilha
- `src/lateral.ts:95` — título padrão
- `src/main.ts:450` e `:871` — texto de entrada e rótulo da raiz no seletor
- `index.html:8` — `<title>`
- testes que afirmam a string: `tests/tree.test.ts`, `tests/lateral.test.ts`,
  `tests/operacoes.test.ts`, `tests/markdown.test.ts`, `tests/materiais.test.ts`,
  `tests/non-regression.test.ts`, `tests/api-session.test.ts`
- `scripts/certificar_handoff_05..08.py` — scripts de certificação; atualizar, ou passam a
  reprovar por motivo errado
- `docs/` é **saída de build** (GitHub Pages): **rebuildar**, não editar
  `docs/assets/index-*.js` à mão.

### V.3 — Arquivos que **não** devem ser tocados

`04_IA_Workspace/Logs/*`, `04_IA_Workspace/Outputs/*`, `04_IA_Workspace/Capturas/*`,
`04_IA_Workspace/Backups/*`, `05_Sistema/Backups/*`, `copilot/*`,
`.obsidian/graph.json.bak_*` (dois arquivos). São registro histórico; reescrevê-los inflaria o
diff e falsificaria o registro.

Também **não** reescrever os handoffs e relatórios antigos dos dois projetos —
`gitnote/HANDOFF_04_FLASHCARDS.md`, `gitnote/HANDOFF_06_SECAO_DESTAQUE_ROLAGEM.md`,
`notas-web/HANDOFF_01,02,05,06,07,08` e `notas-web/RELATORIO_IMPLEMENTACAO.md`. São o registro
do que foi feito com o nome antigo. **Este handoff 10 é a exceção**: como ele ainda vai ser
executado, atualizar as ocorrências dele para `NOTAS` ao fechar a Parte V.

`.obsidian/workspace.json` tem o caminho antigo, mas é estado de janela que o próprio Obsidian
reescreve ao abrir — não mexer à mão.

**Varredura completa do ecossistema, confirmada por scan:** fora do vault, do `notas-web` e dos
handoffs, **nada** referencia o nome. `E:\CENTRAL` deu **zero** arquivos; `E:\Life SO` deu
**um único relatório** (`meu-dashboard/RELATORIO_EDITOR_MD_20260828.md`, histórico, não tocar).

### V.4 — Efeitos colaterais a avisar ao Bruno antes de rodar

1. **O celular.** Depois do `git pull` no app, as preferências `lastOpenedFolder`,
   `defaultPathForNewNote` e a `pastaPadrao` (Fase C) ainda apontam para `06_Conhecimento`.
   A guarda da Fase C cobre a abertura; as outras duas precisam de conferência manual nos
   Ajustes. **Fazer a Parte V depois da Fase C**, para a guarda já estar no aparelho.
2. **Smart Connections / `.smart-env`**: os vetores são indexados por caminho. As 140 notas
   vão reindexar. Custo de tempo, não perda.
3. **O carimbo de "última visualização" da Fase D** é por `relativePath` — as 140 notas
   renomeadas perdem o carimbo e caem para a data de modificação. Aceitável; se incomodar,
   uma passada única reescrevendo o prefixo na tabela `Aberturas` resolve.
4. **A pasta de anexos da Fase I** (`_anexos/`) foi proposta **dentro** da pasta de notas
   justamente para ser carregada pela renomeação. Se a Fase I.0 for feita antes da Parte V, as
   exceções do `.gitignore` e o `attachmentFolderPath` do Obsidian apontam para
   `06_Conhecimento/_anexos` e **precisam ser reescritos** aqui — são duas linhas, mas se
   passarem batido o Obsidian volta a colar na raiz e o git volta a ignorar, em silêncio.
5. O nome `NOTAS` quebra o padrão numérico das irmãs (`00_Inbox`, `04_IA_Workspace`,
   `05_Sistema`, `07_Oraculo`, `09_Trilha-Pessoal`) e vai passar a aparecer **fora de ordem**
   na barra lateral do Obsidian (a ordenação alfabética põe `NOTAS` depois dos números).
   **É escolha dele e ele já decidiu** — está registrado só para não virar surpresa. Se um dia
   quiser a ordem de volta, `06_NOTAS` resolveria sem perder a elegância.

### Critério de aceitação da Parte V

- O Obsidian abre sem link quebrado: rodar a verificação de links do vault antes e depois e
  comparar a contagem.
- `notas-web`: `npm test` verde e a página publicada lê a pasta nova.
- O app no celular sincroniza e abre na pasta certa.
- Nenhum arquivo de `Logs/`, `Outputs/`, `Capturas/`, `Backups/` ou `copilot/` no diff.

---

## Decisões que dependem do Bruno

1. ~~`TextFieldState` / `BasicTextField2`~~ — **DECIDIDO em 20/09: vai ser feito.** Virou a
   **Fase J**, com o levantamento da documentação, o raio de alcance medido, as sub-fases e o
   critério de abandono. O que sobra aqui não é decisão, é ordem: **H.1 e H.2 primeiro**, e
   se o custo por tecla já cair o suficiente ali, J deixa de ser urgência e passa a valer pelo
   `scrollState`, pelo desfazer embutido e pelos dois defeitos do modelo antigo — o que ainda
   justifica fazê-la, sem pressa.

   **Levantado na documentação oficial em 20/09 — o risco que travava a decisão caiu:**
   - `TextFieldBuffer.addStyle(SpanStyle | ParagraphStyle, início, fim)` existe **desde o
     Compose 1.9 (agosto/2025)** e é chamável **dentro do `OutputTransformation`**. O projeto
     usa o BOM `2026.06.01` — **a API está disponível hoje**. O tema colorido do live preview
     porta: `SpanStyle` cobre cor, peso, tamanho e `baselineShift`, que é exatamente o que
     `MarkdownLivePreviewTransformation` aplica hoje (cor por elemento, tamanho de título,
     índice sub/sobrescrito da fórmula).
   - **O mapeamento de offsets deixa de ser problema nosso.** A documentação oficial diz que a
     diferença-chave para a `VisualTransformation` é que *"você não precisa calcular os
     mapeamentos de offset"*. Os dois `IntArray(source.length + 1)` por tecla somem.
   - Esconder marcador vira `delete()` no buffer; substituir a fórmula por Unicode vira
     `replace()` — os dois com mapeamento automático. É **menos** código que hoje.
   - **Bônus que resolve a pendência do handoff 07:** o `BasicTextField` novo expõe
     `scrollState`. A faixa de rolagem, o sumário (Fase E) e a busca (Fase G) passariam a rolar
     de verdade, em vez de mover o cursor e torcer.
   - **A ressalva que sobra:** `transformOutput` é chamado *"toda vez que um texto novo precisa
     ser exibido"* e o resultado é **descartado depois de desenhar** — o framework não memoriza
     nada. `MarkdownScanner.scan()` rodaria de novo ali dentro.
     **H.2 (memorizar a varredura) é pré-requisito da migração, não alternativa a ela.**
   - **H.1 é absorvido:** a seleção pode ser lida de dentro do `transformOutput` (o buffer
     carrega texto e seleção), então o `activeLines` deixa de depender da chave do `remember`.
   - Restrição a respeitar: **mudar seleção/cursor dentro do `transformOutput` não tem efeito**.
     O código atual não faz isso, então não afeta — mas quem migrar precisa saber.

   Conclusão para a decisão: **fazer H.1 + H.2 primeiro e medir**. Se o número não cair o
   suficiente, a migração deixou de ser salto no escuro — falta só uma prova de conceito do
   live preview colorido com `addStyle` para confirmar na prática o que a documentação promete.
2. **Listagem recursiva** (Fase B.2): depois da mudança, abrir uma pasta mostra só as notas
   dela, não as das subpastas. É o que ele pediu, mas muda o hábito. Se quiser as duas coisas,
   vira um botão "incluir subpastas".
3. **O teto de 10** (Fase D.3): fixo em 10 ou ajustável nos Ajustes? Começar fixo.
4. **O trabalho de segurança não commitado** na árvore agora — o que fazer com ele antes de
   abrir a Fase A.
5. **Imagens no repositório (I.0)** — é o bloqueio da Fase I inteira e só ele decide:
   qual pasta de anexos, qual exceção no `.gitignore`, qual teto por imagem, e o que fazer com
   as 20 imagens soltas na raiz do vault. Sem essa decisão, nenhum código de imagem serve
   para nada.
6. ~~"Redirecionamento" das imagens~~ — **respondido em 20/09**: é **redimensionar a imagem
   no próprio documento**, como no Obsidian, gravando `![[nome.png|largura]]`. Ver I.6. Não é
   visualizador de tela cheia.
7. **Imagem remota na nota (I.7)**: renderizar `![](https://…)` entrega o IP dele ao servidor
   de terceiro a cada abertura. Proposta é não renderizar; a decisão é dele. **Única pergunta
   da Fase I ainda aberta.**

---

## O que NÃO fazer

- Não compilar localmente. Não instalar JDK/SDK/Gradle.
- Não mexer no `applicationId` nem na chave de assinatura (`app/nightly-signing-key.jks`
  é versionada de propósito).
- Não içar a rolagem para fora do `TextField` no modelo antigo — **já foi tentado e reprovou**
  no handoff 07 (o cursor deixava de ser trazido para a tela). A saída legítima é o
  `scrollState` do `BasicTextField` novo, e ela pertence à **Fase J.5**, não a um remendo.
- Não começar a Fase J sem H.2 medido, e não começar J.3 sem J.2 aprovada.
- **Não rodar J.2, J.3, J.4 ou J.5 em modelo de execução.** São Opus 5 com esforço máximo.
  Quem terminar J.1 para, avisa e entrega o prompt da J.2 — não emenda.
- Não reformatar tabelas existentes do vault (handoff 08: poluiria o histórico).
- Não reescrever os registros históricos do vault na Parte V.
- Não juntar fases num commit só. Cada fase: testes, commit, empurrar, ler o log, relatório.
- Não aceitar código novo no editor sem responder: **isto roda a cada tecla? e numa nota de
  1.946 linhas?** Já aconteceu três vezes neste app (âncora O(n²) na abertura,
  `tableRegionAt` a 158 ms, `lineOfOffset` alocando por tecla).

---

## Relatório esperado

Um `RESULTADO_10_<FASE>.md` por fase, em `E:\Projetos\gitnote\`, com: o que mudou, os números
`PERF_` antes e depois, os testes escritos (nome e o que provam), o que **não** foi feito e por
quê, e o número da release do fork gerada pelo CI.
