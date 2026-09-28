package io.github.wiiznokes.gitnote.aulas

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import java.io.File

const val MATERIALS_MANIFEST_PATH = "05_Sistema/notas-web/materiais.json"

@Serializable
data class MaterialEntry(
    val id: String,
    val name: String,
    val size: Long,
    val modifiedTime: String,
    val caminho: String,
    val tipo: String? = null,
) {
    val isHtml: Boolean get() = name.endsWith(".html", ignoreCase = true)
}

@Serializable
data class MaterialsManifest(
    val versao: Int,
    @SerialName("geradoEm") val generatedAt: String,
    val arquivos: List<MaterialEntry>,
)

fun parseMaterialsManifest(text: String): MaterialsManifest {
    val manifest = lessonJson.decodeFromString<MaterialsManifest>(text)
    require(manifest.versao == 1 || manifest.versao == 2) { "Versão desconhecida do manifesto." }
    require(manifest.arquivos.map { it.caminho }.distinct().size == manifest.arquivos.size) {
        "Manifesto contém caminhos repetidos."
    }
    manifest.arquivos.forEach { item ->
        require(item.id.matches(Regex("[A-Za-z0-9_-]+"))) { "ID inválido no manifesto." }
        val parts = item.caminho.split('/')
        require(parts.size >= 2 && parts.none { it.isBlank() || it == "." || it == ".." || '\\' in it }) {
            "Caminho inválido no manifesto."
        }
        require(parts.last() == item.name && item.size >= 0) { "Material inválido no manifesto." }
    }
    return manifest
}

data class CacheEntry(val id: String, val bytes: Long, val lastUsed: Long)

fun cacheEvictions(
    entries: List<CacheEntry>,
    limitBytes: Long,
    incomingBytes: Long,
    keepId: String,
): List<String> {
    var used = entries.filter { it.id != keepId }.sumOf { it.bytes } + incomingBytes
    if (used <= limitBytes) return emptyList()
    val removals = mutableListOf<String>()
    entries.filter { it.id != keepId }.sortedBy { it.lastUsed }.forEach { entry ->
        if (used > limitBytes) {
            removals += entry.id
            used -= entry.bytes
        }
    }
    return removals
}

class MaterialCache(private val context: Context) {
    private val directory = File(context.filesDir, "materiais-html").apply { mkdirs() }
    private val preferences = context.getSharedPreferences("materiais_html", Context.MODE_PRIVATE)

    fun file(id: String): File = File(directory, "$id.html")

    fun read(id: String): String? = file(id).takeIf(File::isFile)?.also {
        it.setLastModified(System.currentTimeMillis())
    }?.readText(Charsets.UTF_8)

    fun store(id: String, bytes: ByteArray): File {
        val limit = preferences.getInt("limit_mb", 250).coerceIn(50, 2_000) * 1_000_000L
        require(bytes.size.toLong() <= limit) { "Material maior que o limite do cache offline." }
        val entries = directory.listFiles().orEmpty().filter(File::isFile).map {
            CacheEntry(it.nameWithoutExtension, it.length(), it.lastModified())
        }
        cacheEvictions(entries, limit, bytes.size.toLong(), id).forEach { file(it).delete() }
        val target = file(id)
        val temporary = File(directory, ".$id.${System.nanoTime()}.tmp")
        temporary.writeBytes(bytes)
        if (target.exists()) check(target.delete()) { "Não foi possível atualizar o material offline." }
        check(temporary.renameTo(target)) { "Não foi possível guardar o material offline." }
        return target
    }

    fun settings(): Pair<Int, Boolean> =
        preferences.getInt("limit_mb", 250) to preferences.getBoolean("any_network", false)

    fun updateSettings(limitMb: Int, anyNetwork: Boolean) {
        preferences.edit()
            .putInt("limit_mb", limitMb.coerceIn(50, 2_000))
            .putBoolean("any_network", anyNetwork)
            .apply()
    }
}
