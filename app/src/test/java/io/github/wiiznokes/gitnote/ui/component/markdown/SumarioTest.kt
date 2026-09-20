package io.github.wiiznokes.gitnote.ui.component.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTime

class SumarioTest {
    @Test
    fun reconheceNiveisETiraMarcadoresSemPerderOffsetOuLinha() {
        val texto = (1..6).joinToString("\n") { nivel ->
            "${"#".repeat(nivel)} Título **forte** _itálico_ `código` ==marca=="
        }
        val itens = sumarioDe(texto)
        assertEquals((1..6).toList(), itens.map { it.nivel })
        assertEquals((0..5).toList(), itens.map { it.linha })
        assertEquals("Título forte itálico código marca", itens.first().texto)
        assertEquals(texto.indexOf("## Título"), itens[1].offset)
    }

    @Test
    fun ignoraTagsCercasTabelaECerquilhaSoltaEmLfECrlf() {
        for (quebra in listOf("\n", "\r\n")) {
            val texto = listOf(
                "# válido", "#flashcards", "#", "####### demais",
                "```kotlin", "# dentro de cerca", "```",
                "~~~", "## dentro de tilde", "~~~",
                "| # dentro de tabela |", "## segundo",
            ).joinToString(quebra)
            val itens = sumarioDe(texto)
            assertEquals(listOf("válido", "segundo"), itens.map { it.texto })
            assertEquals(listOf(0, 11), itens.map { it.linha })
            assertEquals(texto.indexOf("## segundo"), itens.last().offset)
        }
    }

    @Test
    fun perfSumarioNaNotaDe1946Linhas() {
        val linhas = MutableList(1_946) { index ->
            if (index % 7 == 0) "## Título $index com **ênfase**"
            else "Parágrafo $index com texto de estudo"
        }
        val base = linhas.joinToString("\n")
        linhas[linhas.lastIndex] += "x".repeat(180_046 - base.length)
        val nota = linhas.joinToString("\n")
        repeat(3) { sumarioDe(nota) }
        val amostras = List(5) {
            measureTime { sumarioDe(nota) }.inWholeMicroseconds / 1_000.0
        }
        val mediana = amostras.sorted()[2]
        println("PERF_SUMARIO linhas=1946 chars=180046 amostras_ms=$amostras mediana_ms=$mediana")
        assertEquals(180_046, nota.length)
        assertEquals(278, sumarioDe(nota).size)
        assertTrue(mediana < 3.0, "mediana do sumário: $mediana ms")
    }
}
