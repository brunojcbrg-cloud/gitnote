Leia E:\Projetos\gitnote\PEDIDOS_21_09.md e E:\Projetos\gitnote\ESTADO_10.md
antes de qualquer coisa. Leia a FASE I do HANDOFF_10_DESEMPENHO_E_NAVEGACAO.md
antes de mexer em imagem.

TAREFA: fechar os dois pedidos que faltam e, no fim, DESLIGAR O COMPUTADOR.
Eu nao vou estar na frente da maquina. Trabalhe ate acabar.

ONDE TUDO ESTA (21/09/2026, fim do dia)
- App Android: E:\Projetos\gitnote, master `2921ea4`, release b78, CI verde,
  471 testes. Arvore limpa.
- Web: E:\Projetos\notas-web, master `001745b`, 163 testes verdes.
- Vault: E:\Obsidian\CONHECIMENTO, commit `2de3f6e` (pasta 06_Conhecimento/_anexos
  versionada e Obsidian configurado para colar la).
- Ja entregues nesta leva: imagem no modo leitura do app (b75), expandir topico
  sem voltar ao inicio (b77), recolher no sumario (b77), sumario que nao salta
  ao recolher (b78), wikilink no modo ao vivo da web e colar imagem na web.

=============================================================================
TAREFA 1 — TAMANHO DA LETRA NO APP (pedido 1, aberto)
=============================================================================
Ele quer mudar o tamanho de visualizacao das letras das notas e dos titulos,
no MODO LEITURA e no EDITOR.

O encanamento ja existe, conferido:
- Leitura: `markdownTypographyThemed(colors, scale)` em
  ui/screen/app/grid/markdownHelper.kt JA recebe um `scale` e hoje e chamada
  sem ele (1f). MarkDown.kt:~430 e quem chama.
- Edicao: MarkDown.kt calcula `val baseFontSize = MaterialTheme.typography
  .bodyLarge.fontSize` e passa para a previa ao vivo
  (`rememberMarkdownVisualTransformation`) e para a altura de linha do rolador.

Falta:
1. Preferencia em data/AppPreferences.kt, no padrao de `theme`/`sortOrder`
   (enumPreference) ou um floatPreference. Sugestao: enum com 5 degraus
   (Pequena / Media / Grande / Maior / Maxima) -> fator 0.85 a 1.6. Enum
   grava o NOME da constante: ha teste que tranca isso, nao renomeie a toa.
2. Controle nos Ajustes (SettingsScreen.kt), no padrao dos outros seletores.
3. Passar o fator aos DOIS caminhos: `markdownTypographyThemed(colors, escala)`
   na leitura e `baseFontSize * escala` na edicao.

ARMADILHA MEDIDA, nao ignore: `EDIT_LINE_HEIGHT_FACTOR` e o
`FastScrollLineOverlay` calculam a altura de linha a partir do `baseFontSize`.
Mudar a fonte sem levar o fator para esses dois desalinha o rolador rapido --
o dedo vai para uma linha e a tela vai para outra. Deixe um teste disso.

Strings novas em values/strings.xml E em values-pt-rBR/strings.xml.

=============================================================================
TAREFA 2 — COLAR IMAGEM NO CELULAR (pedido 4 / Fase I.3, aberto)
=============================================================================
A WEB JA FOI FEITA e define o contrato. Leia E:\Projetos\notas-web\src\anexos.ts
(`nomeDeColagem`, `comprimirImagem`, `validarNomeDeAnexo`, `enviarAnexo`) e o
handler `colarImagem` em src\main.ts. O app tem de gravar INDISTINGUIVEL:
- nome `Pasted image <aaaammddhhmmss>.png`, o mesmo do Obsidian;
- PNG, maior lado reduzido para 1600 px;
- pasta 06_Conhecimento/_anexos/;
- insere `![[nome.png]]` na posicao do cursor.
Divergir disso e regressao: a mesma nota tem de abrir igual no Obsidian, na web
e no celular.

