package io.github.wiiznokes.gitnote.ui.component.markdown

/**
 * Texto ja convertido, com as faixas que devem descer ou subir da linha de base.
 * As faixas sao posicoes em [text], nao no LaTeX de origem.
 */
data class MathText(
    val text: String,
    val subscripts: List<IntRange> = emptyList(),
    val superscripts: List<IntRange> = emptyList(),
)

/**
 * Converte o LaTeX das notas no texto legivel equivalente.
 *
 * Isto NAO e um motor de LaTeX. E a traducao do subconjunto que as notas usam de
 * verdade -- letras gregas, relacoes, setas, `\text{}` e indices -- para Unicode
 * mais marcas de subscrito/sobrescrito que o Compose desenha nativamente. A escolha
 * e deliberada: o editor monta `AnnotatedString`, nao HTML, entao um KaTeX exigiria
 * um WebView por formula, e um JLaTeXMath devolveria bitmap que nao acompanha o
 * tamanho da fonte nem a selecao de texto.
 *
 * Regra de ouro: comando desconhecido volta como o proprio nome, nunca como
 * excecao e nunca como texto vazio. Perder o conteudo e pior que mostra-lo cru.
 */
object LatexToUnicode {

    private val SIMBOLOS: Map<String, String> = mapOf(
        // gregas minusculas
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ",
        "epsilon" to "ε", "varepsilon" to "ε", "zeta" to "ζ", "eta" to "η",
        "theta" to "θ", "vartheta" to "θ", "iota" to "ι", "kappa" to "κ",
        "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ",
        "pi" to "π", "rho" to "ρ", "sigma" to "σ", "tau" to "τ",
        "upsilon" to "υ", "phi" to "φ", "varphi" to "φ", "chi" to "χ",
        "psi" to "ψ", "omega" to "ω",
        // gregas maiusculas
        "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ",
        "Xi" to "Ξ", "Pi" to "Π", "Sigma" to "Σ", "Upsilon" to "Υ",
        "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
        // relacoes e operadores
        "ge" to "≥", "geq" to "≥", "le" to "≤", "leq" to "≤",
        "neq" to "≠", "ne" to "≠", "approx" to "≈", "equiv" to "≡",
        "sim" to "∼", "propto" to "∝", "pm" to "±", "mp" to "∓",
        "times" to "×", "cdot" to "·", "div" to "÷", "ast" to "*",
        "infty" to "∞", "partial" to "∂", "nabla" to "∇",
        "sum" to "∑", "prod" to "∏", "int" to "∫",
        "degree" to "°", "circ" to "∘", "bullet" to "•",
        "in" to "∈", "notin" to "∉", "subset" to "⊂", "supset" to "⊃",
        "cup" to "∪", "cap" to "∩", "emptyset" to "∅",
        "forall" to "∀", "exists" to "∃", "neg" to "¬",
        "land" to "∧", "lor" to "∨",
        // setas
        "rightarrow" to "→", "to" to "→", "longrightarrow" to "→",
        "leftarrow" to "←", "gets" to "←", "longleftarrow" to "←",
        "leftrightarrow" to "↔", "longleftrightarrow" to "↔",
        "Rightarrow" to "⇒", "Leftarrow" to "⇐", "Leftrightarrow" to "⇔",
        "uparrow" to "↑", "downarrow" to "↓", "rightleftharpoons" to "⇌",
        "mapsto" to "↦",
        // pontuacao e espacos
        "ldots" to "…", "dots" to "…", "cdots" to "⋯",
        "quad" to "  ", "qquad" to "    ", "," to " ", ";" to " ", ":" to " ",
        " " to " ", "!" to "",
        // escapes literais
        "%" to "%", "&" to "&", "#" to "#", "\$" to "\$", "_" to "_",
        "{" to "{", "}" to "}", "\\" to " ",
    )

    /** Comandos cujo argumento entra literal, sem conversao. */
    private val LITERAIS = setOf("text", "textrm", "textbf", "textit", "mathrm", "mathbf", "mathit", "mathsf", "operatorname")

    /** Comandos que viram um simbolo seguido do argumento em sobrescrito. */
    private val SETAS_COM_ROTULO = mapOf(
        "xrightarrow" to "→",
        "xleftarrow" to "←",
    )

