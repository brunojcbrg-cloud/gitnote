# RESULTADO 10 — FASE B

## O que mudou

**B.1 — projeção sem conteúdo.** Novo tipo `ui/model/GridRow.kt`
(`relativePath, id, lastModifiedTimeMillis, isUnique, selected`), sem nenhum campo de
conteúdo — substitui `GridNote` (que embutia `Note` inteiro via `@Embedded`), removido de
`ui/model/Grid.kt`. `gridNotesRaw` (`Dao.kt`) passa a devolver
`PagingSource<Int, GridRow>`.

**B.2 — consulta por pasta, não por prefixo.** `gridNotes` (`Dao.kt`) trocou
`WHERE relativePath LIKE :path || '%'` por `WHERE parentPath(relativePath) = :path`.
Mudança de comportamento intencional e já anotada no handoff: abrir uma pasta lista só
as notas soltas dela; as subpastas se abrem pela gaveta. `gridNotesWithQuery` (a busca,
que continua recursiva) trocou `LIKE :path || '%'` por
`(:path = '' OR relativePath LIKE :path || '/%')`, corrigindo o defeito latente que
fazia abrir `Medicina` casar também com `Medicina2/...`.

**B.3 — tirar a função de janela do caminho quente.** Em `gridNotes`, dentro de uma
única pasta o nome do arquivo é sempre único (é a chave primária), então
`COUNT(*) OVER (PARTITION BY fileName)` foi removido e `isUnique` virou a constante
`1 AS isUnique` — sem custo de janela nem de `fullName()`. Como bônus direto da mesma
mudança: como toda linha devolvida já compartilha o mesmo prefixo de pasta, ordenar por
`relativePath` dá o mesmo resultado que ordenar por `fileName()`, então o `AZ`/`ZA` de
`gridNotes` também deixou de chamar `fullName()` (prova em
`NoteFolderFilterLogicTest`/comentário no código — é consequência matemática direta do
prefixo comum, não uma mudança independente). `gridNotesWithQuery` **manteve** o
desambiguador (nomes repetidos existem de verdade entre pastas diferentes numa busca
recursiva) e passou a selecionar só `relativePath, id, lastModifiedTimeMillis` (mais
`score`/`fileName` usados só no `ORDER BY`) em vez de `Notes.*` — também parou de trazer
`content`.

**B.4 — card só com título e data.** `GridScreen.kt:NoteCard` não chama mais
`MarkdownCustom`/`Text(gridNote.note.content)`; mostra título (já existia, com a lógica
de `isUnique`/`showFullPathOfNotes` intacta) + data formatada
(`DateFormat.getDateTimeInstance(MEDIUM, SHORT)`), igual a `ListView.kt:NoteListRow`.
`MarkdownCustom` (`markdownHelper.kt`) ficou sem nenhum chamador e foi apagada;
`MarkdownCustomInner` (modo leitura) não foi tocada. Clicar no card/linha ou apagar
não usa mais o `Note` que vinha embutido — chama `vm.abrirNota(relativePath) { note -> ... }`,
que busca com `dao.noteByRelativePath` em `Dispatchers.IO` e volta pro main thread só
pra invocar o callback. `GridViewModel.selectedNotes` trocou de `List<Note>` para
`Set<String>` (caminhos); `deleteNote`/`deleteSelectedNotes` agora recebem/guardam
caminho(s) e buscam o `Note` inteiro só na hora de apagar.

Nenhum arquivo do trabalho de segurança não commitado foi tocado.

## Números PERF_

**Não escrevi `PERF_GRID_QUERY`.** O critério de aceitação pedia medir `gridNotes`
antes/depois com 4.309 notas sintéticas somando 27 MB — mas tanto a consulta **antiga**
quanto a **nova** dependem de funções SQLite customizadas (`fullName`, e agora também
`parentPath`) que só carregam via `RepoDatabase.buildFactory` (requery), e essa lib
nativa não roda na JVM de teste (`UnsatisfiedLinkError`, medido na Fase A). Ou seja: essa
medição já era impossível neste CI **antes** da Fase B existir — não é uma regressão
desta fase, é a mesma causa raiz da divergência abaixo. Registrando para não passar a
impressão de que o número "sumiu" por descuido.

