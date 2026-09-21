# Pedidos do Bruno em 21/09/2026

Seis coisas, fora das fases do handoff 10. **Cinco e meia entregues** (o pedido 4
saiu na web e falta no app).

| # | Pedido | Status |
|---|---|---|
| 1 | Mudar o tamanho da letra das notas e títulos, no modo leitura **e** no editor | **entregue** — botões + e − na própria nota; falta ver em aparelho |
| 2 | Expandir tópico recolhido voltava para o início da nota | **entregue** — b77, com o painel acertado na b78 |
| 3 | Recolher títulos no painel do sumário (mostrar ou não os subtópicos) | **entregue** — b77 |
| 4 | Colar imagem com Ctrl+V direto no editor da web e no celular | **web entregue** (`001745b`); **app aberto** |
| 5 | Sair do app durante a edição perdia tudo o que tinha sido digitado | **entregue** — `9fab3c6`, na b79; falta ver em aparelho |
| 6 | Faltava o Tab: recuar um item de lista para virar subtópico | **entregue** — botões na barra de formatação; falta ver em aparelho |

Entregues no commit `25f63b2` (+ `ca4…` de conserto do teste), release **b77
(26.08.1.77)**, [run 35552142059](https://github.com/brunojcbrg-cloud/gitnote/actions/runs/35552142059):
**470 testes verdes, 0 falhas** — eram 448, e os 22 novos são exatamente estes.

---

## 2. A nota voltava ao início ao expandir um tópico

**A causa não é a rolagem, é o reparse.** `MarkdownStateImpl.updateInput` faz
`if (!newInput.retainState) stateFlow.value = State.Loading(...)`. Trocar o texto
— que é o que recolher e expandir fazem — joga o estado para `Loading`, o corpo
vira um `Box` vazio, a altura rolável cai para zero e o `ScrollState` **corta** a
posição para 0. Não havia nada de errado com a âncora de leitura.

Conserto em duas metades:

1. **`retainState = true`** no modo leitura. O interruptor é da própria
   biblioteca e estava desligado: agora o conteúdo já desenhado fica na tela
   enquanto o texto novo é reparseado, e não há mais o quadro vazio.
2. **Âncora da dobra.** No clique, guarda onde o título estava **na tela** — não
   a rolagem, porque depois da dobra o conteúdo acima dele tem outro tamanho — e
   devolve a rolagem assim que o título volta a ser medido. Se ele não aparecer
   em 30 quadros, **não rola para lugar nenhum**: rolar para zero sem ter achado
   o título era o próprio defeito.

Vale para os dois sentidos e também para "Recolher tudo" / "Expandir tudo".

## 3. Recolher no sumário

Seta em cada título que tem subtítulo, no painel. Recolher ali esconde o
subtítulo **da lista** — não mexe no texto da nota nem no recolhimento do modo
leitura, que são os botões da linha de cima. Quem não tem subtítulo ganha o
espaço da seta, para o texto não desalinhar. O estado é por nota e sobrevive a
fechar e reabrir o painel, nos dois modos. O título atual escondido dentro de um
recolhido marca o ancestral visível, em vez de sumir ou voltar ao topo.

## O que não foi medido

Nada em aparelho. A lógica saiu do composable e virou função pura porque nenhum
teste constrói o `MarkDownVM` real (`GitManager` carrega `git_wrapper`), então o
que está provado é a aritmética da âncora, a regra de visibilidade do sumário e
o comportamento do painel em composição. O `retainState` é trancado por um teste
que lê o código-fonte — é a mesma tática já usada em
`MarkdownCustomInnerRecolherTest`.

**Continua pendente também o teste em aparelho da b75** (imagem no modo leitura,
Fase I.2). A b77 carrega as duas coisas.

---

## 1. Tamanho da letra — entregue em `f3dc4b6`, refeito depois da b79

**A primeira versão pôs o controle nos Ajustes, e não era isso.** Correção do
Bruno depois de ver a b79: *"prefiro que seja na própria nota, com dois botões,
um + para aumentar e outro − para diminuir; e quando eu modificar em uma nota,
tem que ficar salvo como padrão."*

Como ficou: dois botões na **barra de baixo da nota**, com a porcentagem atual
entre eles. Valem nos dois modos — inclusive no de leitura, que é justamente
quando o tamanho incomoda, e onde a barra de formatação fica desligada. Por isso
eles moram na `DefaultRow` e não na `TextFormatRow`.

A preferência deixou de ser um punhado de degraus nomeados e virou **porcentagem
inteira**, de 80% a 200%, de 10 em 10 — com cinco degraus fixos os botões ficariam
saltando. Cada toque grava na hora, na mesma preferência que todas as notas leem:
não existe tamanho "só desta nota". Valor fora da grade é trazido para o degrau
mais próximo, senão um resto de configuração antiga viraria um meio-termo que os
botões nunca mais alcançam.

A entrada nos Ajustes foi removida, para não ficarem dois controles para a mesma
coisa. **496 testes verdes** (8 no `TamanhoDaLetraTest`).

Do desenho original, o que continua valendo: a escala é aplicada na
**tipografia inteira** (`tipografiaEscalada`), não só no corpo: os seis níveis de
título saíam de `MaterialTheme.typography` sem escala nenhuma, e o pedido fala de
notas **e** títulos. Com isso os dois caminhos crescem juntos — o renderizador do
modo leitura e o `TextField` do editor.

A armadilha registrada abaixo está coberta e trancada por teste: o `baseFontSize`
da prévia ao vivo e da altura de linha do rolador rápido sai da mesma tipografia
escalada, então o dedo e a tela não se separam em nenhum degrau — e agora isso é
verificado nos treze degraus, não em cinco.

O encanamento que já existia, e que foi aproveitado:

- Leitura: `markdownTypographyThemed(colors, scale)` em `markdownHelper.kt` já
  recebe um `scale` e hoje é chamada sem ele (`1f`).
- Edição: `MarkDown.kt` já calcula `baseFontSize` a partir de
  `MaterialTheme.typography.bodyLarge.fontSize` e o repassa à prévia ao vivo e
  ao cálculo de altura de linha do rolador rápido.

O cuidado que guiou o desenho, e que continua valendo para quem mexer aqui:
`EDIT_LINE_HEIGHT_FACTOR` e o `FastScrollLineOverlay` dependem de `baseFontSize`
— mudar a fonte sem mexer neles desalinha o rolador.

Ficou de fora, por não ter sido pedido: o gesto de pinça.

## 4. Colar imagem — web entregue, app aberto

São as sub-fases I.3 (app) e I.5 (web) do handoff 10, com o modo leitura já
pronto nos dois clientes.

- ~~**Web**~~ — **entregue em 21/09** (`001745b` de `notas-web`, **163 testes
  verdes**): `paste` no CodeMirror 6, compressão por `canvas` com teto de lado,
  `PUT` na API de conteúdo e `![[nome]]` no cursor. O nome segue o padrão do
  Obsidian (`Pasted image <yyyyMMddHHmmss>.png`) e o caminho é validado para não
  escapar da pasta de anexos. Na mesma leva saiu o wikilink no modo ao vivo
  (`6ab080e`).
- **App**: interceptar imagem da área de transferência e oferecer o seletor
  (`ActivityResultContracts.PickVisualMedia`, que **não** exige
  `READ_MEDIA_IMAGES`), gravar em `06_Conhecimento/_anexos/Pasted image
  <yyyyMMddHHmmss>.png` e inserir `![[nome]]`.
- Os dois gravam **o mesmo nome e o mesmo formato** que o Obsidian, senão os três
  clientes divergem.
- **A armadilha obrigatória:** se o `.gitignore` do vault voltar a ignorar a
  pasta, o arquivo é gravado, o git o ignora e sobe só o `.md` — embed quebrado
  no PC, sem erro nenhum. O app tem de **avisar**.

---

## Estado em 21/09, fim do dia

- **App**: release **b81** (`26.08.1.81`), CI verde, publicada a partir de `14ddd50`.
  Três releases saíram em sequência: **b79** (handoff 11 — sugestão de wikilink, ver
  `RESULTADO_11_SUGESTAO_DE_WIKILINK.md` — mais o tamanho da letra e o conserto do
  rascunho), **b80** (tamanho da letra refeito com os botões na nota) e **b81** (os
  botões de recuo). Nada pendente de merge.
- **Web**: master `001745b`, **163 testes verdes**.
- **Vault**: `2de3f6e` — pasta de anexos e configuração do Obsidian.
- **Nada disso foi visto em aparelho ainda.** A b79 carrega, além do que já vinha da b78
  (imagem no modo leitura, âncora da dobra, recolhimento do sumário), as três entregas
  novas — e as duas medições de aparelho do handoff 11 continuam pendentes.

---

## 5. Sair do app durante a edição perdia o que foi digitado

Relato: *"quando estou editando uma nota pelo celular e preciso ir para outro
programa, como a Internet para pesquisar, quando volto pro app a nota fechou,
voltou para a pasta padrão e tudo o que digitei se perdeu."*

**O mecanismo de rascunho já existia e nunca era acionado no momento certo.** O
`NoteSaver` grava nome, conteúdo, nota anterior e tipo de edição em arquivo, e o
`AppScreen` sabe ressuscitar a edição a partir dele: se `isEditUnsaved()` é
verdadeiro, a pilha inicial nasce com `Grid` + `Edit(EditParams.Saved(...))`.
Só que a gravação acontecia **exclusivamente** em `TextVM.onCleared()` — e
`onCleared` não roda quando o sistema mata o processo com o app em segundo plano.
Ou seja: o seguro existia e só era acionado no caso em que não era preciso.

Conserto: a gravação virou `TextVM.guardarRascunho()`, chamada de dois lugares —
o `onCleared` de sempre e, agora, `Lifecycle.Event.ON_STOP` na tela de edição,
que é o último instante que o Android garante antes de tirar o app da frente.
A condição de gravar **não** mudou: continua `shouldSaveWhenQuitting &&
!isPreviousNoteTheSame()`, então sair pelo diálogo "sair sem salvar" segue
descartando, e nada é gravado quando o texto na tela é igual ao do disco.

Efeito colateral bom: mesmo quando o processo **não** morre, voltar ao app
reabre a nota com o texto, porque o rascunho já está em disco quando a árvore é
remontada.

**A outra metade, que não mexi:** a nota fechar e a lista voltar para a pasta
padrão não é falta de memória do Android — é o portão de segurança.
`MainActivity.onStart` faz `aberto = false` **em toda volta ao app**, antes da
leitura assíncrona da preferência ("Hide the navigation before asynchronous
preference reads can show a frame of notes"), e isso desmonta a árvore inteira
do app: o `rememberSaveable` da pilha de navegação morre junto e ela renasce na
`Home`. Acontece **mesmo com a trava desligada**, que é o padrão
(`travaDeAbertura = false`). Resolver isso é não esconder quando a trava está
comprovadamente desligada — mas mexe no gate de segurança e precisa de aparelho
para conferir, então fica como decisão separada.

Provas: `RascunhoSobreviveAoSegundoPlanoTest` (4 testes estruturais, no molde de
`RecolhimentoNaoAlteraTextoSalvoTest`, porque `TextVM` não instancia em JVM e
ciclo de vida do Compose não roda em teste de unidade). Suíte inteira: **494
testes, 0 falhas**.

---

## 6. Faltava o Tab: recuar um item para virar subtópico

Pedido: *"no app não tem nenhum botão ou controle que me permita dar o espaço
semelhante ao dado com a tecla Tab, que faz uma linha ser organizada em tópico."*

Dois botões novos na barra de formatação, ao lado dos de lista: recuar e voltar
— o Tab e o Shift+Tab que o teclado do celular não tem.

**O recuo é uma tabulação, e isso foi medido, não escolhido por gosto.** No vault,
1.489 itens de lista aninhados usam tabulação contra 515 com espaços, e é o que a
tecla Tab do Obsidian insere. Recuar com espaços faria a mesma nota aninhar de um
jeito no celular e de outro no computador.

**Só item de lista recua.** Parágrafo recuado vira **bloco de código** no markdown
— quatro espaços ou uma tabulação no começo é a sintaxe de código indentado —, e
transformar o texto em código silenciosamente seria pior que não fazer nada. Pelo
mesmo motivo a citação (`>`) fica de fora: `\t> x` não é citação aninhada, é código.
Numa seleção de várias linhas, os itens andam e o que não é item fica parado.

Voltar (o Shift+Tab) também desfaz recuo escrito com espaço, porque o vault tem
515 itens assim e eles precisam ser editáveis pelo botão.

**507 testes verdes** (11 novos, e estes são de lógica pura: entra texto, sai texto).

### 5.1 — o botão de salvar apagava o texto (achado com a b81 no aparelho)

Relato do Bruno depois de usar a b81: *"escrevi na nota e saí e voltei várias
vezes, o rascunho ficou. Mas quando cliquei no botão ✓ para salvar, voltou para a
pasta raiz e apagou o que eu escrevi e não salvou nada."*

**Duas coisas somadas, e a segunda é grave.**

**A pasta raiz** é a pilha de navegação que o rascunho reconstrói. `AppScreen`
monta `Home` → `Grid` → `Edit(Saved)`, e esse `Grid` não tem pasta: é a raiz. Ao
sair da nota, o "voltar" cai ali em vez de na pasta onde ele estava. É feio, mas
não perde nada.

**O texto sumir é `applyNoteUpdate`, e o defeito é anterior a tudo isto.** A
gravação era, nesta ordem: **apagar** o arquivo antigo, **criar** o novo, **escrever**
o texto. Falhar no `create` apaga a nota; falhar no `write` deixa um arquivo
**vazio** — que é exatamente "apagou o que eu escrevi". Pior: quando o caminho não
muda, que é o caso de todo salvamento comum, não havia razão nenhuma para apagar
antes; bastava sobrescrever.

O que tornou isso fatal agora foi o rascunho: `save()` dá a edição por salva e
navega **antes** de a escrita acontecer (`CoroutineScope(Dispatchers.IO).launch`,
com o comentário "best effort" no próprio código), e o `onCleared` em seguida
apagava o rascunho. Ou seja, a última cópia do texto era destruída antes de a
primeira existir.

Conserto em duas metades:

1. **Escrever antes de apagar.** Sobrescreve o arquivo quando o caminho não muda;
   quando muda (renomear/mover), grava o novo e só então remove o antigo.
2. **O rascunho só é apagado quando a escrita confirma.** `escritaConfirmada` fica
   falso do clique até `updateNote`/`createNote` devolverem sucesso. Se a escrita
   falhar, ou o processo morrer no meio, a nota reabre com o texto.

**509 testes verdes.** Duas travas antigas deste mesmo arquivo apontaram a mudança
e foram atualizadas — a intenção delas (descarte explícito manda; `NoteSaver` não
pode ser chamado de qualquer lugar) continua trancada.

**O que ainda não está explicado:** *por que* a escrita falhou no aparelho dele.
Com o conserto, a mesma falha passa a ser recuperável e o app mostra o erro em vez
de engolir — o texto da mensagem é o que falta para nomear a causa.
