Leia E:\Projetos\gitnote\ESTADO_10.md antes de qualquer coisa. Leia o
HANDOFF_10_DESEMPENHO_E_NAVEGACAO.md se for escrever codigo.

ONDE TUDO PAROU (21/09/2026)
- Handoff 10 cumprido: A, B, C, D, E, F, K, G, H (H.1, H.2, H.3, H.5) e J.1.
- Seguranca fechada e publicada: release b73.
- I.0 decidido e feito no vault: pasta 06_Conhecimento/_anexos/ versionada, com
  as 21 imagens que eram anexo de nota, e o Obsidian configurado para colar la.
- Fase I na web entregue: notas-web commit 2191b7f, 143 testes verdes.
- FASE I NO APP, MODO LEITURA (I.2): entregue em 21/09, commit d2be3ac, release
  b75, 448 testes verdes na primeira rodada (eram 410; +38, nenhum caiu).
  Detalhe em RESULTADO_10_I_APP.md.

PRIMEIRO DE TUDO: O TESTE QUE SO O BRUNO FAZ
Nada da I.2 foi visto em aparelho. Nenhum teste desenha um PNG de verdade; o que
esta provado e que o embed chega ao transformador com caminho e largura certos.
Antes de comecar qualquer frente nova, peca a ele para instalar a b75, puxar o
vault e abrir no modo LEITURA uma nota com imagem — por exemplo
06_Conhecimento/Medicina/Materias Basicas/Microbiologia/Aula Introducao a
micro.md, que tem `![[Pasted image 20260920093913.png|496]]`.
- Se a imagem aparecer: registre em ESTADO_10.md e siga para as frentes abaixo.
- Se NAO aparecer: pergunte qual StorageConfig a instalacao dele usa (App ou
  Device) e peca o logcat. O suspeito n. 1 e a raiz do repositorio
  (AppPreferences.repoPathSafely) nao bater com onde o .png esta.

FRENTES ABERTAS. Pergunte ao Bruno qual, execute SO uma.

1. FASE I.3 + I.6 NO APP - colar/escolher imagem e redimensionar.
   I.3: botao na TextFormatRow com ActivityResultContracts.PickVisualMedia (nao
   usar READ_MEDIA_IMAGES), colar do clipboard, gravar em
   06_Conhecimento/_anexos/Pasted image <yyyyMMddHHmmss>.png com compressao, e
   inserir `![[nome]]` no cursor. Conferir que o binario entra no add/commit.
   Teste obrigatorio do .gitignore: com a pasta ignorada, o app tem de AVISAR.
   I.6 no app e a folha com predefinicoes (25/50/75/100%) e controle deslizante
   no modo leitura, que reescreve `![[nome|N]]` — NAO alca de arrasto.
   Grava so a largura, inteira, como o Obsidian faz.

2. FASE J.2 - o conserto do custo por tecla no editor.
   Prompt pronto: E:\Projetos\gitnote\PROXIMO_PROMPT_J2.md
   EXIGE Opus 5 com esforco maximo e o aval explicito dele. Tambem e o que
   destrava imagem no modo de EDICAO: VisualTransformation nao hospeda
   composable, entao hoje o embed so vira imagem no modo leitura.

3. PARTE V - renomear 06_Conhecimento para NOTAS.
   Sem impedimento tecnico. O app nao hardcoda o nome para achar o anexo: a
   listagem procura `_anexos` sob qualquer pasta de topo se o caminho padrao nao
   existir. Quem tem o nome cravado e o notas-web (src/github.ts:5 e
   src/anexos.ts:4).

4. DECISAO PENDENTE, nao e fase: `![alt|496](caminho.png)` (tabela I.1 do
   handoff) nao renderiza a 496 em NENHUM cliente — nem na web nem no app. O
   Obsidian aceita. Consertar exige mexer nos dois clientes ao mesmo tempo;
   consertar so um seria regressao. Pergunte se ele quer.

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
- O contrato de imagem e o mesmo nos tres clientes (Obsidian, notas-web, app).
  Divergir dele e regressao; se precisar mudar, muda nos tres.
- Se medir algo diferente do que o handoff afirma, PARE, registre em
  ESTADO_10.md e pergunte. A Fase H ja derrubou uma premissa dele, e a Fase I
  derrubou quatro.

AO TERMINAR
Escreva RESULTADO_10_<FASE>.md, atualize ESTADO_10.md e regrave este arquivo.
