package io.github.wiiznokes.gitnote.atualizador

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UpdateModelsTest {
    private val fixture: String = requireNotNull(
        javaClass.classLoader?.getResourceAsStream("atualizador/release-b86.json"),
    ).bufferedReader().use { it.readText() }

    @Test
    fun `fixture real b86 le tag versionCode digest e asset`() {
        val release = parseUpdateRelease(fixture).getOrThrow()
        assertEquals(86, release.buildNumber)
        assertEquals(106L, release.versionCode)
        assertEquals("sha256:237bab015a478896f773468411f190b87dbcd1e2081ddcad7ab7d8701d5b5683", release.asset.digest)
        assertEquals("gitnote-fork-86.apk", release.asset.name)
    }

    @Test
    fun `mais nova oferece e igual ou antiga nao oferece`() {
        assertIs<UpdateEvaluation.Available>(evaluateUpdate(fixture, 105, "nightly"))
        assertEquals(UpdateEvaluation.Current, evaluateUpdate(fixture, 106, "nightly"))
        assertEquals(UpdateEvaluation.Current, evaluateUpdate(fixture, 107, "nightly"))
    }

    @Test
    fun `tag corpo divergentes e digest ausente sao rejeitados`() {
        val divergent = fixture.replace("versionCode | `106`", "versionCode | `999`")
        val noDigest = fixture.replace(
            "\"digest\": \"sha256:237bab015a478896f773468411f190b87dbcd1e2081ddcad7ab7d8701d5b5683\",",
            "",
        )
        assertIs<UpdateEvaluation.Rejected>(evaluateUpdate(divergent, 1, "nightly"))
        assertIs<UpdateEvaluation.Rejected>(evaluateUpdate(noDigest, 1, "nightly"))
    }

    @Test
    fun `build debug fica desligado`() {
        assertEquals(UpdateEvaluation.DisabledBuild, evaluateUpdate(fixture, 1, "debug"))
    }

    @Test
    fun `403 de limite falha sem liberar notificacao`() {
        assertEquals(
            ReleaseHttpOutcome.Failure("limite da API (403)"),
            releaseHttpOutcome(403, "{\"message\":\"API rate limit exceeded\"}"),
        )
        assertFalse(shouldRequestUpdateNotificationPermission("nightly", 35, "Última verificação: falhou (limite)", false))
        assertFalse(shouldRequestUpdateNotificationPermission("debug", 35, "Life SO b87 disponível", false))
        assertTrue(shouldRequestUpdateNotificationPermission("nightly", 35, "Life SO b87 disponível", false))
    }

    @Test
    fun `progresso do download so grava a cada 10 por cento, sempre em 100`() {
        assertTrue(deveAtualizarProgressoDownload(-1, 0))
        assertFalse(deveAtualizarProgressoDownload(0, 1))
        assertFalse(deveAtualizarProgressoDownload(0, 9))
        assertTrue(deveAtualizarProgressoDownload(9, 10))
        assertFalse(deveAtualizarProgressoDownload(41, 42))
        assertTrue(deveAtualizarProgressoDownload(41, 50))
        assertTrue(deveAtualizarProgressoDownload(99, 100))
        assertTrue(deveAtualizarProgressoDownload(90, 100))
    }

    @Test
    fun `texto de progresso mostra percentual e megabytes, ou so reticencias sem tamanho`() {
        assertEquals(
            "Baixando b92 — 42% (10.5 MB de 25.0 MB)",
            textoProgressoDownload("b92", 42, 10_500_000, 25_000_000),
        )
        assertEquals("Baixando b92…", textoProgressoDownload("b92", 0, 0, 0))
    }

    @Test
    fun `sha errado rejeita e apaga arquivo`() {
        val directory = Files.createTempDirectory("gitnote-update-test").toFile()
        val file = File(directory, "update.apk").apply { writeText("apk de teste") }
        assertFalse(verifySha256AndSize(file, file.length(), "sha256:${"0".repeat(64)}"))
        assertFalse(file.exists())

        val valid = File(directory, "valid.apk").apply { writeText("apk de teste") }
        val digest = MessageDigest.getInstance("SHA-256").digest(valid.readBytes())
            .joinToString("") { "%02x".format(it) }
        assertTrue(verifySha256AndSize(valid, valid.length(), "sha256:$digest"))
        directory.deleteRecursively()
    }
}
