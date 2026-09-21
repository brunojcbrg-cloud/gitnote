# RESULTADO 10 — FASE G

Busca dentro da nota (G.1 e G.2), entregue em 2026-09-20.

Commit de código: `0112399`. Limpeza documental prévia: `065c737`.
CI: [run 35542498709](https://github.com/brunojcbrg-cloud/gitnote/actions/runs/35542498709),
job **Unit tests + APK** verde, APK construído, release **b68 — 26.08.1.68**.

## O que mudou

- `BuscaNaNota.kt` implementa `ocorrencias` como busca literal, sem regex do usuário.
  Normaliza termo e texto com NFD, remove marcas `Mn` e guarda os offsets originais na
  primeira passada. A comparação ignora maiúsculas por padrão; a função aceita o modo
  sensível. Resultados sobrepostos são incluídos.
- `EditScreen.kt` oferece lupa e linha de busca com campo, posição/total, anterior,
  próximo e fechar. Termo e índice usam `rememberSaveable`. A busca espera 150 ms e roda
  em `Dispatchers.Default`. No modo edição, a ocorrência selecionada vira
  `updateSelection(TextRange(inicio, fim))` por `TextVM.selecionarOcorrencia`.
- No modo leitura Markdown, a consulta usa o conteúdo já preparado para renderização.
  O offset vira linha com `lineOfOffset(renderedLineStarts, ...)` e a rolagem usa
  `nearestAnchorAtOrBefore`. Uma seção recolhida que contém o resultado é aberta.
  A ocorrência atual recebe as cores `colors.highlight` e
  `colors.highlightBackground` pelo annotator existente.
- Strings novas foram adicionadas em `values` e `values-pt-rBR`.

## Medição

`PERF_BUSCA`, nota sintética de **1.946 linhas / 180.046 caracteres**, 5 amostras no
runner do CI após 2 aquecimentos: **3,531 / 3,607 / 3,598 / 3,613 / 3,385 ms**;
mediana **3,598 ms**, abaixo do alvo de 10 ms. Não existe número anterior para a busca,
pois a função foi criada nesta fase. O debounce e o cálculo fora da composição impedem
que a consulta seja executada a cada quadro da digitação do termo.

## Testes escritos

| Teste | O que prova |
|---|---|
| `BuscaNaNotaTest.buscaSemAcentoNosDoisLadosEMapeiaOffsetsOriginais` | `cranio` encontra `crânio` e `CRÂNIO`; modo sensível preserva a caixa; offset UTF-16 com emoji e marca combinante volta ao trecho original. |
| `BuscaNaNotaTest.termoVazioERegraDeSobreposicao` | Termo vazio devolve lista vazia; a regra escolhida é incluir as duas ocorrências de `aa` em `aaa`. |
| `BuscaNaNotaTest.umaLetraEm180MilCaracteresDevolveContagemCerta` | `a` em 180.000 caracteres retorna 180.000 intervalos e os offsets das extremidades. |
| `BuscaNaNotaTest.perfBuscaNaNotaDe1946Linhas` | Imprime cinco amostras e a mediana `PERF_BUSCA`, além de confirmar tamanho, linhas e contagem. |
| `MarkdownEditorUiTest.buscarEAvancarTresVezesSelecionaAQuartaOcorrenciaNoTextField` | Robolectric compõe a barra e um `TextField`, digita o termo, toca › três vezes e confere a seleção da quarta ocorrência. |

O log do job lista **374 testes `PASSED`**, inclusive os cinco acima; nenhum falhou.
A base da Fase K era 369, portanto a frase “331 testes atuais” no pedido ficou desatualizada.

## Limites e trabalho deixado para depois

- O teste Robolectric compõe a barra e um `TextField` diretamente. Não instancia
  `MarkDownVM`/`TextVM` reais, pois `StorageManager` aciona `GitManager` e a biblioteca
  nativa `git_wrapper` não carrega na JVM. A ligação ao VM foi revisada no código,
  conforme o padrão das Fases A/C/E/F.
- O destaque no modo leitura marca a ocorrência **atual** nos tokens de texto visível;
  sintaxe Markdown que não aparece no renderizador não recebe cor. A busca e a contagem
  continuam cobrindo o conteúdo preparado inteiro.
- Não houve teste em aparelho. O CI prova compilação, testes Robolectric e APK.
- Não comecei G+1, H, I, J ou Parte V. O trabalho de segurança local permaneceu fora
  dos dois commits desta fase.
- Não surgiu decisão nova para a Fase G. Continuam dependentes do Bruno I.0, I.7,
  Parte V e o aval específico para J.2; a próxima execução técnica é H.1 + H.3 + H.5.

## Estado prévio corrigido

Antes do código, `065c737` corrigiu a afirmação velha de que H.2 estava pendente:
H.2 está entregue e medida na b65; falta H.1 para liberar a Fase J. Os worktrees
`gitnote-h2` e `gitnote-j1` já estavam ausentes do disco e de `git worktree list`,
então suas linhas e o comando de limpeza foram removidos do `ESTADO_10.md`.
