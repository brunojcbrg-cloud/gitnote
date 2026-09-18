# HANDOFF 07 — Rolagem no modo edição, não perder o lugar, e lista automática

**Data:** 2026-09-17
**Destinatário:** Codex
**Repositório:** `E:\Projetos\gitnote` (fork `brunojcbrg-cloud/gitnote`)
**Commit base:** `0b63cf7`
**Tamanho:** médio. Três frentes independentes; a C pode já estar quase pronta no código.

---

## 0. Como ler este handoff

Cada parte tem **fatos medidos** (já apurados, não reapurar), **o que fazer** e **testes obrigatórios**.
Onde está escrito "verifique antes de implementar", verifique de verdade e **reporte o que encontrou**,
mesmo que a conclusão seja "o handoff estava errado". Hipótese derrubada por medição é entrega, não fracasso.

Não há SDK Android nem JDK na máquina do Bruno. **Não tente compilar localmente** — o
`fork-release.yml` dispara sozinho em push na `master` e roda `testDebugUnitTest` antes de montar o APK.
Escreva os testes, commite, empurre, leia o log do job "Unit tests + APK".

---

## 1. Os dois pedidos, nas palavras dele

> "rolagem também no modo edição, que só tem no modo de visualização"

> "eu vou em determinado ponto da nota, quando eu clico para editar, aí volta para o início da nota e
> eu tenho que descer tudo para editar a nota. Quando eu clicar em determinado ponto da nota para
> editar, não pode mexer a tela, só aparecer o ponteiro e eu conseguir editar"

> "quero o modo de preenchimento automático no modo de edição igual acontece no obsidian: quando tem
> linhas com `-`, ao dar quebra de linha, tem que aparecer também na linha debaixo"

Isso vira três partes: **A** (rolagem rápida no editor), **B** (não perder o lugar ao trocar de modo),
**C** (continuação automática de lista).

---

## 2. Mapa do código (fatos medidos — não reapurar)

| Arquivo | Papel |
|---|---|
| `ui/screen/app/edit/EditScreen.kt` | Scaffold, botão de cadeado (`vm.setReadOnlyMode`), `GenericTextField` |
| `ui/screen/app/edit/MarkDown.kt` | `MarkDownContent`: ramo leitura (Box + `verticalScroll` + `FastScrollOverlay`) e ramo edição (`GenericTextField` com `MarkdownLivePreviewTransformation`) |
| `ui/viewmodel/edit/TextVM.kt` | estado do texto, histórico undo/redo, salvamento |
| `ui/viewmodel/edit/MarkDownVM.kt` | `onValueChange` chama `markdownSmartEditor`; botões de formatação |
| `ui/viewmodel/edit/MarkdownSmartEditor.kt` | **a continuação de lista já existe aqui** |
| `ui/screen/app/grid/markdownHelper.kt` | `MarkdownCustomInner` + `positionedHeading` (âncoras de heading) |
| `ui/component/markdown/MarkdownScanner.kt` | scanner de markdown usado pelo live preview e pelos wikilinks |

Medições já feitas por leitura, que economizam rodadas:

1. **`markdownSmartEditor` está ligado e é chamado em todo `onValueChange` do markdown**
   (`MarkDownVM.kt:43`). A parte C **não é escrever do zero**: é descobrir por que não chega ao Bruno.
2. **O modo edição não tem `ScrollState` acessível.** O `TextField` do M3 (`GenericTextField`,
   `EditScreen.kt:305`) usa `fillMaxSize()` e rola por dentro. Nem o `TextField` do M3 nem o
   `BasicTextField` baseado em `TextFieldValue` expõem `scrollState`. É por isso que o
   `FastScrollOverlay` só existe no modo leitura — está escrito no handoff 06.
3. **`TextVM` nasce com o cursor em zero**: `TextFieldValue(previousNote.content, selection = TextRange(0))`
   (`TextVM.kt:91` e `:113`). Some-se a isso que o ramo de leitura e o de edição são composables
   diferentes, cada um com seu scroll: ao trocar de modo, o scroll do outro ramo é descartado.
   **Essa é a causa mecânica do "volta para o início".**
4. **O marcador de lista NÃO é escondido pelo live preview.** No `MarkdownScanner`, `MdKind.BULLET`
   e `MdKind.ORDERED` são criados com `markers = emptyList()` (linhas 127 e 157), e a
   `MarkdownLivePreviewTransformation` só esconde o que está em `markers`. Ou seja: **a hipótese
   "o traço some por causa do tema" está derrubada** — não gaste rodada nela.
