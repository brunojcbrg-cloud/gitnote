# RESULTADO 10 — FASE C

## O que mudou

**Preferência nova.** `AppPreferences.pastaPadrao` (`stringPreference("pastaPadrao", "")`)
guarda a pasta relativa que a grade deve abrir no início, ao lado de
`defaultPathForNewNote`.

**Decisão pura e testável.** `ui/viewmodel/PastaInicial.kt` — objeto puro,
`escolher(rememberLastOpenedFolder, lastOpenedFolder, pastaPadrao): String` — encapsula
exatamente a prioridade pedida pelo Bruno: lembrar a última pasta aberta vence a pasta
padrão, que vence a raiz (`""`). `GridViewModel._currentNoteFolderRelativePath` passou a
nascer com `PastaInicial.escolher(...)` em vez do `if` embutido de antes.

**Guarda contra pasta inexistente.** `Dao.isFolderExist(relativePath)` — `SELECT
EXISTS(...)` sobre `NoteFolders`, espelhando `isNoteExist`. No `init` do
`GridViewModel`, se a pasta inicial escolhida não é a raiz, uma corrotina
(`viewModelScope.launch`) confere `dao.isFolderExist`; se não existir, cai para `""` e
mostra o toast `default_startup_folder_not_found`. A checagem é assíncrona e não bloqueia
a construção do VM nem a abertura da tela — é exatamente o caso da Parte V (renomear
`06_Conhecimento` para `NOTAS`) com a preferência do celular ainda apontando para o nome
velho.

**Configurações.** Nova entrada "Pasta padrão ao iniciar" logo abaixo de "Default path
for new notes" (`SettingsScreen.kt`), reaproveitando o mesmo `PickFolderDialog` — inclui
botão "Limpar" que só aparece quando a preferência não está vazia (volta para `""` =
raiz).

**Navegação até a raiz.** Não mexi em `DrawerScreen`/`RowNFoldersNavigation` além de
adicionar um item novo: quando `pastaPadrao != ""` e a pasta atual é diferente dela, a
gaveta ganha "Voltar à pasta padrão" no topo da lista, que chama `openFolder(pastaPadrao)`
— o botão Início (`openFolder("")`), o FAB de subir um nível e a trilha clicável
continuam existindo e levando à raiz sem qualquer restrição nova.

Nenhum arquivo do trabalho de segurança não commitado foi tocado (ver "Como o commit foi
feito" abaixo — `AppPreferences.kt` e `SettingsScreen.kt` estavam nessa lista e exigiram
cuidado extra).

## Números PERF_

Não há PERF_ nesta fase — o critério de aceitação de C não pede medição, e nada aqui
toca o caminho de tecla do editor ou a consulta pesada da grade (essa é a Fase B/D).

## Testes escritos

1. **`PastaInicialTest`** (`ui/viewmodel/`) — 5 casos cobrindo a prioridade inteira:
   sem pasta padrão e sem lembrar → raiz; com pasta padrão e sem lembrar → pasta padrão;
   lembrando com última pasta preenchida → última pasta (vence a padrão); lembrando mas
   sem última pasta → cai para a padrão; lembrando sem última pasta nem padrão → raiz.
   Não depende de `MyApp.appModule`/`GitManager` — testa só a função pura.
2. **`IsFolderExistTest`** (`data/room/`) — Room em memória (SQLite padrão, sem
   `RepoDatabase.buildFactory`, mesma ressalva de `NotesContainingTagPerfTest`; a
   consulta não usa nenhuma das 4 funções customizadas do requery) prova que
   `isFolderExist` acha uma pasta inserida e não acha uma pasta ausente — é literalmente
   o caso "Parte V renomeou a pasta e a preferência do celular ficou apontando para o
   nome velho".

## O que não foi feito e por quê

- **Não constrói `GridViewModel` de verdade em teste** para provar
  `currentNoteFolderRelativePath.value` ponta a ponta com a preferência real — mesmo
  bloqueio das Fases A/B (`GitManager`/`StorageManager` concretos, carregam lib nativa
  no `init`, não rodam na JVM). Por isso extraí `PastaInicial.escolher` como função pura
  e testei ela sozinha; é a mesma decisão que o `GridViewModel` usa de fato
  (`GridViewModel.kt:63-68`), só que fora do caminho que trava o teste. Registrando aqui
  a mesma divergência, sem pedir para o Bruno decidir de novo — já é o padrão aceito nas
  Fases A e B.
- Não criei preferência nova para "incluir subpastas" nem toquei em nada de D/E/F/G/H/J.
- Não toquei na Parte V (renomeação) — ela é sessão isolada, e a guarda de "pasta padrão
  inexistente" desta fase é o que a torna segura quando vier.

## Como o commit foi feito (trabalho de segurança preservado)

`AppPreferences.kt` e `SettingsScreen.kt` já estavam com trabalho de segurança não
commitado (regra 9 da seção 0 do handoff). Minhas mudanças nesses dois arquivos foram
isoladas do resto (uma linha nova em `AppPreferences.kt`; um bloco de UI novo em
`SettingsScreen.kt`, sem tocar nas linhas de segurança) e comitadas separadamente:
reconstruí uma versão de cada arquivo a partir do `HEAD` + só as minhas linhas, comitei
essa versão, e devolvi o arquivo completo (fase C + segurança) para a árvore de trabalho
logo em seguida — sem `git stash`/`checkout`/`reset` que arriscasse descartar algo. O
`git diff` de cada arquivo depois disso mostra exatamente as mesmas linhas de segurança
de antes, inalteradas e ainda não commitadas.

## Divergência: um bug de import pego pelo CI, não pelo handoff

O primeiro push (`f72eb8e`) quebrou o `compileDebugKotlin`: `import
androidx.compose.foundation.lazy.item` não existe como símbolo importável — `item(...)`
é método de `LazyListScope`, disponível de graça dentro do bloco do `LazyColumn`, sem
import (é exatamente como `GridScreen.kt` já usa `item(span = ...)` na grade, sem
importar nada). Corrigido no commit seguinte (`3b41b45`), removendo a linha de import.
Não é divergência do handoff — é erro meu, registrado aqui porque o handoff pede não
"corrigir e seguir em silêncio": o CI ficou vermelho por 1m42s antes do conserto, com
evidência no run 35516675734.

## Release do CI

Push 1 (`f72eb8e`) — **CI vermelho**: run
[35516675734](https://github.com/brunojcbrg-cloud/gitnote/actions/runs/35516675734),
`compileDebugKotlin` falhou por causa do import de `item` (ver acima).

Push 2 (`3b41b45`, o conserto) — **CI verde**: run
[35516861531](https://github.com/brunojcbrg-cloud/gitnote/actions/runs/35516861531),
release **b55 (26.08.1.55)**, publicada em 2026-09-20T14:37:28Z. Os 6 testes novos
(`PastaInicialTest` × 5, `IsFolderExistTest` × 1) aparecem no log, todos `PASSED`;
nenhum teste existente ficou vermelho.
