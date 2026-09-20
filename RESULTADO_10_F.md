# RESULTADO 10 — FASE F (F.1 e F.2; F.3 não feita, por instrução)

**Fechada em 2026-09-20**: master verde na rodada `35524423286`, release **b64
(26.08.1.64)** publicada. 344 testes, 0 falhas.

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

## O CI pegou 3 problemas que a leitura não pegou

Os dois primeiros pushes quebraram o CI (compilação e depois testes). Os três problemas,
todos consertados antes deste relatório:

1. **`compileDebugKotlin` falhou**: `import androidx.compose.foundation.layout.weight`
   em `markdownHelper.kt` colidia com uma propriedade interna homônima do mesmo pacote
   (`RowColumnParentData?.weight`). `RowScope.weight` é membro implícito dentro de
   `Row { }` — não precisa de import (o mesmo padrão que `SumarioLateral.kt` já usava
   sem importar). Import removido.
2. **`compileDebugUnitTestKotlin` falhou**: `assertExists`/`assertDoesNotExist` são
   membros de `SemanticsNodeInteraction`, não extensões top-level importáveis por esse
   caminho — `MarkdownEditorUiTest.kt` já usava `assertExists()` sem importar. Imports
   removidos dos dois arquivos de teste novos.
3. **Três testes falharam de verdade** (341/344 passaram):
   - `DobraTest.recolherTudoNumaNotaSemAninhamentoDeixaSoAsLinhasDeTitulo` esperava 278,
     veio 279 — o último título preserva sua própria quebra de linha (fica antes do
     corte dele), então `split("\n")` conta uma linha vazia extra no fim.
     `removeSuffix("\n")` antes do split corrige, sem mudar o que está sendo provado.
   - Os outros dois (`SumarioLateralTest` e `MarkdownCustomInnerRecolherTest`)
     sobreviveram a mais duas rodadas de tentativa e viraram a seção abaixo.

## As duas falhas que seguraram a master por quatro rodadas

As rodadas `35522309543`, `35522529452`, `35522716693` e `35523618557` deixaram a master
vermelha, ou seja, **nenhuma release saiu entre a b58 (Fase E) e a b64**. Nas duas últimas
sobraram estes dois testes. Os palpites das tentativas anteriores (trocar a tag por texto,
trocar o teste de UI por leitura de código-fonte, acrescentar `waitForIdle`) não pegaram a
causa; ela saiu do relatório HTML da rodada `35523618557` — que só vira artefato quando
falha — mais a leitura do código da própria biblioteca de markdown.

**Nenhum dos dois era defeito do recolher.** Os dois erravam na borda entre o que o
composable emite e o que o teste procura na árvore semântica, e as duas correções mantêm as
asserções originais: muda só como o teste alcança o nó.

### 1. `MarkdownCustomInnerRecolherTest` — o markdown ainda nem tinha sido parseado

Mensagem exata: `Failed to inject touch input. Reason: Expected exactly '1' node but could
not find any node that satisfies: (Text + InputText + EditableText contains 'Título')`.
Não era asserção de valor: **não existia nó nenhum** com aquele texto.

Causa, lida em `MarkdownState.kt` da `com.mikepenz:multiplatform-markdown-renderer` v0.43.0:
`rememberMarkdownState` só chama `parseBlocking()` dentro do `remember { }` quando
`immediate = true`, e esse parâmetro tem default `LocalInspectionMode.current` — falso em
teste. Fora disso o parse vai para `withContext(Dispatchers.Default)`, disparado por um
`LaunchedEffect`, e **nem `waitForIdle()` nem `runOnIdle` esperam trabalho numa thread de
fora do Compose**. O teste media a árvore com o estado ainda em `State.Loading`, que desenha
o `loading = { Box(modifier) }` — vazio. Em produção o parse termina em milissegundos e o
título aparece; o modo leitura nunca esteve quebrado.

Conserto: compor sob `CompositionLocalProvider(LocalInspectionMode provides true)` — o mesmo
interruptor que a biblioteca usa nos previews —, o que torna o parse síncrono na primeira
composição. Nada de produção mudou. Um `waitUntil` de 5 s ficou como rede, para o caso de
alguém tirar o modo de inspeção depois.

Efeito colateral bom: com a árvore realmente renderizada, o teste
`semCallbackDeToggleOTituloContinuaSemChevronClicavelExtra` **voltou a ser prova de
comportamento** (`assertHasNoClickAction()` no título renderizado sem o callback), em vez da
leitura de código-fonte que a rodada anterior tinha colocado no lugar. A checagem estrutural
continua ali como segunda asserção, mas já não é a única.

### 2. `SumarioLateralTest` — o botão estava fora do recorte

Mensagem exata: `expected:<2> but was:<null>`, com o `assertExists()` da mesma tag passando
na linha anterior. Ou seja: o botão existe, o toque foi injetado, e nada aconteceu.

Causa: a linha de ações é um `Row` com `horizontalScroll` dentro de um painel de
`min(280dp, 62% da tela)` — 198 dp na tela padrão do Robolectric. "Collapse all" e
"Expand all" já consomem essa largura, então o botão de nível fica além do recorte. O nó tem
coordenadas (por isso `assertExists` passa e o clique não dá erro), mas o ponto tocado cai
fora da área clipada e não atinge botão nenhum. Num aparelho o usuário rola a linha; o teste
é que clicava sem rolar.

Conserto: `performScrollTo()` antes do `performClick()`. A asserção segue a mesma — o
callback de verdade tem de ser chamado com o nível 2.

> Fica registrado, sem ser defeito: num telefone de 360–410 dp o painel dá 223–254 dp, então
> os botões de nível também nascem fora da viewport e só aparecem se o usuário arrastar a
> linha na horizontal. É rolável de propósito e o critério da Fase F não fala em layout, mas
> vale olhar isso quando alguém encostar nesse painel de novo.

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

Commits: `38a27b8` (F.1+F.2), `1d429fc` / `43b8863` / `27b8bfc` (compilação e testes) e
`829456a` (as duas falhas acima). Só os arquivos abaixo entraram: `Dobra.kt` (novo),
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

No CI, rodada **35524423286** (verde, release **b64 — 26.08.1.64**), os testes novos:

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

`PERF_DOBRAR`: `linhas=1946 chars=180046 recolhidas=93 amostras_ms=[7.214, 0.996, 0.959, 0.953, 0.937]` → **mediana 0,959 ms**. Dobrar 93 das 278 seções da nota de 180 KB custa menos de 1 ms — cabe folgado num toque.

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
