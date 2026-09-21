package io.github.wiiznokes.gitnote.ui.screen.app.edit

import androidx.compose.material3.Typography as TipografiaM3
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.isSpecified
import io.github.wiiznokes.gitnote.data.TamanhoDaLetra
import io.github.wiiznokes.gitnote.ui.theme.Typography
import io.github.wiiznokes.gitnote.ui.theme.escalar
import io.github.wiiznokes.gitnote.ui.theme.tipografiaEscalada
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * O pedido 1 de 21/09/2026: mudar o tamanho da letra das notas e dos titulos.
 *
 * O que estes testes trancam e a armadilha medida no handoff: `EDIT_LINE_HEIGHT_FACTOR`
 * e o `FastScrollLineOverlay` estimam a altura da linha como `baseFontSize * 1,5`.
 * Se a fonte crescer sem a altura de linha crescer junto, o arrasto do rolador leva
 * o dedo a uma linha e a tela a outra -- e ninguem percebe num teste de tela.
 */
class TamanhoDaLetraTest {

    /**
     * `EnumPreference` grava `value.name`. Renomear uma constante aqui perde a
     * preferencia ja gravada no aparelho; e o mesmo travamento de `PrazoDaTrava`.
     */
    @Test
    fun oTamanhoEhPersistidoPeloNomeDaConstante() {
        assertEquals(
            listOf("Pequena", "Media", "Grande", "Maior", "Maxima"),
            TamanhoDaLetra.entries.map { it.name },
        )
        assertEquals(
            listOf(0.85f, 1.0f, 1.2f, 1.4f, 1.6f),
            TamanhoDaLetra.entries.map { it.fator },
        )
    }

    /** Quem nunca abriu os Ajustes tem de ver exatamente a tela de antes. */
    @Test
    fun oPadraoNaoMexeEmNada() {
        assertEquals(1.0f, TamanhoDaLetra.Media.fator)
        assertSame(Typography, tipografiaEscalada(Typography, TamanhoDaLetra.Media.fator))
    }

    /**
     * A armadilha do rolador rapido, em todos os degraus: a altura de linha do
     * corpo continua sendo exatamente `fontSize * EDIT_LINE_HEIGHT_FACTOR`, que e a
     * conta que `MarkDownContent` faz para posicionar o arrasto.
     */
    @Test
    fun oRoladorRapidoContinuaAlinhadoEmTodosOsDegraus() {
        TamanhoDaLetra.entries.forEach { tamanho ->
            val corpo = tipografiaEscalada(Typography, tamanho.fator).bodyLarge
            assertEquals(
                TextUnitType.Sp,
                corpo.fontSize.type,
                "o tamanho da fonte precisa ser sp para a conta em px do rolador valer",
            )
            assertEquals(TextUnitType.Sp, corpo.lineHeight.type, tamanho.name)
            assertEquals(
                corpo.fontSize.value * EDIT_LINE_HEIGHT_FACTOR,
                corpo.lineHeight.value,
                0.001f,
                "degrau ${tamanho.name}: o rolador estima a linha como fontSize * $EDIT_LINE_HEIGHT_FACTOR",
            )
        }
    }

    /** E a razao ja tem de valer na tipografia de origem, senao o degrau 1,0 mente. */
    @Test
    fun aTipografiaDoAppJaObedeceAoFatorDeAlturaDeLinha() {
        val corpo = Typography.bodyLarge
        assertEquals(corpo.fontSize.value * EDIT_LINE_HEIGHT_FACTOR, corpo.lineHeight.value, 0.001f)
    }

    /**
     * O `scale` que ja existia em `markdownTypographyThemed` mexia so no corpo: os
     * seis niveis de titulo saiam de `MaterialTheme.typography` sem escala nenhuma.
     * O pedido fala de titulo tambem.
     */
    @Test
    fun oTituloCresceJuntoComOCorpo() {
        val normal = tipografiaEscalada(Typography, 1.0f)
        val maxima = tipografiaEscalada(Typography, 1.6f)
        val niveis: List<(TipografiaM3) -> TextStyle> = listOf(
            { it.headlineLarge }, { it.headlineMedium }, { it.headlineSmall },
            { it.titleLarge }, { it.titleMedium }, { it.titleSmall },
        )
        niveis.forEach { nivel ->
            assertEquals(
                nivel(normal).fontSize.value * 1.6f,
                nivel(maxima).fontSize.value,
                0.001f,
            )
        }
    }

    /** Escalar nao pode inventar valor onde a tipografia nao declarou nenhum. */
    @Test
    fun unidadeNaoDeclaradaContinuaNaoDeclarada() {
        assertTrue(!escalar(TextUnit.Unspecified, 1.6f).isSpecified)
        assertEquals(24f, escalar(12.sp, 2f).value, 0.001f)
    }
}
