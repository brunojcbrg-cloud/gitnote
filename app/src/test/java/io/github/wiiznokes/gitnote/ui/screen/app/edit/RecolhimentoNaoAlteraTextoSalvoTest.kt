package io.github.wiiznokes.gitnote.ui.screen.app.edit

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * F.2: o recolhimento (`Dobra.kt`) só transforma o texto que é entregue para
 * `MarkdownCustomInner` no modo leitura. `GenericTextField` (edição) e o botão de
 * salvar continuam recebendo o `textContent`/`vm.content` original, nunca
 * `conteudoExibido`/`textoDobrado`. Não dá para provar isso instanciando `MarkDownVM`
 * de verdade neste repositório (bloqueio de `GitManager`/`git_wrapper`, Fases A/C/E);
 * a prova aqui é estrutural, lendo o código-fonte.
 */
class RecolhimentoNaoAlteraTextoSalvoTest {

    private val raizModulo = File(System.getProperty("user.dir") ?: ".")

    private fun lerFonte(caminhoRelativo: String): String {
        val arquivo = File(raizModulo, caminhoRelativo)
        check(arquivo.exists()) { "arquivo nao encontrado: ${arquivo.absolutePath}" }
        return arquivo.readText()
    }

    @Test
    fun modoEdicaoNuncaUsaOTextoDobrado() {
        val fonte = lerFonte("src/main/java/io/github/wiiznokes/gitnote/ui/screen/app/edit/MarkDown.kt")
        // "    } else {" no topo da função (4 espaços) é o único fecho do
        // `if (isReadOnlyModeActive) { ... } else { ... }` de MarkDownContent -- os
        // "} else {" aninhados (temas, etc.) vêm com mais indentação e não casam aqui.
        val marcador = "\n    } else {\n"
        val inicioModoEdicao = fonte.indexOf(marcador)
        check(inicioModoEdicao >= 0) { "nao achei o ramo do modo edicao" }
        val modoEdicao = fonte.substring(inicioModoEdicao + marcador.length)

        assertFalse(
            modoEdicao.contains("conteudoExibido") || modoEdicao.contains("textoDobrado"),
            "o modo edicao nao pode renderizar nem salvar o texto dobrado",
        )
        assertTrue(
            modoEdicao.contains("GenericTextField(") && modoEdicao.contains("textContent = textContent"),
            "GenericTextField continua recebendo o textContent original",
        )
    }

    @Test
    fun conteudoExibidoSoAlimentaOMarkdownCustomInnerDoModoLeitura() {
        val fonte = lerFonte("src/main/java/io/github/wiiznokes/gitnote/ui/screen/app/edit/MarkDown.kt")

        assertEquals(
            1,
            Regex("content = conteudoExibido").findAll(fonte).count(),
            "conteudoExibido so pode entrar em MarkdownCustomInner",
        )
    }
}
