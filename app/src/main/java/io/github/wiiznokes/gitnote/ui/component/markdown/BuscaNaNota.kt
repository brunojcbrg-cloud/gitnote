package io.github.wiiznokes.gitnote.ui.component.markdown

import java.text.Normalizer
import java.util.Locale

/** Busca literal; resultados podem se sobrepor e usam offsets inclusivos do texto original. */
fun ocorrencias(
    texto: String,
    termo: String,
    diferenciarMaiusculas: Boolean = false,
): List<IntRange> {
    if (termo.isEmpty() || texto.isEmpty()) return emptyList()
    val needle = normalizarBusca(termo, diferenciarMaiusculas).texto
    if (needle.isEmpty()) return emptyList()
    val normalizado = normalizarBusca(texto, diferenciarMaiusculas)
    val haystack = normalizado.texto
    val encontrados = ArrayList<IntRange>()
    var inicio = haystack.indexOf(needle)
    while (inicio >= 0) {
        val originalInicio = normalizado.inicios[inicio]
        val originalFim = normalizado.fins[inicio + needle.length - 1] - 1
        encontrados.add(originalInicio..originalFim)
        inicio = haystack.indexOf(needle, inicio + 1)
    }
    return encontrados
}

/** Constrói o mapa de offsets na mesma passada que emite os caracteres normalizados. */
private data class TextoNormalizado(val texto: String, val inicios: IntArray, val fins: IntArray)

private fun normalizarBusca(texto: String, diferenciarMaiusculas: Boolean): TextoNormalizado {
    val normalizado = StringBuilder(texto.length)
    var inicios = IntArray(texto.length.coerceAtLeast(1))
    var fins = IntArray(inicios.size)
    var tamanho = 0
    var origem = 0

    fun emitir(caractere: Char, fimOriginal: Int) {
        if (tamanho == inicios.size) {
            inicios = inicios.copyOf(inicios.size * 2)
            fins = fins.copyOf(fins.size * 2)
        }
        normalizado.append(caractere)
        inicios[tamanho] = origem
        fins[tamanho++] = fimOriginal
    }

    while (origem < texto.length) {
        val ponto = texto.codePointAt(origem)
        val fimOriginal = origem + Character.charCount(ponto)
        if (ponto <= 0x7f) {
            val caractere = ponto.toChar()
            emitir(if (diferenciarMaiusculas) caractere else caractere.lowercaseChar(), fimOriginal)
        } else {
            val decomposto = Normalizer.normalize(String(Character.toChars(ponto)), Normalizer.Form.NFD)
            val semCaixa = if (diferenciarMaiusculas) decomposto else decomposto.lowercase(Locale.ROOT)
            semCaixa.forEach { caractere ->
                if (Character.getType(caractere) != Character.NON_SPACING_MARK.toInt()) {
                    emitir(caractere, fimOriginal)
                } else if (tamanho > 0) {
                    fins[tamanho - 1] = fimOriginal
                }
            }
        }
        origem = fimOriginal
    }
    return TextoNormalizado(normalizado.toString(), inicios.copyOf(tamanho), fins.copyOf(tamanho))
}