No app:
1. Botao de imagem na barra de formatacao (TextFormatRow, MarkDown.kt), ao lado
   do de tabela, abrindo `ActivityResultContracts.PickVisualMedia`. NAO use
   READ_MEDIA_IMAGES -- o photo picker nao exige permissao.
2. Colar: interceptar `ClipData.Item.uri` com MIME image/*.
3. Comprimir antes de gravar (o app ja tem amostragem em duas passadas em
   ui/component/markdown/ImagensDaNota.kt -- reaproveite, nao reescreva).
4. Inserir o embed com quebra de linha antes e depois, como `insertTable` faz.
5. Conferir que o BINARIO entra no add/commit do StorageManager: o fluxo de
   hoje e orientado a nota, nao a arquivo qualquer.

TESTE OBRIGATORIO DA ARMADILHA: se o .gitignore do vault voltar a ignorar a
pasta de anexos, o app grava o arquivo, o git ignora, sobe so o .md e o embed
aparece quebrado no PC SEM ERRO NENHUM. O app tem de AVISAR nesse caso. Teste
com e sem a excecao no .gitignore.

=============================================================================
O QUE NAO FAZER SEM FALAR COMIGO
=============================================================================
- J.2 (migracao do editor para TextFieldState): exige Opus 5 com esforco maximo
  e meu aval explicito. Prompt pronto em PROXIMO_PROMPT_J2.md. NAO comece.
- Parte V (renomear 06_Conhecimento para NOTAS): decisao minha, mexe no vault.
- `![alt|496](x.png)`: nao funciona em nenhum cliente hoje. Consertar exige
  mexer na web e no app juntos. Registre, nao conserte sozinho.
- F.3 e H.4 vem depois da J.

=============================================================================
REGRAS
=============================================================================
- APP: nao compile localmente, nao instale JDK/SDK/Gradle. Quem compila e o
  GitHub Actions no push da master; leia o job "Unit tests + APK". Rodada de
  ~10 min. Quando falhar: gh run download <id> -n test-report-<n>.
- WEB: ai pode tudo localmente -- `npm test` e `npm run build` (o build gera
  docs/, que e o que o GitHub Pages publica; commite o docs/ junto).
- Teste de UI do app e Robolectric. Ao compor markdown em teste use
  CompositionLocalProvider(LocalInspectionMode provides true); em lista que rola,
  performScrollToNode antes do clique.
- Nenhum teste consegue construir GridViewModel/MarkDownVM reais (GitManager
  carrega git_wrapper): extraia funcao pura e teste ela.
- CUIDADO COM SCRIPT QUE ESCREVE KOTLIN: escrever `\n` dentro de literal por
  script ja quebrou o build duas vezes hoje (virou quebra de linha de verdade).
  Prefira string bruta com trimIndent, ou escreva o arquivo direto.
- Nao trate mediana isolada como prova. Prova boa aqui e contagem de chamadas,
  identidade de referencia ou teste funcional.
- Se medir algo diferente do que o handoff afirma, PARE, registre em
  ESTADO_10.md e siga pelo que voce mediu. A Fase I ja derrubou quatro premissas.

=============================================================================
AO TERMINAR — NESTA ORDEM
=============================================================================
1. CI verde nos dois repositorios, com o numero de testes no relatorio.
2. Atualize PEDIDOS_21_09.md e ESTADO_10.md com o que foi feito e o que NAO foi
   medido (nada aqui e testado em aparelho -- diga isso sem enfeitar).
3. Regrave PROXIMO_PROMPT.md com o estado novo, dizendo que o teste em aparelho
   da b78 em diante continua pendente e e so meu.
4. Commite e empurre TUDO, nos dois repositorios. Confira `git status` limpo.
5. SO DEPOIS DISSO, desligue o computador:
   `shutdown /s /t 60 /c "Trabalho concluido"`
   NAO desligue se sobrar arquivo sem commit, CI vermelho ou tarefa pela metade.
   Nesse caso, pare, deixe tudo escrito no PROXIMO_PROMPT.md e me avise no
   relatorio final -- o computador fica ligado.
