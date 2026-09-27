package io.github.wiiznokes.gitnote.manager

import kotlin.test.Test
import kotlin.test.assertEquals

class GitSyncSnapshotTest {
    @Test fun updated() = assertEquals(GitSyncSummary.Updated, summarizeGitSync(0, 0, 0))

    @Test fun behind() = assertEquals(GitSyncSummary.Behind(2), summarizeGitSync(0, 2, 0))

    @Test fun localEdits() = assertEquals(GitSyncSummary.LocalEdits(3), summarizeGitSync(0, 0, 3))

    @Test fun localCommitsAreLocalEdits() = assertEquals(
        GitSyncSummary.LocalEdits(0),
        summarizeGitSync(1, 0, 0),
    )

    @Test fun divergedGraph() = assertEquals(
        GitSyncSummary.Diverged(1, 2, 0),
        summarizeGitSync(1, 2, 0),
    )

    @Test fun remoteAndWorkingTreeAreDiverged() = assertEquals(
        GitSyncSummary.Diverged(0, 2, 4),
        summarizeGitSync(0, 2, 4),
    )
}
