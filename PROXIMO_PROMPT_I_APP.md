Leia E:\Projetos\gitnote\HANDOFF_10_DESEMPENHO_E_NAVEGACAO.md, seção 0 (regras do
repositório) e FASE I inteira. Leia também ESTADO_10.md.

TAREFA: renderizar imagem nas notas do app Android. SOMENTE isso.
Não toque na Fase J, em F.3/H.4 nem na Parte V.

O QUE JÁ FOI DECIDIDO E FEITO (não refazer, não perguntar de novo)

I.0 está FECHADO desde 20/09/2026, pelo Bruno:
- A pasta de anexos é `06_Conhecimento/_anexos/`, dentro da árvore de notas.
- O vault já a criou, com as 21 imagens que eram anexo de nota (as 20 soltas na
  raiz e a única que estava em 06_Conhecimento). Commit `2de3f6e` do repositório
  `brunojcbrg-cloud/vault-conhecimento`, já empurrado.
- O `.gitignore` do vault mantém o bloco geral de imagens e abre exceção só para
  essa pasta (duas linhas: a da pasta e a do conteúdo). Antes disso havia **0**
  imagens rastreadas para 405 no disco — era por isso que imagem colada no
  Obsidian nunca aparecia no celular. Não é falha de renderizador.
- `.obsidian/app.json` recebeu `attachmentFolderPath`, `newLinkFormat: shortest` e
  `useMarkdownLinks: false`, então toda colagem nova cai na pasta e grava
  `![[nome.png]]`.
- I.7 decidido: **imagem remota NÃO renderiza**. `![](https://…)` continua como
  texto. Entregar o IP do leitor a servidor de terceiro a cada abertura não vale a
  conveniência. O Bruno disse que vai evitar imagem remota.
- Não foram movidas, de propósito, as 384 imagens de `05_Sistema`, `07_Oraculo`,
  `export`, `04_IA_Workspace`, `Excalidraw` e `.obsidian`: são material de plugin e
  de sistema, nenhuma é citada por nota, e levariam ~70 MB ao clone do celular.

A WEB JÁ FOI FEITA — use como referência do contrato (commit `2191b7f` de
`E:\Projetos\notas-web`, `src/anexos.ts` e `src/markdown.ts`):
- `![[nome.png]]` resolve **pelo nome** dentro da pasta de anexos, como o Obsidian
  faz; caminho completo também vale.
- `![[nome.png|496]]` e `|800x600` viram largura; apelido não numérico vira texto
  alternativo.
- Imagem remota fica como texto.
Os três clientes têm de concordar nisso. Divergir do contrato é regressão.

O QUE MEDI NO APP (ponto de partida, conferir antes de confiar)
- `ui/screen/app/grid/markdownHelper.kt:75` — `imageTransformer: ImageTransformer =
  NoOpImageTransformerImpl()`. É o parâmetro que precisa de uma implementação real;
  hoje a imagem simplesmente não é desenhada.
- **Não há Coil nem Glide no projeto.** A biblioteca é
  `com.mikepenz:multiplatform-markdown-renderer` 0.43.0, com os artefatos `-m3` e
  `-android` já no `libs.versions.toml`. CONFIRA o artefato de imagem dessa versão
  antes de acrescentar dependência: chutar coordenada custa uma rodada de CI de
  ~10 min. Se não existir artefato pronto, escreva o `ImageTransformer` à mão.
- `![[nome.png]]` **não é sintaxe GFM**: o parser não produz nó de imagem para ela.
  O projeto já tem tratamento próprio de wikilink (`WikilinkSupport.kt`) — o embed
  de imagem tem de entrar por ali ou por um pré-passe, não pelo parser.
- **O risco de SAF não se confirmou, e isso é bom:** `data/platform/FileSystem.kt` usa
  `java.nio.file.Paths`, não `DocumentFile`/`contentResolver`. Onde o app lê o `.md`,
  lê o `.png` ao lado, nos dois `StorageConfig`. Confirme numa leitura rápida antes de
  apoiar a fase nisso, mas não gaste sessão desenhando contorno para SAF.
- **Limite conhecido do modo de edição:** `VisualTransformation` não hospeda
  composable, então imagem inline no live preview do app não sai como na web. Modo
  leitura primeiro; o de edição pode exigir a Fase J (`TextFieldState`) antes.

CRITÉRIO DE ACEITAÇÃO
- Teste de resolução (função pura, JVM): nome curto acha o arquivo em `_anexos`;
  caixa e acento não importam; caminho completo vale; nome inexistente devolve nulo
  e a nota não quebra.
- Teste de largura: `|496` e `|800x600`; apelido não numérico vira alt.
- Teste de que imagem remota NÃO vira imagem.
- Teste Robolectric compondo o markdown com um transformer falso, para provar que o
  embed chega até ele. Lembre de
  `CompositionLocalProvider(LocalInspectionMode provides true)`, senão o parser roda
  fora do Compose e o teste mede o estado Loading.
- Os 380+ testes atuais continuam verdes, com os nomes no log do CI.
- Modo leitura primeiro. Modo de edição (live preview) só depois, e pode ser outra
  sessão — diga qual escolheu e por quê.

REGRAS DO REPOSITÓRIO
- Não compile localmente. Não instale JDK, SDK nem Gradle. Quem compila é o GitHub
  Actions no push da master; leia o log do job "Unit tests + APK".
- Quando a rodada falhar, baixe `gh run download <id> -n test-report-<n>`: o log do
  job mostra só FAILED, o artefato traz a exceção.
- Strings novas vão em `values/strings.xml` E em `values-pt-rBR/strings.xml`.
- O trabalho de segurança já foi commitado (b73) — a árvore está limpa. Se aparecer
  arquivo modificado que não é seu, PARE e pergunte.
- Nenhum teste consegue construir `GridViewModel`/`MarkDownVM` reais (`GitManager`
  carrega `git_wrapper`): extraia função pura e teste ela.

AO TERMINAR
Escreva `RESULTADO_10_I_APP.md`, atualize `ESTADO_10.md` e regrave
`PROXIMO_PROMPT.md`. Depois pare e me chame para testar no aparelho: é o único
lugar onde dá para ver se a imagem aparece de verdade.
