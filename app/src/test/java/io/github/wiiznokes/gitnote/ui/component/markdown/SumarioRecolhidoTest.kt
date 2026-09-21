package io.github.wiiznokes.gitnote.ui.component.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Recolher títulos **no painel do sumário** (pedido do Bruno em 21/09): esconde
 * o subtítulo da lista, sem tocar no texto da nota nem no recolhimento do modo
 * leitura, que é outra coisa e tem outros botões.
 */
class SumarioRecolhidoTest {

    private val nota = """
        # Um
        ## Um.A
        ### Um.A.1
        ## Um.B
        # Dois
        ## Dois.A
        # Tres
    """.trimIndent()

    private val itens = sumarioDe(nota)

    private fun offsetDe(titulo: String) = itens.first { it.texto == titulo }.offset

    private fun textos(lista: List<ItemDeSumario>) = lista.map { it.texto }

    @Test
    fun aNotaDeTesteFoiLidaComoEsperado() {
        assertEquals(
            listOf("Um", "Um.A", "Um.A.1", "Um.B", "Dois", "Dois.A", "Tres"),
            textos(itens),
        )
    }

    @Test
    fun soGanhaSetaQuemTemSubtitulo() {
        val comFilho = titulosComSubtitulos(itens)
        assertEquals(
            setOf(offsetDe("Um"), offsetDe("Um.A"), offsetDe("Dois")),
            comFilho,
        )
        assertTrue(offsetDe("Tres") !in comFilho, "titulo sem filho nao tem seta")
        assertTrue(offsetDe("Um.B") !in comFilho)
    }

    @Test
    fun semNadaRecolhidoAListaEAMesma() {
        assertEquals(itens, itensVisiveisDoSumario(itens, emptySet()))
    }

    @Test
    fun recolherEscondeSoOsDescendentes() {
        val visiveis = itensVisiveisDoSumario(itens, setOf(offsetDe("Um")))
        assertEquals(listOf("Um", "Dois", "Dois.A", "Tres"), textos(visiveis))
    }

    @Test
    fun recolherUmSubtituloEscondeSoOQueEstaDentroDele() {
        val visiveis = itensVisiveisDoSumario(itens, setOf(offsetDe("Um.A")))
        assertEquals(listOf("Um", "Um.A", "Um.B", "Dois", "Dois.A", "Tres"), textos(visiveis))
    }

    @Test
    fun recolhidoDentroDeRecolhidoNaoReaparece() {
        // Recolher o pai esconde o filho recolhido junto -- e expandir so o pai
        // depois tem de devolver o filho ainda recolhido, nao a arvore inteira.
        val ambos = setOf(offsetDe("Um"), offsetDe("Um.A"))
        assertEquals(listOf("Um", "Dois", "Dois.A", "Tres"), textos(itensVisiveisDoSumario(itens, ambos)))
        assertEquals(
            listOf("Um", "Um.A", "Um.B", "Dois", "Dois.A", "Tres"),
            textos(itensVisiveisDoSumario(itens, setOf(offsetDe("Um.A")))),
        )
    }

    @Test
    fun recolherOTituloSemFilhoNaoEscondeOsIrmaos() {
        val visiveis = itensVisiveisDoSumario(itens, setOf(offsetDe("Um.B")))
        assertEquals(textos(itens), textos(visiveis))
    }

    @Test
    fun recolherDoisRamosIndependentes() {
        val visiveis = itensVisiveisDoSumario(itens, setOf(offsetDe("Um"), offsetDe("Dois")))
        assertEquals(listOf("Um", "Dois", "Tres"), textos(visiveis))
    }

    @Test
    fun offsetQueNaoEDeTituloNaoMudaNada() {
        assertEquals(textos(itens), textos(itensVisiveisDoSumario(itens, setOf(99_999))))
    }

    @Test
    fun listaVaziaNaoQuebra() {
        assertEquals(emptyList(), itensVisiveisDoSumario(emptyList(), setOf(0)))
        assertEquals(emptySet(), titulosComSubtitulos(emptyList()))
        assertEquals(emptySet(), titulosComSubtitulos(listOf(ItemDeSumario(1, "so", 0, 0))))
    }
}
