package io.github.wiiznokes.gitnote.aulas

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

suspend fun readLessonFile(context: Context, uri: Uri): LessonFile = withContext(Dispatchers.IO) {
    var name = uri.lastPathSegment ?: "arquivo"
    var size = -1L
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
        }
    }
    if (size < 0) {
        size = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull()?.takeIf { it >= 0 } ?: -1L
    }
    if (size < 0) {
        size = runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
        }.getOrNull()?.takeIf { it >= 0 } ?: -1L
    }
    if (size < 0) throw IOException("O provedor nao informou o tamanho de $name.")
    val stamp = runCatching {
        context.contentResolver.openInputStream(uri)?.use { RecordingMetadataReader.read(name, it) }
    }.getOrNull()
    LessonFile(
        uri = uri.toString(),
        nome = name,
        bytes = size,
        gravadoEm = stamp?.value,
        fonteData = stamp?.source,
        mimeType = context.contentResolver.getType(uri),
    )
}