5. **O pré-processamento do modo leitura muda offsets mas não muda linhas.**
   `preprocessWikilinksForReading` troca `[[X]]` por `[X](gitnote://...)` dentro da mesma linha, e as
   quebras do Obsidian foram resolvidas por `obsidianLineBreaksAnnotatorConfig` (nativo do renderizador),
   **sem** reescrever o texto. Isso é o alicerce da parte B: **o número da linha é comum aos dois modos**.
   Exigir teste de invariante para isso (§6.B1).

Medições no vault real do Bruno (`E:\Obsidian\CONHECIMENTO`, 3.838 notas `.md`):

| Marcador | Linhas |
|---|---|
| `- ` | 27.412 |
| `> ` | **14.416** |
| `- [ ]` / `- [x]` | 3.968 |
| `1. ` | 3.611 |
| `* ` | 1.419 |

| Métrica | Valor |
|---|---|
| arquivos com CRLF | 134 (3,5%) |
| mediana de linhas por nota | 23 |
| notas com > 200 linhas | 140 |
| notas com > 500 linhas | 56 |
| maior nota | 32.798 linhas / 1,03 MB |

Duas coisas saltam daí: **citação (`>`) é o segundo marcador mais usado e hoje não é continuado**,
e **existe nota de 1 MB** — o que torna a medição de desempenho da parte A obrigatória.

---

## 3. Parte A — rolagem rápida no modo edição

### 3.1 O que entregar

O mesmo gesto que já existe no modo leitura (segurar na borda direita e arrastar, `FastScrollOverlay`
em `MarkDown.kt:257`) funcionando no modo edição. **Reaproveite o composable**, não escreva outro.

### 3.2 Caminho recomendado

Içar o scroll para fora do `TextField`:

```kotlin
val editScrollState = rememberScrollState()
BoxWithConstraints {
    Box(Modifier.fillMaxSize().verticalScroll(editScrollState)) {
        GenericTextField(
            modifier = Modifier.fillMaxWidth().heightIn(min = maxHeight),  // wrap-content em altura
            ...
        )
    }
    FastScrollOverlay(scrollState = editScrollState, modifier = Modifier.align(Alignment.CenterEnd))
}
```

O `heightIn(min = ...)` existe para que tocar na área vazia abaixo do texto ainda foque o campo —
sem ele, uma nota curta deixa de responder ao toque na metade de baixo da tela.

**Verifique antes de implementar:** confirme que nenhuma versão do Compose do BOM `2026.06.01` passou a
expor um `scrollState` no `TextField`/`BasicTextField` baseado em `TextFieldValue`. Se expuser, use —
é mais barato e mais correto que içar o scroll. Reporte o que achou.

### 3.3 O que medir (e o critério de desistência)

- **O cursor continua sendo trazido para a tela ao digitar?** Com o scroll içado, quem rola é o pai; o
  `bringIntoView` do cursor precisa subir a cadeia. Se digitar no fim de uma nota longa deixar o cursor
  fora da viewport, a abordagem falhou — diga isso em vez de empurrar com a barriga.
- **Desempenho numa nota grande de verdade** (use uma de 500+ linhas do vault). Se a digitação ficar
  visivelmente lenta, **antes de culpar o scroll** olhe a `MarkdownLivePreviewTransformation`: ela roda
  `MarkdownScanner.scan()` no documento inteiro e aloca dois `IntArray(n+1)` **a cada tecla**
  (`MarkDown.kt:239`). Essa lentidão já existe hoje e está fora do escopo deste handoff — mas meça e
  reporte separando as duas coisas.
- **Critério de desistência:** se o scroll içado quebrar o cursor ou a seleção de texto e não houver
  conserto barato, reverta a parte A, mantenha o scroll interno e reporte. Não entregue um editor pior
  do que o de hoje em troca de uma barra de rolagem.

### 3.4 Armadilha já medida (handoff 06)

O overlay ocupa uma faixa de 28dp na borda direita e só captura depois de
`detectDragGesturesAfterLongPress` — justamente para não matar o toque no que está embaixo. No modo
edição o que está embaixo é o posicionamento do cursor. **Teste explicitamente: tocar na faixa direita
posiciona o cursor normalmente**, e o long press ali não abre a seleção de texto do Android.

---

## 4. Parte B — não perder o lugar ao trocar de modo

### 4.1 O que entregar

1. Alternar leitura → edição coloca o cursor **na linha que estava no topo da tela** e mostra essa
   mesma região, sem voltar ao começo.
2. Alternar edição → leitura mostra a região onde o cursor estava.
3. Se ele tocou num ponto do texto no modo leitura antes de apertar o cadeado, é **esse** o ponto que
   vale como âncora (é o pedido literal: "clicar em determinado ponto... só aparecer o ponteiro").

