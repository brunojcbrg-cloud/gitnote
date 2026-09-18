# RESULTADO 07 — rolagem no editor, não perder o lugar, lista automática

**Fecha o** `HANDOFF_07_ROLAGEM_E_LISTA_AUTOMATICA.md` (commit base `0b63cf7`).
**Escrito em:** 2026-09-18.
**Por quê existe:** o §8 do handoff pedia um relatório com números medidos. Ele não foi
escrito, e os números só existiam nos logs do CI — que expiram. Está tudo aqui.

---

## 1. Placar

| Parte | Estado | Onde está a prova |
|---|---|---|
| A — rolagem rápida no modo edição | **entregue por outro caminho**, depois de a 1ª tentativa reprovar | §4 |
| B — não perder o lugar ao trocar de modo | entregue, inclusive o §4.4 (âncora pelo toque) | §3 |
| C — continuação automática de lista | entregue | §2 |

Nada foi para os Ajustes, o `applicationId` não mudou, o `MarkdownScanner` e o
pré-processamento de wikilink não foram reescritos, e o editor **não** migrou para
`TextFieldState` (§7 do handoff).

**Estado verificado:** run 35349087214 — **215 testes, 0 falhas, 0 pulados**, APK montado e
publicado como **b34 (26.08.1.34)**. Desde essa rodada o CI **lista no log cada teste que
executou**: antes, uma rodada verde não provava que um teste novo tinha rodado, porque o
relatório HTML só vira artefato quando a rodada falha.

---

## 2. Parte C — o que a rodada 1 mediu, antes de mudar código

Os 22 casos do §6.C foram escritos primeiro e empurrados vermelhos (commit `1d11075`,
run 35255616130). **44 testes (22 casos × LF/CRLF), 22 falharam.**

| Terminador | Passavam | Falhavam |
|---|---|---|
| `\n` | 17 de 22 | 06 tarefa marcada, 07 citação, 15 citação vazia, 19 dentro de bloco de código, 22 Enter sobre seleção |
| `\r\n` | 5 de 22 | todos os que mexem em lista: 01–16 e 22 |

Ou seja: a leitura do handoff estava certa pela metade. `markdownSmartEditor` **já existia e já
estava ligado**, e para uma nota simples em LF funcionava. O que não chegava ao Bruno eram os
134 arquivos CRLF do vault (onde o `\r` entrava no título e o marcador saía errado) e a
citação `>`, que nunca foi implementada — 14.416 linhas do vault.

Conserto em `ab54219` (run 35256394953, verde). Hoje são **23 casos × 2 terminadores = 46
testes**. O caso 23 é o §5.3 aplicado sem esperar o aparelho reprovar: toda alteração
programática devolve `composition = null`, senão o Gboard descarta a inserção.

Decisões que o handoff mandou documentar:
- **caso 16**: Enter num `  - ` vazio remove o marcador **e** a indentação.
- **caso 22**: Enter substituindo uma seleção **continua** a lista (é o do Obsidian). O guarda
  antigo `prev.text.length >= v.text.length` foi trocado por uma verificação da região
  alterada (`insertedLineBreakStart`), que reconhece a quebra inserida mesmo quando ela
  substitui um trecho maior.

---

## 3. Parte B — a invariante se sustentou

O teste mais importante do handoff (§6.B1) passou: `preprocessWikilinksForReading` devolve o
mesmo número de `\n`, na mesma ordem, com `[[Nota]]`, `[[Nota|alias]]`, `[[#Seção]]`,
`==destaque==`, bloco de código cercado e várias linhas com vários wikilinks, em LF e CRLF.
**É isso que autoriza usar número de linha como endereço comum aos dois modos.**

A âncora é um `Int` no `MarkDownVM`, morre com a nota, nunca vai para o disco. O mapa de
blocos é um `mutableMapOf` comum — não observável, como o §4.3 exigia — e o caminho das
âncoras de heading (navegação `[[Nota#Seção]]`) não foi tocado.

O §4.4 foi entregue: o toque é observado em `PointerEventPass.Initial` **sem `consume()`**,
então o wikilink e a seleção de texto continuam recebendo o evento.

---

## 4. Parte A — a primeira tentativa reprovou, e a segunda mudou de caminho

### 4.1 O que reprovou

`bcff354` fez o do §3.2: içou a rolagem para um `Box` pai, com `heightIn(min = maxHeight)`.
O teste `cursorAtTheEndBringsTheHoistedParentScrollIntoView` (nota de 100 linhas, cursor no
fim) mostrou que **o pai não rolava até o cursor** — exatamente o critério de desistência do
§3.3. Run 35261840556, vermelha. `ca2b16d` reverteu.

Ressalva honesta do instrumento: a medição é Robolectric, não aparelho, e o teste mediu o
foco inicial, não a digitação. Mas o critério do handoff era "não entregue um editor pior em
troca de uma barra", e reverter foi a decisão certa.

