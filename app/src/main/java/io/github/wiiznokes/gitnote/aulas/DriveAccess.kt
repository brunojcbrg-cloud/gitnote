package io.github.wiiznokes.gitnote.aulas

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
const val LIFE_SO_AULAS_FOLDER = "LifeSO_Aulas"

val lessonJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = true
    prettyPrint = true
}

data class DriveAuthorizationResult(
    val accessToken: String? = null,
    val resolution: PendingIntent? = null,
)

class DriveAuthorization(context: Context) {
    private val client = Identity.getAuthorizationClient(context)
    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
        .build()

    fun request(callback: (Result<DriveAuthorizationResult>) -> Unit) {
        client.authorize(request)
            .addOnSuccessListener { result ->
                callback(Result.success(DriveAuthorizationResult(result.accessToken, result.pendingIntent)))
            }
            .addOnFailureListener { callback(Result.failure(it)) }
    }

    fun finishResolution(data: Intent?): Result<String> = runCatching {
        requireNotNull(data) { "Autorizacao cancelada." }
        requireNotNull(client.getAuthorizationResultFromIntent(data).accessToken) {
            "O Google nao devolveu um token do Drive."
        }
    }

    fun tokenBlocking(): DriveAuthorizationResult {
        val result = Tasks.await(client.authorize(request), 45, TimeUnit.SECONDS)
        return DriveAuthorizationResult(result.accessToken, result.pendingIntent)
    }
}

class LessonJobStore(private val context: Context) {
    private val directory = File(context.filesDir, "aulas/jobs").apply { mkdirs() }

    fun save(job: LessonUploadJob): File {
        val target = File(directory, "${job.idAula}.json")
        val temporary = File(directory, ".${job.idAula}.${System.nanoTime()}.tmp")
        temporary.writeText(lessonJson.encodeToString(job), Charsets.UTF_8)
        check(temporary.renameTo(target)) { "Nao foi possivel persistir a aula para envio." }
        return target
    }

    fun read(path: String): LessonUploadJob =
        lessonJson.decodeFromString(File(path).readText(Charsets.UTF_8))
}

