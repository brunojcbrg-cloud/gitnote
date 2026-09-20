Leia E:\Projetos\gitnote\HANDOFF_10_DESEMPENHO_E_NAVEGACAO.md inteiro antes de escrever
qualquer código, com atenção à seção 0 e à FASE H. Leia também ESTADO_10.md,
RESULTADO_10_G.md e o trecho de H.2 em ESTADO_10.md. A master terminou a Fase G
verde na release b68. H.2 já está entregue e medida na b65.

TAREFA DESTA SESSÃO: execute SOMENTE H.1 + H.3 + H.5. Não comece H.4, F.3, J.2,
I ou Parte V. H.1 é o pré-requisito que ainda falta para liberar a Fase J.

H.1 — Em MarkDown.kt, pare de reconstruir a transformação quando o cursor anda
dentro da mesma linha. Calcule activeMarkdownLines antes do remember e use esse
conjunto como chave em vez da seleção inteira. Preserve a aparência do live preview
e o cache de MarkdownScanner.scan da H.2; não mude a arquitetura do editor.

H.3 — Meça PERF_LIVE_PREVIEW antes e depois de H.1 na mesma nota sintética de
1.946 linhas / 180.046 caracteres, no mesmo runner, com pelo menos 5 amostras e
mediana. Escreva teste funcional que prove que mover a seleção na mesma linha não
reconstrói a transformação. Registre a dispersão do runner; não trate uma mediana
isolada como prova se as amostras se sobrepõem. Se H.1 + H.2 chegar à faixa de poucos
milissegundos, pare aí na otimização do preview. H.4 só cabe em fase própria após
necessidade medida e não pertence a esta sessão.

H.5 — Ponha teto explícito no número de passos de TextVM.history (proposta: 100)
e teste o descarte dos mais antigos. Tire o Note inteiro do Parcelable de
AppDestination.Edit/ EditParams.Idle: navegue com relativePath e busque a nota
por dao.noteByRelativePath ao abrir. Preserve EditParams.Saved com o conteúdo da
edição ainda não gravada. Teste a recuperação e o tamanho do estado salvo.

REGRAS
- Não compile localmente nem instale JDK, SDK ou Gradle. O CI roda no push da
  master; leia o log do job "Unit tests + APK" e a release publicada.
- Há alterações locais de segurança não commitadas (PortaoDeSeguranca.kt e outros
  arquivos). Não commite, reverta ou edite essas linhas. Se o seu arquivo coincidir,
  isole no commit somente as suas linhas e documente o método.
- Fora da Fase J, não migre para TextFieldState nem altere a arquitetura do editor.
- Commite apenas os arquivos da sua fase, com mensagem começando por
  "handoff 10 H:". Não encerre com CI vermelho nem desabilite testes.
- Se medir uma premissa diferente da afirmada no handoff, PARE, registre em
  ESTADO_10.md e pergunte ao Bruno antes de seguir.

AO TERMINAR
Escreva E:\Projetos\gitnote\RESULTADO_10_H.md com mudanças, números PERF_ antes
e depois, testes escritos e o que provam, limites, pendências e release do CI.
Atualize ESTADO_10.md com data, commit, release e divergências. Prepare o prompt da
fase seguinte para revisão do Bruno e pare. Não comece J.2 automaticamente: ela
exige Opus 5, esforço máximo e aval explícito.
