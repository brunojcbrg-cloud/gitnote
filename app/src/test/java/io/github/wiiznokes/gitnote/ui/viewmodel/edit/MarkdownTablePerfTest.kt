package io.github.wiiznokes.gitnote.ui.viewmodel.edit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.measureTime

/**
 * `tableRegionAt` roda a cada tecla enquanto a barra de formatacao esta aberta
 * (`TableActionButton`, `remember(value.text, value.selection.start)`).
 * Aqui se mede o custo na maior forma real do vault, para comparar com os 27,4 ms
 * por tecla que a pre-visualizacao ja custa na mesma nota.
 */
class MarkdownTablePerfTest {

    @Test
    fun tableLookupOnALargeNoteIsMeasured() {
        val fonte = notaGrande(linhas = 1_946, caracteres = 180_046)
        val cursor = fonte.length / 2

        repeat(2) { tableRegionAt(fonte, cursor) }

        val amostras = List(5) {
            measureTime { tableRegionAt(fonte, cursor) }.inWholeMicroseconds / 1_000.0
        }
        val mediana = amostras.sorted()[amostras.size / 2]

        println(
            "PERF_TABLE_REGION_AT linhas=1946 chars=180046 " +
                "amostras_ms=$amostras mediana_ms=$mediana",
        )

        val semTabela = notaGrandeSemTabela(linhas = 1_946, caracteres = 180_046)
        val amostrasSemTabela = List(5) {
            measureTime { tableRegionAt(semTabela, cursor) }.inWholeMicroseconds / 1_000.0
        }
        println(
            "PERF_TABLE_REGION_AT_SEM_TABELA " +
                "amostras_ms=$amostrasSemTabela " +
                "mediana_ms=${amostrasSemTabela.sorted()[amostrasSemTabela.size / 2]}",
        )

        assertEquals(1_946, fonte.count { it == '\n' } + 1)
        assertEquals(180_046, fonte.length)
    }

    private fun notaGrande(linhas: Int, caracteres: Int): String {
        val corpo = MutableList(linhas) { index ->
            when (index % 5) {
                0 -> "# Secao $index com **enfase**"
                1 -> "- item $index com texto corrido"
                2 -> "| coluna $index | valor $index |"
                3 -> "| --- | --- |"
                else -> "Paragrafo $index com texto"
            }
        }
        return encherAte(corpo, caracteres)
    }

    private fun notaGrandeSemTabela(linhas: Int, caracteres: Int): String {
        val corpo = MutableList(linhas) { index -> "Paragrafo $index sem pipe nenhum" }
        return encherAte(corpo, caracteres)
    }

    private fun encherAte(linhas: MutableList<String>, caracteres: Int): String {
        var faltando = caracteres - linhas.sumOf { it.length } - (linhas.size - 1)
        require(faltando >= 0)
        // O enchimento so entra em linha sem pipe: encher a linha da tabela
        // quebraria a tabela e a medicao deixaria de valer.
        val elegiveis = linhas.indices.filter { !linhas[it].contains('|') }
        elegiveis.forEachIndexed { posicao, index ->
            val restantes = elegiveis.size - posicao
            val enchimento = faltando / restantes
            linhas[index] += "x".repeat(enchimento)
            faltando -= enchimento
        }
        check(faltando == 0)
        return linhas.joinToString("\n")
    }
}
