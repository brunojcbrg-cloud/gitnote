package io.github.wiiznokes.gitnote.manager

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitWorkingTreeChange(
    val path: String,
    val kind: String,
)

@Serializable
data class GitRecentCommit(
    @SerialName("short_hash") val shortHash: String,
    val author: String,
    val timestamp: Long,
    val message: String,
    val files: List<String>,
)

data class GitSyncSnapshot(
    val ahead: Int,
    val behind: Int,
    val changes: List<GitWorkingTreeChange>,
    val recentCommits: List<GitRecentCommit>,
)

sealed interface GitSyncSummary {
    data object Loading : GitSyncSummary
    data object Updated : GitSyncSummary
    data class Behind(val commits: Int) : GitSyncSummary
    data class LocalEdits(val notes: Int) : GitSyncSummary
    data class Diverged(val ahead: Int, val behind: Int, val notes: Int) : GitSyncSummary
    data class Failed(val message: String) : GitSyncSummary
}

fun summarizeGitSync(ahead: Int, behind: Int, localChanges: Int): GitSyncSummary = when {
    ahead > 0 && behind > 0 -> GitSyncSummary.Diverged(ahead, behind, localChanges)
    behind > 0 && localChanges > 0 -> GitSyncSummary.Diverged(ahead, behind, localChanges)
    behind > 0 -> GitSyncSummary.Behind(behind)
    localChanges > 0 || ahead > 0 -> GitSyncSummary.LocalEdits(localChanges)
    else -> GitSyncSummary.Updated
}
