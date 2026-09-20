package io.github.wiiznokes.gitnote.ui.viewmodel.edit

import kotlin.test.Test
import kotlin.test.assertEquals

class TextHistoryLimitTest {
    @Test
    fun discardsOldestSnapshotsAfterOneHundredSteps() {
        val history = (0..120).map { "version $it" }.toMutableList()

        history.keepNewestHistorySteps(MAX_HISTORY_STEPS)

        assertEquals(100, history.size)
        assertEquals("version 21", history.first())
        assertEquals("version 120", history.last())

        history += "version 121"
        history.keepNewestHistorySteps(MAX_HISTORY_STEPS)
        assertEquals(100, history.size)
        assertEquals("version 22", history.first())
        assertEquals("version 121", history.last())
    }
}
