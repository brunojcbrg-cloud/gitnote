package io.github.wiiznokes.gitnote.ui.screen.app.grid

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * Fase K.1: tocar numa pasta da grade navega para ela, e o item de subir um nivel volta
 * para a mae (criterio 3). Compoe os composables direto, como a Fase E fez com
 * `SumarioLateral` — `GridScreen`/`GridViewModel` nao sao componiveis neste repositorio
 * (`GitManager` carrega `git_wrapper` no init; ver ESTADO_10.md, divergencia da Fase A).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PastasNaGradeUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val itens = listOf(
        ItemDeNavegacao.SubirUmNivel(destino = "06_Conhecimento"),
        ItemDeNavegacao.Pasta(
            relativePath = "06_Conhecimento/Medicina/Materias Basicas",
            nome = "Materias Basicas",
            quantidadeDeNotas = 137,
        ),
    )

    @Test
    fun tocarNaPastaAbreOCaminhoDelaESubirUmNivelVoltaParaAMae() {
        val abertas = mutableListOf<String>()

        composeRule.setContent {
            Box(Modifier.width(375.dp).height(600.dp)) {
                Column {
                    itens.forEach { item ->
                        LinhaDeNavegacao(item = item, onAbrirPasta = { abertas.add(it) })
                    }
                }
            }
        }

        composeRule.onNodeWithText("Materias Basicas").performClick()
        composeRule.onNodeWithText("Up one level").performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf("06_Conhecimento/Medicina/Materias Basicas", "06_Conhecimento"),
                abertas,
            )
        }
    }

    @Test
    fun aPastaMostraAContagemDeNotasQueADrawerFoldersJaTraz() {
        composeRule.setContent {
            Box(Modifier.width(375.dp).height(600.dp)) {
                LinhaDeNavegacao(item = itens[1], onAbrirPasta = {})
            }
        }

        composeRule.onNodeWithText("137").assertExists()
    }

    @Test
    fun aMensagemDeVazioApareceNoLugarDaTelaEmBranco() {
        composeRule.setContent {
            Box(Modifier.width(375.dp).height(600.dp)) {
                GradeVazia()
            }
        }

        composeRule.onNodeWithText("This folder has no notes or subfolders").assertExists()
    }
}
