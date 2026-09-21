# Resultado 11 — sugestão de wikilink enquanto digita (app Android)

Fecho do `HANDOFF_11_SUGESTAO_DE_WIKILINK.md`. A sessão que escreveu o código parou no
limite de uso logo depois de rodar a suíte, antes de consolidar o estado. Este documento
é a consolidação, com os números relidos dos XMLs da própria rodada — não do relato.

**Ponta:** `1a343f1` em `master`, **10 commits à frente do `origin/master` e não
empurrados** (a instrução da sessão era não empurrar: push dispara build no Actions).
Portanto **não há release nova**; a última publicada continua sendo a b78.

---

## 1. O que foi entregue, fatia por fatia

| Fatia do handoff | Estado | Onde |
|---|---|---|
| 3.1 Fonte pura de sugestões | entregue | `ui/component/markdown/WikilinkSupport.kt` |
| 3.2 Gatilho no editor | entregue | `ui/viewmodel/edit/MarkdownSmartEditor.kt` |
| 3.3 Popup ancorado no cursor | entregue (falta prova em aparelho) | `ui/screen/app/edit/WikilinkEditor.kt` |
| 3.4 Seções (`[[#` e `[[Nome#`) | entregue | `MarkDownVM.kt` + `WikilinkSupport.kt` |
| 3.5 Teclado físico (opcional) | entregue | `WikilinkEditor.kt:116-119` |

Pontos do contrato do §2 que estão cobertos por teste, e não só por leitura:

- ordena começa-com > contém > resto, ignorando caixa e acentos, sem fuzzy de letra solta;
- homônimo não é desempatado à força — mostra a pasta de cada candidato e mantém os dois
  (os 3 nomes repetidos do vault são ambíguos por construção);
- o gatilho recusa cerca de código, código inline, `![[`, fechamento, apelido e seleção,
  e não reaproveita uma abertura de outra linha;
- o aceite troca só o trecho digitado e escreve o texto do contrato (nome, sem `.md`);
- seção de outra nota é lida **sob demanda** e cacheada por versão — uma leitura por nota.

O botão Voltar fecha o popup antes de fechar a tela (`EditScreen.kt:142-145`), que era o
risco de perder edição apontado no §3.3.

## 2. Certificação

Rodada num worktree limpo da ponta commitada, para que nenhum arquivo local entrasse na
conta. Lido dos 57 XMLs de `app/build/test-results/testDebugUnitTest`:

**484 testes, 0 falhas, 0 erros, 0 ignorados** (57 suítes).

Desses, **13 nasceram neste handoff**: 5 em `WikilinkSuggestionTest`, 7 em
`WikilinkTriggerTest` e 1 em `WikilinkPerKeyPerfTest`.

## 3. Custo por tecla — o controle em JVM, e o que ele não é

`WikilinkPerKeyPerfTest` monta uma nota sintética de **340.992 bytes** (333 KB, o tamanho
da maior nota do vault), aquece 50 vezes e tira a mediana de 9 amostras de 200 repetições:

| | mediana |
|---|---|
| antes (só a edição) | 0,0004875 ms |
| depois (edição + caminho da sugestão) | 0,00062 ms |
| diferença | **+0,0001325 ms (+27,2%)** |

Duas ressalvas que precisam andar juntas com esse número:

1. **O percentual assusta e o absoluto não.** A base aqui é o caminho de decisão por
   tecla, que custa meio microssegundo; o custo por tecla que trava o editor é da ordem de
   dezenas de milissegundos (`RESULTADO_10_H2.md`). 0,13 µs é ruído perto disso.
2. **Isto não é a medição que o handoff exige.** É JVM de desktop, mede só o desvio do
   caminho da sugestão e não desenha um quadro. A medição válida é no mesmo aparelho, na
   nota real de 333 KB.

O commit `1a343f1` ("evitar alocação por tecla comum") entrou justamente para que a tecla
que não abre sugestão não pague alocação; antes dele a mesma medição dava 0,000737 →
0,0010385 ms.

## 4. O que continua pendente — e por quê

`adb` foi consultado: **nenhum aparelho conectado**. Ficam abertas exatamente as duas
provas que o handoff reserva a hardware real:

1. **Popup sobre o teclado virtual.** Nenhum teste desenha `Popup`, e o teclado é
   justamente o que cobre a tela. Medir: com o teclado aberto, o popup aparece inteiro e
   não tapa a linha digitada.
2. **Custo por tecla no aparelho**, na nota real de 333 KB, antes e depois.

Enquanto essas duas não forem feitas, **a fatia Android não está pronta** — o que está
pronto e provado é a lógica pura.

## 5. Auditoria dos commits (nenhum arquivo alheio entrou)

Os 9 commits de código do handoff tocam 12 arquivos, todos do assunto: `Dao.kt`,
`MarkdownLivePreviewTransformation.kt`, `WikilinkSupport.kt`, `EditScreen.kt`,
`MarkDown.kt`, `WikilinkEditor.kt`, `MarkDownVM.kt`, `MarkdownSmartEditor.kt` e os 4
arquivos de teste.

Havia trabalho local não commitado na árvore **antes e durante** esta sessão, de outra
frente (pedido 1 — tamanho da letra — e pedido 4 — colar imagem no app): `AppPreferences.kt`,
`SettingsScreen.kt`, `TamanhoDoTexto.kt`, `ColagemDeAnexo.kt`, `TamanhoDaLetraTest.kt` e as
duas `strings.xml`, além de trechos de `EditScreen.kt` e `MarkDown.kt`. **Nada disso entrou
nos commits** — verificado procurando esses símbolos nas linhas adicionadas do intervalo
`658c035..1a343f1`: zero ocorrências. O trabalho segue intacto na árvore, para a sessão dele.

**Achado de passagem, não consertado aqui:** `app/schemas/…RepoDatabase/3.json` está
sem rastreio. O `1.json` e o `2.json` são versionados, e a versão 3 do banco existe desde
`33576e1` (handoff 10 D) — ou seja, a exportação ficou de fora lá atrás e é regerada a cada
rodada de teste. É dívida do handoff 10, não desta sessão; corrigir é um commit de uma linha
na frente certa.

## 6. Ambiente temporário

O worktree de certificação `E:/Projetos/gitnote-cert-wt` foi criado só para rodar a suíte
longe dos arquivos locais. Conferido que não continha nada único (apenas o `3.json` gerado
e saída de build) e **removido**. `git worktree list` volta a mostrar só o repositório.

## 7. Próximo passo

Quando houver aparelho: instalar a ponta, fazer as duas medições do §4 e registrar aqui.
Se o custo por tecla subir no aparelho, a fatia volta — é a regressão que este editor já
teve duas vezes.
