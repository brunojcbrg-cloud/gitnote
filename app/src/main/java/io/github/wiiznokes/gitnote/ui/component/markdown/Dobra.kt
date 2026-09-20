package io.github.wiiznokes.gitnote.ui.component.markdown

/** Uma seção vai do fim da linha do título até o próximo título de nível <= ao dele. */
data class Secao(
    val titulo: ItemDeSumario,
    val inicioCorpo: Int,
    val fimCorpo: Int,
)

/**
 * Segmenta o texto em seções a partir do sumário já extraído. Cada seção cobre o
 * corpo do seu título (linhas seguintes) até o próximo título de nível igual ou
 * mais alto (nível numérico menor ou igual) — por isso recolher uma seção de nível 2
 * também recolhe os níveis 3 a 6 dentro dela: eles fazem parte do seu corpo.
 */
fun secoesDe(texto: String, sumario: List<ItemDeSumario>): List<Secao> =
    sumario.mapIndexed { indice, item ->
        val inicioCorpo = fimDaLinha(texto, item.offset)
        var fimCorpo = texto.length
        for (proximo in indice + 1 until sumario.size) {
            if (sumario[proximo].nivel <= item.nivel) {
                fimCorpo = sumario[proximo].offset
                break
            }
        }
        Secao(item, inicioCorpo, fimCorpo.coerceAtLeast(inicioCorpo))
    }

private fun fimDaLinha(texto: String, offsetTitulo: Int): Int {
    var indice = offsetTitulo
    while (indice < texto.length && texto[indice] != '\n') indice++
    return if (indice < texto.length) indice + 1 else indice
}

data class TextoDobrado(val visivel: String, val mapa: MapaDeDobra)

/**
 * Converte offsets entre o texto original e o texto visível (com os corpos
 * recolhidos removidos). Um offset que caía dentro de um trecho removido não tem
 * posição própria no texto visível: `paraVisivel` devolve o ponto onde o corte
 * começa (é para lá que uma rolagem ou um cursor pedindo esse offset deve ir).
 * `paraOriginal` é a inversa exata só para offsets que sobrevivem no texto visível.
 * O ponto exato onde um corte cai (`corte.offsetVisivel`) é ambíguo em original —
 * tanto o início do trecho escondido quanto o título que vem logo depois dele (o
 * que encerrou a seção) mapeiam para ali. A convenção escolhe o **fim** do corte,
 * porque é a posição visível de verdade: o início do corte nunca é alcançável a
 * partir do texto visível, só o que vem depois dele.
 */
class MapaDeDobra(private val cortes: List<Corte>) {
    data class Corte(val inicioOriginal: Int, val fimOriginal: Int, val offsetVisivel: Int)

    fun paraVisivel(offsetOriginal: Int): Int {
        var deslocamento = 0
        for (corte in cortes) {
            if (offsetOriginal < corte.inicioOriginal) break
            if (offsetOriginal < corte.fimOriginal) return corte.offsetVisivel
            deslocamento += corte.fimOriginal - corte.inicioOriginal
        }
        return offsetOriginal - deslocamento
    }

    fun paraOriginal(offsetVisivel: Int): Int {
        var deslocamento = 0
        for (corte in cortes) {
            if (offsetVisivel < corte.offsetVisivel) break
            if (offsetVisivel == corte.offsetVisivel) return corte.fimOriginal
            deslocamento += corte.fimOriginal - corte.inicioOriginal
        }
        return offsetVisivel + deslocamento
    }
}

/**
 * Remove o corpo das seções recolhidas (identificadas pelo offset do título) e
 * devolve o texto visível junto do mapeamento de offsets. `recolhidas` vazio devolve
 * o texto original, sem alocar uma cópia nova.
 */
fun dobrar(texto: String, recolhidas: Set<Int>): TextoDobrado {
    if (recolhidas.isEmpty()) return TextoDobrado(texto, MapaDeDobra(emptyList()))

    val sumario = sumarioDe(texto)
    val secoes = secoesDe(texto, sumario)

    val cortesBrutos = secoes
        .asSequence()
        .filter { it.titulo.offset in recolhidas }
        .filter { it.fimCorpo > it.inicioCorpo }
        .map { it.inicioCorpo to it.fimCorpo }
        .sortedBy { it.first }
        .toList()

    // Seções recolhidas aninhadas (pai e filho ambos marcados) produzem intervalos
    // sobrepostos ou contidos: unir para não duplicar o corte nem quebrar o mapa.
    val unidos = ArrayList<Pair<Int, Int>>()
    for (intervalo in cortesBrutos) {
        val ultimo = unidos.lastOrNull()
        if (ultimo != null && intervalo.first <= ultimo.second) {
            unidos[unidos.size - 1] = ultimo.first to maxOf(ultimo.second, intervalo.second)
        } else {
            unidos += intervalo
        }
    }

    if (unidos.isEmpty()) return TextoDobrado(texto, MapaDeDobra(emptyList()))

    val visivel = StringBuilder(texto.length)
    val cortesMapa = ArrayList<MapaDeDobra.Corte>(unidos.size)
    var cursor = 0
    for ((inicio, fim) in unidos) {
        visivel.append(texto, cursor, inicio)
        cortesMapa += MapaDeDobra.Corte(inicio, fim, visivel.length)
        cursor = fim
    }
    visivel.append(texto, cursor, texto.length)

    return TextoDobrado(visivel.toString(), MapaDeDobra(cortesMapa))
}
