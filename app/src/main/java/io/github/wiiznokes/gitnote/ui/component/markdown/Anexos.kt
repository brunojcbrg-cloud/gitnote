package io.github.wiiznokes.gitnote.ui.component.markdown

import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/**
 * Imagens embutidas na nota (Fase I.1/I.2 do handoff 10).
 *
 * Este arquivo é a metade pura: acha o embed no texto, resolve o nome do arquivo
 * e reescreve o trecho como imagem de markdown com um endereço que só o
 * transformador do app entende. Nada aqui toca em Android, para poder ser testado
 * na JVM -- `GitManager` carrega `git_wrapper` e derruba qualquer teste que
 * construa a ViewModel de verdade.
 *
 * O contrato é o mesmo dos outros dois clientes (Obsidian e `notas-web`,
 * `src/anexos.ts` + `src/markdown.ts`, commit 2191b7f):
 *
 * - `![[nome.png]]` resolve **pelo nome** dentro da pasta de anexos;
 * - `![[nome.png|496]]` e `|800x600` viram largura (só a largura é usada);
 * - apelido que não é número vira texto alternativo;
 * - `![alt](caminho/nome.png)` vale, com caminho relativo à raiz do repositório;
 * - imagem remota **não** vira imagem (decisão I.7, 20/09/2026): entregar o IP do
 *   leitor a um servidor de terceiro a cada abertura não vale a conveniência.
 */

/** Pasta única de anexos das notas, a mesma que o Obsidian e a web usam. */
const val PASTA_ANEXOS = "06_Conhecimento/_anexos"

/** Nome da pasta de anexos, usado para sobreviver à renomeação da Parte V. */
private const val NOME_DA_PASTA_DE_ANEXOS = "_anexos"

private const val ESQUEMA_IMAGEM = "gitnote"
private const val HOST_IMAGEM = "image"

private val TIPOS_DE_IMAGEM = setOf(
    "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "tif", "tiff",
)

data class PedidoDeImagem(
    /** Caminho relativo à raiz do repositório. */
    val caminho: String,
    /** Largura em dp gravada na nota, ou nulo para a largura natural. */
    val largura: Int?,
)

data class EmbedDeImagem(
    /** Início do `!` no texto de origem. */
    val inicio: Int,
    /** Fim exclusivo do trecho. */
    val fim: Int,
    /** Nome ou caminho citado pela nota. */
    val alvo: String,
    /** Apelido (wikilink) ou texto alternativo (imagem de markdown). */
    val rotulo: String,
    /** `true` quando o alvo aponta para fora do repositório. */
    val remoto: Boolean,
)

fun ehImagem(nome: String): Boolean {
    val ponto = nome.lastIndexOf('.')
    if (ponto < 0) return false
    return nome.substring(ponto + 1).lowercase(Locale.ROOT) in TIPOS_DE_IMAGEM
}

/** Só o nome do arquivo, aceitando barra normal ou invertida. */
fun nomeDoArquivo(alvo: String): String {
    val limpo = alvo.replace('\\', '/')
    return limpo.substring(limpo.lastIndexOf('/') + 1)
}

/** `496` ou `800x600`, as duas formas que o Obsidian grava ao arrastar a alça. */
fun larguraDoRotulo(rotulo: String): Int? {
    val bruto = rotulo.trim()
    if (bruto.isEmpty()) return null
    val casou = Regex("^(\\d{1,5})(?:[xX]\\d{1,5})?$").find(bruto) ?: return null
    return casou.groupValues[1].toIntOrNull()?.takeIf { it > 0 }
}

private fun normalizarAlvo(alvo: String): String =
    alvo.replace('\\', '/').removePrefix("./").trim()

/**
 * Resolve o alvo do embed contra a lista de caminhos conhecidos.
 *
 * A ordem é a mesma da web: caminho completo primeiro, depois nome dentro da
 * pasta de anexos, depois nome em qualquer lugar. Caixa não importa em nenhum dos
 * passos; acento **importa**, porque é assim nos outros dois clientes, e divergir
 * faria a mesma nota abrir diferente em cada lugar.
 */