### 4.2 Desenho

A âncora é **um número de linha da fonte**, guardado no view model (sobrevive à troca de ramo e à
recomposição; morre com a nota). Nunca persista entre notas e nunca em disco.

```kotlin
// no MarkDownVM
private var anchorLine: Int? = null
fun rememberAnchor(line: Int) { anchorLine = line }
fun consumeAnchor(): Int?      // devolve e zera
```

**Funções puras, em arquivo próprio, com teste** (é o que dá para testar na JVM):

```kotlin
fun lineStartOffsets(text: String): IntArray
fun lineOfOffset(text: String, offset: Int): Int
fun offsetOfLineStart(text: String, line: Int): Int
fun nearestAnchorAtOrBefore(line: Int, anchors: Map<Int, Int>): Int?   // linha -> y
```

**Leitura → edição.** Ao sair do modo leitura, a linha âncora é a do último toque (§4.4) ou, na falta
dele, a do primeiro bloco cujo `y` seja `>= scrollState.value`. Ao entrar no modo edição:
`content.value.copy(selection = TextRange(offsetOfLineStart(texto, linha)))` e
`textFocusRequester.requestFocus()`. **O próprio `TextField` traz o cursor para a tela** — não calcule
o `y` do editor à mão; o `TextField` do M3 não expõe `TextLayoutResult` e tentar isso vira refatoração.

**Edição → leitura.** Linha do cursor → `nearestAnchorAtOrBefore` no mapa de âncoras do modo leitura →
`scrollState.scrollTo(y)`. Como o modo leitura remonta do zero, o mapa só existe depois que os blocos
forem posicionados: reaproveite o padrão que o handoff 06 já usa para a seção inicial
(`snapshotFlow { ... }.filter { it.isNotEmpty() }.first()`, em `MarkDown.kt:166`).

### 4.3 O mapa de âncoras precisa ir além dos headings

Hoje só headings registram posição (`positionedHeading`, `markdownHelper.kt:143`). Uma nota sem
headings não tem âncora nenhuma, e headings dão granularidade de seção — grossa demais.

Estenda o mesmo truque aos demais componentes de bloco de `markdownComponents(...)` — parágrafo, lista
ordenada, lista não-ordenada, item de lista, citação, bloco de código, tabela — registrando
`model.node.startOffset` (offset no **texto renderizado**) convertido para **número de linha**.

Três armadilhas, todas com custo real:

- **Mapa separado, e não observável.** Não jogue os blocos no `headingPositions`
  (`mutableStateMapOf`, observado por um `snapshotFlow`): centenas de blocos escrevendo em estado
  observado durante o layout é recomposição em cascata. Use um `remember { mutableMapOf<Int, Int>() }`
  comum, lido só no instante da troca de modo. **Não mexa no caminho de heading** — ele sustenta a
  navegação por `[[#Seção]]` entregue no handoff 06.
- **`startOffset` é do texto renderizado, não da fonte.** Converta para linha usando o mesmo texto que
  foi passado ao parser (`renderedContent`), nunca `textContent.text`.
- **`y` é calculado somando `scrollState.value` no momento da medição.** Se o bloco for medido durante
  uma rolagem, o valor sai defasado. Recalcule a âncora no momento do uso quando possível, ou aceite o
  erro e documente-o.

### 4.4 O toque que marca o lugar (o pedido literal)

No modo leitura, registre a coordenada do último toque **sem consumir o evento** — um
`pointerInput` que observa em `PointerEventPass.Initial` e não chama `consume()`. Converta
`toque.y + scrollState.value` na linha do bloco mais próximo acima e guarde como âncora.

**Não use `detectTapGestures` nem duplo toque.** Toque simples é do wikilink e long press é da seleção
de texto; o handoff 06 já mediu que um overlay guloso ali mata os dois. Se observar sem consumir se
mostrar impossível, entregue só §4.2 (âncora pelo topo da viewport) e **diga que ficou de fora** — isso
sozinho já resolve o "tenho que descer tudo".

### 4.5 O que não pode regredir

- Abrir uma nota por `[[Nota#Seção]]` continua caindo na seção (handoff 06).
- Criar nota nova continua focando o campo de nome (`EditScreen.kt:126`).
- O botão do cadeado continua gravando a preferência global `isReadOnlyModeActive`.

---

## 5. Parte C — continuação automática de lista

### 5.1 Comece pelo diagnóstico, não pelo código

