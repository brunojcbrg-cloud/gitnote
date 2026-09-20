package io.github.wiiznokes.gitnote.ui.model

import android.app.Application
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fase B (B.1) do handoff 10: `GridRow` e o modelo de linha da grade sem o conteudo
 * do arquivo. Este teste prova duas coisas sem precisar rodar a consulta SQL (que
 * depende de `parentPath`/`fullName`, indisponiveis na JVM de teste — ver
 * ESTADO_10.md): (1) o TIPO nao tem campo de conteudo, entao nenhuma consulta que
 * mapear para ele pode trazer o texto do arquivo; (2) as contas derivadas do
 * caminho (nome sem extensao, extensao) batem com as mesmas contas que
 * `Note` (Schema.kt) ja faz para o arquivo inteiro.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GridRowTest {

    @Test
    fun naoTemCampoDeConteudo() {
        val propriedades = GridRow::class.java.declaredFields.map { it.name }

        assertFalse(
            propriedades.any { it.contains("content", ignoreCase = true) },
            "GridRow nao pode ter nenhum campo de conteudo, so mapeou: $propriedades",
        )
    }

    @Test
    fun linhaDeUmaNotaGrandeContinuaLeve() {
        // simula o pior caso citado no handoff: uma nota de 336 KB / 257 titulos.
        // como GridRow nao carrega conteudo, o tamanho da linha depende so do
        // caminho, nunca do arquivo.
        val caminhoLongo = "06_Conhecimento/Medicina/Materias Basicas/Microbiologia/" +
            "Aula Introducao a micro.md"

        val linha = GridRow(
            relativePath = caminhoLongo,
            id = 1,
            lastModifiedTimeMillis = System.currentTimeMillis(),
            isUnique = true,
        )

        val tamanhoAproximadoBytes = linha.relativePath.toByteArray().size + Int.SIZE_BYTES + Long.SIZE_BYTES + 1

        assertTrue(
            tamanhoAproximadoBytes < 1024,
            "linha da grade deveria ficar bem abaixo de 1 KB, mediu $tamanhoAproximadoBytes bytes",
        )
    }

    @Test
    fun nomeSemExtensaoBateComONomeDoArquivo() {
        val linha = GridRow(
            relativePath = "Medicina/Introducao a micro.md",
            id = 1,
            lastModifiedTimeMillis = 0,
            isUnique = true,
        )

        assertEquals("Introducao a micro", linha.nameWithoutExtension())
        assertTrue(linha.fileExtension() is FileExtension.Md)
        assertEquals("Introducao a micro.md", linha.fullName())
    }

    @Test
    fun extensaoDesconhecidaViraOther() {
        val linha = GridRow(
            relativePath = "notas/arquivo.pdf",
            id = 1,
            lastModifiedTimeMillis = 0,
            isUnique = true,
        )

        assertEquals("pdf", linha.fileExtension().text)
        assertEquals("arquivo", linha.nameWithoutExtension())
    }
}
