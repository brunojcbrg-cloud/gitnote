Leia E:\Projetos\gitnote\ESTADO_10.md e E:\Projetos\gitnote\RESULTADO_10_H.md antes de
qualquer coisa. Leia o HANDOFF_10_DESEMPENHO_E_NAVEGACAO.md se for escrever código.

ONDE O HANDOFF 10 PAROU (20/09/2026)
A lista de fases acabou: A, B, C, D, E, F, K, G, H (H.1, H.2, H.3, H.5) e J.1 estao
entregues. A ultima release e a b71. Sobraram quatro coisas, e TODAS dependem de uma
escolha do Bruno. Nao escolha por ele e nao comece nenhuma sem ele dizer qual.

PASSO 0 - O UNICO PENDENTE MECANICO
A branch `handoff10-h3-textfield` tem um commit a frente da master: `bc7b550`, o teste
Robolectric que mede o custo do cursor no `TextField` real. E teste puro, nao muda
producao, e as duas rodadas do CI passaram com 382 testes. Integrar a master publica a
release seguinte. PERGUNTE ao Bruno antes de mesclar; se ele autorizar, faca
fast-forward na master, empurre e leia o job "Unit tests + APK".

AS QUATRO ESCOLHAS (pergunte qual, execute so uma)

1. FASE J.2 - o caminho tecnico que a Fase H apontou.
   A H.3 provou que o custo por tecla nao cai trocando a chave do `remember`:
   `CoreTextField` refaz `filter()` porque a selecao faz parte de `TextFieldValue`.
   Quem resolve isso e a migracao para `TextFieldState`. O pre-requisito (H.1 + H.2)
   esta cumprido. O prompt pronto esta em E:\Projetos\gitnote\PROXIMO_PROMPT_J2.md e
   EXIGE Opus 5 com esforco maximo e o aval explicito dele. Nao rode com outro modelo.

2. PARTE V - renomear `06_Conhecimento` para `NOTAS`.
   Sem impedimento tecnico desde a Fase C. O app Android nao hardcoda o nome; quem tem
   o nome cravado e o projeto notas-web (`src/github.ts:5`), e as 961+ ocorrencias no
   vault sao, na maioria, registro historico que NAO pode ser reescrito. So comeca com
   ele dizendo que quer agora.

3. FASE I - imagens nas notas.
   Travada no I.0: o Bruno precisa decidir a pasta de anexos e a excecao no `.gitignore`
   do vault (hoje o `.gitignore` exclui todas as imagens, e ha 0 rastreadas). Pendente
   tambem o I.7: renderizar ou nao imagem remota `![](https://...)`. Sem essas duas
   respostas, a fase inteira fica bloqueada.

4. F.3 / H.4 - recolher titulos no modo de edicao e edicao por secao.
   F.3 foi adiada de proposito para a sessao de H.4, e H.4 caiu para ultimo recurso:
   so se reavalia depois de J.3. Ou seja, esta opcao normalmente vem DEPOIS da 1.

REGRAS QUE VALEM EM QUALQUER UMA DELAS
- Nao compile localmente. Nao instale JDK, SDK nem Gradle: nao ha SDK Android nesta
  maquina. Quem compila e o GitHub Actions, que dispara sozinho no push da master.
- Ha trabalho de seguranca nao commitado na arvore (`PortaoDeSeguranca.kt`,
  `PortaoDeSegurancaTest.kt` e 8 arquivos modificados). Nao commite, nao reverta e nao
  encoste neles. Se um arquivo seu coincidir, isole suas linhas como a Fase C fez.
- Teste de UI e Robolectric, nunca aparelho. Nenhum teste consegue construir
  `GridViewModel`/`MarkDownVM` reais (`GitManager` carrega `git_wrapper`): extraia funcao
  pura e teste ela, mais teste estrutural que le o codigo-fonte.
- Ao compor markdown em teste, use `CompositionLocalProvider(LocalInspectionMode provides
  true)`; em conteiner que rola, `performScrollTo()` antes do clique; banco em memoria com
  `allowMainThreadQueries()`. Quando a rodada falhar, baixe o artefato
  `gh run download <id> -n test-report-<n>` - e ele que traz a excecao completa.
- Strings novas vao em `values/strings.xml` E em `values-pt-rBR/strings.xml`.
- Nao trate mediana isolada como prova: o runner do CI e ruidoso e ja enganou duas fases.
  Prova boa aqui e contagem de chamadas ou identidade de referencia, nao tempo.

AO TERMINAR
Escreva `RESULTADO_10_<FASE>.md`, atualize `ESTADO_10.md` (tabela + divergencias) e
regrave este arquivo com o prompt da sessao seguinte. Depois pare.
