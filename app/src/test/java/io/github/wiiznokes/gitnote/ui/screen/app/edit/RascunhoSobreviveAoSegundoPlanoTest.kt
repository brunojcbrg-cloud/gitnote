package io.github.wiiznokes.gitnote.ui.screen.app.edit

import java.io.File
import kotlin.test.Test
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
        assertTrue(
            Regex("NoteSaver\\.save\\(").findAll(viewModel).count() == 1,
            "NoteSaver.save so pode ser chamado de um lugar em TextVM",
        )
    }

    @Test
    fun aCondicaoDeGravarContinuaSendoADoDescarteExplicito() {
        // O rascunho não pode passar por cima de um descarte deliberado do usuário
        // (diálogo "sair sem salvar") nem gravar quando nada mudou.
        assertTrue(
            viewModel.contains("shouldSave = shouldSaveWhenQuitting && !isPreviousNoteTheSame()"),
            "a condicao de gravar o rascunho nao pode ser afrouxada",
        )
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
