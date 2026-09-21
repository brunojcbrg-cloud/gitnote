package io.github.wiiznokes.gitnote.ui.component.markdown

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder

/**
 * Fase I.2 -- o contrato de imagem, do lado puro.
 *
 * O mesmo contrato ja esta implementado na web (`notas-web`, `src/anexos.ts` e
 * `src/markdown.ts`, commit 2191b7f) e no Obsidian. Divergir daqui faz a mesma
 * nota abrir diferente em cada lugar, que e exatamente o que esta fase veio
 * consertar -- por isso cada caso abaixo espelha um teste de `tests/anexos.test.ts`.
 */
class AnexosTest {

    @get:Rule
    val pasta = TemporaryFolder()

    private val anexos = listOf(
        "$PASTA_ANEXOS/Pasted image 20260920093913.png",
        "$PASTA_ANEXOS/Ação do Antibiótico.PNG",
        "$PASTA_ANEXOS/mesmo-nome.png",
        "06_Conhecimento/Medicina/mesmo-nome.png",
    )

    private fun renderizar(fonte: String): String =
        preprocessarImagens(fonte) { alvo -> resolverAnexo(anexos, alvo) }

    /** Extrai o destino do primeiro `![...](destino)` do resultado. */
    private fun destinoDe(renderizado: String): String? {
        val abre = renderizado.indexOf("](")
        if (abre < 0) return null
        val fecha = renderizado.indexOf(')', abre + 2)
        if (fecha < 0) return null
        return renderizado.substring(abre + 2, fecha)
    }

    // ---------- resolucao ----------

    @Test
    fun nomeCurtoAchaOArquivoNaPastaDeAnexos() {
        assertEquals(
            "$PASTA_ANEXOS/Pasted image 20260920093913.png",
            resolverAnexo(anexos, "Pasted image 20260920093913.png"),
        )
    }

    @Test
    fun caixaEAcentoNaoAtrapalham() {
        // O arquivo esta gravado com acento e com PNG maiusculo; a nota cita em
        // minusculas. O Obsidian acha; os tres clientes tem de achar tambem.
        assertEquals(
            "$PASTA_ANEXOS/Ação do Antibiótico.PNG",
            resolverAnexo(anexos, "ação do antibiótico.png"),
        )
    }

    @Test
    fun caminhoCompletoVale() {
        assertEquals(
            "06_Conhecimento/Medicina/mesmo-nome.png",
            resolverAnexo(anexos, "06_Conhecimento/Medicina/mesmo-nome.png"),
        )
    }

    @Test
    fun nomeRepetidoPreferAPastaDeAnexos() {
        assertEquals("$PASTA_ANEXOS/mesmo-nome.png", resolverAnexo(anexos, "mesmo-nome.png"))
    }

    @Test
    fun barraInvertidaEPontoBarraSaoNormalizados() {
        assertEquals(
            "06_Conhecimento/Medicina/mesmo-nome.png",
            resolverAnexo(anexos, ".\\06_Conhecimento\\Medicina\\mesmo-nome.png"),
        )
    }

    @Test
    fun nomeInexistenteDevolveNulo() {
        assertNull(resolverAnexo(anexos, "nao-existe.png"))
    }

    @Test
    fun nomeInexistenteNaoQuebraANota() {
        val fonte = "antes\n![[nao-existe.png]]\ndepois"
        val saida = renderizar(fonte)
        assertFalse(saida.contains("gitnote://image"), "nao pode virar imagem")
        assertTrue(saida.startsWith("antes\n"), "o texto em volta continua: $saida")
        assertTrue(saida.endsWith("\ndepois"), "o texto em volta continua: $saida")
        assertTrue(saida.contains("nao-existe.png"), "o nome continua visivel: $saida")
    }

    // ---------- largura ----------

    @Test
    fun larguraSimples() {
        assertEquals(496, larguraDoRotulo("496"))
    }

    @Test
    fun larguraPorAlturaLeSoALargura() {
        assertEquals(800, larguraDoRotulo("800x600"))
    }

    @Test
    fun apelidoNaoNumericoNaoELargura() {
        assertNull(larguraDoRotulo("abc"))
        assertNull(larguraDoRotulo("496px"))
        assertNull(larguraDoRotulo(""))
    }

    @Test
    fun larguraDaNotaChegaAoEndereco() {
        val saida = renderizar("![[Pasted image 20260920093913.png|496]]")
        val pedido = assertNotNull(parseUriDeImagem(assertNotNull(destinoDe(saida))))
        assertEquals("$PASTA_ANEXOS/Pasted image 20260920093913.png", pedido.caminho)
        assertEquals(496, pedido.largura)
        assertTrue(
            saida.startsWith("![Pasted image 20260920093913.png]("),
            "sem apelido, o alt e o nome do arquivo: $saida",
        )
    }