### 4.2 O que ficou no lugar

O `TextField` continua rolando por dentro, como sempre. A faixa de 28dp na borda direita, ao
ser arrastada, **pede uma linha** e move o **cursor** para o início dela
(`MarkDownVM.moveCursorToLine`). Quem rola é o próprio `TextField`, atrás do cursor.

Por que isso é mais seguro do que içar a rolagem:

- **não muda a estrutura do editor** — nada de `heightIn`, nada de scroll pai;
- **aposta num mecanismo que já estava em produção**: é exatamente assim que a parte B põe o
  cursor na linha certa ao sair do modo leitura. Se um dia esse mecanismo falhar, a parte B
  falha junto — é um risco só, não dois;
- **o pior caso é não fazer nada**, em vez de quebrar o cursor.

O que está medido na JVM (run 35346914948, Robolectric, com `TextField` de verdade na tela):

- `longPressOnTheRightEdgeAsksForALineNearTheFinger` — segurar no fim da faixa pede uma linha
  perto do fim da nota (> 150 de 200). **É a ligação do gesto, que era o que faltava provar.**
- `aShortNoteHasNoGutterToAskForLines` — numa nota que cabe na tela, o mesmo gesto não move
  nada.
- `FastScrollMathTest` — extremos, clamp fora da faixa, meio da faixa, viewport menor que o
  polegar e divisão por zero na estimativa de linhas visíveis.

O que **só o aparelho mostra**: que o `TextField` de fato rola até o cursor. É o mesmo
mecanismo da parte B; os dois caem ou passam juntos (§7, passos 11 e 17).

O que ele **não** é: um scrollbar de verdade. O texto para onde o `TextField` quiser para
deixar o cursor visível — normalmente com a linha pedida junto à borda de baixo, não no topo.
E o cursor muda de lugar (o que, para editar, é o que se quer de todo jeito).

Duas escolhas que valem registro:

- **Rédea de 90 ms** entre movimentos de cursor durante o arrasto. Cada movimento refaz a
  `MarkdownLivePreviewTransformation` da nota inteira; sem a rédea, numa nota grande o arrasto
  engasga. Ao soltar o dedo, a linha final é sempre aplicada.
- **Quando a barra aparece:** só quando a nota não cabe na tela, estimando
  `linhas visíveis = altura / (tamanho da fonte × 1,5)`. É estimativa: linha longa quebra em
  várias linhas visuais, então o real é menor que a estimativa. Erra para o lado de mostrar a
  barra, nunca para o de escondê-la.

---

## 5. Desempenho — os números

**Live preview, medido no CI (run 35301603388):**

```
PERF_MARKDOWN_LIVE_PREVIEW lines=1946 chars=180046
samples_ms=[26.6, 29.5, 28.1, 27.4, 27.4, 31.3, 26.1] median_ms=27.4
```

27,4 ms **por tecla** numa nota de 1.946 linhas / 180 mil caracteres, num runner x86 do
GitHub. No celular é pior. Isso é anterior ao handoff 07 e continua de pé: a
`MarkdownLivePreviewTransformation` roda `MarkdownScanner.scan()` no documento inteiro e
aloca dois `IntArray(n+1)` a cada tecla. **É o próximo gargalo do editor.**

**Dois defeitos achados por leitura depois da entrega do Codex, e consertados aqui:**

1. **Abrir nota longa em leitura era O(n²).** Cada bloco que se posicionava varria o mapa de
   todos os blocos já posicionados (`localPositionOf` em cada um) só para recalcular a âncora
   do topo. Numa nota de 200 linhas dá ~20 mil chamadas; na de 1.946, ~1,9 milhão — um pico na
   abertura. Agora é **uma varredura só**, depois que os blocos assentam.
2. **Alocação por tecla.** `lineOfOffset(text, offset)` montava o vetor de inícios de linha a
   cada chamada, e ela roda a cada tecla no editor. Passou a contar as quebras sem alocar, e a
   remedição da linha do cursor espera a digitação parar (120 ms) — com a primeira medição
   imediata, para que trocar de modo depressa não perca o lugar.

---

## 5-B. Defeito achado pelo Bruno em 18/09: a caixa de código não dava para tirar

**Sintoma dele:** abriu uma nota que começa com uma caixa de código, viu uma crase, foi apagar,
e "a crase caía para as próximas letras e a crase mesmo não sumia". Não conseguia tirar a caixa.

