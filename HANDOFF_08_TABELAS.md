# HANDOFF 08 — inserir e reconfigurar tabelas no editor

**Data:** 2026-09-18
**Destinatário:** Codex
**Repositório:** `E:\Projetos\gitnote` (fork `brunojcbrg-cloud/gitnote`)
**Commit base:** `01f0c06`
**Tamanho:** médio-grande. O núcleo é código puro e testável; a parte arriscada é uma só, e está isolada na §6.

---

## 0. Como ler este handoff

Cada parte tem **fatos medidos** (já apurados, não reapurar), **o que fazer** e **testes obrigatórios**.
Onde estiver escrito "meça antes", meça de verdade e **reporte o que encontrou**, mesmo que a conclusão
seja "o handoff estava errado". Hipótese derrubada por medição é entrega, não fracasso.

Não há SDK Android nem JDK na máquina do Bruno. **Não tente compilar localmente.** O `fork-release.yml`
dispara sozinho em push na `master` e roda `testDebugUnitTest` antes de montar o APK. Desde 18/09 o CI
**lista no log cada teste que executou** (`testLogging` em `app/build.gradle.kts`) — use isso como prova,
porque o relatório HTML só vira artefato quando a rodada falha.

Leia antes de começar: `RESULTADO_07_ROLAGEM_E_LISTA_AUTOMATICA.md`. Ele tem os números do editor e as
armadilhas já pagas.

---

## 1. O pedido, nas palavras dele

> "No modo de edição, eu gostaria que existisse algum botão para inserir tabela. A tabela é inserida em
> md mesmo, mas eu que teria que escrever cada caractere da tabela. Mas às vezes eu me embolo na hora de
> escrever isso e não consigo montar a tabela. Eu preciso de um botão que vai me perguntar o modelo da
> tabela que desejo criar. Por exemplo, quero uma tabela 3x2, aí ele pergunta quantas colunas e quantas
> linhas quero nessa tabela, aí eu escrevo e ele monta o esqueleto da tabela de forma fácil para mim e eu
> só preencho depois."

> "E quero que o app detecte, quando estiver no modo edição, que é uma tabela e, ao clicar e segurar ali
> na região da tabela, ele me dê opções de configuração da tabela, como alterar colunas e linhas. Ao
> selecionar, ele reconfigure a tabela, mas, caso já tenha conteúdo dentro dela, não apague, apenas
> adicione ou tire as colunas e linhas que eu indiquei."

Isso vira três partes: **A** (inserir), **B** (reconhecer e reconfigurar) e **C** (o núcleo puro que as
duas usam). Escreva **C primeiro** — A e B são casca fina em cima dele.

---

## 2. Mapa do código (fatos medidos — não reapurar)

| Arquivo | Papel |
|---|---|
| `ui/screen/app/edit/MarkDown.kt:598` | `TextFormatRow` — a barra de formatação expandida, onde o botão novo entra |
| `ui/screen/app/edit/BottomBar.kt:131` | `SmallButton` — `IconButton` de **36dp**, ícone de 20dp |
| `ui/screen/app/edit/BottomBar.kt:41` | `DefaultRow` — a barra fechada, com o botão que expande a de formatação |
| `ui/screen/app/edit/EditScreen.kt:282` | quem decide entre `TextFormatRow` e `DefaultRow` (`textFormatExpanded`) |
| `ui/screen/app/edit/EditScreen.kt:320` | `GenericTextField` — o `TextField` do M3 que é o editor |
| `ui/viewmodel/edit/MarkDownVM.kt:50-93` | `onTitle`, `onBold`, `onQuote`, `onUnorderedList`… — **o padrão a seguir** |
| `ui/viewmodel/edit/MarkdownSmartEditor.kt` | as funções puras que aqueles botões chamam |
| `ui/component/BaseDialog.kt` | `BaseDialog(expanded: MutableState<Boolean>) { … }` |
| `ui/component/GetStringDialog.kt` | exemplo de diálogo com campo, `keyboardType`, foco automático |
| `ui/screen/app/grid/markdownHelper.kt:154` | o modo **leitura já renderiza tabela** (`CurrentComponentsBridge.table`) |

