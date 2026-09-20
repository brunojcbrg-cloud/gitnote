# RESULTADO 10 — FASE F (F.1 e F.2; F.3 não feita, por instrução)

## O que mudou

- `Dobra.kt` (novo, `ui/component/markdown`): `secoesDe` segmenta o texto a partir do
  sumário (corpo de um título vai até o próximo de nível igual ou mais alto — por isso
  recolher um nível 2 também recolhe os níveis 3-6 dentro dele). `dobrar(texto, recolhidas)`
  remove o corpo das seções marcadas, une intervalos aninhados/sobrepostos e devolve
  `TextoDobrado(visivel, mapa)`. `MapaDeDobra.paraVisivel`/`paraOriginal` convertem offsets
  entre o texto original e o visível; round-trip exato para todo offset que sobrevive no
  texto visível (ver divergência 2).
- Modo leitura (`MarkDown.kt`): estado `recolhidas: Set<Int>` (offsets de título,
  `rememberSaveable` por nota) alimenta `dobrar(renderedContent, recolhidas)`. O texto
  passado para `MarkdownCustomInner` passa a ser o dobrado; `registerBlock` e
  `onHeadingPositioned` convertem o offset do parser (visível) de volta para o original
  antes de guardar em `headingPositions`/`blockCoordinates`, então toda a rolagem/âncora
  que já existia continua funcionando sem mudança. Clicar num título escondido dentro de
  uma seção recolhida expande essa seção antes de rolar.
- `markdownHelper.kt`: `positionedHeading` ganhou `onHeadingCollapseToggle`/
  `isHeadingCollapsed` opcionais. Quando presentes, envolve o título num `Row` clicável
  com `Icons.Rounded.KeyboardArrowDown` (expandido) / `KeyboardArrowUp` (recolhido) —
  sem tocar em `material-icons-extended`. Sem esses parâmetros (todo outro chamador,
  ex. `FlashcardScreens.kt`), o comportamento é idêntico ao de antes da Fase F.
- `SumarioLateral.kt`: três ações opcionais (`onRecolherTudo`, `onExpandirTudo`,
  `onRecolherAteNivel`) — uma linha de botões acima da lista, só aparece se pelo menos
  um callback for passado. "Recolher tudo" marca todos os títulos do sumário (usa o
  mesmo `dobrar`, cascata inclusa — ver divergência 1). "Recolher até nível N" marca só
  os títulos daquele nível.
- Strings novas (`outline_collapse_all`, `outline_expand_all`) em `values/` e
  `values-pt-rBR/`.
