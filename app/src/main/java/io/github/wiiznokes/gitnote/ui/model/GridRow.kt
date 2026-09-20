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
}