Medições já feitas por leitura, que economizam rodadas:

1. **Todo botão de formatação segue o mesmo caminho:** função pura `(TextFieldValue) -> TextFieldValue`
   em `MarkdownSmartEditor.kt`, chamada por um método do `MarkDownVM` que termina em
   `super.onValueChange(newValue)` (`MarkDownVM.kt:70`). É isso que põe a mudança no histórico de
   undo/redo e dispara o salvamento. **Faça igual.** Uma edição de tabela = **um** passo de undo.
2. **A `TextFormatRow` já tem 11 botões de 36dp = 396dp**, e a tela dele tem **360dp**. O
   `Modifier.scrollable(...)` da linha 609 **não rola conteúdo nenhum** — `scrollable` só informa
   deltas; quem move filhos é `horizontalScroll`. Ou seja: a barra provavelmente **já corta** botões
   hoje, e o seu será o 12º. **Meça** (um print do Robolectric com a largura da linha serve) e, se
   cortar, troque por `Modifier.horizontalScroll(rememberScrollState())`. Reporte a largura medida.
3. **`material-icons-extended` está no projeto** (`app/build.gradle.kts:183`), então
   `Icons.Default.TableChart` / `Icons.Default.GridOn` estão disponíveis.
4. **O modo leitura não precisa de nada.** Tabela já renderiza lá. Este handoff é só do editor.
5. **O editor é texto puro com live preview.** Não existe `TextLayoutResult` acessível nem
   `ScrollState` no `TextField` (handoff 07). Qualquer ideia de "grade visual" esbarra na migração
   para `TextFieldState`, que continua **proibida** (§8).

---

## 3. Medições no vault real (`E:\Obsidian\CONHECIMENTO`, 3.814 notas)

| Métrica | Valor |
|---|---|
| tabelas | **241**, em 113 notas |
| colunas | 2 → 83 tabelas; 3 → 89; 4 → 37; 5 → 10; 6 → 6; 7 → 12; 8 → 3; **9 → 1** |
| linhas de corpo | tipicamente 2 a 10; mais comuns 4, 3, 6 e 5 |
| com espaço depois do pipe (`\| a \| b \|`) | **231 de 241** |
| coladas (`\|a\|b\|`) | 10 |
| sem pipe externo | 3 |
| com alinhamento (`:--`) | ~12; o resto usa `---` |
| linhas de corpo de tamanho irregular | **7 tabelas de 241** |
| células vazias no corpo | 15 |
| tabelas em arquivo CRLF | **0** (mas 134 arquivos do vault são CRLF) |

Três coisas saltam daí e viram regra:

- **O formato a gerar é `| a | b |` com `---` no separador.** É o que 231 de 241 tabelas usam.
- **Não "arrume" tabela existente.** Reserializar as 241 no estilo canônico encheria o histórico do
  vault de diferenças inúteis. Ao reconfigurar, **preserve o estilo do original** (pipe externo ou não,
  espaço ou colado, alinhamento por coluna).
- **11.221 linhas do vault têm pipe, e 3.960 delas NÃO são tabela** (35%). Um detector que olhe só para
  "a linha tem `|`" erra em um terço dos casos. Detector é a §5.1 e tem teste próprio.

Mais duas armadilhas medidas: **58 pipes escapados (`\|`)** e **314 pipes dentro de código inline
(`` `a|b` ``)** no vault. Nenhum dos dois divide célula.

---

## 4. Parte C — o núcleo puro (escreva primeiro)

Arquivo novo: `ui/viewmodel/edit/MarkdownTable.kt`. Nada de Compose aqui, só Kotlin — é o que roda na JVM.

