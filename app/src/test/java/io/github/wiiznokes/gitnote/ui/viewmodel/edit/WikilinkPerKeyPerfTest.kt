package io.github.wiiznokes.gitnote.ui.viewmodel.edit

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.system.measureNanoTime
import org.junit.Assert.assertTrue
import org.junit.Test

class WikilinkPerKeyPerfTest {
    private val bytesAlvo = 333 * 1024
    private val notaGrande = buildString(bytesAlvo + 64) {
        while (length < bytesAlvo) append("linha comum da nota grande sem gatilho de sugestao\n")
    }.take(bytesAlvo)

    @Test
    fun medeCustoPorTeclaAntesEDepoisDaSugestaoNaMesmaNotaDe333Kb() {
        val anterior = TextFieldValue(notaGrande, TextRange(notaGrande.length))
        val digitado = TextFieldValue(notaGrande + "x", TextRange(notaGrande.length + 1))

        repeat(50) {
            medirAntes(anterior, digitado, 1)
            medirDepois(anterior, digitado, 1)
        }
        var checksumAntes = 0L
        var checksumDepois = 0L
        val amostrasAntes = mutableListOf<Double>()
        val amostrasDepois = mutableListOf<Double>()
        repeat(9) {
            amostrasAntes += measureNanoTime {
                checksumAntes += medirAntes(anterior, digitado, 200)
            } / 200.0 / 1_000_000.0
            amostrasDepois += measureNanoTime {
                checksumDepois += medirDepois(anterior, digitado, 200)
            } / 200.0 / 1_000_000.0
        }
        val medianaAntes = amostrasAntes.sorted()[amostrasAntes.size / 2]
        val medianaDepois = amostrasDepois.sorted()[amostrasDepois.size / 2]
        val deltaMs = medianaDepois - medianaAntes
        val deltaPercentual = deltaMs / medianaAntes * 100.0
        println(
            "PERF_WIKILINK_333KB " +
                "before_median_ms=$medianaAntes after_median_ms=$medianaDepois " +
                "delta_ms=$deltaMs delta_percent=$deltaPercentual " +
                "before_samples_ms=$amostrasAntes after_samples_ms=$amostrasDepois " +
                "bytes=${notaGrande.toByteArray().size}",
        )
        assertTrue(checksumAntes > 0)
        assertTrue(checksumDepois > 0)
        assertTrue(editMarkdownValue(anterior, digitado).text.endsWith("x"))
    }

    private fun medirAntes(anterior: TextFieldValue, digitado: TextFieldValue, repeticoes: Int): Long {
        var checksum = 0L
        repeat(repeticoes) {
            checksum += editMarkdownValue(anterior, digitado).selection.start
        }
        return checksum
    }

    private fun medirDepois(anterior: TextFieldValue, digitado: TextFieldValue, repeticoes: Int): Long {
        var checksum = 0L
        repeat(repeticoes) {
            val editado = editMarkdownValue(anterior, digitado)
            val anteriorPuro = EdicaoDeTexto(anterior.text, anterior.selection)
            val editadoPuro = EdicaoDeTexto(editado.text, editado.selection)
            if (deveAtualizarSugestaoWikilink(anteriorPuro, editadoPuro, gatilhoAtivo = false)) {
                checksum += gatilhoSugestaoWikilink(editado.text, editado.selection)?.abertura ?: 0
            }
            checksum += editado.selection.start
        }
        return checksum
    }
}