    @Test
    fun larguraPorAlturaNaoGeraAltura() {
        val saida = renderizar("![[Pasted image 20260920093913.png|800x600]]")
        val pedido = assertNotNull(parseUriDeImagem(assertNotNull(destinoDe(saida))))
        assertEquals(800, pedido.largura)
        assertFalse(saida.contains("600"), "a altura nao pode ser gravada: $saida")
    }

    @Test
    fun apelidoNaoNumericoViraTextoAlternativo() {
        val saida = renderizar("![[mesmo-nome.png|Corte coronal]]")
        assertTrue(saida.startsWith("![Corte coronal]("), saida)
        val pedido = assertNotNull(parseUriDeImagem(assertNotNull(destinoDe(saida))))
        assertNull(pedido.largura)
    }

    @Test
    fun semLarguraOEnderecoSaiSemLargura() {
        val saida = renderizar("![[mesmo-nome.png]]")
        val pedido = assertNotNull(parseUriDeImagem(assertNotNull(destinoDe(saida))))
        assertNull(pedido.largura)
    }

    // ---------- imagem remota (decisao I.7) ----------

    @Test
    fun imagemRemotaNaoViraImagem() {
        val saida = renderizar("![diagrama](https://exemplo.com/foto.png)")
        assertFalse(saida.contains("gitnote://image"), saida)
        assertTrue(saida.contains("https://exemplo.com/foto.png"), saida)
    }

    @Test
    fun imagemRemotaEEscapadaParaAparecerComoTexto() {
        // Copiada crua, ela viraria um no de imagem que o transformador recusa e o
        // trecho sumiria da tela sem aviso nenhum.
        val saida = renderizar("![diagrama](http://exemplo.com/foto.png)")
        assertTrue(saida.startsWith("\\!\\["), "tem de sair escapada: $saida")
    }

    @Test
    fun dataUrlNaoViraImagem() {
        val saida = renderizar("![x](data:image/png;base64,AAAA)")
        assertFalse(saida.contains("gitnote://image"), saida)
    }

    @Test
    fun embedDeNotaNaoViraImagem() {
        val saida = renderizar("![[Outra Nota]]")
        assertFalse(saida.contains("gitnote://image"), saida)
        assertTrue(saida.contains("Outra Nota"), saida)
    }

    // ---------- imagem de markdown ----------

    @Test
    fun imagemDeMarkdownComCaminhoVale() {
        val saida = renderizar("![corte](06_Conhecimento/Medicina/mesmo-nome.png)")
        val pedido = assertNotNull(parseUriDeImagem(assertNotNull(destinoDe(saida))))
        assertEquals("06_Conhecimento/Medicina/mesmo-nome.png", pedido.caminho)
        assertTrue(saida.startsWith("![corte]("), saida)
    }

    @Test
    fun tituloDaImagemDeMarkdownNaoEntraNoCaminho() {
        val saida = renderizar("![corte](mesmo-nome.png \"legenda\")")
        val pedido = assertNotNull(parseUriDeImagem(assertNotNull(destinoDe(saida))))
        assertEquals("$PASTA_ANEXOS/mesmo-nome.png", pedido.caminho)
    }

    // ---------- varredura ----------

    @Test
    fun embedDentroDeCercaDeCodigoFicaComoEsta() {
        val fonte = "```\n![[mesmo-nome.png]]\n```"
        assertEquals(fonte, renderizar(fonte))
    }

    @Test
    fun embedDentroDeCodigoEmLinhaFicaComoEsta() {
        val fonte = "veja `![[mesmo-nome.png]]` aqui"
        assertEquals(fonte, renderizar(fonte))
    }

    @Test
    fun notaSemImagemNaoEAlterada() {
        val fonte = "# Titulo\n\ntexto com [[wikilink]] e **negrito**\n"
        assertEquals(fonte, renderizar(fonte))
    }

    @Test
    fun doisEmbedsNaMesmaLinha() {
        val saida = renderizar("![[mesmo-nome.png|100]] e ![[mesmo-nome.png|200]]")
        assertEquals(2, Regex("gitnote://image").findAll(saida).count(), saida)
        assertTrue(saida.contains("w=100"), saida)
        assertTrue(saida.contains("w=200"), saida)
        assertTrue(saida.contains(" e "), saida)
    }

    @Test
    fun oTextoEmVoltaDoEmbedNaoESeMexido() {
        val saida = renderizar("antes ![[mesmo-nome.png]] depois")
        assertTrue(saida.startsWith("antes !["), saida)
        assertTrue(saida.endsWith(" depois"), saida)
    }

    // ---------- endereco ----------