## Testes escritos

Todos em cima da decisão do Bruno (ver divergência abaixo): comprovar a lógica em
Kotlin puro e o texto do SQL de produção, já que a consulta em si não roda no CI.

1. **`NoteFolderFilterLogicTest`** (`data/room/`) — `Note.parentPath()` faz exatamente a
   mesma conta que a função SQL `parentPath()` (mesma expressão,
   `substringBeforeLast("/", "")`). Roda a árvore sintética do critério de aceitação da
   Fase B (raiz com 3, `A/` com 5, `A/B/` com 7, `A2/` com 2) contra essa lógica:
   abrir `""` dá 3, abrir `"A"` dá 5 (não 12) e não inclui nada de `A2`. Também documenta
   o defeito do `LIKE` antigo (`"Medicina2/nota.md".startsWith("Medicina")` é `true`) e
   prova que o padrão novo (`+"/"`) não casa.
2. **`GridRowTest`** (`ui/model/`) — `GridRow` não tem nenhum campo com "content" no
   nome (reflexão sobre os campos declarados); uma linha com o caminho mais comprido do
   vault real fica bem abaixo de 1 KB; `nameWithoutExtension()`/`fileExtension()`
   reproduzem o que `Note` já faz.
3. **`GridQuerySqlTextTest`** (`data/room/`) — lê o texto de `Dao.kt` (mesmo estilo do
   `GridScreenFlashcardsRemovedTest` da Fase A) e confere que o SQL **de produção**: usa
   `parentPath(relativePath) = :path` em `gridNotes` (não mais o `LIKE` de prefixo); não
   tem mais `COUNT(*) OVER` nem a coluna `content` em `gridNotes`; `gridNotesWithQuery`
   mantém a janela e `fullName`, e tem o padrão corrigido
   `(:path = '' OR relativePath LIKE :path || '/%')`; `gridNotesRaw` devolve `GridRow`,
   não `GridNote`.
4. **`GridScreenCardSemConteudoTest`** (`ui/screen/app/grid/`) — mesmo estilo: `NoteCard`
   não chama mais `MarkdownCustom(` nem lê `.note.content`; mostra a data formatada;
   abrir a nota passa por `vm.abrirNota(...)`; `MarkdownCustom` foi apagada de
   `markdownHelper.kt` e `MarkdownCustomInner` continua.

## O que não foi feito e por quê

- **Nenhum teste roda a consulta SQL de verdade** (`gridNotes`/`gridNotesWithQuery`
  contra um banco Room de verdade com `parentPath`/`fullName` registrados) — decisão do
  Bruno, ver divergência abaixo. O que está provado é a lógica (Kotlin puro) e o texto
  exato do SQL que vai pro banco; a ponte entre os dois (rodar aquele texto contra aquela
  lógica dentro do SQLite de verdade) fica sem prova automatizada neste CI.
- Não toquei em `drawerFolders` (só é citada como referência no handoff) nem em nada da
  Fase C/D em diante.
- `showFullNoteHeight`/`noteMinWidth` continuam existindo mas, como o handoff já avisava,
  `showFullNoteHeight` deixou de ter efeito visível (o card agora tem altura fixa de
  título+data) — não apaguei o ajuste, só registrando aqui como o handoff pediu.

## Divergências (também em `ESTADO_10.md`)

Confirmado no início da Fase B, exatamente como a divergência 2 da Fase A antecipava:
`gridNotes`/`gridNotesWithQuery`/`drawerFolders` só funcionam com as 4 funções SQLite
customizadas do `RepoDatabase.buildFactory` (requery), que não carrega na JVM de teste.
Pesquisei duas alternativas (SQLite puro em JVM via `org.xerial:sqlite-jdbc`; API interna
não documentada do Robolectric) e perguntei ao Bruno antes de escrever qualquer teste.
Ele escolheu aceitar a lacuna: implementar a fase inteira em produção e testar a lógica
isoladamente (item acima), documentando que os testes de SQL ponta a ponta não são
executáveis neste CI hoje. Texto completo da pergunta e da resposta em `ESTADO_10.md`.

## Release do CI

_(preencher depois do push)_
