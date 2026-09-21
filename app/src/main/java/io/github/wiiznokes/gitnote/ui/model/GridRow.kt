package io.github.wiiznokes.gitnote.ui.model

/**
 * Linha da grade/lista de notas, sem o conteudo do arquivo (Fase B do handoff 10).
 * Quem precisa do [io.github.wiiznokes.gitnote.data.room.Note] inteiro busca sob
 * demanda com `dao.noteByRelativePath`.
 */
data class GridRow(
    val relativePath: String,
    val id: Int,
    val lastModifiedTimeMillis: Long,
    val isUnique: Boolean,
    val selected: Boolean = false,
) {

    fun fullName(): String {
        return relativePath.substringAfterLast("/")
    }

    fun fileExtension(): FileExtension {
        return relativePath.substringAfterLast(".", missingDelimiterValue = "")
            .let { FileExtension.match(it) }
    }

    fun nameWithoutExtension(): String {
        val fullName = fullName()
        return fullName.substring(
            startIndex = 0,
            endIndex = fullName.length - (fileExtension().text.length + 1)
        )
    }

    /**
     * Titulo do card quando a listagem e recursiva (Fase K.2): o caminho a partir da
     * pasta que esta aberta, sem a extensao.
     *
     * Nota solta na propria pasta continua aparecendo so com o nome; nota que veio de
     * uma descendente aparece como `Subpasta/nota`. Sem isso, duas notas de mesmo nome
     * em subpastas diferentes ficariam identicas na tela — e o desambiguador antigo
     * (funcao de janela sobre o conjunto inteiro) nao pode voltar, porque era metade do
     * custo que a Fase B tirou do caminho quente.
     */
    fun tituloRelativoA(pastaAberta: String): String {
        val semPasta = if (pastaAberta.isEmpty()) {
            relativePath
        } else {
            relativePath.removePrefix("$pastaAberta/")
        }

        val extensao = fileExtension().text
        return if (extensao.isNotEmpty() && semPasta.endsWith(".$extensao")) {
            semPasta.dropLast(extensao.length + 1)
        } else {
            semPasta
        }
    }
}
