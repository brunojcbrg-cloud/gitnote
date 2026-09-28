package io.github.wiiznokes.gitnote.aulas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LessonReadyStateTest {
    private val ready = MobileLesson(
        idAula = "a",
        nomeFinal = "Genética",
        status = "pronta",
        etapa = "concluida",
        progresso = 100,
        html = "https://drive.google.com/file/d/drive-html-123/view",
    )

    @Test
    fun `notifica somente na transicao para pronta e uma vez`() {
        val state = MobileLessonState(schema = 2, aulas = listOf(ready))
        assertEquals(emptyList(), newlyReadyLessons(state, null))
        assertEquals(listOf("a"), newlyReadyLessons(state, emptySet()).map { it.idAula })
        assertEquals(emptyList(), newlyReadyLessons(state, setOf("a")))
        assertEquals(setOf("a"), readyLessonIds(state))
    }

    @Test
    fun `extrai id do link html e recusa link estranho`() {
        assertEquals("drive-html-123", driveIdFromHtml(ready.html))
        assertNull(driveIdFromHtml("https://example.com/aula"))
    }
}
