# RESULTADO 08 — inserir e reconfigurar tabelas no editor

**Fecha o:** `HANDOFF_08_TABELAS.md` (base `01f0c06`).  
**Escrito em:** 2026-09-18.

## 1. Placar

| Parte | Estado | Prova |
|---|---|---|
| C — núcleo puro | entregue | `MarkdownTableTest`, `MarkdownTableInsertionTest` e `MarkdownTableDetectionTest` no log do CI |
| A — inserir pelo botão | entregue | `tableButtonDialogConfirmsDefaultThreeByTwoInRealTextField` |
| B — detectar e reconfigurar pelo mesmo botão | entregue | `sameTableButtonOpensConfigurationWhenCursorIsInsideTable` e testes puros do detector |
| B — atalho por toque longo | **não entregue por segurança** | medição da §2 |

O editor continua baseado em `TextFieldValue`; não houve migração para `TextFieldState`, mudança no
modo leitura, reescrita do `MarkdownScanner`, opção nova nos Ajustes nem alteração de `applicationId`.

## 2. Rodada 1 do toque longo (§6.2)

O probe usou `PointerEventPass.Initial`, não chamou `consume()` e injetou uma pressão imóvel de
**600 ms**: o timeout Android usado pelo teste é **500 ms**, e o `longClick` do Compose acrescenta
100 ms. Ele tentou abrir o `BaseDialog` mantendo o `TextField` real por baixo.

- **Run 35352719256, API 35:** ao abrir o diálogo durante a seleção, o Robolectric caiu em
  `android.widget.Magnifier$InternalPopupWindow.destroy`: a `Surface` do magnifier era nula. A pilha
  prova que o caminho de seleção/magnifier do Android estava ativo, mas a queda ocorreu antes das
  asserções que verificariam o intervalo selecionado e o toque para desfazê-lo.
- **Run 35353058596, API 27:** a tentativa de eliminar apenas o magnifier não chegou ao corpo do
  teste; o Robolectric recusou o pacote em `ShadowPackageParser`/`PackageParserException`.

O teto de duas tentativas de UI foi atingido sem uma medição afirmativa de que a seleção sobrevive.
Por isso o gesto foi removido e **nenhum `pointerInput` novo ficou sobre o editor**. A conclusão é
conservadora: o toque longo comum continua sendo exclusivamente do Android para selecionar texto, e
a configuração de tabela sai pelo caminho garantido — o botão sensível ao cursor. Não se trocou uma
seleção comprovadamente funcional por um atalho sem prova.

## 3. Largura da barra

`textFormatRowWithTwelveButtonsIsWiderThanScreen` mediu no CI:

```text
MEASURED_TEXT_FORMAT_ROW_WIDTH_DP=465.0
```

São **465,0 dp** de conteúdo numa tela de **360 dp**, excesso de **105,0 dp**. O antigo `scrollable`
só recebia deltas e não deslocava os filhos; ele foi substituído por `horizontalScroll`, que permite
chegar ao botão de tabela e aos botões seguintes sem cortá-los.

## 4. Decisões de preservação

### Ida e volta e estilo

O parser guarda o texto cru das células e a grafia crua de cada célula separadora. Assim,
`renderTable(parseTable(x)) == x` byte a byte para as 12 formas de teste: pipes externos ou não,
espaçada ou colada, alinhamentos, 2 e 9 colunas, 1 e 10 linhas, célula vazia, pipe escapado, pipe em
código inline, LF e CRLF. Colunas novas seguem o estilo da tabela; alinhamentos sobreviventes mantêm
a mesma posição.

### Linha irregular

- Se faltam células, são acrescentadas células vazias à direita.
- Se sobram células, todo o excedente é juntado na última célula prevista com o separador literal
  `" | "`. O pipe não é convertido em `\|`, conforme o handoff; nenhum texto some, embora a linha
  continue visualmente irregular.

### Coluna removida

O alinhamento da coluna removida é descartado junto com ela. Alinhamentos das colunas que continuam
existindo não mudam de índice. Colunas novas nascem com `MdAlign.NONE`, renderizadas como `---`.

Antes de uma redução, cabeçalho e corpo são contados. Se qualquer célula não vazia for removida, o
primeiro toque em “Configurar tabela” mostra o número real — por exemplo, “2 célula(s) com conteúdo
serão perdidas. Continuar?” — e só o segundo toque aplica a mudança. A aplicação inteira passa uma
única vez por `super.onValueChange`, portanto é um passo de desfazer.

## 5. CI e testes executados