`markdownSmartEditor` já implementa continuação de `-`, `*`, `1.` e `- [ ]`, e já remove o marcador
quando o Enter cai num item vazio. **Está ligado.** Ou seja: ou ele falha nos casos reais do vault, ou
o valor que ele devolve não chega ao `TextField`.

Rodada 1 — **reproduza em teste JVM** os casos do §6.C com o texto entrando pelo mesmo caminho do app
(`MarkDownVM.onValueChange` se der para instanciar; senão `markdownSmartEditor` direto, dizendo qual
escolheu e por quê). Reporte **quais casos passam e quais falham** antes de mudar qualquer linha.

Rodada 2 — conserte o que falhou. Os candidatos que a leitura já aponta estão em §5.2 e §5.3.

### 5.2 Defeitos e lacunas já encontrados por leitura

1. **Citação (`>`) não é continuada.** `ListItemInfo.parse` só reconhece `-`, `*` e `n.`. São 14.416
   linhas de citação no vault. No Obsidian, Enter numa linha `> ` continua a citação, e Enter numa
   citação vazia sai dela. Implemente com a mesma forma do resto (inclusive o "sair quando vazia").
2. **Tarefa marcada gera tarefa marcada.** `ListItemInfo.prefix()` devolve `[x] ` quando a linha de
   origem estava marcada. O Obsidian abre o item novo **sempre desmarcado**. Corrija para `[ ] `.
3. **CRLF.** 134 arquivos do vault têm `\r\n`. O Enter do teclado insere só `\n`, então a linha anterior
   pode chegar ao parser terminando em `\r`, e o regex `(.+)?` engole esse `\r` como parte do título.
   Teste os dois finais de linha em todos os casos do §6.C, e garanta que o prefixo inserido não
   arrasta `\r` para a linha nova.
4. **Enter substituindo uma seleção.** O guarda `if (prev.text.length >= v.text.length) return v`
   (`MarkdownSmartEditor.kt:27`) existe para o Backspace, mas também desliga a continuação quando o
   Enter substitui um trecho selecionado maior que 1 caractere. Decida (e teste) qual comportamento
   quer; o do Obsidian é continuar a lista.

### 5.3 Se os testes passarem e no aparelho continuar sem funcionar

Aí o problema está na camada do `TextField`/IME, e existe uma hipótese concreta antes de qualquer outra:
**a `composition` do IME é preservada.** Todo retorno do smart editor usa `v.copy(text = ..., selection = ...)`,
que mantém o `composition` do valor antigo apontando para offsets que não existem mais no texto novo.
Com o Gboard compondo palavra (autocorreção ligada), isso é caminho conhecido para a alteração
programática ser descartada. **Teste zerar a composição** no valor devolvido sempre que o texto mudar.