class DriveRestClient(
    private val context: Context,
    private val accessToken: String,
) {
    private val filesUrl = "https://www.googleapis.com/drive/v3/files"
    private val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files"

    fun ensureRootAndStateFile(): String {
        val root = findFirst("name = '${escapeQuery(LIFE_SO_AULAS_FOLDER)}' and mimeType = 'application/vnd.google-apps.folder' and trashed = false")
            ?: createFolder(LIFE_SO_AULAS_FOLDER, null, null)
        val state = findFirst("name = 'estado_aulas.json' and '$root' in parents and trashed = false")
        if (state == null) {
            uploadBytes(
                name = "estado_aulas.json",
                parent = root,
                mimeType = "application/json",
                bytes = "{\"schema\":1,\"atualizado_em\":\"\",\"pc_publicado_em\":\"\",\"aulas\":[]}".toByteArray(),
                appProperties = mapOf("lifeSoKind" to "state"),
            )
        }
        return root
    }

    fun uploadLesson(job: LessonUploadJob) {
        val root = ensureRootAndStateFile()
        val folderQuery = "appProperties has { key='lifeSoLessonId' and value='${escapeQuery(job.idAula)}' } and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        val lessonFolder = findFirst(folderQuery)
            ?: createFolder(job.pastaDrive, root, mapOf("lifeSoLessonId" to job.idAula))
        job.arquivos.forEachIndexed { index, file ->
            val fileKey = "$index:${file.nome}:${file.bytes}"
            val existing = findFirst(
                "appProperties has { key='lifeSoLessonId' and value='${escapeQuery(job.idAula)}' } and " +
                    "appProperties has { key='lifeSoFileKey' and value='${escapeQuery(fileKey)}' } and trashed = false"
            )
            if (existing == null) uploadContentUri(file, lessonFolder, job.idAula, fileKey)
        }
        val ready = lessonJson.encodeToString(job.readyMarker()).toByteArray(Charsets.UTF_8)
        val readyId = findFirst("name = 'PRONTO.json' and '$lessonFolder' in parents and trashed = false")
        if (readyId == null) {
            uploadBytes(
                "PRONTO.json", lessonFolder, "application/json", ready,
                mapOf("lifeSoLessonId" to job.idAula, "lifeSoKind" to "ready"),
            )
        } else {
            updateBytes(readyId, "application/json", ready)
        }
    }

    fun downloadState(): MobileLessonState {
        val root = ensureRootAndStateFile()
        val id = findFirst("name = 'estado_aulas.json' and '$root' in parents and trashed = false")
            ?: return MobileLessonState()
        val connection = connection("$filesUrl/$id?alt=media", "GET")
        return connection.useResponse { input -> lessonJson.decodeFromString(input.bufferedReader().readText()) }
    }

    private fun uploadContentUri(file: LessonFile, parent: String, lessonId: String, fileKey: String) {
        val uri = Uri.parse(file.uri)
        val metadata = metadata(
            file.nome, parent,
            mapOf("lifeSoLessonId" to lessonId, "lifeSoFileKey" to fileKey),
        ).toByteArray(Charsets.UTF_8)
        val boundary = "LifeSO-${System.nanoTime()}"
        val prefix = ("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" +
            metadata.toString(Charsets.UTF_8) + "\r\n--$boundary\r\nContent-Type: ${file.mimeType ?: "application/octet-stream"}\r\n\r\n")
            .toByteArray(Charsets.UTF_8)
        val suffix = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        val connection = connection("$uploadUrl?uploadType=multipart&fields=id", "POST")
        connection.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        connection.doOutput = true
        connection.setFixedLengthStreamingMode(prefix.size.toLong() + file.bytes + suffix.size)
        connection.outputStream.use { output ->
            output.write(prefix)
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Nao consegui reabrir ${file.nome}." }
                input.copyTo(output)
            }
            output.write(suffix)
        }
        connection.requireSuccess()
    }

    private fun uploadBytes(name: String, parent: String, mimeType: String, bytes: ByteArray, appProperties: Map<String, String>): String {
        val boundary = "LifeSO-${System.nanoTime()}"
        val meta = metadata(name, parent, appProperties)
        val body = ("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n" +
            "--$boundary\r\nContent-Type: $mimeType\r\n\r\n").toByteArray() + bytes +
            "\r\n--$boundary--\r\n".toByteArray()
        val connection = connection("$uploadUrl?uploadType=multipart&fields=id", "POST")
        connection.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        connection.doOutput = true
        connection.outputStream.use { it.write(body) }
        return connection.useResponse { input ->
            lessonJson.parseToJsonElement(input.bufferedReader().readText()).jsonObject["id"]!!.jsonPrimitive.content
        }
    }

    private fun updateBytes(id: String, mimeType: String, bytes: ByteArray) {
        val connection = connection("$uploadUrl/$id?uploadType=media", "PATCH")
        connection.setRequestProperty("Content-Type", mimeType)
        connection.doOutput = true
        connection.outputStream.use { it.write(bytes) }
        connection.requireSuccess()
    }

    private fun createFolder(name: String, parent: String?, appProperties: Map<String, String>?): String {
        val json = buildJsonObject {
            put("name", name)
            put("mimeType", "application/vnd.google-apps.folder")
            if (parent != null) put("parents", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(parent)) })
            if (appProperties != null) put("appProperties", buildJsonObject { appProperties.forEach { (k, v) -> put(k, v) } })
        }.toString()
        val connection = connection("$filesUrl?fields=id", "POST")
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        connection.doOutput = true
        connection.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        return connection.useResponse { input ->
            lessonJson.parseToJsonElement(input.bufferedReader().readText()).jsonObject["id"]!!.jsonPrimitive.content
        }
    }

    private fun findFirst(query: String): String? {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8)
        val connection = connection("$filesUrl?q=$encoded&spaces=drive&fields=files(id,name)&pageSize=10", "GET")
        return connection.useResponse { input ->
            lessonJson.parseToJsonElement(input.bufferedReader().readText()).jsonObject["files"]
                ?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
        }
    }

    private fun metadata(name: String, parent: String, properties: Map<String, String>): String = buildJsonObject {
        put("name", name)
        put("parents", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(parent)) })
        put("appProperties", buildJsonObject { properties.forEach { (key, value) -> put(key, value) } })
    }.toString()

    private fun connection(url: String, method: String): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000
            readTimeout = 120_000
            setRequestProperty("Authorization", "Bearer $accessToken")
        }

    private inline fun <T> HttpURLConnection.useResponse(block: (java.io.InputStream) -> T): T {
        try {
            requireSuccess()
            return inputStream.use(block)
        } finally {
            disconnect()
        }
    }

    private fun HttpURLConnection.requireSuccess() {
        if (responseCode !in 200..299) {
            val detail = errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw DriveHttpException(responseCode, detail)
        }
    }

    private fun escapeQuery(value: String): String = value.replace("\\", "\\\\").replace("'", "\\'")
}

class DriveHttpException(val status: Int, detail: String) : IOException("Drive HTTP $status: ${detail.take(500)}")