O estado funcional ficou verde no **run 35352176776**: “Unit tests + APK” passou, montou o APK e a
release foi publicada. O log lista nominalmente, entre outros:

- `parseAndRenderRoundTripTwelveRealFormsByteForByte`;
- `cellsOfIgnoresEscapedAndInlineCodePipes`;
- `buildTableCreatesRequestedDimensionsAndParsesBack`;
- todos os quatro testes de redimensionamento do núcleo;
- os sete testes de inserção, incluindo documento vazio, parágrafo, cerca e CRLF;
- os nove testes do detector/redimensionamento no documento;
- `tableButtonDialogConfirmsDefaultThreeByTwoInRealTextField`;
- `sameTableButtonOpensConfigurationWhenCursorIsInsideTable`;
- `textFormatRowWithTwelveButtonsIsWiderThanScreen`.

A rodada diagnóstica imediatamente anterior enumerou **241 testes**; depois das duas correções de
expectativa, os mesmos testes aparecem como `PASSED` no run verde. Não houve compilação local.

## 6. O que ficou de fora

1. **Toque longo para abrir configuração**, pelas duas limitações instrumentais e pela ausência de
   prova de que não prende ou destrói a seleção. O botão contextual entrega toda a função sem esse
   risco.
2. **Medição em aparelho.** Esta máquina não tem SDK Android nem JDK; toda verificação automatizada é
   CI/Robolectric. O roteiro abaixo cobre o que só o aparelho confirma.

## 7. Roteiro manual no aparelho

1. Instale o APK da última release do fork, abra uma nota e entre no modo de edição. Expanda a barra
   de formatação; ela deve rolar horizontalmente e o ícone de tabela deve ficar acessível.
2. Ponha o cursor num documento vazio e toque no ícone de tabela. Deve abrir “Nova tabela” com
   **3 colunas** e **2 linhas, sem contar o cabeçalho**, já preenchidos.
3. Confira a prévia: três células vazias no cabeçalho, três separadores e duas linhas vazias. Digite
   0, 11 colunas ou 51 linhas; o botão de confirmar deve ficar desativado. Volte a 3 e 2.
4. Toque em “Inserir tabela”. Deve aparecer o esqueleto Markdown 3×2, em linha própria, com uma linha
   em branco ao redor e o cursor dentro da primeira célula do cabeçalho.
5. Preencha as três células do cabeçalho e as seis células do corpo com valores diferentes.
6. Com o cursor em qualquer linha da tabela — cabeçalho, separador ou corpo — toque no mesmo ícone.
   Deve abrir **“Configurar tabela”**, mostrando 3 colunas e 2 linhas, não “Nova tabela”.
7. Altere para **4 colunas e 3 linhas** e confirme. Todos os nove valores antigos devem continuar no
   mesmo lugar; deve nascer uma coluna vazia à direita e uma linha vazia embaixo.
8. Use Desfazer uma vez. A tabela deve voltar inteira para 3×2; Refazer uma vez deve retornar a 4×3.
9. Abra novamente a configuração, preencha alguma célula da quarta coluna e tente reduzir para três
   colunas. O primeiro toque deve informar exatamente quantas células preenchidas serão perdidas e
   pedir “Continuar”; só o segundo pode remover.
10. Repita a redução deixando vazias todas as células que sairão. Não deve aparecer aviso de perda.
11. Em uma tabela antiga sem pipes externos (`a | b`), aumente e diminua dimensões. Ela deve continuar
    sem pipes externos; espaços e alinhamentos das colunas sobreviventes não devem ser padronizados.
12. Em uma linha de tabela com células a mais que o cabeçalho, redimensione. O texto excedente deve
    continuar na última célula, unido por ` | `, sem desaparecer.
13. Ponha o cursor no meio de um parágrafo e insira outra tabela. Ela deve entrar depois da linha
    inteira, nunca cortando o parágrafo; o texto anterior e posterior deve permanecer idêntico.
14. Dentro de um bloco cercado por três crases, insira uma tabela. Ela deve entrar literalmente, com
    uma quebra simples de cada lado e sem linhas em branco artificiais dentro da cerca.
15. Numa nota CRLF vinda do Windows, insira e redimensione uma tabela. As quebras devem continuar CRLF
    e o Git não deve mostrar o restante da nota como reescrito.
16. Segure o dedo sobre a tabela. Deve continuar aparecendo a seleção normal do Android; nenhuma folha
    de tabela abre por esse gesto. Feche a seleção e confirme que copiar/colar ainda funciona.
17. Troque para leitura. A tabela deve continuar renderizando como antes; este trabalho não alterou o
    modo leitura.
