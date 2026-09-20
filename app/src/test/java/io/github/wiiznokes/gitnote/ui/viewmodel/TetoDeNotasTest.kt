package io.github.wiiznokes.gitnote.ui.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TetoDeNotasTest {
    @Test
    fun quarentaNotasComecamEmDezEVoltandoAPastaRecomecamEmDez() {
        val quarenta = (1..40).toList()
        var teto: Int? = TetoDeNotas.PRIMEIRA_LEVA

        assertEquals(10, quarenta.take(TetoDeNotas.paraConsulta("", teto)!!).size)
        assertEquals(40, TetoDeNotas.totalParaRodape("", teto, quarenta.size))

        teto = null // Mostrar todas nesta visita.
        assertEquals(40, (TetoDeNotas.paraConsulta("", teto)?.let { quarenta.take(it) } ?: quarenta).size)
        assertNull(TetoDeNotas.totalParaRodape("", teto, quarenta.size))

        teto = TetoDeNotas.PRIMEIRA_LEVA // openFolder em outra pasta.
        assertEquals(10, quarenta.take(TetoDeNotas.paraConsulta("", teto)!!).size)
    }

    @Test
    fun buscaNaoAplicaTetoEDevolveMaisDeDezResultados() {
        assertNull(TetoDeNotas.paraConsulta("termo", TetoDeNotas.PRIMEIRA_LEVA))
        assertNull(TetoDeNotas.totalParaRodape("termo", TetoDeNotas.PRIMEIRA_LEVA, 40))
        assertNull(TetoDeNotas.totalParaRodape("", TetoDeNotas.PRIMEIRA_LEVA, 10))
    }
}
