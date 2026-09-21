package io.github.wiiznokes.gitnote.ui.screen.app.edit

import androidx.compose.material3.Typography as TipografiaM3
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.isSpecified
import io.github.wiiznokes.gitnote.ui.theme.TAMANHO_DA_LETRA_MAXIMO
import io.github.wiiznokes.gitnote.ui.theme.TAMANHO_DA_LETRA_MINIMO
import io.github.wiiznokes.gitnote.ui.theme.TAMANHO_DA_LETRA_PADRAO
import io.github.wiiznokes.gitnote.ui.theme.TAMANHO_DA_LETRA_PASSO
import io.github.wiiznokes.gitnote.ui.theme.Typography
import io.github.wiiznokes.gitnote.ui.theme.aumentarTamanhoDaLetra
import io.github.wiiznokes.gitnote.ui.theme.diminuirTamanhoDaLetra
import io.github.wiiznokes.gitnote.ui.theme.escalar
import io.github.wiiznokes.gitnote.ui.theme.fatorDaLetra
import io.github.wiiznokes.gitnote.ui.theme.limitarTamanhoDaLetra
import io.github.wiiznokes.gitnote.ui.theme.tipografiaEscalada
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * O pedido 1 de 21/09/2026, na forma que o Bruno pediu depois de ver a primeira
 * versao: dois botoes (+ e -) na propria nota, e o que ele escolher vira o padrao
 * de todas as notas.
 *
 * O que estes testes trancam e a armadilha medida no handoff: `EDIT_LINE_HEIGHT_FACTOR`
 * e o `FastScrollLineOverlay` estimam a altura da linha como `baseFontSize * 1,5`.
 * Se a fonte crescer sem a altura de linha crescer junto, o arrasto do rolador leva
 * o dedo a uma linha e a tela a outra -- e ninguem percebe num teste de tela.
 */
class TamanhoDaLetraTest {

    private val degraus: List<Int> =
        (TAMANHO_DA_LETRA_MINIMO..TAMANHO_DA_LETRA_MAXIMO step TAMANHO_DA_LETRA_PASSO).toList()

    /** Quem nunca tocou nos botoes tem de ver exatamente a tela de antes. */
    @Test
    fun oPadraoNaoMexeEmNada() {
        assertEquals(1.0f, fatorDaLetra(TAMANHO_DA_LETRA_PADRAO))
        assertSame(Typography, tipografiaEscalada(Typography, fatorDaLetra(TAMANHO_DA_LETRA_PADRAO)))
    }

    /** Um toque, um passo -- e o valor nunca sai da grade nem da faixa. */
    @Test
    fun cadaToqueAndaUmPasso() {
        assertEquals(110, aumentarTamanhoDaLetra(100))
        assertEquals(90, diminuirTamanhoDaLetra(100))
        assertEquals(
            degraus,
            generateSequence(TAMANHO_DA_LETRA_MINIMO) { anterior ->
                aumentarTamanhoDaLetra(anterior).takeIf { it != anterior }
            }.toList(),
            "subir do minimo ao maximo tem de passar por todos os degraus, sem repetir",
        )
    }

    /** O botao para no fim da faixa em vez de guardar valor absurdo. */
    @Test
    fun osExtremosSeguram() {
        assertEquals(TAMANHO_DA_LETRA_MAXIMO, aumentarTamanhoDaLetra(TAMANHO_DA_LETRA_MAXIMO))
        assertEquals(TAMANHO_DA_LETRA_MINIMO, diminuirTamanhoDaLetra(TAMANHO_DA_LETRA_MINIMO))
        assertEquals(TAMANHO_DA_LETRA_MAXIMO, limitarTamanhoDaLetra(10_000))
        assertEquals(TAMANHO_DA_LETRA_MINIMO, limitarTamanhoDaLetra(-3))
    }

    /**
     * Valor fora da grade (preferencia antiga, arquivo editado a mao) nao pode
     * virar um degrau meio-termo que os botoes nunca mais alcancam.
     */
    @Test
    fun valorForaDaGradeEhTrazidoParaODegrauMaisProximo() {
        assertEquals(110, limitarTamanhoDaLetra(113))
        assertEquals(120, limitarTamanhoDaLetra(117))
        assertTrue(degraus.contains(limitarTamanhoDaLetra(137)))
    }

    /**
     * A armadilha do rolador rapido, em todos os degraus: a altura de linha do
     * corpo continua sendo exatamente `fontSize * EDIT_LINE_HEIGHT_FACTOR`, que e a
     * conta que `MarkDownContent` faz para posicionar o arrasto.
     */
    @Test
    fun oRoladorRapidoContinuaAlinhadoEmTodosOsDegraus() {
        degraus.forEach { porcentagem ->
            val corpo = tipografiaEscalada(Typography, fatorDaLetra(porcentagem)).bodyLarge
            assertEquals(
                TextUnitType.Sp,
                corpo.fontSize.type,
                "o tamanho da fonte precisa ser sp para a conta em px do rolador valer",
            )
            assertEquals(TextUnitType.Sp, corpo.lineHeight.type, "$porcentagem%")
            assertEquals(
                corpo.fontSize.value * EDIT_LINE_HEIGHT_FACTOR,
                corpo.lineHeight.value,
                0.001f,
                "degrau $porcentagem%: o rolador estima a linha como fontSize * $EDIT_LINE_HEIGHT_FACTOR",
            )
        }
    }

    /** E a razao ja tem de valer na tipografia de origem, senao o degrau 100% mente. */
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
        val normal = tipografiaEscalada(Typography, fatorDaLetra(100))
        val maior = tipografiaEscalada(Typography, fatorDaLetra(160))
        val niveis: List<(TipografiaM3) -> TextStyle> = listOf(
            { it.headlineLarge }, { it.headlineMedium }, { it.headlineSmall },
            { it.titleLarge }, { it.titleMedium }, { it.titleSmall },
        )
        niveis.forEach { nivel ->
            assertEquals(
                nivel(normal).fontSize.value * 1.6f,
                nivel(maior).fontSize.value,
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
