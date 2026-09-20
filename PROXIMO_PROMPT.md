Leia E:\Projetos\gitnote\HANDOFF_10_DESEMPENHO_E_NAVEGACAO.md inteiro antes de
escrever qualquer código, com atenção especial à seção 0 (regras do repositório).

QUAL FASE É A MINHA
Leia E:\Projetos\gitnote\ESTADO_10.md. Se não existir, crie-o com esta ordem de
fases, todas "pendente":
  A, B, C, D, E, F, G, H, J.1
(as fases I e Parte V estão bloqueadas em decisões do Bruno; J.2 em diante exige
aval dele e outro modelo — não entram nesta lista)
Execute A PRIMEIRA FASE PENDENTE e SOMENTE ela. Não comece a seguinte de jeito
nenhum, nem que sobre tempo ou contexto.

O QUE FAZER NESTA FASE
1. Releia a seção da fase no handoff e implemente só o que está nela.
2. Escreva os testes que o critério de aceitação daquela fase exige, incluindo os
   testes PERF_ quando pedidos, com medição antes e depois.
3. Commite apenas os arquivos que você tocou, com mensagem começando por
   "handoff 10 <fase>:".
4. Empurre para a master e LEIA o log do job "Unit tests + APK" no GitHub Actions.
5. Se algum teste ficar vermelho, conserte antes de encerrar. Não encerre com o CI
   vermelho e não desabilite teste para passar.
6. Escreva E:\Projetos\gitnote\RESULTADO_10_<FASE>.md no formato do fim do handoff:
   o que mudou, os números PERF_ antes e depois, os testes escritos e o que cada um
   prova, o que não foi feito e por quê, e o número da release gerada pelo CI.
7. Atualize ESTADO_10.md: marque a fase como entregue, com a data, o commit e a
   release; e anote qualquer divergência entre o handoff e o código real.

REGRAS DO REPOSITÓRIO QUE SÃO ERRADAS COM FREQUÊNCIA
- Não compile localmente. Não instale JDK, SDK nem Gradle: não há SDK Android nesta
  máquina. Quem compila é o GitHub Actions, que dispara sozinho no push da master.
- Há trabalho de segurança não commitado na árvore (PortaoDeSeguranca.kt e outros
  7 arquivos modificados). Não commite, não reverta e não encoste neles.
- Teste de UI é Robolectric, nunca aparelho. Teste de custo por tecla segue o padrão
  de MarkdownTablePerfTest.kt.
- Fora da Fase J, não encoste na arquitetura do editor.

SE ALGO DIVERGIR DO HANDOFF
O handoff foi escrito por leitura do código, não por execução. Se você medir algo
diferente do que ele afirma, PARE, registre em ESTADO_10.md e me pergunte. Não
"corrija" o handoff por conta própria nem siga adiante com a premissa mudada.

COMO ENCERRAR (obrigatório)
Ao terminar, faça as duas coisas:
1. Grave E:\Projetos\gitnote\PROXIMO_PROMPT.md com o prompt da próxima sessão — que
   é ESTE MESMO TEXTO, sem alteração, já que ele descobre a fase pelo ESTADO_10.md.
2. Imprima na conversa, num bloco de código, esse prompt pronto para eu copiar, e
   acima dele um resumo de 5 linhas: fase entregue, testes escritos, números
   medidos, release do CI, e o que ficou pendente para eu decidir.
Depois disso, pare. Não comece a próxima fase.