```kotlin
data class MdTableCell(val text: String)          // o conteudo cru da celula, sem os pipes
enum class MdAlign { NONE, LEFT, CENTER, RIGHT }

data class MdTable(
    val header: List<String>,
    val aligns: List<MdAlign>,
    val rows: List<List<String>>,                 // so o corpo
    val style: MdTableStyle,                      // como o original estava escrito
)

data class MdTableStyle(
    val outerPipes: Boolean,                      // `| a |` vs `a | b`
    val padded: Boolean,                          // espaco depois/antes do pipe
    val lineEnding: String,                       // "\n" ou "\r\n"
)

fun parseTable(text: String, headerLine: Int): MdTable?
fun renderTable(table: MdTable): String
fun buildTable(columns: Int, bodyRows: Int, lineEnding: String): String
fun resizeTable(table: MdTable, columns: Int, bodyRows: Int): MdTable
fun cellsOf(line: String): List<String>           // divisao que respeita \| e codigo inline
```

Regras que não são negociáveis:

- **`cellsOf` não divide em `\|` nem dentro de `` ` ` ``.** São 58 + 314 casos reais no vault.
- **`renderTable(parseTable(x)) == x`, byte a byte**, para tabela já bem-formada. Isso é teste (§7.C1)
  e é o que impede o app de reescrever o vault do Bruno.
- **Linha irregular nunca perde texto.** O número de colunas é o do cabeçalho. Linha com células a
  menos ganha células vazias; linha com células a mais tem o excedente **juntado na última célula**
  (com o pipe de volta, `a | b` vira `a \| b`? **não** — junte com `" | "` literal e documente). Decida,
  documente no relatório e teste. O que não pode é sumir texto.
- **`resizeTable` só acrescenta ou tira nas pontas**: coluna nova entra à direita, linha nova entra
  embaixo; remoção tira da direita e de baixo. Conteúdo das células que continuam existindo **não muda**.
- **Remoção que apaga conteúdo tem que ser anunciada.** `resizeTable` não decide sozinha: devolva
  também quantas células **não vazias** serão perdidas, para a interface perguntar antes (§5.2).
- **Terminador de linha**: use o dominante do documento, não `\n` fixo. 134 arquivos do vault são CRLF.

---

## 5. Parte A — o botão que insere a tabela

### 5.1 O que entregar

Um 12º botão na `TextFormatRow` (ícone de tabela) que abre um diálogo com **dois campos numéricos**:
**colunas** e **linhas** — onde "linhas" é o **corpo**, sem contar o cabeçalho. Escreva isso no rótulo
("linhas, sem contar o cabeçalho"), senão ele pede 2 e recebe 3.

- Limites: colunas **1 a 10** (a maior do vault tem 9), linhas **1 a 50**. Fora disso, o botão de
  confirmar fica desligado — não corrija o número por baixo do pano.
- Padrão pré-preenchido: **3 colunas, 2 linhas** (é o exemplo dele, e 3 é a largura mais comum do vault).
- Mostre no diálogo uma **prévia em texto** do esqueleto, montada pela mesma `buildTable` — assim a
  prévia não pode divergir do que será inserido.
- Reaproveite `BaseDialog`. Não invente um sistema de diálogo novo.

### 5.2 Onde o texto entra

- A tabela entra **em linha própria**, nunca no meio de uma linha com texto. Se o cursor estiver no meio
  de uma linha não vazia, a tabela vai **depois** dessa linha.
- Garanta **exatamente uma linha em branco antes e uma depois** da tabela, sem criar linhas em branco
  duplicadas nem colar a tabela no parágrafo anterior.
- **O cursor termina dentro da primeira célula do cabeçalho.** É o que faz "eu só preencho depois"
  funcionar: ele digita direto.