**Medido** (teste de diagnóstico, run 35348542221), com `` ``` `` no início da nota:

```
SPAN   kind=CODE_FENCE range=0..41 markers=[0..41] line=0   ← a LINHA INTEIRA é marcador
TELA   = "\n\nDiferencas entre paredes"                     ← a linha 0 sumiu da tela
T2O    0->42                                                ← a posição visual 0 é o caractere 42
BACKSPACE cursor1:apaga[45]='`'  cursor3:apaga[49]='D'      ← apaga na linha de baixo
```

Ou seja: a linha da cerca era marcada como marcador **inteira**, e escondida junto com o texto
escrito depois das crases. A tela ficava com uma linha vazia e o cursor, ao ser posicionado ali,
apontava para outro ponto do documento — apagar comia letras de outra linha e a crase, que estava
escondida, nunca sumia.

**Conserto:** a linha da cerca deixa de ser marcador. Continua estilizada como código, mas fica
visível e apagável, como todo o resto do que está no arquivo. Quatro testes fixam isso, incluindo
*"o Backspace apaga o caractere que está sob o cursor"* em todas as posições de um bloco cercado.

**Detalhe do arquivo dele, que não é defeito do app:** a nota tem **duas** crases, não três — e o
texto está na **mesma linha** da cerca. Em Markdown, o que vem depois das crases na linha da cerca
é o nome da linguagem, não conteúdo. A forma que vira caixa é a cerca sozinha na linha, o conteúdo
na linha seguinte, e a cerca de novo sozinha no fim.

## 6. O que ficou de fora

- **Rolagem "de verdade" no editor** (mover a viewport sem mexer no cursor) continua
  impossível sem migrar para `TextFieldState`/`InputTransformation`, que o §7 proíbe. Se um
  dia isso for aberto, é o mesmo capítulo que resolve o custo do live preview.
- **Teste de UI de ponta a ponta** da troca de modo (§6.D, itens 1 e 2): exigiria compor o
  `MarkDownContent` inteiro com `MarkDownVM`, prefs e DataStore. O que dá para medir na JVM
  está medido; o resto é o roteiro do §7.
- **Nenhuma medição em aparelho.** Não há SDK Android na máquina; tudo aqui é CI + leitura.

---

## 7. Roteiro manual — o que conferir no aparelho

Instale o APK da última release do fork e faça, em ordem:

**Lista automática**
1. Nota nova, digitar `- item`, Enter → nasce `- ` na linha de baixo.
2. Repetir com `* item`, `1. item` (tem que virar `2. `), `9. item` (tem que virar `10. `).
3. `- [ ] tarefa` → Enter → `- [ ] `. Depois `- [x] tarefa` → Enter → **`- [ ] `, desmarcado**.
4. `> citação` → Enter → `> `.
5. Item indentado (`  - item` ou com tab) → Enter → mantém a indentação.
6. Enter no **meio** do texto de um item → o marcador vai para a linha nova, com o resto do texto.
7. Enter num `- ` vazio → o marcador some e sobra linha vazia. Idem `- [ ] ` e `> `.
8. Digitar `texto comum` e Enter → **sem** marcador. `-sem espaço` → nada. `2026-09-17 comecei` → nada.
9. Dentro de bloco ``` ``` ```, `- ` e Enter → **não** continua a lista.
10. Abrir uma nota que veio do PC com quebra do Windows e repetir 1 e 3 (é onde quebrava).

**Não perder o lugar**
11. Abrir nota longa em leitura, descer até o meio, apertar o cadeado → **fica onde estava**,
    com o cursor na linha do topo da tela.
12. Tocar num ponto do texto em leitura e então apertar o cadeado → o cursor nasce **naquele**
    ponto, sem a tela pular.
13. Voltar para leitura → volta para a mesma região.
14. Abrir uma nota por `[[Nota#Seção]]` → continua caindo na seção (não pode ter regredido).
15. Criar nota nova → o foco continua indo para o campo do nome.

**Rolagem no editor (o novo)**
16. Nota longa, modo edição: **segurar** na borda direita → aparece o polegar da barra.
17. Arrastar para baixo → o texto acompanha e o cursor vai junto. Soltar no fim → última linha.
18. Nota curta (que cabe na tela) → a barra **não** aparece.
19. **Toque simples** na borda direita (sem segurar) → posiciona o cursor normalmente, sem
    abrir a barra. Este é o teste que protege o uso normal do editor.
20. Em leitura, o mesmo toque simples na borda direita sobre um wikilink → abre o link.

**Caixa de código (§5-B)**
21. Abrir em edição uma nota que tenha um bloco ``` ``` ```: as crases **aparecem** na tela.
22. Pôr o cursor depois de uma crase e apagar → some **aquela** crase, e nada mais.
23. Apagar as três crases de cima e as três de baixo → a caixa sai da nota.

**Desempenho**
24. Abrir uma nota de 500+ linhas em leitura e ver se abre sem travar (era o defeito O(n²)).
25. Digitar numa nota muito grande: vai estar lento — é o live preview, medido em §5, e é o
    próximo trabalho, não uma regressão desta rodada.
