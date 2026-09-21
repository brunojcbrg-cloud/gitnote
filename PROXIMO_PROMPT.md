Leia E:\Projetos\gitnote\ESTADO_10.md antes de qualquer coisa. Leia o
HANDOFF_10_DESEMPENHO_E_NAVEGACAO.md se for escrever codigo.

ONDE TUDO PAROU (20/09/2026, fim do dia)
- Handoff 10 cumprido: A, B, C, D, E, F, K, G, H (H.1, H.2, H.3, H.5) e J.1.
- Trabalho de seguranca fechado e publicado: release b73, quatro jobs verdes.
  A arvore do repositorio esta limpa.
- I.0 decidido e feito no vault: pasta 06_Conhecimento/_anexos/ versionada, com
  as 21 imagens que eram anexo de nota, e o Obsidian configurado para colar la.
- Fase I na web entregue: notas-web commit 2191b7f, 143 testes verdes.

TRES FRENTES ABERTAS. Pergunte ao Bruno qual, execute SO uma.

1. FASE I NO APP ANDROID - destravada, e o contrato ja existe na web.
   Prompt pronto: E:\Projetos\gitnote\PROXIMO_PROMPT_I_APP.md
   E a unica frente em que ele ainda ve o problema na tela: as imagens ja estao
   no git e ja aparecem na web, mas o app segue com NoOpImageTransformerImpl.

2. FASE J.2 - o conserto do custo por tecla no editor.
   Prompt pronto: E:\Projetos\gitnote\PROXIMO_PROMPT_J2.md
   EXIGE Opus 5 com esforco maximo e o aval explicito dele. A Fase H provou que
   trocar a chave do remember nao resolve; quem resolve e a migracao para
   TextFieldState.

3. PARTE V - renomear 06_Conhecimento para NOTAS.
   Sem impedimento tecnico desde a Fase C. O app nao hardcoda o nome; quem tem o
   nome cravado e o notas-web (src/github.ts:5) e, agora, a pasta de anexos
   (06_Conhecimento/_anexos), que muda de caminho junto.

F.3 (recolher no modo de edicao) e H.4 (edicao por secao) continuam depois da J.

REGRAS QUE VALEM EM QUALQUER UMA
- Nao compile localmente. Nao instale JDK, SDK nem Gradle. Quem compila e o
  GitHub Actions no push da master; leia o job "Unit tests + APK".
- Teste de UI e Robolectric. Ao compor markdown em teste, use
  CompositionLocalProvider(LocalInspectionMode provides true); em conteiner que
  rola, performScrollTo() antes do clique; banco em memoria com
  allowMainThreadQueries().
- Quando a rodada falhar, baixe gh run download <id> -n test-report-<n>.
- Strings novas vao em values/strings.xml E em values-pt-rBR/strings.xml.
- Nao trate mediana isolada como prova: o runner e ruidoso e ja enganou duas
  fases. Prova boa aqui e contagem de chamadas ou identidade de referencia.
- Se medir algo diferente do que o handoff afirma, PARE, registre em
  ESTADO_10.md e pergunte. A Fase H ja derrubou uma premissa dele.

AO TERMINAR
Escreva RESULTADO_10_<FASE>.md, atualize ESTADO_10.md e regrave este arquivo.
