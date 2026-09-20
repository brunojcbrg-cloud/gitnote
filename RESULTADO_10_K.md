# RESULTADO 10 — FASE K

Pastas navegáveis na grade (K.1) e a leva inicial de 10 recursiva (K.2).
Conserto da regressão da Fase B relatada em 20/09: pasta que só tem subpastas abria em branco.

Commits: `7d02ddf` (a fase) e `382aaf7` (conserto dos dois testes novos).
Release do CI: **b67 — 26.08.1.67**, run
[35535779960](https://github.com/brunojcbrg-cloud/gitnote/actions/runs/35535779960), verde.

---

## O que mudou

### K.1 — pasta é item da grade

- **`ui/screen/app/grid/PastasNaGrade.kt` (novo).** `ItemDeNavegacao` (`SubirUmNivel` |
  `Pasta`), o módulo puro `PastasNaGrade.itens(...)`/`mostrarVazio(...)` e os dois
  composables: `LinhaDeNavegacao` (ícone, nome, contagem) e `GradeVazia` (a mensagem).
- **`GridScreen.kt`.** `currentNoteFolderRelativePath` e `drawerFolders` sobem para o
  nível da tela e alimentam a gaveta **e** a grade. **Nenhuma consulta nova**: é o mesmo
  `StateFlow` que a gaveta já consumia, como a K.1 mandava.
- As pastas entram **acima das notas** nos dois modos: na grade escalonada como itens de
  linha inteira (`StaggeredGridItemSpan.FullLine`), na lista (`ListView.kt`) como linhas
  comuns. O item de **subir um nível** aparece no topo quando o caminho não é a raiz e usa
  o `getParentPath` que o FAB da gaveta já usava.
- **Teto de 10 não se aplica às pastas** — ele é das notas, e as pastas nem passam pela
  consulta paginada.
- **Durante a busca as pastas somem** (inclusive o item de subir), porque o resultado da
  busca é de notas e é recursivo.
- **Estado vazio de verdade:** sem pasta e sem nota, aparece "Esta pasta não tem notas nem
  subpastas". A mensagem espera o Paging terminar de carregar (`LoadState.Loading`) para
  não piscar.
- Strings novas em `values/strings.xml` e `values-pt-rBR/strings.xml`
  (`go_up_one_level`, `empty_folder`).

### K.2 — a leva de 10 volta a ser recursiva

- **`data/room/GradeSql.kt` (novo).** O texto do SQL da grade saiu do DAO. Filtro:
  `(:path = '' OR relativePath LIKE :path || '/%')` — **com a barra**, para `Medicina` não
  casar com `Medicina2/` nem com um arquivo `Medicina.md` solto ao lado. Ordem da Fase D
  (`MAX(lastOpenedTimeMillis, lastModifiedTimeMillis) DESC`), `LIMIT 10` na abertura.
- **`Dao.kt`.** `gridNotes` passa a montar a consulta por `GradeSql.notasDaPasta(...)`;
  `countNotesInFolder` ("Mostrar todas (N)") passa a contar **a pasta e as descendentes**,
  pelo mesmo filtro.
- **O ganho da Fase B continua de pé, e agora está provado por execução, não por leitura
  de texto:** a projeção tem 4 colunas (`relativePath, id, lastModifiedTimeMillis,
  1 AS isUnique`) e **nenhuma** é `content`; **não há função de janela**.
- Com a recursão, `AZ`/`ZA` voltaram a ordenar por `fullName(relativePath)` (nome do
  arquivo), como era antes da Fase B — ordenar por `relativePath` agruparia por pasta. As
  outras três ordens não usam função customizada.

### Três decisões tomadas com o Bruno antes de escrever código

1. **Base de comparação do PERF.** O `RESULTADO_10_B.md` **não tem** número de
   `PERF_GRID_QUERY` — ele registra, com todas as letras, que não foi escrito. Decidido:
   medir os três cenários da Fase K de verdade e registrar a ausência da base, sem
   inventar quarto número. Detalhe na seção de medição.
2. **Título do card.** Com listagem recursiva, duas notas de mesmo nome em subpastas
   diferentes apareceriam idênticas, e o desambiguador antigo (função de janela) não pode
   voltar. Decidido: o título passa a ser **o caminho a partir da pasta aberta**
   (`GridRow.tituloRelativoA`). Nota solta na própria pasta continua mostrando só o nome;
   nota de descendente aparece como `Subpasta/nota`. Custo zero — é recorte de string na
   tela.
3. **Contagem de notas por pasta.** O `JOIN` de `drawerFolders` usava
   `LIKE f.relativePath || '%'` **sem barra**: a pasta `Medicina` contava as notas de
   `Medicina2/` e até um `Medicina.md` ao lado. É o mesmo defeito que a K.2 proíbe de
   voltar, e o número passou a aparecer também na grade. Decidido: **corrigir junto**.

---

## Números `PERF_GRID_QUERY`

Consulta da grade na **raiz** do vault sintético de **4.309 notas somando 27 MB** (as
medidas do vault real, seção 1 do handoff). A primeira página é medida como o Room de
paginação a monta — `SELECT * FROM (<consulta>) LIMIT 50 OFFSET 0` — e o cursor é
percorrido até o fim. 5 amostras, mediana, run 35535779960.

| Cenário | Amostras (ms) | Mediana |
|---|---|---|
| Com teto de 10 | 3,178 / 2,333 / 1,612 / 1,445 / 1,338 | **1,612 ms** |
| Sem teto, primeira página (50 linhas) | 2,312 / 1,933 / 1,732 / 1,521 / 1,436 | **1,732 ms** |
| Contagem de "Mostrar todas" (4.309) | 0,546 / 0,145 / 0,123 / 0,117 / 0,116 | **0,123 ms** |
| **Fase B, para comparar** | **não existe** | — |

**O quarto número não existe, e isso não é descuido desta sessão.** O `RESULTADO_10_B.md`
diz: *"Não escrevi `PERF_GRID_QUERY`"* — porque a consulta da Fase B usava `parentPath()`,
função SQLite customizada que só carrega pelo requery e não roda na JVM do CI
(`UnsatisfiedLinkError`, medido na Fase A). A consulta anterior à Fase B, com `fullName()`
e função de janela, tinha o mesmo bloqueio. **A medição só passou a ser possível agora**:
o filtro recursivo da K.2 dispensou `parentPath()`, e na ordem padrão a consulta deixou de
usar qualquer função customizada.

**A primeira página sem teto ficou em 1,7 ms — muito abaixo dos ~100 ms que o handoff pôs
como gatilho de índice.** Nenhum índice novo é necessário, e nada foi improvisado. Vale a
ressalva de sempre (ESTADO_10.md, H.2): o runner do CI é ruidoso e a dispersão aqui é de
1,3 a 3,2 ms; o que sustenta a conclusão não é a mediana exata, é a ordem de grandeza —
três consultas na casa de 1 ms sobre 4.309 notas e 27 MB.

---

## Critério de aceitação, item por item

1. **`06_Conhecimento` mostra 6 pastas e as 10 notas mais recentes das descendentes.**
   Medido hoje no vault, com os três caminhos do critério:

   | Pasta | Subpastas | Notas diretas | Notas recursivas (a grade mostra as 10 primeiras) |
   |---|---|---|---|
   | `06_Conhecimento` | **6** | **0** | 140 |
   | `06_Conhecimento/Medicina` | **5** | **0** | 132 |
   | `06_Conhecimento/Medicina/Matérias Básicas` | **10** | **0** | 123 |

   Os três batem exatamente com o handoff. `PastasNaGradeTest` reproduz os três casos
   (6, 5 e 10 itens de pasta) e `GradeSqlTest.pastaSemNotaDiretaDeixaDeVoltarVazia` prova
   pelo SQL que uma pasta sem nota direta deixou de devolver zero linhas.
2. **Árvore sintética da Fase B:** `GradeSqlTest.abrirUmaPastaTrazAsNotasDelaEDasDescendentes`
   (abrir `A` = 12 = 5 de `A` + 7 de `A/B`), `oTetoDeDezCortaAListagemRecursiva` (10 com
   teto) e `abrirUmaPastaNaoTrazNadaDaIrmaDeNomeParecido` (nada de `A2`). **Executados no
   SQLite de verdade**, não conferidos como texto.
3. **Tocar numa pasta navega; subir um nível volta para a mãe:**
   `PastasNaGradeUiTest.tocarNaPastaAbreOCaminhoDelaESubirUmNivelVoltaParaAMae` (Robolectric,
   clique de verdade nos dois itens, na ordem) e `PastasNaGradeTest.oItemDeSubirUmNivelVoltaParaAMae`.
4. **Buscando, as pastas somem e o resultado segue recursivo:**
   `PastasNaGradeTest.buscandoAsPastasSomem` (lista vazia com `query` preenchida) e
   `GridQuerySqlTextTest.gridNotesWithQueryContinuaRecursiva...` (a busca mantém o `LIKE`
   recursivo com a barra e o desambiguador, intocada nesta fase).
5. **Pasta sem nada mostra a mensagem:** `PastasNaGradeTest.pastaSemNadaMostraAMensagemDeVazio`
   e `PastasNaGradeUiTest.aMensagemDeVazioApareceNoLugarDaTelaEmBranco`.
6. **`PERF_GRID_QUERY` nos três cenários:** tabela acima.
7. **Testes das Fases B e D verdes**, em especial os dois que o critério cita:
   - projeção sem conteúdo → `GridRowTest.naoTemCampoDeConteudo`,
     `GridQuerySqlTextTest.gridNotesNaoTemMaisFuncaoDeJanelaNemColunaDeConteudo` e, novo e
     mais forte, `GradeSqlTest.aProjecaoContinuaSemAColunaDeConteudo`, que lê os nomes das
     colunas do cursor numa nota de 336 KB;
   - carimbo de abertura sobrevive à reindexação →
     `HistoricoDurabilidadeTest.aberturaSobreviveAReindexacaoEOrdenaComDataDeModificacao`,
     intocado e verde.
   **369 testes no log do CI, todos `PASSED`.**

---

## Testes escritos (23 novos)

| Teste | O que prova |
|---|---|
| `GradeSqlTest` (9) | **Executa o SQL de produção num SQLite de verdade**: pasta traz descendentes; `A2` não entra; `Medicina` não casa com `Medicina2/` nem com `Medicina.md`; teto de 10 corta e `null` solta; raiz vê o vault inteiro; pasta só com subpastas devolve linhas; a projeção tem 4 colunas e nenhuma é conteúdo; sem função de janela; a ordem da Fase D vale entre descendentes |
| `GridQueryPerfTest` (1) | `PERF_GRID_QUERY` nos três cenários, com 4.309 notas / 27 MB; afirma também que a primeira página tem 10 com teto e 50 sem |
| `PastasNaGradeTest` (8) | O módulo puro: 6/5/10 pastas nos três caminhos reais; subir um nível vai para a mãe; na raiz não há item de subir; buscando some tudo; vazio aparece só quando não há pasta nem nota e o Paging já carregou; chaves estáveis e únicas |
| `PastasNaGradeUiTest` (3) | Robolectric: clicar na pasta e no "subir um nível" chama `openFolder` com o caminho certo, na ordem; a contagem aparece; a mensagem de vazio aparece |
| `GridRowTest` (+2) | `tituloRelativoA`: nome puro na própria pasta, `Subpasta/nota` na descendente, caminho inteiro na raiz; arquivo sem extensão não perde letra |

Atualizados (a Fase K reverte de propósito o filtro que eles guardavam):
`GridQuerySqlTextTest` e `GradeFaseDSqlTest` passaram a verificar o **construtor**
`GradeSql` em vez do texto de `Dao.kt`, mantendo as duas proibições da Fase B (sem
`content`, sem `COUNT(*) OVER`) e trocando `parentPath(...) = :path` pelo `LIKE` com
barra. `NoteFolderFilterLogicTest` teve o cabeçalho e as mensagens corrigidos: a lógica
de pasta exata que ele exercita agora sustenta `drawerFolders`/`noteFolders` (as subpastas
diretas, inclusive as que a K.1 põe na grade), não mais a listagem de notas.

---

## O que não foi feito e por quê

- **Não medi a consulta da Fase B para comparar.** Ela não roda neste CI (`parentPath`),
  e a alternativa seria reescrever `parentPath` em SQL puro só para o teste — um número
  com asterisco, que não é o custo da função real. Decisão do Bruno nesta sessão.
- **Não toquei em `gridNotesWithQuery`** (a busca). Já era recursiva e já tinha a barra
  desde a Fase B.
- **Não construí `GridViewModel` nem compus `GridScreen` de verdade** — mesmo bloqueio de
  sempre (`GitManager` carrega `git_wrapper` no `init`; ESTADO_10.md, Fase A). A decisão
  de quais itens aparecem foi extraída para um módulo puro e os composables foram
  compostos direto, como as Fases E e F fizeram.
- **Não mexi em `showFullNoteHeight`/`noteMinWidth`**, nem no trabalho de segurança não
  commitado (8 arquivos + `PortaoDeSeguranca.kt` e o teste dele continuam exatamente como
  estavam; o `git status` depois dos dois commits mostra as mesmas linhas de antes).
- **Não entrei em `E:/Projetos/gitnote-pos`** nem encostei em `MarkDown.kt`/`MarkDownVM.kt`.
- **Não comecei a Fase G.**

---

## Divergências e observações

1. **A base de PERF da Fase B não existe** (ver acima). Perguntei antes de agir; a decisão
   está registrada aqui e no `ESTADO_10.md`.
2. **Duas consequências da recursão que o handoff não previa** foram decididas com ele
   antes do código: o título do card e o `JOIN` da contagem. As duas estão no topo deste
   relatório.
3. **A contagem de pastas do vault mudou desde a manhã.** O handoff registra 1.598 pastas
   e 513 abrindo em branco; a varredura de hoje à noite (excluindo pastas ocultas) deu
   **1.952 pastas e 548 abrindo em branco**. É retrato do momento, como já aconteceu com a
   contagem de títulos na Fase E — e as três pastas do critério de aceitação batem
   exatamente. O tamanho do estrago era maior do que o handoff registrou, não menor.
4. **O primeiro push ficou vermelho** (run
   [35535567673](https://github.com/brunojcbrg-cloud/gitnote/actions/runs/35535567673)) e o
   motivo **não era o SQL**: o Room barra `query()` direto na thread principal
   (`assertNotMainThread`), e no Robolectric o teste roda nela. Os 9 testes novos que
   executam SQL falharam por isso; os outros 360 passaram. `allowMainThreadQueries()` nos
   dois bancos em memória resolveu (`382aaf7`) — mudança só de teste. O caminho foi o que a
   Fase F deixou escrito: baixar o artefato `test-report-<n>`, que traz a exceção
   completa, em vez de adivinhar pelo log do job.

---

## Linha para o `ESTADO_10.md`

| K | entregue | 2026-09-20 | 382aaf7 | b67 (26.08.1.67) |
