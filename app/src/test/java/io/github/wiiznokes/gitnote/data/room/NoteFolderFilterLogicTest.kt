package io.github.wiiznokes.gitnote.data.room

import android.app.Application
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Particionamento de caminho por pasta **exata** — a conta que a funcao SQLite
 * customizada `parentPath` faz e que `Note.parentPath()` (Schema.kt) repete
 * (`substringBeforeLast("/", missingDelimiterValue = "")`).
 *
 * Ela entrou na Fase B como filtro de `gridNotes`. Desde a **Fase K.2** a listagem da
 * grade voltou a ser recursiva (ver `GradeSqlTest`, que a executa de verdade), e quem
 * continua usando `parentPath` sao `drawerFolders` e `noteFolders`, a listagem de
 * **subpastas diretas** — inclusive as pastas que a Fase K.1 passou a mostrar na grade.
 * Essa parte continua sem poder rodar no CI (`parentPath`/`fullName` so carregam via
 * `RequerySQLiteOpenHelperFactory`; ver ESTADO_10.md), entao a logica e exercitada aqui
 * contra a arvore sintetica da Fase B (raiz com 3, `A/` com 5, `A/B/` com 7, `A2/` com 2).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NoteFolderFilterLogicTest {

    private fun arvoreSintetica(): List<Note> {
        val raiz = List(3) { Note.new(relativePath = "raiz_$it.md") }
        val pastaA = List(5) { Note.new(relativePath = "A/nota_$it.md") }
        val pastaAB = List(7) { Note.new(relativePath = "A/B/nota_$it.md") }
        val pastaA2 = List(2) { Note.new(relativePath = "A2/nota_$it.md") }
        return raiz + pastaA + pastaAB + pastaA2
    }

    /** Filhos diretos da raiz — nao e mais o que a grade mostra, e sim como a gaveta parte a arvore. */
    @Test
    fun abrirRaizDevolveSoAsNotasSoltasDaRaiz() {
        val notas = arvoreSintetica()

        val naRaiz = notas.filter { it.parentPath() == "" }

        assertEquals(3, naRaiz.size, "filhos diretos da raiz sao 3; o vault inteiro tem 17")
    }

    @Test
    fun abrirPastaADevolveSoOsFilhosDiretosDeA() {
        val notas = arvoreSintetica()

        val emA = notas.filter { it.parentPath() == "A" }

        assertEquals(5, emA.size, "filhos diretos de A sao 5; A + A/B (o que a grade lista desde a K.2) sao 12")
        assertTrue(emA.all { it.relativePath.startsWith("A/") && !it.relativePath.startsWith("A/B/") })
    }

    @Test
    fun abrirPastaANaoTrasNadaDeA2() {
        val notas = arvoreSintetica()

        val emA = notas.filter { it.parentPath() == "A" }

        assertFalse(emA.any { it.relativePath.startsWith("A2") }, "A2 nao e filha de A")
    }

    /**
     * `gridNotesWithQuery` continua recursivo (busca em subpastas) e por isso nao pode
     * usar igualdade de `parentPath`; a Fase B troca o `LIKE :path || '%'` (que casava
     * "Medicina2" ao abrir "Medicina") por `LIKE :path || '/%'` com excecao pra raiz.
     * Aqui a mesma comparacao e feita com `startsWith`, que e a semantica do `LIKE` do
     * SQLite para esse padrao sem curingas no meio.
     */
    @Test
    fun buscaRecursivaNaoCasaPastaComPrefixoParecido() {
        val relativePath = "Medicina2/nota.md"
        val pastaAberta = "Medicina"

        val padraoAntigoComDefeito = relativePath.startsWith(pastaAberta)
        val padraoNovoCorrigido = relativePath.startsWith("$pastaAberta/")

        assertTrue(padraoAntigoComDefeito, "documenta o defeito: o padrao antigo casava Medicina2")
        assertFalse(padraoNovoCorrigido, "o padrao novo nao pode casar Medicina2 ao abrir Medicina")
    }

    @Test
    fun buscaRecursivaNaRaizContinuaVendoTudo() {
        val notas = arvoreSintetica()
        val pastaAberta = ""

        // regra da Fase B: (:path = '' OR relativePath LIKE :path || '/%')
        val visiveisNaBusca = notas.filter { note ->
            pastaAberta == "" || note.relativePath.startsWith("$pastaAberta/")
        }

        assertEquals(notas.size, visiveisNaBusca.size, "na raiz a busca continua recursiva sobre tudo")
    }

    @Test
    fun buscaRecursivaEmAContinuaAchandoOQueEstaEmABSemPegarA2() {
        val notas = arvoreSintetica()
        val pastaAberta = "A"

        val visiveisNaBusca = notas.filter { note ->
            pastaAberta == "" || note.relativePath.startsWith("$pastaAberta/")
        }

        // 5 de A + 7 de A/B = 12, nada de A2
        assertEquals(12, visiveisNaBusca.size)
        assertFalse(visiveisNaBusca.any { it.relativePath.startsWith("A2") })
    }
}
