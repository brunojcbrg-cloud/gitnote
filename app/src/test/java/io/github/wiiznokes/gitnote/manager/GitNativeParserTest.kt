package io.github.wiiznokes.gitnote.manager

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private fun String.toNativeHex(): String =
    toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }

class GitNativeParserTest {

    // aheadBehind

    @Test
    fun aheadBehindNormalResponse() {
        assertEquals(3 to 1, parseAheadBehind("3:1"))
    }

    @Test
    fun aheadBehindErrorResponseThrows() {
        val e = assertFailsWith<GitException> { parseAheadBehind("ERR:graph_ahead_behind failed") }
        assertTrue(e.message!!.contains("graph_ahead_behind failed"))
    }

    // status

    @Test
    fun statusNormalResponse() {
        val path = "notas/aula.md".toNativeHex()
        assertEquals(
            listOf(GitWorkingTreeChange(path = "notas/aula.md", kind = "modified")),
            parseStatus("modified|$path"),
        )
    }

    @Test
    fun statusEmptyResponseIsCleanWorkingTree() {
        assertEquals(emptyList(), parseStatus(""))
    }

    @Test
    fun statusErrorResponseThrows() {
        assertFailsWith<GitException> { parseStatus("ERR:status failed") }
    }

    // recentCommits

    @Test
    fun recentCommitsNormalResponse() {
        val author = "Bruno".toNativeHex()
        val message = "sincroniza".toNativeHex()
        val file = "notas/aula.md".toNativeHex()
        val result = parseRecentCommits("abc1234|$author|1727470000|$message|$file")
        assertEquals(
            listOf(
                GitRecentCommit(
                    shortHash = "abc1234",
                    author = "Bruno",
                    timestamp = 1727470000L,
                    message = "sincroniza",
                    files = listOf("notas/aula.md"),
                ),
            ),
            result,
        )
    }

    @Test
    fun recentCommitsEmptyResponseIsNoHistory() {
        assertEquals(emptyList(), parseRecentCommits(""))
    }

    @Test
    fun recentCommitsErrorResponseThrows() {
        assertFailsWith<GitException> { parseRecentCommits("ERR:revwalk failed") }
    }
}