    fun converter(latex: String): MathText {
        val saida = StringBuilder()
        val subscritos = mutableListOf<IntRange>()
        val sobrescritos = mutableListOf<IntRange>()
        converterEm(latex, 0, latex.length, saida, subscritos, sobrescritos)
        return MathText(
            text = saida.toString(),
            subscripts = subscritos.toList(),
            superscripts = sobrescritos.toList(),
        )
    }

    private val SUBSCRITO: Map<Char, Char> = mapOf(
        '0' to '\u2080', '1' to '\u2081', '2' to '\u2082', '3' to '\u2083', '4' to '\u2084',
        '5' to '\u2085', '6' to '\u2086', '7' to '\u2087', '8' to '\u2088', '9' to '\u2089',
        '+' to '\u208A', '-' to '\u208B', '=' to '\u208C', '(' to '\u208D', ')' to '\u208E',
        'a' to '\u2090', 'e' to '\u2091', 'o' to '\u2092', 'x' to '\u2093', 'h' to '\u2095',
        'k' to '\u2096', 'l' to '\u2097', 'm' to '\u2098', 'n' to '\u2099', 'p' to '\u209A',
        's' to '\u209B', 't' to '\u209C', 'i' to '\u1D62', 'r' to '\u1D63', 'u' to '\u1D64',
        'v' to '\u1D65', 'j' to '\u2C7C',
    )

    private val SOBRESCRITO: Map<Char, Char> = mapOf(
        '0' to '\u2070', '1' to '\u00B9', '2' to '\u00B2', '3' to '\u00B3', '4' to '\u2074',
        '5' to '\u2075', '6' to '\u2076', '7' to '\u2077', '8' to '\u2078', '9' to '\u2079',
        '+' to '\u207A', '-' to '\u207B', '=' to '\u207C', '(' to '\u207D', ')' to '\u207E',
        'a' to '\u1D43', 'b' to '\u1D47', 'c' to '\u1D9C', 'd' to '\u1D48', 'e' to '\u1D49',
        'f' to '\u1DA0', 'g' to '\u1D4D', 'h' to '\u02B0', 'i' to '\u2071', 'j' to '\u02B2',
        'k' to '\u1D4F', 'l' to '\u02E1', 'm' to '\u1D50', 'n' to '\u207F', 'o' to '\u1D52',
        'p' to '\u1D56', 'r' to '\u02B3', 's' to '\u02E2', 't' to '\u1D57', 'u' to '\u1D58',
        'v' to '\u1D5B', 'w' to '\u02B7', 'x' to '\u02E3', 'y' to '\u02B8', 'z' to '\u1DBB',
    )

    /**
     * A mesma formula, em texto puro.
     *
     * O modo de leitura entrega uma String ao renderizador, entao nao ha como pedir
     * deslocamento de linha de base como no editor: o indice tem de virar caractere.
     * Faixa cujo conteudo nao tem equivalente Unicode inteiro fica na linha, porque
     * meio indice convertido le pior que nenhum.
     */
    fun textoSimples(math: MathText): String {
        if (math.subscripts.isEmpty() && math.superscripts.isEmpty()) return math.text

        val saida = StringBuilder(math.text)
        val faixas = math.subscripts.map { it to SUBSCRITO } + math.superscripts.map { it to SOBRESCRITO }
        faixas.forEach { (faixa, tabela) ->
            if (faixa.first < 0 || faixa.last >= saida.length) return@forEach
            val convertida = CharArray(faixa.count())
            var i = 0
            for (posicao in faixa) {
                val equivalente = tabela[saida[posicao]] ?: return@forEach
                convertida[i] = equivalente
                i++
            }
            i = 0
            for (posicao in faixa) {
                saida[posicao] = convertida[i]
                i++
            }
        }
        return saida.toString()
    }

    private fun converterEm(
        origem: String,
        inicio: Int,
        fim: Int,
        saida: StringBuilder,
        subscritos: MutableList<IntRange>,
        sobrescritos: MutableList<IntRange>,
    ) {
        var i = inicio
        while (i < fim) {
            when (val c = origem[i]) {
                '\\' -> i = comando(origem, i, fim, saida, subscritos, sobrescritos)

                '_', '^' -> {
                    val arg = argumento(origem, i + 1, fim)
                    if (arg == null) {
                        saida.append(c)
                        i++
                    } else {
                        // "^\circ" e grau, nao sobrescrito: 37^\circ C vira 37 graus C.
                        val cru = origem.substring(arg.conteudoInicio, arg.conteudoFim).trim()
                        if (c == '^' && (cru == "\\circ" || cru == "\\degree")) {
                            saida.append('°')
                        } else {
                            val de = saida.length
                            converterEm(origem, arg.conteudoInicio, arg.conteudoFim, saida, subscritos, sobrescritos)
                            if (saida.length > de) {
                                val faixa = de until saida.length
                                if (c == '_') subscritos += faixa else sobrescritos += faixa
                            }
                        }
                        i = arg.proximo
                    }
                }

                '{', '}' -> i++
                '~' -> { saida.append(' '); i++ }
                '&' -> { saida.append(' '); i++ }
                else -> { saida.append(c); i++ }
            }
        }
    }

