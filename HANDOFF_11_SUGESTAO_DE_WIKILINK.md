# Handoff 11 — sugestão de wikilink enquanto digita (app Android)

Pedido do Bruno, 21/09/2026: *"quero que a tela da web tenha o mesmo mecanismo do
Obsidian, que eu coloco os colchetes e ele já sugere a nota que quero linkar, a nota
ou título da nota — e também quero isso no app do celular."*

A parte da web está em `E:\Projetos\notas-web\HANDOFF_09_SUGESTAO_DE_WIKILINK.md`.
**Os dois clientes têm de inserir texto idêntico**: a mesma nota abre no Obsidian, na
web e aqui, e divergir faz a nota parecer quebrada no outro lugar. O §2 abaixo é
cópia literal do contrato de lá — se você mudar algo nele, muda nos dois.

---

## 0. O que já está medido (não gaste sessão remedindo)

**O vault:** 141 notas em `06_Conhecimento`, 2.538 cabeçalhos, 2,1 MB de markdown,
maior nota 333 KB, nota com mais cabeçalhos 272, e **3 nomes de nota repetidos**
(`Microambiente Tumoral.md`, `Sem título.md`, `Sem título 1.md`). Existem 348
wikilinks, em 41 alvos distintos, um deles com 169 ocorrências.

**O que o app já tem:**

- **Room** (`data/room/RepoDatabase.kt`, `data/room/Dao.kt`) indexa as notas. A lista
  de nomes sai de uma consulta, **não** de varredura de disco.
- `ui/component/markdown/WikilinkSupport.kt` — `resolveWikilinkTargets`,
  `resolveSectionHeading`, `HeadingAnchor`. É o módulo puro que decide para onde um
  wikilink aponta. **A sugestão tem de concordar com ele**: sugerir algo que ele não
  resolve é prometer um link quebrado.
- `ui/viewmodel/edit/MarkdownSmartEditor.kt` (709 linhas) é onde já mora a edição
  inteligente — continuação automática de lista, tabelas. O gatilho `[[` é parente
  dessas regras e nasce aqui, não numa classe nova solta.
- `ui/screen/app/edit/EditScreen.kt` (455 linhas) hospeda o `BasicTextField`.
- `MarkdownScanner.kt` recusa `![[` de propósito (linhas 196-198), e é o mesmo
  scanner do modo de edição. Quem varre embed é `Anexos.kt`.

**A limitação da Fase J não bloqueia este trabalho.** `VisualTransformation` não
hospeda composable — é por isso que imagem no modo de edição está parada. A sugestão
**não é inline**: é um `Popup` ancorado ao cursor, desenhado por cima. O que você
precisa do campo é a posição do cursor, que vem do `TextLayoutResult`
(`getCursorRect` / `getBoundingBox`), não uma decoração dentro do texto.

---

## 1. A decisão que precede o código: de onde vêm os títulos

Nome de nota é barato: está no Room.

Título é onde a engenharia acontece — 2.538 cabeçalhos em 2,1 MB:

| caminho | custo | o que quebra |
|---|---|---|
| indexar todos os cabeçalhos numa tabela do Room | 2,1 MB lidos na sincronização, + migração de schema | o app **já tem custo por tecla medido** e travamento na lista; ver `RESULTADO_10_H2.md` |
| ler os cabeçalhos da nota alvo sob demanda, ao digitar `[[Nome#` | um `readText` por nota alvo, cacheado | primeira abertura do popup tem atraso |
| só seção da nota aberta (`[[#`) | zero | não atende "título de outra nota" |

Aqui o disco é local (`FileSystem.kt` usa `java.nio.Paths`, não SAF), então ler uma
nota é muito mais barato que na web. Mesmo assim: **nada de varrer 141 arquivos a
cada tecla**. Se você escolher indexar, indexe na sincronização, em
`Dispatchers.IO`, e meça o antes e o depois — o histórico deste app é de travar por
trabalho feito no lugar errado.

---

## 2. O contrato do texto inserido (idêntico ao da web)

1. **Insere nome, não caminho** — a resolução é por nome.
2. **Sem `.md`.**
3. **Seção com `#`:** `[[Medula Espinal#Substância cinzenta]]`; seção da nota aberta,
   `[[#Substância cinzenta]]`.
4. **Apelido com `|`** fica fora deste handoff.
5. **Os 3 nomes repetidos são ambíguos por construção.** Mostre a pasta de cada
   candidato e não finja que desempatou: o resolvedor não desempata por escolha do
   popup.
6. **Nada é inserido sozinho** — sem aceite automático, sem completar ao perder o
   foco.

---

## 3. Fatias (cada uma fecha sozinha)

**3.1 — Fonte pura de sugestões.** Kotlin puro, sem Compose e sem Android, ao lado de
`WikilinkSupport.kt`: recebe a lista de nomes (com pasta), o texto digitado depois de
`[[` e a nota atual; devolve candidatos ordenados (começa com > contém > resto;
empate desfeito pela pasta atual). Acentos e caixa ignorados. Testável em JVM.

**3.2 — O gatilho no editor.** Em `MarkdownSmartEditor.kt`: detectar `[[` aberto e
não fechado **na mesma linha**, à esquerda do cursor. Não disparar dentro de cerca de
código, e **não disparar depois de `!`** (`![[` é imagem — sugerir nota ali gera
embed quebrado; sugerir anexo é pedido separado, pergunte antes).

**3.3 — O popup.** `Popup` ancorado no `getCursorRect` do `TextLayoutResult`. Não
pode cobrir a linha digitada, e tem de sumir ao rolar. Toque insere; toque fora
fecha. Botão Voltar fecha o popup **antes** de fechar a tela — hoje ele fecha a tela,
e isso perderia edição.

**3.4 — Seções.** `[[#` lê os cabeçalhos do texto em edição (grátis, já está na
memória). `[[Nome#` conforme a decisão do §1, sempre fora da thread principal.

**3.5 — Teclado físico**, se houver: setas e Enter. Opcional; o alvo é o toque.

---

## 4. Como certificar

- `./gradlew test` para as fatias puras (3.1 e a varredura de cabeçalho). Hoje a
  bateria está em **448 testes verdes** (release b75); nenhum pode ficar vermelho.
- **Aparelho de verdade** para o popup: nenhum teste desenha `Popup` sobre o teclado
  virtual, e o teclado é exatamente o que cobre a tela. Meça: com o teclado aberto, o
  popup aparece inteiro e não tapa a linha digitada.
- Meça o **custo por tecla** antes e depois, no mesmo aparelho e na mesma nota grande
  (use a de 333 KB). Se subir, a fatia não está pronta — é a regressão que este app
  já teve duas vezes.

---

## 5. Armadilhas herdadas

- **Custo por tecla é o defeito crônico deste editor** (`RESULTADO_10_H2.md`): H.1 e
  H.2 não o resolveram. Qualquer trabalho por tecla — filtrar, ordenar, ler arquivo —
  entra medido ou não entra.
- **Marcador que cobre a linha inteira vira texto invisível** com o cursor deslocado
  (foi o caso da cerca de código).
- **`MarkdownScanner` recusa `![[` de propósito.** Não "conserte" isso de passagem.
- **Pasta vazia não existe no git**, então a pasta de um candidato só pode vir do
  caminho da nota.
- O app grava o arquivo direto; **as regras de salvamento não podem ser afrouxadas**
  para acomodar inserção de sugestão.
