package io.github.wiiznokes.gitnote.aulas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LessonUploadStatusTest {
    @Test
    fun `calcula progresso real pelos bytes enviados`() {
        assertEquals(42, LessonUploadProgress(3, 6, 420L, 1_000L).percent)
        assertEquals(100, LessonUploadProgress(3, 6, 1_001L, 1_000L).percent)
    }

    @Test
    fun `mapeia todos os estados do WorkManager para texto visivel`() {
        assertEquals("Na fila (sem rede)", lessonUploadUiStatus(
            LessonWorkSnapshot(LessonWorkPhase.ENQUEUED)
        ).text)
        assertEquals("Na fila (sem rede)", lessonUploadUiStatus(
            LessonWorkSnapshot(LessonWorkPhase.BLOCKED)
        ).text)
        assertEquals("Enviando arquivo 3 de 6 — 42%", lessonUploadUiStatus(
            LessonWorkSnapshot(LessonWorkPhase.RUNNING, 3, 6, 42)
        ).text)
        assertEquals("Enviado — esperando o PC", lessonUploadUiStatus(
            LessonWorkSnapshot(LessonWorkPhase.SUCCEEDED)
        ).text)
        assertTrue(lessonUploadUiStatus(
            LessonWorkSnapshot(LessonWorkPhase.CANCELLED)
        ).canRetry)
    }

    @Test
    fun `falha de autorizacao pede novo login e permite tentar novamente`() {
        val status = lessonUploadUiStatus(
            LessonWorkSnapshot(
                phase = LessonWorkPhase.FAILED,
                failureReason = LessonFailureReason.AUTHORIZATION,
            )
        )
        assertEquals("Falhou: entre de novo no Google", status.text)
        assertTrue(status.canRetry)
    }

    @Test
    fun `falha generica mostra motivo`() {
        val status = lessonUploadUiStatus(
            LessonWorkSnapshot(
                phase = LessonWorkPhase.FAILED,
                failureReason = LessonFailureReason.OTHER,
                failureMessage = "servidor indisponível",
            )
        )
        assertEquals("Falhou: servidor indisponível", status.text)
        assertTrue(status.canRetry)
    }
}
