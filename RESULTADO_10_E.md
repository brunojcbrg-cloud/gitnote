# RESULTADO 10 — FASE E

## O que mudou

- `sumarioDe` extrai títulos ATX de níveis 1–6 numa passagem linear, preserva linha e offset de origem, ignora tags sem espaço e cercas de código, aceita LF/CRLF e remove marcadores de ênfase apenas da legenda exibida.
- `SumarioLateral` mostra todos os títulos em `LazyColumn`, com indentação por nível e destaque do título atual. Ocupa até 62% da largura e no máximo 280 dp, com fundo translúcido e sem escurecer a nota.
- O botão na barra superior abre o painel nos modos de leitura e edição. A faixa de rolagem direita também o mostra durante o arrasto e o esconde ao soltar. Na leitura, o salto usa as âncoras medidas e o bloco anterior quando a âncora ainda não foi medida; na edição, usa `MarkDownVM.moveCursorToLine`. O gesto de rolagem existente não foi substituído.
- `Back` fecha o painel antes de sair da nota. As legendas foram incluídas em inglês e português do Brasil.

Commits: `ce7aa8a` (implementação) e `afa2580` (correção de compilação). Só os oito arquivos da Fase E entraram nesses commits; as alterações de segurança não commitadas foram preservadas.

## Medição e testes

Uma medição local do vault em `06_Conhecimento` percorreu as **140 notas `.md`** com as mesmas regras de ATX/cerca usadas pelo módulo e encontrou **2.487 títulos**; a maior nota tinha **269**. O handoff registrava 2.465 e a primeira medição desta fase, antes da edição atual do Bruno, tinha 2.486. O número mudou durante a edição das notas e não é fixado no aplicativo. O código Kotlin não foi executado diretamente sobre o vault nesta máquina; a medição local usou uma implementação equivalente em Python, e os casos sintéticos do módulo Kotlin passaram no CI.

No job [Unit tests + APK](https://github.com/brunojcbrg-cloud/gitnote/actions/runs/35520294383/job/106103091940), passaram:

1. `SumarioTest.reconheceNiveisETiraMarcadoresSemPerderOffsetOuLinha` — seis níveis, texto exibido, linha e offset.
2. `SumarioTest.ignoraTagsCercasTabelaECerquilhaSoltaEmLfECrlf` — tags, cercas, tabela, `#` isolado e as duas formas de quebra de linha.
3. `SumarioTest.perfSumarioNaNotaDe1946Linhas` — `PERF_SUMARIO` em 1.946 linhas / 180.046 caracteres: amostras **2,005 / 1,991 / 2,006 / 2,025 / 1,999 ms**, mediana **2,005 ms**, abaixo do alvo de 3 ms.
4. `SumarioLateralTest.painelCom269TitulosNavegaParaLinhaSemOcuparMaisDe62PorCentoEm320dp` — composição de 269 títulos, toque e largura.
5. `SumarioLateralTest.painelEm375dpMantemDocumentoVisivel` — largura no segundo tamanho pedido.

`testDebugUnitTest` e o build do APK terminaram com `BUILD SUCCESSFUL`; os testes Rust também passaram. A release [b58 — 26.08.1.58](https://github.com/brunojcbrg-cloud/gitnote/releases/tag/b58) foi publicada.

Não há medição `PERF_SUMARIO` anterior para comparar: o extrator foi criado nesta fase. A primeira medição no CI ficou 0,995 ms abaixo do alvo de 3 ms.

## Limites da verificação

- O Robolectric testa o painel e o offset de destino, mas não constrói `MarkDownVM` real: `GitManager` carrega `git_wrapper` na JVM. A ligação do callback ao `vm.moveCursorToLine` foi revisada no código; não houve teste em aparelho.
- Na leitura, o salto para título ainda não medido usa o bloco conhecido anterior e espera até três quadros pela âncora. Não foi medido em aparelho com uma nota de centenas de títulos.
- A primeira execução do CI, [35520173127](https://github.com/brunojcbrg-cloud/gitnote/actions/runs/35520173127), falhou em `compileDebugKotlin` por `Int * Dp` na indentação. O commit `afa2580` corrigiu para `Dp * Int`, e a execução seguinte passou.

Nenhuma fase após E foi iniciada.
