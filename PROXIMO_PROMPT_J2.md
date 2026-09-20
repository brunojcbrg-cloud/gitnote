MODELO: Opus 5 (claude-opus-5). ESFORÇO: máximo. Confira os dois antes de começar.

Leia E:\Projetos\gitnote\HANDOFF_10_DESEMPENHO_E_NAVEGACAO.md inteiro, com atenção
à seção 0 (regras do repositório) e à Fase J completa. Leia também ESTADO_10.md e
RESULTADO_10_H.md — o custo por tecla medido na Fase H é a régua desta sessão.

TAREFA: executar SOMENTE a FASE J.2 (o OutputTransformation novo ao lado do antigo).
Não toque em J.3. Não troque o TextField. Nada do app muda de comportamento nesta
sessão: no fim, as duas implementações coexistem e a antiga continua sendo a que roda.

ANTES DE ESCREVER CÓDIGO
1. Confirme que H.2 foi entregue e medido. Se não foi, PARE e me diga.
2. Crie a etiqueta de retorno: git tag handoff10-antes-de-J2 na master verde, e
   anote-a no relatório. É para onde voltamos se esta fase reprovar.

O PORTÃO DESTA FASE
J.2 só é aprovada se a nova implementação produzir o MESMO resultado visível da
antiga. Adapte MarkdownLivePreviewTransformationTest e MathRenderingTest para uma
bateria parametrizada que roda contra as DUAS e exige igualdade: texto renderizado,
intervalos de estilo, e o mapeamento de offset dentro e fora da linha ativa.
Se não sair igual, NÃO force, NÃO afrouxe o teste e NÃO siga para J.3: pare,
escreva o que divergiu e me chame. O app segue intacto porque nada foi trocado.

MEDIÇÃO
PERF_OUTPUT_TRANSFORMATION na nota sintética de 1.946 linhas / 180.046 caracteres,
no padrão de MarkdownTablePerfTest.kt, comparado com o PERF_LIVE_PREVIEW da Fase H.
Os dois números vão no relatório.

REGRAS DO REPOSITÓRIO
- Não compile localmente; quem compila é o GitHub Actions no push da master. Leia o
  log do job "Unit tests + APK".
- Não encoste no trabalho de segurança não commitado (PortaoDeSeguranca.kt e os
  outros 7 arquivos modificados).
- Não migre nenhum arquivo da coluna "NÃO migra" de J.0.1.

AO TERMINAR
Escreva E:\Projetos\gitnote\RESULTADO_10_J2.md com: o que mudou, a tabela de
conversão que você de fato aplicou, os dois números PERF_, o veredito do portão
(aprovado ou reprovado, com evidência), a etiqueta de retorno criada, e o que ficou
pendente. Atualize ESTADO_10.md. Depois pare e me chame antes de qualquer J.3.