Os comentários do autor original em `TextVM.kt:135-144` descrevem esse terreno ("the value v will not be
the last one set by `content.value`"), então não é hipótese inventada aqui. Se confirmar, é conserto de
uma linha; se não confirmar, **diga o que mediu** e pare — não migre o editor para `TextFieldState`
atrás disso (ver §7).

---

## 6. Testes obrigatórios

Tudo roda com `./gradlew testDebugUnitTest`, em `app/src/test/`. **Implemente todos**. Se algum não fizer
sentido depois de ler o código, diga qual e por quê — não omita em silêncio.

### 6.A — rolagem (o que der na JVM)

A rolagem em si não é testável sem UI. Cubra o que é lógica:
- `FastScrollOverlay`: fração do dedo → offset de rolagem, nos extremos (topo, fundo) e fora da faixa
  (valores negativos e acima da altura ficam presos em 0 e `maxValue`).
- Conteúdo que não rola (`maxValue == 0`) não move nada e não mostra o polegar.

### 6.B — âncoras

**B1 (invariante que sustenta a parte B):** para cada nota-amostra, `preprocessWikilinksForReading`
devolve texto com **a mesma quantidade de `\n`** e com o conteúdo de cada linha na mesma ordem.
Inclua casos com `[[Nota]]`, `[[Nota|alias]]`, `[[#Seção]]`, `==destaque==`, bloco de código cercado e
uma linha com vários wikilinks. Se essa invariante cair, a parte B inteira cai — é o teste mais
importante deste handoff.

- `lineOfOffset` / `offsetOfLineStart` são inversos, em texto com `\n`, com `\r\n`, com linha vazia,
  com linha final sem quebra e em texto vazio.
- `lineOfOffset` em offset 0, no último caractere e em `text.length`.
- `nearestAnchorAtOrBefore`: linha exata, linha entre duas âncoras, linha antes da primeira âncora
  (devolve a primeira ou nulo — escolha, documente e teste), mapa vazio.
- Ida e volta: linha → offset → linha devolve a mesma linha, para todas as linhas de uma nota de amostra.

### 6.C — continuação de lista

Para **cada** caso: texto de entrada, posição do cursor, e o texto + cursor esperados depois do Enter.
Rode **duas vezes, com `\n` e com `\r\n`**.

Continuar:
1. `- item` → nova linha `- `
2. `* item` → `* `
3. `1. item` → `2. `
4. `9. item` → `10. `
5. `- [ ] tarefa` → `- [ ] `
6. `- [x] tarefa` → **`- [ ] `** (desmarcado — §5.2.2)
7. `> citação` → `> ` (§5.2.1)
8. `  - item` (indentado com espaços) → `  - `
9. `\t- item` (indentado com tab) → `\t- `
10. Enter no **meio** do texto de um item: o marcador vai para a linha nova e o resto do texto vai junto
11. Enter no fim de `- item` que **não** é a última linha do documento
12. Enter numa linha de lista dentro de um documento com CRLF em toda parte

Encerrar a lista:
13. `- ` (item vazio) → Enter apaga o marcador e deixa linha vazia
14. `- [ ] ` vazio → idem
15. `> ` vazio → sai da citação
16. `  - ` vazio indentado → Enter remove o marcador (decida se também remove a indentação; documente)

Não mexer:
17. `texto comum` → Enter dá linha vazia, sem prefixo
18. `-sem espaço` → não é lista, nada acontece
19. `- ` dentro de bloco de código cercado → **não** continua a lista
20. `2026-09-17 comecei` (linha que começa com dígitos e não é lista) → nada acontece
21. Backspace no início de um item continua removendo o marcador como hoje (não regredir)

Regressão do §5.2.4:

22. Selecionar um trecho dentro de um item e pressionar Enter: diga qual é o comportamento e fixe-o.

### 6.D — camada de UI (esforço limitado)

O projeto **não tem** Robolectric nem `compose-ui-test`, e o CI não roda emulador (`ci.yml:53` e
`fork-release.yml:105` só rodam `testDebugUnitTest`). Tente acrescentar `compose-ui-test-junit4` +
Robolectric e cobrir três comportamentos:
- trocar para o modo edição com âncora posicionada deixa o cursor na linha esperada;
- trocar para o modo leitura rola para a âncora esperada;
- digitar Enter numa linha de lista, pelo `TextField` de verdade, produz o marcador na linha nova.

**Teto de esforço: se em duas tentativas a infraestrutura não subir** (ou se o job de CI passar de ~10
minutos por causa disso), **desista, remova a dependência e reporte**. Em troca, entregue no fim do
relatório um **roteiro manual numerado** para o Bruno conferir no aparelho, com o que ele deve ver em
cada passo — ele valida no celular.

---

## 7. O que NÃO fazer

- **Não migre o editor para `TextFieldState` / `InputTransformation`.** É a API que resolveria as três
  partes de uma vez, sim, e é por isso que o aviso está aqui: ela arrasta junto o `TextVM` inteiro —
  histórico de undo/redo próprio, os nove botões de formatação, o salvamento, o `NoteSaver` — e põe em
  risco o live preview, os wikilinks e o destaque já entregues. Se, ao medir, você concluir que não há
  saída sem ela, **pare e escreva o porquê**; é decisão do Bruno abrir esse capítulo.
- **Não crie opção nos Ajustes** para nada disto. Os três comportamentos são o padrão do Obsidian, e é
  paridade com o PC que ele quer. Mais uma chave é mais um estado que pode estar errado sem ele perceber.
- **Não reescreva o `MarkdownScanner` nem o pré-processamento dos wikilinks.** Se precisar de mais
  informação deles, acrescente — não refaça.
- **Não altere o `applicationId`.**
- **Não toque no caminho das âncoras de heading** usado pela navegação de seção.

---

## 8. Entrega

Commits pequenos e separados por parte (A, B, C), na `master`. O `fork-release.yml` roda sozinho no push.

No fim, escreva um relatório curto com:

1. **O que você mediu na rodada 1 da parte C** — quais casos já passavam antes de qualquer mudança.
   Se a continuação de lista já funcionava em teste, diga isso com todas as letras e mostre onde o
   caminho até o aparelho se rompe.
2. Se o scroll içado (§3.2) manteve o cursor na tela, e o que a nota grande mostrou.
3. Se a invariante de linhas (§6.B1) se sustentou.
4. O que ficou de fora e por quê.
5. O roteiro manual do §6.D, se a camada de UI não pôde ser testada.

Conclusão sem número medido não conta.