    private fun comando(
        origem: String,
        inicio: Int,
        fim: Int,
        saida: StringBuilder,
        subscritos: MutableList<IntRange>,
        sobrescritos: MutableList<IntRange>,
    ): Int {
        var i = inicio + 1
        if (i >= fim) {
            saida.append('\\')
            return i
        }

        // Comando de um caractere que nao e letra: \, \; \% \& \\ ...
        if (!origem[i].isLetter()) {
            val nome = origem[i].toString()
            saida.append(SIMBOLOS[nome] ?: nome)
            return i + 1
        }

        val nomeInicio = i
        while (i < fim && origem[i].isLetter()) i++
        val nome = origem.substring(nomeInicio, i)
        // O espaco depois do comando NAO e comido, ao contrario do LaTeX de verdade:
        // sem ele "\ge 14" viraria "<=14" colado, que le pior na tela do celular.

        when {
            nome in LITERAIS -> {
                val arg = argumento(origem, i, fim)
                if (arg != null) {
                    saida.append(origem, arg.conteudoInicio, arg.conteudoFim)
                    return arg.proximo
                }
            }

            nome == "frac" || nome == "dfrac" || nome == "tfrac" -> {
                val numerador = argumento(origem, i, fim)
                val denominador = if (numerador != null) argumento(origem, numerador.proximo, fim) else null
                if (numerador != null && denominador != null) {
                    converterEm(origem, numerador.conteudoInicio, numerador.conteudoFim, saida, subscritos, sobrescritos)
                    saida.append('/')
                    converterEm(origem, denominador.conteudoInicio, denominador.conteudoFim, saida, subscritos, sobrescritos)
                    return denominador.proximo
                }
            }

            nome == "sqrt" -> {
                val arg = argumento(origem, i, fim)
                if (arg != null) {
                    saida.append('√')
                    converterEm(origem, arg.conteudoInicio, arg.conteudoFim, saida, subscritos, sobrescritos)
                    return arg.proximo
                }
            }

            nome in SETAS_COM_ROTULO -> {
                val arg = argumento(origem, i, fim)
                if (arg != null) {
                    saida.append(SETAS_COM_ROTULO.getValue(nome))
                    val de = saida.length
                    converterEm(origem, arg.conteudoInicio, arg.conteudoFim, saida, subscritos, sobrescritos)
                    if (saida.length > de) sobrescritos += de until saida.length
                    return arg.proximo
                }
            }
        }

        val simbolo = SIMBOLOS[nome]
        // Comando desconhecido devolve o proprio nome: mostrar cru e melhor que sumir.
        saida.append(simbolo ?: nome)
        return i
    }

    private class Argumento(val conteudoInicio: Int, val conteudoFim: Int, val proximo: Int)

    /**
     * Le o argumento a partir de [inicio]: um grupo `{...}` com chaves balanceadas,
     * um comando `\nome`, ou um unico caractere.
     */
    private fun argumento(origem: String, inicio: Int, fim: Int): Argumento? {
        var i = inicio
        while (i < fim && origem[i] == ' ') i++
        if (i >= fim) return null

        if (origem[i] == '{') {
            var profundidade = 1
            var j = i + 1
            while (j < fim && profundidade > 0) {
                when (origem[j]) {
                    '{' -> profundidade++
                    '}' -> profundidade--
                    '\\' -> j++
                }
                j++
            }
            // Chave sem fechamento: consome ate o fim, sem perder o conteudo.
            val fecha = if (profundidade == 0) j - 1 else fim
            return Argumento(i + 1, fecha, minOf(j, fim))
        }

        if (origem[i] == '\\') {
            var j = i + 1
            if (j < fim && origem[j].isLetter()) {
                while (j < fim && origem[j].isLetter()) j++
            } else if (j < fim) {
                j++
            }
            return Argumento(i, j, j)
        }

        return Argumento(i, i + 1, i + 1)
    }
}
