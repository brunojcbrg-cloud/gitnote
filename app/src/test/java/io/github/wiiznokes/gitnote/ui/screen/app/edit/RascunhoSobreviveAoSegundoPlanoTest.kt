package io.github.wiiznokes.gitnote.ui.screen.app.edit

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Relato do Bruno em 21/09/2026: sair do app para pesquisar no navegador e voltar
 * fechava a nota, devolvia a lista de notas e perdia tudo o que tinha sido digitado.
 *
 * A causa é que o rascunho só era gravado em `TextVM.onCleared`, e `onCleared` não
 * roda quando o sistema mata o processo com o app em segundo plano. `ON_STOP` é o
 * último instante que o Android garante, então é lá que o rascunho tem de ser
 * gravado — e `AppScreen` já sabe ressuscitar a edição a partir dele
 * (`NoteSaver.isEditUnsaved`).
 *
 * Não dá para instanciar `TextVM` de verdade neste repositório (`GitManager` carrega
 * `git_wrapper`) nem exercitar o ciclo de vida do Compose num teste de JVM; a prova
 * aqui é estrutural, no mesmo molde de `RecolhimentoNaoAlteraTextoSalvoTest`.
 */
class RascunhoSobreviveAoSegundoPlanoTest {

    private val raizModulo = File(System.getProperty("user.dir") ?: ".")

    private fun lerFonte(caminhoRelativo: String): String {
        val arquivo = File(raizModulo, caminhoRelativo)
        check(arquivo.exists()) { "arquivo nao encontrado: ${arquivo.absolutePath}" }
        return arquivo.readText().replace("\r\n", "\n")
    }

    private val telaDeEdicao by lazy {
        lerFonte("src/main/java/io/github/wiiznokes/gitnote/ui/screen/app/edit/EditScreen.kt")
    }

    private val viewModel by lazy {
        lerFonte("src/main/java/io/github/wiiznokes/gitnote/ui/viewmodel/edit/TextVM.kt")
    }

    @Test
    fun aTelaDeEdicaoGravaORascunhoAoIrParaSegundoPlano() {
        assertTrue(
            telaDeEdicao.contains("Lifecycle.Event.ON_STOP") &&
                telaDeEdicao.contains("vm.guardarRascunho()"),
            "EditScreen precisa gravar o rascunho em ON_STOP",
        )
        assertTrue(
            telaDeEdicao.contains("lifecycleOwner.lifecycle.addObserver(") &&
                telaDeEdicao.contains("lifecycleOwner.lifecycle.removeObserver("),
            "o observador de ciclo de vida tem de ser removido no onDispose",
        )
    }

    @Test
    fun onClearedEAOStopGravamPelaMesmaPorta() {
        assertTrue(
            Regex("fun guardarRascunho\\(\\)").containsMatchIn(viewModel),
            "TextVM precisa expor guardarRascunho()",
        )
        // Uma porta só: se `onCleared` voltar a montar a chamada de NoteSaver por
        // conta própria, as duas saídas podem divergir na condição de gravar.
        assertTrue(
            Regex("override fun onCleared\\(\\) \\{\\s*guardarRascunho\\(\\)\\s*\\}")
                .containsMatchIn(viewModel),
            "onCleared tem de delegar para guardarRascunho()",
        )
        // São dois usos de NoteSaver e só dois: `guardarRascunho`, que grava, e
        // `confirmarEscrita`, que só apaga. Um terceiro lugar montando a chamada
        // por conta própria é como as condições divergem.
        assertEquals(
            2,
            Regex("NoteSaver\\.save\\(").findAll(viewModel).count(),
            "NoteSaver.save so pode aparecer em guardarRascunho e confirmarEscrita",
        )
        val confirmacao = viewModel.substringAfter("private fun confirmarEscrita()")
            .substringBefore("fun save(")
        assertTrue(
            confirmacao.contains("shouldSave = false"),
            "confirmarEscrita so pode apagar rascunho, nunca gravar um",
        )
    }

