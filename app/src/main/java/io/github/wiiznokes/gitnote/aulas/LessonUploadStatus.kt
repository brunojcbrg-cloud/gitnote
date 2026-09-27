package io.github.wiiznokes.gitnote.aulas

enum class LessonWorkPhase {
    ENQUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    BLOCKED,
}

enum class LessonFailureReason {
    AUTHORIZATION,
    OTHER,
}

data class LessonWorkSnapshot(
    val phase: LessonWorkPhase,
    val fileIndex: Int = 0,
    val fileTotal: Int = 0,
    val percent: Int = 0,
    val failureReason: LessonFailureReason? = null,
    val failureMessage: String? = null,
)

data class LessonUploadUiStatus(
    val text: String,
    val percent: Int? = null,
    val canRetry: Boolean = false,
)

fun lessonUploadUiStatus(snapshot: LessonWorkSnapshot): LessonUploadUiStatus = when (snapshot.phase) {
    LessonWorkPhase.ENQUEUED, LessonWorkPhase.BLOCKED ->
        LessonUploadUiStatus("Na fila (sem rede)")

    LessonWorkPhase.RUNNING -> {
        val current = snapshot.fileIndex.coerceAtLeast(1)
        val total = snapshot.fileTotal.coerceAtLeast(current)
        val percent = snapshot.percent.coerceIn(0, 100)
        LessonUploadUiStatus("Enviando arquivo $current de $total — $percent%", percent)
    }

    LessonWorkPhase.SUCCEEDED ->
        LessonUploadUiStatus("Enviado — esperando o PC")

    LessonWorkPhase.FAILED -> when (snapshot.failureReason) {
        LessonFailureReason.AUTHORIZATION ->
            LessonUploadUiStatus("Falhou: entre de novo no Google", canRetry = true)

        else -> LessonUploadUiStatus(
            "Falhou: ${snapshot.failureMessage?.takeIf { it.isNotBlank() } ?: "não foi possível enviar"}",
            canRetry = true,
        )
    }

    LessonWorkPhase.CANCELLED ->
        LessonUploadUiStatus("Falhou: envio cancelado", canRetry = true)
}
