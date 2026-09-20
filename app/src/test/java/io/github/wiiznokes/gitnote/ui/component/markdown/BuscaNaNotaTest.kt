package io.github.wiiznokes.gitnote.ui.component.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.measureTime

class BuscaNaNotaTest {
    @Test
    fun buscaSemAcentoNosDoisLadosEMapeiaOffsetsOriginais() {
        val texto = "crânio CRÂNIO cranio"
        assertEquals(listOf(0..5, 7..12, 14..19), ocorrencias(texto, "cranio"))
        assertEquals(listOf(0..5, 14..19), ocorrencias(texto, "crânio", diferenciarMaiusculas = true))
        assertEquals(listOf(2..7), ocorrencias("😀crânio", "cranio"))
        assertEquals(listOf(0..1), ocorrencias("a\u0301", "á"))
    }

    @Test
    fun termoVazioERegraDeSobreposicao() {
        assertEquals(emptyList(), ocorrencias("abc", ""))
        // Sobreposições são incluídas: a segunda busca começa um caractere após a primeira.
        assertEquals(listOf(0..1, 1..2), ocorrencias("aaa", "aa"))
    }

    @Test
    fun umaLetraEm180MilCaracteresDevolveContagemCerta() {
        val texto = "a".repeat(180_000)
        val achados = ocorrencias(texto, "a")
        assertEquals(180_000, achados.size)
        assertEquals(0..0, achados.first())
        assertEquals(179_999..179_999, achados.last())
    }

    @Test
    fun perfBuscaNaNotaDe1946Linhas() {
        val base = (0 until 1_946).joinToString("\n") { "Paragrafo $it com texto a" }
        val texto = base.padEnd(180_046, 'x')
        val esperado = texto.count { it == 'a' }
        repeat(2) { ocorrencias(texto, "a") }
        val amostras = List(5) {
            var total = 0
            val duracao = measureTime { total = ocorrencias(texto, "a").size }
            assertEquals(esperado, total)
            duracao.inWholeMicroseconds / 1_000.0
        }
        val mediana = amostras.sorted()[2]
        println("PERF_BUSCA linhas=1946 chars=180046 amostras_ms=$amostras mediana_ms=$mediana")
        assertEquals(1_946, texto.count { it == '\n' } + 1)
        assertEquals(180_046, texto.length)
    }
}
