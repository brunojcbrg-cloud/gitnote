package io.github.wiiznokes.gitnote.aulas

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LessonFile(
    val uri: String,
    val nome: String,
    val bytes: Long,
    @SerialName("gravado_em") val gravadoEm: String? = null,
    @SerialName("fonte_data") val fonteData: String? = null,
    val mimeType: String? = null,
)

@Serializable
data class LessonUploadJob(
    @SerialName("id_aula") val idAula: String,
    @SerialName("criado_em") val criadoEm: String,
    @SerialName("pasta_drive") val pastaDrive: String,
    @SerialName("nome_sugerido") val nomeSugerido: String = "",
    val arquivos: List<LessonFile>,
)

@Serializable
data class ReadyFile(
    val nome: String,
    val bytes: Long,
    @SerialName("gravado_em") val gravadoEm: String? = null,
    @SerialName("fonte_data") val fonteData: String? = null,
)

@Serializable
data class ReadyMarker(
    @SerialName("id_aula") val idAula: String,
    @SerialName("criado_em") val criadoEm: String,
    val arquivos: List<ReadyFile>,
    @SerialName("nome_sugerido") val nomeSugerido: String = "",
    val origem: String = "life-so-android",
)

@Serializable
data class MobileLesson(
    @SerialName("id_aula") val idAula: String,
    @SerialName("processado_em") val processadoEm: String = "",
    @SerialName("aula_gravada_em") val aulaGravadaEm: RecordedStamp? = null,
    val materia: String = "",
    /** Subpasta entre a materia e a aula (P1, P2...). Vazio quando a aula fica na raiz. */
    val unidade: String = "",
    @SerialName("arquivos_originais") val arquivosOriginais: List<String> = emptyList(),
    @SerialName("nome_final") val nomeFinal: String = "",
    @SerialName("pendente_triagem") val pendenteTriagem: Boolean = false,
    val status: String = "",
)

@Serializable
data class RecordedStamp(
    val data: String? = null,
    val hora: String? = null,
    val fonte: String? = null,
)

@Serializable
data class MobileLessonState(
    val schema: Int = 1,
    @SerialName("atualizado_em") val atualizadoEm: String = "",
    @SerialName("pc_publicado_em") val pcPublicadoEm: String = "",
    val aulas: List<MobileLesson> = emptyList(),
)

class LessonDraft(initial: List<LessonFile> = emptyList()) {
    private val byUri = linkedMapOf<String, LessonFile>()

    init {
        add(initial)
    }

    fun add(files: Iterable<LessonFile>): List<LessonFile> {
        files.forEach { file -> byUri.putIfAbsent(file.uri, file) }
        return values()
    }

    fun remove(uri: String): List<LessonFile> {
        byUri.remove(uri)
        return values()
    }

    fun clear() {
        byUri.clear()
    }

    fun values(): List<LessonFile> = byUri.values.toList()
}

fun LessonUploadJob.readyMarker(): ReadyMarker = ReadyMarker(
    idAula = idAula,
    criadoEm = criadoEm,
    arquivos = arquivos.map {
        ReadyFile(
            nome = it.nome,
            bytes = it.bytes,
            gravadoEm = it.gravadoEm,
            fonteData = it.fonteData,
        )
    },
    nomeSugerido = nomeSugerido,
)

enum class UploadFailureAction { RETRY, FAIL }

fun uploadFailureAction(isNetworkFailure: Boolean): UploadFailureAction =
    if (isNetworkFailure) UploadFailureAction.RETRY else UploadFailureAction.FAIL
