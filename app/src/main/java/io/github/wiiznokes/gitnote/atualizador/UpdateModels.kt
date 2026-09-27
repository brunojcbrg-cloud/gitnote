package io.github.wiiznokes.gitnote.atualizador

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

const val UPDATE_RELEASES_URL =
    "https://api.github.com/repos/brunojcbrg-cloud/gitnote/releases/latest"
const val UPDATE_CHECK_INTERVAL_SECONDS = 3_600

private val updateJson = Json { ignoreUnknownKeys = true }
private val tagPattern = Regex("^b(\\d+)$")
private val versionCodePattern = Regex("versionCode\\s*\\|\\s*`?(\\d+)`?")

@Serializable
data class GitHubReleaseAsset(
    val name: String,
    val size: Long,
    val digest: String? = null,
    @SerialName("browser_download_url") val downloadUrl: String,
)

@Serializable
data class GitHubReleaseResponse(
    @SerialName("tag_name") val tagName: String,
    val body: String = "",
    val assets: List<GitHubReleaseAsset> = emptyList(),
)

data class UpdateRelease(
    val buildNumber: Int,
    val versionCode: Long,
    val asset: GitHubReleaseAsset,
) {
    val tag: String get() = "b$buildNumber"
}

sealed interface UpdateEvaluation {
    data class Available(val release: UpdateRelease) : UpdateEvaluation
    data object Current : UpdateEvaluation
    data class Rejected(val reason: String) : UpdateEvaluation
    data object DisabledBuild : UpdateEvaluation
}

sealed interface ReleaseHttpOutcome {
    data class Success(val body: String) : ReleaseHttpOutcome
    data class Failure(val reason: String) : ReleaseHttpOutcome
}

fun releaseHttpOutcome(status: Int, body: String): ReleaseHttpOutcome = when (status) {
    in 200..299 -> ReleaseHttpOutcome.Success(body)
    403, 429 -> ReleaseHttpOutcome.Failure("limite da API ($status)")
    else -> ReleaseHttpOutcome.Failure("GitHub HTTP $status")
}

fun shouldRequestUpdateNotificationPermission(
    buildType: String,
    sdkInt: Int,
    status: String,
    permissionGranted: Boolean,
): Boolean = buildType == "nightly" &&
    sdkInt >= 33 &&
    status.startsWith("Life SO b") &&
    status.endsWith(" disponível") &&
    !permissionGranted

fun parseUpdateRelease(rawJson: String): Result<UpdateRelease> = runCatching {
    val response = updateJson.decodeFromString<GitHubReleaseResponse>(rawJson)
    val buildNumber = requireNotNull(tagPattern.matchEntire(response.tagName)?.groupValues?.get(1)?.toIntOrNull()) {
        "tag inesperada"
    }
    val versionCode = requireNotNull(versionCodePattern.find(response.body)?.groupValues?.get(1)?.toLongOrNull()) {
        "versionCode ausente"
    }
    require(versionCode == 20L + buildNumber) { "tag e versionCode divergem" }
    val expectedName = "gitnote-fork-$buildNumber.apk"
    val asset = requireNotNull(response.assets.singleOrNull { it.name == expectedName }) {
        "asset esperado ausente ou duplicado"
    }
    require(asset.size > 0L) { "tamanho inválido" }
    require(asset.digest?.matches(Regex("^sha256:[0-9a-fA-F]{64}$")) == true) { "digest ausente ou inválido" }
    UpdateRelease(buildNumber, versionCode, asset)
}

fun evaluateUpdate(
    rawJson: String,
    installedVersionCode: Long,
    buildType: String,
): UpdateEvaluation {
    if (buildType != "nightly") return UpdateEvaluation.DisabledBuild
    return parseUpdateRelease(rawJson).fold(
        onSuccess = { release ->
            if (release.versionCode > installedVersionCode) UpdateEvaluation.Available(release)
            else UpdateEvaluation.Current
        },
        onFailure = { UpdateEvaluation.Rejected(it.message ?: "resposta inesperada") },
    )
}

fun verifySha256AndSize(file: File, expectedSize: Long, expectedDigest: String): Boolean {
    val expectedHex = expectedDigest.removePrefix("sha256:").lowercase()
    val actualHex = MessageDigest.getInstance("SHA-256").let { digest ->
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
    val valid = file.length() == expectedSize && actualHex == expectedHex
    if (!valid) file.delete()
    return valid
}