fun resolverAnexo(caminhos: List<String>, alvo: String): String? {
    val limpo = normalizarAlvo(alvo)
    if (limpo.isEmpty()) return null
    caminhos.firstOrNull { it == limpo }?.let { return it }
    caminhos.firstOrNull { it.equals(limpo, ignoreCase = true) }?.let { return it }

    val nome = nomeDoArquivo(limpo).lowercase(Locale.ROOT)
    caminhos.firstOrNull {
        it.substringBeforeLast('/', "").endsWith(NOME_DA_PASTA_DE_ANEXOS) &&
            nomeDoArquivo(it).lowercase(Locale.ROOT) == nome
    }?.let { return it }

    return caminhos.firstOrNull { nomeDoArquivo(it).lowercase(Locale.ROOT) == nome }
}

/**
 * Lista os anexos publicados, em caminhos relativos à raiz.
 *
 * Tenta `06_Conhecimento/_anexos` e, se ela não existir, procura uma pasta
 * `_anexos` logo abaixo de qualquer pasta de topo -- é o que mantém isto de pé
 * quando a Parte V renomear `06_Conhecimento` para `NOTAS`.
 */
fun listarAnexos(raiz: String): List<String> {
    if (raiz.isBlank()) return emptyList()
    val raizFs = File(raiz)
    if (!raizFs.isDirectory) return emptyList()

    val pasta = File(raizFs, PASTA_ANEXOS).takeIf { it.isDirectory }
        ?: raizFs.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.map { File(it, NOME_DA_PASTA_DE_ANEXOS) }
            ?.firstOrNull { it.isDirectory }
        ?: return emptyList()

    val prefixo = pasta.toRelativeString(raizFs).replace('\\', '/')
    return pasta.listFiles()
        ?.asSequence()
        ?.filter { it.isFile && ehImagem(it.name) }
        ?.map { "$prefixo/${it.name}" }
        ?.sorted()
        ?.toList()
        ?: emptyList()
}

/**
 * Resolve contra a pasta de anexos e, se falhar, contra o caminho literal no
 * disco -- quem escreveu `![](06_Conhecimento/Medicina/foto.png)` continua sendo
 * atendido.
 */
fun resolverAnexoNoRepo(raiz: String, caminhos: List<String>, alvo: String): String? {
    resolverAnexo(caminhos, alvo)?.let { return it }
    if (raiz.isBlank()) return null
    val limpo = normalizarAlvo(decodificarPercentual(alvo))
    if (limpo.isEmpty() || !limpo.contains('/') || limpo.startsWith("/") || limpo.contains("..")) {
        return null
    }
    return limpo.takeIf { ehImagem(it) && File(File(raiz), it).isFile }
}

/** Endereço que só o transformador de imagem do app entende. */
fun uriDeImagem(caminho: String, largura: Int?): String {
    val consulta = buildList {
        add("path=" + encodeQueryValue(caminho))
        if (largura != null) add("w=$largura")
    }.joinToString("&")
    return "$ESQUEMA_IMAGEM://$HOST_IMAGEM?$consulta"
}

fun parseUriDeImagem(valor: String): PedidoDeImagem? {
    val uri = runCatching { URI(valor) }.getOrNull() ?: return null
    if (!uri.scheme.equals(ESQUEMA_IMAGEM, ignoreCase = true)) return null
    if (!uri.host.equals(HOST_IMAGEM, ignoreCase = true)) return null
    val caminho = queryValue(uri.rawQuery, "path")?.takeIf { it.isNotBlank() } ?: return null
    val largura = queryValue(uri.rawQuery, "w")?.toIntOrNull()?.takeIf { it > 0 }
    return PedidoDeImagem(caminho = caminho, largura = largura)
}

/**
 * Quantas vezes dividir a imagem ao decodificar para não estourar a memória.
 *
 * Uma foto de celular de 4 MB descomprime em dezenas de MB de bitmap. A amostra é
 * sempre potência de dois (é o que o `BitmapFactory` aceita) e **nunca** desce
 * abaixo da largura pedida: sub-amostrar demais borra a imagem na tela.
 */
