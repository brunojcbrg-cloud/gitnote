package io.github.wiiznokes.gitnote.ui.viewmodel

/** Limite aplicado so a listagem da pasta; buscas continuam completas. */
internal object TetoDeNotas {
    const val PRIMEIRA_LEVA = 10

    fun paraConsulta(query: String, teto: Int?): Int? = if (query.isEmpty()) teto else null

    fun totalParaRodape(query: String, teto: Int?, total: Int): Int? =
        if (query.isEmpty() && teto != null && total > teto) total else null
}