    @Test
    fun aCondicaoDeGravarContinuaSendoADoDescarteExplicito() {
        // O rascunho não pode passar por cima de um descarte deliberado do usuário
        // (diálogo "sair sem salvar"), e não grava quando nada mudou — salvo
        // enquanto uma escrita disparada ainda não confirmou.
        val condicao = viewModel.substringAfter("fun guardarRascunho()").take(400)
        assertTrue(
            condicao.contains("shouldSaveWhenQuitting &&"),
            "o descarte explicito continua mandando no rascunho",
        )
        assertTrue(
            condicao.contains("!isPreviousNoteTheSame()"),
            "texto igual ao do disco nao vira rascunho",
        )
    }

    /**
     * Relato do Bruno em 21/09/2026, com a b81 instalada: o rascunho sobreviveu a
     * sair e voltar várias vezes, mas o **botão de salvar** devolveu a pasta raiz,
     * apagou o texto e não salvou nada.
     *
     * A gravação era destrutiva por construção: `applyNoteUpdate` apagava o arquivo
     * antigo, criava o novo e só então escrevia o texto. Falhar no `create` apagava
     * a nota; falhar no `write` deixava um arquivo vazio. Como o botão dá a edição
     * por salva antes de a escrita acontecer, o rascunho era apagado junto — e o
     * texto deixava de existir em qualquer lugar.
     */
    @Test
    fun aEscritaAconteceAntesDeQualquerCoisaSerApagada() {
        val armazenamento =
            lerFonte("src/main/java/io/github/wiiznokes/gitnote/manager/StorageManager.kt")
        val inicio = armazenamento.indexOf("private suspend fun applyNoteUpdate")
        check(inicio >= 0) { "nao achei applyNoteUpdate" }
        val corpo = armazenamento.substring(inicio, armazenamento.indexOf("suspend fun createNote"))

        val escrita = corpo.indexOf("newFile.write(")
        val remocao = corpo.indexOf("previousFile.delete()")
        check(escrita >= 0 && remocao >= 0) { "nao achei a escrita ou a remocao" }
        assertTrue(
            escrita < remocao,
            "o texto novo tem de estar em disco antes de o arquivo antigo sair",
        )
        assertTrue(
            corpo.contains("if (!mesmoArquivo) {"),
            "salvar sem mudar de caminho nao pode apagar arquivo nenhum",
        )
        assertTrue(
            corpo.contains("if (!newFile.exist()) {"),
            "arquivo que ja existe e sobrescrito, nao recriado vazio",
        )
    }

    /** O rascunho só pode ser apagado depois que a escrita foi confirmada. */
    @Test
    fun oRascunhoSobreviveAoSalvarAteAEscritaConfirmar() {
        assertTrue(
            viewModel.contains(
                "shouldSave = shouldSaveWhenQuitting &&\n" +
                    "                (!isPreviousNoteTheSame() || !escritaConfirmada)"
            ),
            "enquanto a escrita nao confirma, o rascunho continua valendo",
        )
        assertTrue(
            Regex("escritaConfirmada = false").containsMatchIn(viewModel),
            "salvar tem de marcar a escrita como nao confirmada",
        )
        // A confirmação só pode vir depois do sucesso de quem escreve de verdade.
        listOf("storageManager.updateNote", "storageManager.createNote").forEach { chamada ->
            val posicao = viewModel.indexOf(chamada)
            check(posicao >= 0) { "nao achei $chamada" }
            val confirmacao = viewModel.indexOf("confirmarEscrita()", posicao)
            assertTrue(
                confirmacao > posicao,
                "$chamada precisa confirmar a escrita depois do sucesso",
            )
        }
    }

    @Test
    fun aVoltaDoAppAindaRessuscitaAEdicaoNaoSalva() {
        val navegacao = lerFonte("src/main/java/io/github/wiiznokes/gitnote/ui/screen/app/AppNav.kt")
        assertTrue(
            navegacao.contains("NoteSaver.isEditUnsaved()") &&
                navegacao.contains("EditParams.Saved("),
            "a pilha inicial tem de reabrir a nota quando existe rascunho",
        )
    }
}
