package io.github.wiiznokes.gitnote.aulas

import io.github.wiiznokes.gitnote.ui.screen.app.aulas.freshnessMessage
import io.github.wiiznokes.gitnote.ui.screen.app.aulas.safeShortName
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LessonFlowTest {
    @Test
    fun secondSelectionKeepsPreviousAndDeduplicatesUri() {
        val first = LessonFile("content://a", "parte 1.mp3", 10)
        val duplicate = first.copy(nome = "nome ignorado.mp3")
        val second = LessonFile("content://b", "parte 2.mp3", 20)
        val draft = LessonDraft(listOf(first))

        assertEquals(listOf(first, second), draft.add(listOf(duplicate, second)))
    }

    @Test
    fun readyMarkerIsUtf8AndKeepsAccentsAndLongNames() {
        val longName = "Áudio de revisão çãõ " + "muito-longo-".repeat(30) + ".mp3"
        val job = LessonUploadJob(
            idAula = "abc",
            criadoEm = "2026-09-15T10:20:00-03:00",
            pastaDrive = "2026-09-15_1020_aula",
            nomeSugerido = "Introdução à Genética",
            arquivos = listOf(LessonFile("content://a", longName, 70_512_345, "2026-08-11T12:41:51", "ID3:TDRC")),
        )
        val encoded = lessonJson.encodeToString(job.readyMarker())
        val decoded = lessonJson.decodeFromString<ReadyMarker>(encoded.toByteArray().toString(Charsets.UTF_8))

        assertEquals(longName, decoded.arquivos.single().nome)
        assertTrue("Introdução à Genética" in encoded)
        assertFalse("mojibake" in encoded)
    }

    @Test
    fun networkFailureRetriesWithoutDiscardingIndependentJobs() {
        val first = LessonUploadJob("1", "now", "p1", arquivos = listOf(LessonFile("u1", "a.mp3", 1)))
        val second = LessonUploadJob("2", "now", "p2", arquivos = listOf(LessonFile("u2", "b.mp3", 1)))
        assertEquals(UploadFailureAction.RETRY, uploadFailureAction(isNetworkFailure = true))
        assertEquals("1", first.idAula)
        assertEquals("2", second.idAula)
        assertTrue(first.idAula != second.idAula)
        val nextDraft = LessonDraft(first.arquivos)
        nextDraft.clear()
        assertEquals(second.arquivos, nextDraft.add(second.arquivos))
    }

    @Test
    fun stateContainsFiveHistoryFieldsAndStalePcIsExplicit() {
        val text = """{"schema":1,"atualizado_em":"2026-09-15T10:25:00-03:00","pc_publicado_em":"2026-09-15T10:20:00-03:00","aulas":[{"id_aula":"x","processado_em":"2026-09-15T10:22:00-03:00","aula_gravada_em":{"data":"2026-08-11","hora":"12:41:51","fonte":"ID3:TDRC"},"materia":"Genética","arquivos_originais":["parte 1.mp3"],"nome_final":"DNA e RNA","pendente_triagem":false,"status":"concluida"}]}"""
        val state = lessonJson.decodeFromString<MobileLessonState>(text)
        val lesson = state.aulas.single()
        assertEquals("2026-09-15T10:22:00-03:00", lesson.processadoEm)
        assertEquals("2026-08-11", lesson.aulaGravadaEm?.data)
        assertEquals("Genética", lesson.materia)
        assertEquals(listOf("parte 1.mp3"), lesson.arquivosOriginais)
        assertEquals("DNA e RNA", lesson.nomeFinal)
        assertTrue("120 min" in freshnessMessage(state.pcPublicadoEm, ZonedDateTime.parse("2026-09-15T12:20:00-03:00")))
    }

    @Test
    fun shortDriveNameIsSafeAndBounded() {
        val result = safeShortName("  Fisiologia: coração/pressão? " + "x".repeat(100))
        assertTrue(result.length <= 48)
        assertFalse(result.contains(':'))
        assertTrue(result.startsWith("Fisiologia coracao pressao"))
    }
}

class RecordingMetadataReaderTest {
    @Test
    fun readsTdrc() {
        val bytes = id3(mapOf("TDRC" to "2026-08-11T12:41:51"))
        val value = RecordingMetadataReader.read("a.mp3", ByteArrayInputStream(bytes))
        assertEquals("2026-08-11T12:41:51", value?.value)
        assertEquals("ID3:TDRC", value?.source)
    }

    @Test
    fun readsLegacyTdatTimeTyer() {
        val bytes = id3(mapOf("TYER" to "2026", "TDAT" to "1108", "TIME" to "1241"))
        val value = RecordingMetadataReader.read("a.mp3", ByteArrayInputStream(bytes))
        assertEquals("2026-08-11T12:41:00", value?.value)
        assertEquals("ID3:TDAT/TIME/TYER", value?.source)
    }

    @Test
    fun readsMvhd() {
        val unix = 1_786_446_111L
        val creation = unix + 2_082_844_800L
        val atom = ByteBuffer.allocate(20).order(ByteOrder.BIG_ENDIAN)
            .putInt(20).put("mvhd".toByteArray()).putInt(0).putInt(creation.toInt()).putInt(0).array()
        val value = RecordingMetadataReader.read("a.m4a", ByteArrayInputStream(atom))
        assertEquals("M4A:mvhd", value?.source)
        assertTrue(value?.value?.startsWith("2026-") == true)
    }

    @Test
    fun missingMetadataIsNullWithoutCrash() {
        assertNull(RecordingMetadataReader.read("a.mp3", ByteArrayInputStream("sem tags".toByteArray())))
    }

    private fun id3(frames: Map<String, String>): ByteArray {
        val body = frames.entries.flatMap { (id, value) ->
            val text = byteArrayOf(3) + value.toByteArray(Charsets.UTF_8)
            (id.toByteArray().asList() + ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(text.size).array().asList() +
                byteArrayOf(0, 0).asList() + text.asList())
        }.toByteArray()
        val size = byteArrayOf(
            ((body.size shr 21) and 0x7f).toByte(), ((body.size shr 14) and 0x7f).toByte(),
            ((body.size shr 7) and 0x7f).toByte(), (body.size and 0x7f).toByte(),
        )
        return "ID3".toByteArray() + byteArrayOf(3, 0, 0) + size + body
    }
}