fun calcularAmostragem(larguraOriginal: Int, larguraAlvo: Int): Int {
    if (larguraOriginal <= 0 || larguraAlvo <= 0) return 1
    var amostra = 1
    while (larguraOriginal / (amostra * 2) >= larguraAlvo) amostra *= 2
    return amostra
}

/**
 * Acha os embeds de imagem do texto.
 *
 * Varredura linear própria, e não o [MarkdownScanner]: ele ignora `![[` de
 * propósito (a guarda do `!` na abertura do wikilink) e é o mesmo scanner do modo
 * de edição, onde o custo por tecla já foi medido na Fase H. Somar um tipo novo
 * lá mexeria na prévia ao vivo, que não é o escopo desta fase.
 *
 * Cerca de código e código em linha ficam de fora: dentro deles o texto é literal.
 */
fun acharEmbeds(source: String): List<EmbedDeImagem> {
    if (source.isEmpty()) return emptyList()
    val achados = mutableListOf<EmbedDeImagem>()
    var dentroDeCerca = false
    var inicioDaLinha = 0

    while (inicioDaLinha <= source.length) {
        var fimDaLinha = inicioDaLinha
        while (fimDaLinha < source.length && source[fimDaLinha] != '\n') fimDaLinha++

        var primeiro = inicioDaLinha
        while (primeiro < fimDaLinha && (source[primeiro] == ' ' || source[primeiro] == '\t')) primeiro++
        val ehCerca = primeiro + 3 <= fimDaLinha && source.startsWith("```", primeiro)

        if (ehCerca) {
            dentroDeCerca = !dentroDeCerca
        } else if (!dentroDeCerca) {
            acharEmbedsNaLinha(source, inicioDaLinha, fimDaLinha, achados)
        }

        if (fimDaLinha == source.length) break
        inicioDaLinha = fimDaLinha + 1
    }
    return achados
}

private fun acharEmbedsNaLinha(
    source: String,
    inicio: Int,
    fim: Int,
    achados: MutableList<EmbedDeImagem>,
) {
    var i = inicio
    while (i < fim) {
        val atual = source[i]
        if (atual == '`') {
            val fechamento = source.indexOf('`', i + 1)
            i = if (fechamento in (i + 1) until fim) fechamento + 1 else i + 1
            continue
        }
        if (atual != '!' || i + 1 >= fim || source[i + 1] != '[') {
            i++
            continue
        }
        if (i > inicio && source[i - 1] == '\\') {
            i++
            continue
        }

        val embed = embedDeWikilink(source, i, fim) ?: embedDeMarkdown(source, i, fim)
        if (embed == null) {
            i++
        } else {
            achados += embed
            i = embed.fim
        }
    }
}

/** `![[nome.png|496]]` */
private fun embedDeWikilink(source: String, inicio: Int, fim: Int): EmbedDeImagem? {
    if (inicio + 3 > fim || !source.startsWith("![[", inicio)) return null
    val conteudoInicio = inicio + 3
    val fechamento = source.indexOf("]]", conteudoInicio)
    if (fechamento < 0 || fechamento + 2 > fim) return null

    val conteudo = source.substring(conteudoInicio, fechamento)
    val separador = conteudo.indexOf('|')
    val antesDoApelido = (if (separador < 0) conteudo else conteudo.substring(0, separador)).trim()
    val apelido = if (separador < 0) "" else conteudo.substring(separador + 1).trim()
    val corte = antesDoApelido.indexOf('#')
    val alvo = if (corte < 0) antesDoApelido else antesDoApelido.substring(0, corte).trim()
    if (alvo.isEmpty()) return null

    return EmbedDeImagem(
        inicio = inicio,
        fim = fechamento + 2,
        alvo = alvo,
        rotulo = apelido,
        remoto = ehRemoto(alvo),
    )
}