    @Test
    fun oEnderecoSobreviveAEspacoEAcento() {
        val caminho = "$PASTA_ANEXOS/Ação do Antibiótico.PNG"
        val pedido = assertNotNull(parseUriDeImagem(uriDeImagem(caminho, 496)))
        assertEquals(caminho, pedido.caminho)
        assertEquals(496, pedido.largura)
        assertFalse(uriDeImagem(caminho, null).contains(' '), "espaco cru quebraria o link")
    }

    @Test
    fun enderecoDeOutroEsquemaNaoEDeImagem() {
        assertNull(parseUriDeImagem("https://exemplo.com/foto.png"))
        assertNull(parseUriDeImagem("gitnote://note?name=Nota"))
        assertNull(parseUriDeImagem("file:///data/foto.png"))
    }

    // ---------- amostragem ----------

    @Test
    fun amostragemNuncaDesceAbaixoDaLarguraPedida() {
        // 4000 px para um contentor de 400: 8 -> 500 px, que ainda cobre os 400.
        assertEquals(8, calcularAmostragem(4000, 400))
        assertEquals(1, calcularAmostragem(300, 400))
        assertEquals(1, calcularAmostragem(799, 400))
        assertEquals(2, calcularAmostragem(800, 400))
        assertEquals(1, calcularAmostragem(0, 400))
        assertEquals(1, calcularAmostragem(4000, 0))
    }

    @Test
    fun amostragemDe4000Por3000CabeEmUmBitmapDe400() {
        val amostra = calcularAmostragem(4000, 400)
        val bytesDoOriginal = 4000L * 3000L * 4L
        val bytesAmostrado = (4000L / amostra) * (3000L / amostra) * 4L
        assertTrue(bytesAmostrado < bytesDoOriginal / 32, "amostrado: $bytesAmostrado")
        assertTrue((4000 / amostra) >= 400, "nao pode ficar menor que o contentor")
    }

    // ---------- listagem no disco ----------

    @Test
    fun listagemPegaSoAsImagensDaPastaDeAnexos() {
        val raiz = pasta.newFolder("repo")
        val destino = File(raiz, PASTA_ANEXOS)
        destino.mkdirs()
        File(destino, "uma.png").writeText("x")
        File(destino, "outra.JPG").writeText("x")
        File(destino, "leia-me.md").writeText("x")
        File(raiz, "solta.png").writeText("x")

        val listados = listarAnexos(raiz.path)
        assertEquals(
            listOf("$PASTA_ANEXOS/outra.JPG", "$PASTA_ANEXOS/uma.png"),
            listados.sorted(),
        )
    }

    @Test
    fun listagemAchaAPastaDeAnexosDepoisDaRenomeacaoDaParteV() {
        val raiz = pasta.newFolder("repo-renomeado")
        val destino = File(raiz, "NOTAS/_anexos")
        destino.mkdirs()
        File(destino, "uma.png").writeText("x")

        assertEquals(listOf("NOTAS/_anexos/uma.png"), listarAnexos(raiz.path))
    }

    @Test
    fun listagemDeRaizInexistenteVoltaVazia() {
        assertEquals(emptyList(), listarAnexos(""))
        assertEquals(emptyList(), listarAnexos(File(pasta.root, "nao-existe").path))
    }

    @Test
    fun caminhoCompletoNoDiscoValeMesmoForaDaPastaDeAnexos() {
        val raiz = pasta.newFolder("repo-completo")
        val destino = File(raiz, "06_Conhecimento/Medicina")
        destino.mkdirs()
        File(destino, "foto.png").writeText("x")

        assertEquals(
            "06_Conhecimento/Medicina/foto.png",
            resolverAnexoNoRepo(raiz.path, emptyList(), "06_Conhecimento/Medicina/foto.png"),
        )
        assertNull(resolverAnexoNoRepo(raiz.path, emptyList(), "06_Conhecimento/Medicina/nao.png"))
    }

    @Test
    fun caminhoQueSobeNaArvoreERecusado() {
        val raiz = pasta.newFolder("repo-seguro")
        assertNull(resolverAnexoNoRepo(raiz.path, emptyList(), "../fora/foto.png"))
        assertNull(resolverAnexoNoRepo(raiz.path, emptyList(), "/etc/foto.png"))
    }

    @Test
    fun ehImagemReconheceAsExtensoesDoContrato() {
        assertTrue(ehImagem("a.png"))
        assertTrue(ehImagem("a.JPEG"))
        assertTrue(ehImagem("a.webp"))
        assertTrue(ehImagem("a.svg"))
        assertFalse(ehImagem("a.md"))
        assertFalse(ehImagem("a"))
        assertFalse(ehImagem("a.png.md"))
    }
}
