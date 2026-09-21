# RESULTADO 10 — FASE H

H.1 e H.5 implementadas na master em 2026-09-20; H.3 medida em 2026-09-20 na
branch `handoff10-h3-textfield`. H.2 já havia sido entregue na release b65.

## O que mudou

- H.1 (`b422ccf`): a prévia usa `activeMarkdownLines` como chave do `remember`.
  Mover o cursor dentro da mesma linha preserva a instância de
  `MarkdownLivePreviewTransformation`; mudar de linha cria outra.
- H.5 (`b422ccf`): o histórico do editor tem teto de 100 entradas. O estado
  `EditParams.Idle` leva só o caminho da nota; o editor consulta o DAO ao abrir.
  `EditParams.Saved` continua carregando conteúdo ainda não gravado.
- H.3 (`bc7b550`): um teste Robolectric mede o `TextField` Material 3 inteiro
  ao mover a seleção dentro da mesma linha de uma nota sintética de **1.946 linhas
  e 180.046 caracteres**. O teste compara a antiga chave por seleção com a chave
  atual, conta chamadas a `filter()` e cronometra separadamente o tempo total e
  o tempo dentro do filtro. O comentário do microbenchmark antigo foi corrigido:
  ele mede só a chave, não o custo do campo.

## Medição H.3

Cada cenário teve dois movimentos de aquecimento e cinco amostras no mesmo
`TextField` e no mesmo runner. O tempo total vai da atualização de seleção até
`ComposeTestRule.waitForIdle()`; portanto inclui o agendamento do teste e a
recomposição, além do filtro. `filter_ms` soma o tempo das chamadas observadas.

| Run do CI | Chave | Mediana total | Mediana em `filter()` | Chamadas por movimento | Instância reconstruída |
|---|---|---:|---:|---:|---:|
| [35545589749](https://github.com/brunojcbrg-cloud/gitnote/actions/runs/35545589749) | seleção anterior | 56,518 ms | 34,811 ms | 3 | 7 de 7 movimentos |
| [35545589749](https://github.com/brunojcbrg-cloud/gitnote/actions/runs/35545589749) | linhas ativas H.1 | 58,307 ms | 38,635 ms | 2 | 0 de 7 movimentos |
| [35546014682](https://github.com/brunojcbrg-cloud/gitnote/actions/runs/35546014682) | seleção anterior | 64,825 ms | 27,369 ms | 3 | 7 de 7 movimentos |
| [35546014682](https://github.com/brunojcbrg-cloud/gitnote/actions/runs/35546014682) | linhas ativas H.1 | 24,425 ms | 11,896 ms | 2 | 0 de 7 movimentos |

H.1 elimina uma chamada a `filter()` por movimento e evita recriar a instância.
Ainda restam **duas chamadas completas** quando a seleção muda dentro da mesma
linha. A primeira rodada não mostrou redução do tempo total; a segunda mostrou,
mas com grande dispersão entre rodadas. Não há base para afirmar um ganho estável
nem que a meta de poucos milissegundos foi atingida. Os números de 1–2 ms do
microbenchmark anterior representam só o cálculo da chave e não devem ser usados
como latência do editor.

O motivo das chamadas residuais está no próprio Compose: `CoreTextField` memoriza
o texto transformado com `remember(value, visualTransformation)` e chama
`filterWithValidation` quando a chave muda. A seleção faz parte de
`TextFieldValue`, então mover o cursor invalida essa memória mesmo com uma
instância estável de `VisualTransformation`. Isso é compatível com a
[implementação de `CoreTextField` no AndroidX](https://android.googlesource.com/platform/frameworks/support/+/efd9d4d75aff064f86067880c346ec965e4dfa3f/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/text/CoreTextField.kt#243).

## Validação e limite

As duas rodadas da branch passaram em Rust, `assembleDebug`, `lintDebug` e
`testDebugUnitTest`; os logs mostram **382 testes `PASSED`**, inclusive
`perfWholeTextFieldMovingCursorWithinOneLine`. A master anterior passou no job
**Unit tests + APK** da [release b71](https://github.com/brunojcbrg-cloud/gitnote/actions/runs/35544952882)
com 381 testes e H.1/H.5. Não houve teste em aparelho. O teste H.3 compõe o
mesmo `TextField` Material 3 usado por `GenericTextField`, mas não instancia o
`TextVM`/`MarkDownVM` reais; a medição não é latência de digitação em aparelho.

## Veredito e próximo passo

H.3 está concluída como diagnóstico. H.1 é uma redução funcional de
reconstruções, mas **não resolveu comprovadamente o custo do editor em notas
grandes**. A decisão técnica seguinte pertence à Fase J já planejada; H.4
continua fora de escopo e só deve ser reavaliada depois de J.3. Esta sessão não
alterou código de produção, não iniciou J.2 e não tocou nas mudanças locais de
segurança.