/** `![alt](caminho/nome.png "titulo")` */
private fun embedDeMarkdown(source: String, inicio: Int, fim: Int): EmbedDeImagem? {
    val textoInicio = inicio + 2
    var i = textoInicio
    while (i < fim && source[i] != ']') i++
    if (i + 1 >= fim || source[i + 1] != '(') return null
    val textoFim = i
    val destinoInicio = i + 2
    var j = destinoInicio
    while (j < fim && source[j] != ')') j++
    if (j >= fim) return null

    val destinoBruto = source.substring(destinoInicio, j).trim()
    val destino = semTitulo(destinoBruto)
    if (destino.isEmpty()) return null

    return EmbedDeImagem(
        inicio = inicio,
        fim = j + 1,
        alvo = destino,
        rotulo = source.substring(textoInicio, textoFim),
        remoto = ehRemoto(destino),
    )
}

/** `caminho.png "titulo"` -> `caminho.png`. Sem título, devolve o que veio. */
private fun semTitulo(destino: String): String {
    val aspas = destino.indexOfFirst { it == '"' || it == '\'' }
    if (aspas <= 0) return destino
    return destino.substring(0, aspas).trim()
}

/**
 * Alvo que aponta para fora do repositório. A lista é curta de propósito, como na
 * web: qualquer `esquema:` viraria remoto e um nome de arquivo com dois-pontos
 * deixaria de renderizar sem explicação.
 */
private val ESQUEMAS_DE_FORA = Regex("^(?:https?|data|file|ftp|blob|mailto):", RegexOption.IGNORE_CASE)

private fun ehRemoto(alvo: String): Boolean {
    val limpo = alvo.trim()
    return limpo.contains("://") || ESQUEMAS_DE_FORA.containsMatchIn(limpo)
}

/**
 * Reescreve cada embed resolvido como imagem de markdown apontando para
 * [uriDeImagem]. O que não resolve -- imagem remota, anexo que ainda não
 * sincronizou, embed de nota -- continua visível como texto: sumir em silêncio é
 * pior que aparecer cru, e a nota não pode quebrar por causa de um arquivo que
 * falta.
 */
fun preprocessarImagens(source: String, resolver: (String) -> String?): String {
    val embeds = acharEmbeds(source)
    if (embeds.isEmpty()) return source

    return buildString(source.length + embeds.size * 32) {
        var cursor = 0
        embeds.forEach { embed ->
            append(source, cursor, embed.inicio)
            cursor = embed.fim
            val caminho = if (embed.remoto || !ehImagem(embed.alvo)) {
                null
            } else {
                resolver(embed.alvo)
            }
            if (caminho == null) {
                // Escapado, e nao copiado cru: `![alt](https://...)` cru vira um no
                // de imagem que o transformador recusa, e o trecho sumiria da tela
                // sem aviso. Escapado, ele aparece como texto -- que e o que a web
                // faz com imagem remota e com anexo que nao resolveu.
                append(escaparLiteral(source.substring(embed.inicio, embed.fim)))
                return@forEach
            }
            val largura = larguraDoRotulo(embed.rotulo)
            val alt = if (embed.rotulo.isNotBlank() && largura == null) {
                embed.rotulo
            } else {
                nomeDoArquivo(embed.alvo)
            }
            append("![")
            append(escapeMarkdownLinkText(alt))
            append("](")
            append(uriDeImagem(caminho, largura))
            append(')')
        }
        append(source, cursor, source.length)
    }
}

/** Impede que o trecho nao convertido seja lido como marcacao pelo renderizador. */
private fun escaparLiteral(texto: String): String {
    if (texto.none { it in "*_`[]!\\" }) return texto
    return buildString(texto.length + 8) {
        texto.forEach { caractere ->
            if (caractere in "*_`[]!\\") append('\\')
            append(caractere)
        }
    }
}

private fun decodificarPercentual(valor: String): String =
    if ('%' !in valor) {
        valor
    } else {
        runCatching { URLDecoder.decode(valor, Charsets.UTF_8.name()) }.getOrDefault(valor)
    }
