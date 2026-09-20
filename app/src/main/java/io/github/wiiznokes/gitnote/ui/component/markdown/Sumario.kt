package io.github.wiiznokes.gitnote.ui.component.markdown

data class ItemDeSumario(
    val nivel: Int,
    val texto: String,
    val linha: Int,
    val offset: Int,
)

/** Extrai títulos ATX sem construir uma lista de todas as linhas da nota. Linhas são zero-based. */
fun sumarioDe(texto: String): List<ItemDeSumario> {
    val itens = ArrayList<ItemDeSumario>()
    var inicio = 0
    var linha = 0
    var cerca = '\u0000'
    var tamanhoCerca = 0

    while (inicio < texto.length) {
        var fim = inicio
        while (fim < texto.length && texto[fim] != '\n') fim++
        val fimVisivel = if (fim > inicio && texto[fim - 1] == '\r') fim - 1 else fim

        // CommonMark permite até três espaços antes da cerca, mas os títulos do
        // sumário exigem '#' no começo exato da linha.
        var marcador = inicio
        while (marcador < fimVisivel && marcador - inicio < 4 && texto[marcador] == ' ') marcador++
        val indentacaoValida = marcador - inicio <= 3
        val caractere = if (marcador < fimVisivel) texto[marcador] else '\u0000'
        var comprimento = 0
        if (indentacaoValida && (caractere == '`' || caractere == '~')) {
            while (marcador + comprimento < fimVisivel &&
                texto[marcador + comprimento] == caractere
            ) comprimento++
        }

        if (cerca == '\u0000') {
            if (comprimento >= 3) {
                cerca = caractere
                tamanhoCerca = comprimento
            } else {
                var depois = inicio
                while (depois < fimVisivel && texto[depois] == '#' && depois - inicio < 7) depois++
                val nivel = depois - inicio
                if (nivel in 1..6 && depois < fimVisivel && texto[depois] == ' ') {
                    val titulo = limparMarcadores(texto, depois + 1, fimVisivel)
                    itens += ItemDeSumario(nivel, titulo, linha, inicio)
                }
            }
        } else if (caractere == cerca && comprimento >= tamanhoCerca &&
            (marcador + comprimento until fimVisivel).all { texto[it] == ' ' || texto[it] == '\t' }
        ) {
            cerca = '\u0000'
            tamanhoCerca = 0
        }

        if (fim == texto.length) break
        inicio = fim + 1
        linha++
    }
    return itens
}

private fun limparMarcadores(texto: String, inicio: Int, fim: Int): String {
    val limpo = StringBuilder(fim - inicio)
    for (indice in inicio until fim) {
        when (texto[indice]) {
            '*', '_', '`', '=' -> Unit
            else -> limpo.append(texto[indice])
        }
    }
    return limpo.toString().trim()
}
