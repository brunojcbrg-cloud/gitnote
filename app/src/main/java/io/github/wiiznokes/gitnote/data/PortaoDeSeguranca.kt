package io.github.wiiznokes.gitnote.data

/** Session state is deliberately memory-only. No persisted "authenticated" bit. */
class PortaoDeSeguranca {
    private var paradoEm: Long? = null

    fun aoParar(instante: Long, estavaAberto: Boolean) {
        if (estavaAberto) paradoEm = instante
    }

    fun deveRetravar(instante: Long, prazo: PrazoDaTrava, travaLigada: Boolean): Boolean {
        val inicio = paradoEm ?: return false
        paradoEm = null
        return travaLigada && (instante - inicio).coerceAtLeast(0) >= prazo.milissegundos
    }
}
