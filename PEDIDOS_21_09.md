# Pedidos do Bruno em 21/09/2026

Quatro coisas, fora das fases do handoff 10. **Duas e meia entregues** (o pedido 4
saiu na web e falta no app), uma aberta.

| # | Pedido | Status |
|---|---|---|
| 1 | Mudar o tamanho da letra das notas e títulos, no modo leitura **e** no editor | **aberto** |
| 2 | Expandir tópico recolhido voltava para o início da nota | **entregue** — b77, com o painel acertado na b78 |
| 3 | Recolher títulos no painel do sumário (mostrar ou não os subtópicos) | **entregue** — b77 |
| 4 | Colar imagem com Ctrl+V direto no editor da web e no celular | **web entregue** (`001745b`); **app aberto** |

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

## 1. Tamanho da letra (aberto)

O encanamento já existe e está quase todo pronto:

- Leitura: `markdownTypographyThemed(colors, scale)` em `markdownHelper.kt` já
  recebe um `scale` e hoje é chamada sem ele (`1f`).
- Edição: `MarkDown.kt` já calcula `baseFontSize` a partir de
  `MaterialTheme.typography.bodyLarge.fontSize` e o repassa à prévia ao vivo e
  ao cálculo de altura de linha do rolador rápido.

Falta: uma preferência (`AppPreferences`, no padrão de `Theme`/`SortOrder`), o
controle nos Ajustes (ou um gesto de pinça), e passar o fator aos dois caminhos.
Cuidado medido: `EDIT_LINE_HEIGHT_FACTOR` e o `FastScrollLineOverlay` dependem
de `baseFontSize` — mudar a fonte sem mexer neles desalinha o rolador.
Strings novas em `values/` **e** `values-pt-rBR/`.

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

- **App**: master `2921ea4`, release **b78**, CI verde. Nada pendente de merge.
- **Web**: master `001745b`, **163 testes verdes**.
- **Vault**: `2de3f6e` — pasta de anexos e configuração do Obsidian.
- **Nada disso foi visto em aparelho ainda.** A b78 carrega imagem no modo leitura
  (I.2), a âncora da dobra e o recolhimento do sumário.