- Células nascem **vazias** (`|  |  |`), sem texto de exemplo para apagar.
- Se o cursor estiver dentro de um bloco cercado (``` ``` ```), **insira mesmo assim, literalmente** —
  é texto como qualquer outro. Só não invente linha em branco lá dentro. Teste esse caso.

---

## 6. Parte B — reconhecer a tabela e reconfigurar

### 6.1 O detector (puro, com teste próprio)

```kotlin
data class MdTableRegion(val headerLine: Int, val separatorLine: Int, val lastLine: Int)
fun tableRegionAt(text: String, offset: Int): MdTableRegion?
```

Uma tabela é: uma linha com pipe, **seguida de uma linha separadora**, seguida de zero ou mais linhas
com pipe até a primeira linha vazia ou sem pipe. O cursor conta como "dentro" se estiver em **qualquer**
dessas linhas.

Falsos positivos que o detector **tem que recusar** (são os 3.960 casos do vault):

- linha de prosa com um pipe solto, sem separadora embaixo;
- `---` sozinho numa linha: isso é **régua horizontal**, não separador de tabela — e existe na nota dele;
- pipe dentro de código inline ou dentro de bloco cercado;
- "tabela" sem separadora (cabeçalho e corpo, mas alguém apagou a linha do meio).

### 6.2 O gesto — meça antes, e tenha plano B

O pedido é toque longo em cima da tabela. **O toque longo dentro do `TextField` pertence ao Android**:
é ele que abre as alças de seleção e o menu Copiar/Colar. O handoff 06 já mediu que um `pointerInput`
guloso em cima do conteúdo mata o toque que está embaixo.

**Rodada 1 — meça isto e reporte antes de implementar:** um `pointerInput` que observa em
`PointerEventPass.Initial` **sem `consume()`** (é o que a âncora do modo leitura já faz,
`MarkDown.kt:244`) consegue reconhecer um toque longo — pressão sem movimento por mais de
`viewConfiguration.longPressTimeoutMillis` — e abrir a folha de configuração **sem** matar a seleção de
texto? Diga o que mediu.

- **Se sim**: entregue o toque longo, restrito à região da tabela (o detector diz se está dentro).
  Aceite que a seleção do Android também vai começar por baixo; verifique que fechar a folha não deixa
  a seleção presa.
- **Se não** (a seleção quebra em qualquer lugar do editor): **desista do gesto**, reverta e reporte.
  Não entregue um editor onde não se consegue mais selecionar texto em troca de um atalho.

**O caminho garantido, que sai de qualquer jeito:** o **mesmo botão** da §5 é sensível ao contexto —
quando o cursor está dentro de uma tabela, ele abre **"Configurar tabela"** em vez de "Nova tabela".
Isso não briga com gesto nenhum e é o que garante que o recurso chega ao Bruno. Entregue isto primeiro,
e só depois tente o gesto.

### 6.3 A folha de configuração

- Abre com as dimensões atuais já preenchidas ("3 colunas, 5 linhas").
- Mesmos limites e mesma validação da §5.1.
- **Se a mudança for apagar célula com conteúdo**, o botão de confirmar vira uma pergunta explícita:
  "2 células com conteúdo serão perdidas. Continuar?" — com o número real vindo da `resizeTable`.
  Nunca apague conteúdo em silêncio; é o que ele pediu com todas as letras.
- Aplicar = uma chamada `vm.onTableResize(...)` que termina em `super.onValueChange`, **um passo de undo**.
- Depois de aplicar, o cursor fica na primeira célula que continua existindo — nunca no fim do documento.

---

## 7. Testes obrigatórios

Tudo em `app/src/test/`, rodando com `./gradlew testDebugUnitTest`. **Implemente todos.** Se algum não
fizer sentido depois de ler o código, diga qual e por quê — não omita em silêncio.

### 7.C — núcleo (é onde tem que estar o volume)

**C1. Ida e volta byte a byte.** Para uma amostra de **pelo menos 12 formas reais** (copie-as deste
handoff e do vault): com e sem pipe externo, com e sem espaço, com `:--`, `--:`, `:-:`, 2 e 9 colunas,
corpo de 1 e de 10 linhas, célula vazia, célula com `\|`, célula com `` `a|b` ``, em `\n` e em `\r\n`.
`renderTable(parseTable(x)) == x` em todas.

**C2. `cellsOf`**: `|a|b|`, `| a | b |`, `a | b` (sem pipe externo), `| a \| b |` (uma célula),
`` | `x|y` | z | `` (duas células), célula vazia, célula só com espaços, pipe no fim sem célula depois.

**C3. `buildTable`**: 3×2 (o caso dele), 1×1, 10×50, o separador com o número certo de colunas, e o
resultado **parseia de volta** para uma tabela com as mesmas dimensões.

**C4. `resizeTable`**:
- crescer colunas preserva todo o conteúdo e alinhamentos existentes, e as células novas nascem vazias;
- crescer linhas idem;
- encolher colunas com células vazias na ponta não relata perda;
- encolher colunas com conteúdo relata **o número exato** de células não vazias perdidas;
- encolher para 1 coluna e 1 linha continua sendo tabela válida;
- alinhamento de cada coluna que sobrevive não muda de lugar;
- linha irregular (7 casos reais no vault) é normalizada **sem perder texto**;
- tabela sem pipe externo continua sem pipe externo depois de redimensionada.

### 7.A — inserção

- cursor em documento vazio; no meio de um parágrafo; no fim do arquivo sem quebra final; em linha em
  branco entre dois parágrafos; dentro de bloco cercado;
- o resultado tem **exatamente uma** linha em branco antes e depois, sem duplicar;
- o cursor final está dentro da primeira célula do cabeçalho (posição exata, não "aproximadamente");
- documento CRLF recebe tabela com CRLF;
- inserir não mexe em nenhum caractere fora do trecho inserido.

### 7.B — detector

- cursor no cabeçalho, no separador, na primeira linha do corpo, na última, e na linha em branco logo
  depois (essa **não** é tabela);
- os falsos positivos da §6.1, um teste para cada — incluindo `---` sozinho e pipe em código;
- tabela colada no fim do arquivo, sem quebra final;
- duas tabelas seguidas separadas por uma linha em branco: o cursor na segunda acha **a segunda**.

### 7.D — camada de UI (esforço limitado)

O projeto já tem Robolectric + `compose-ui-test` (entraram no handoff 07) e há teste de editor de
verdade em `MarkdownEditorUiTest.kt`. Cubra:

- abrir o diálogo pelo botão e confirmar 3×2 insere o esqueleto no `TextField` de verdade;
- com o cursor dentro de uma tabela, o mesmo botão abre a folha de **configuração** (e não a de criação);
- a largura da `TextFormatRow` com 12 botões (§2.2): imprima a largura medida no log.

**Teto de esforço:** se a camada de UI custar mais de duas tentativas, entregue o que é puro, **diga o
que ficou de fora** e compense no roteiro manual do §9.

---

## 8. O que NÃO fazer

- **Não migre o editor para `TextFieldState` / `InputTransformation`.** Continua valendo o §7 do handoff
  07: arrasta junto o `TextVM` inteiro, o undo/redo, os botões e o salvamento, e põe em risco o live
  preview e os wikilinks. Se concluir que não há saída sem isso, **pare e escreva o porquê**.
- **Não faça editor visual de tabela** (grade com células tocáveis). O editor é texto; a grade exigiria
  o item acima.
- **Não reformate tabelas existentes.** Nada de alinhar pipes em coluna, nada de padronizar espaço.
  São 241 tabelas no vault; reescrevê-las polui o histórico do Git e é o tipo de mudança que ele não
  pediu.
- **Não crie opção nos Ajustes.**
- **Não mexa no modo leitura**, que já renderiza tabela, nem no `MarkdownScanner` — se precisar de mais
  informação dele, acrescente sem refazer.
- **Não deixe o toque longo da tabela matar a seleção de texto do editor** (§6.2).
- **Não altere o `applicationId`.**

---

## 9. Entrega

Commits pequenos e separados por parte (C, A, B), na `master`. O `fork-release.yml` roda sozinho no push
e o log lista cada teste que rodou.

No fim, escreva **`RESULTADO_08_TABELAS.md` no repositório** — o relatório do handoff 07 não foi escrito
e os números ficaram só em log de CI, que expira. Ele precisa conter:

1. **O que a rodada 1 da §6.2 mediu** sobre o toque longo, com o que aconteceu com a seleção de texto.
2. A largura medida da `TextFormatRow` com 12 botões, e o que você fez a respeito.
3. As decisões que este handoff mandou documentar: como a linha irregular é normalizada, e o que
   acontece com o alinhamento de uma coluna removida.
4. O que ficou de fora e por quê.
5. Um **roteiro manual numerado** para o Bruno conferir no aparelho, com o que ele deve ver em cada
   passo — incluindo criar uma 3×2, preencher, transformar em 4×3 e conferir que nada se perdeu.

Conclusão sem número medido não conta.