- **O texto salvo nunca muda**: `recolhidas`/`dobrar` só afetam o que é passado para
  `MarkdownCustomInner` no modo leitura. O modo edição (`GenericTextField`) continua
  recebendo `textContent` original — provado por teste estrutural (ver "Medição e
  testes").

## Duas coisas encontradas nesta fase (detalhe completo em `ESTADO_10.md`)

1. **Perguntei ao Bruno antes de codar "Recolher tudo"**: a regra de não regressão
   (recolher um título esconde os aninhados dentro dele) e o critério "recolher tudo →
   texto visível com N linhas" só valem juntos numa nota sem aninhamento. Medido na
   maior nota real de `06_Conhecimento` (`Aula Introdução à micro.md`, agora **269**
   títulos, não mais 257 — mudou de novo desde a Fase E — níveis 1/2/3/4 =
   117/25/79/48): recolher tudo em cascata deixa **117** títulos visíveis, não 269.
   Bruno escolheu cascata (a opção recomendada). Os dois testes em `DobraTest.kt`
   provam os dois lados: a frase literal do handoff numa nota flat sintética, e o
   número real (117 de 269) numa nota aninhada.
2. **Bug real em `MapaDeDobra.paraOriginal`**, achado rodando o algoritmo (reimplementado
   em Python) sobre as 140 notas reais com seções aleatórias recolhidas: o título logo
   depois de uma seção recolhida mapeava para o offset original **errado** (o início do
   trecho escondido, não o próprio título). Corrigido trocando o desempate de fronteira
   para o fim do corte. Revalidado com 0 falhas em 2.270 offsets de título testados.

Commits: `<preencher no push>`. Só os arquivos abaixo entraram: `Dobra.kt` (novo),
`MarkDown.kt`, `SumarioLateral.kt`, `markdownHelper.kt`, `strings.xml` (values e
values-pt-rBR), os quatro arquivos de teste novos/alterados, `ESTADO_10.md` e este
relatório. O trabalho de segurança não commitado (`PortaoDeSeguranca.kt` e os outros)
não foi tocado nem incluído.

## Medição e testes

Verificação local (Python, mesma lógica de `sumarioDe`/`secoesDe`/`dobrar`/`MapaDeDobra`,
não roda no CI — mesma limitação da Fase E) sobre as **140 notas reais** de
`06_Conhecimento`:

- `dobrar(texto, emptySet())` devolveu o texto idêntico nas 140 notas (0 falhas).
- Round-trip `paraOriginal(paraVisivel(x)) == x` testado em **2.270 offsets de título**
  visíveis (5 seções aleatórias recolhidas por nota, com seed fixa): **0 falhas** depois
  da correção da divergência 2.
- Exemplo real de nível 2 recolhendo níveis 3+ sem afetar o próximo nível 2, confirmado
  num arquivo de `Fisiologia/Neurofisiologia`.
- "Recolher tudo" na maior nota real (269 títulos, níveis 1/2/3/4 = 117/25/79/48) deixa
  **117** títulos visíveis (os de nível 1) — número registrado para a divergência 1.

No CI, job **[a preencher após o push]**, os testes novos:

1. `DobraTest.secaoDeNivel2CobreOsNiveis3a6AteAProximaDeNivel2`
2. `DobraTest.conjuntoVazioDevolveOTextoIdentico`
3. `DobraTest.recolherNivel2EscondeNiveis3a6EMasNaoAProximaDeNivel2`
4. `DobraTest.converterOffsetParaVisivelEDeVoltaDevolveOOriginalParaTitulosVisiveis`
   (30 rodadas × até 39 seções aleatórias recolhidas, notas sintéticas de 40 títulos)
5. `DobraTest.recolherTudoNumaNotaSemAninhamentoDeixaSoAsLinhasDeTitulo` (nota flat de
   1.946 linhas / 278 títulos, mesmo padrão de `SumarioTest`)
6. `DobraTest.recolherTudoNaNotaRealMaisAninhadaDeixaSoOsTitulosDeNivel1` (miniatura da
   árvore 4×2×3×2 medida na nota real — prova o 117 de 269 sem depender do vault no CI)
7. `DobraTest.perfDobrarNaNotaDe1946Linhas` — `PERF_DOBRAR`, recolhendo 1 a cada 3
   títulos (93 de 278), mediana de 5 amostras, sem limite fixo no teste (a Fase J.1 já
   registrou instabilidade do runner no `PERF_SUMARIO` de 3 ms; aqui só o número vai
   pro relatório, sem `assertTrue` sobre tempo).
8. `SumarioLateralTest.acoesDeDobraNaoAparecemSemCallback` e
   `.recolherTudoExpandirTudoERecolherAteNivelChamamOsCallbacksCertos`
9. `MarkdownCustomInnerRecolherTest` (novo arquivo): clique no título chama o toggle
   com o offset do parser; sem callback, o título continua sem `Row`/ícone extra.
10. `RecolhimentoNaoAlteraTextoSalvoTest` (novo arquivo): prova estrutural, lendo
    `MarkDown.kt`, de que o ramo do modo edição nunca referencia `conteudoExibido`/
    `textoDobrado`, e que `conteudoExibido` só entra em `MarkdownCustomInner`.

`PERF_DOBRAR`: **[preencher com o número do log do CI]**.

## Limites da verificação

- Como nas Fases A/C/E, nenhum teste constrói `MarkDownVM`/`GridViewModel` reais
  (`GitManager` carrega `git_wrapper` só dentro do APK). A integração do estado
  `recolhidas` em `MarkDownContent` foi revisada no código e coberta por um teste
  estrutural, não por um teste de UI ponta a ponta com o VM de verdade.
- A verificação nas 140 notas reais roda localmente (Python), não no CI — mesma
  limitação registrada na Fase E para a contagem de títulos.
- F.3 (recolher no modo de edição) não foi feita nesta sessão, por instrução explícita:
  o handoff pede medir F.2 antes, e `MarkDownVM.kt` (que F.3 precisaria tocar) estava
  sendo alterado em paralelo pela sessão da branch `handoff10-j1`. Fica para a sessão
  de H.4.
- Não testei em aparelho: nem o chevron nem o painel "Recolher até nível N" foram
  vistos rodando de verdade, só via Robolectric.
