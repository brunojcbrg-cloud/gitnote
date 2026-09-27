package io.github.wiiznokes.gitnote.manager

import android.util.Log
import androidx.annotation.Keep
import io.github.wiiznokes.gitnote.MyApp
import io.github.wiiznokes.gitnote.R
import io.github.wiiznokes.gitnote.ui.model.Cred
import io.github.wiiznokes.gitnote.ui.model.GitAuthor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.Result.Companion.failure
import kotlin.Result.Companion.success


enum class GitExceptionType {
    InitLib,
    RepoAlreadyInit,
    RepoNotInit,
    Other
}

class GitException(
    val type: GitExceptionType,
    message: String?
) : Exception(getMessage(type, message)) {

    companion object {
        private fun getMessage(
            type: GitExceptionType,
            message: String?
        ): String? =
            message ?: if (type != GitExceptionType.Other) type.name else null

    }

    constructor(message: String) : this(GitExceptionType.Other, message)
    constructor(type: GitExceptionType) : this(type, null)
}

class GitManager {

    companion object {
        private const val TAG = "GitManager"

        init {
            Log.d(TAG, "init")
            System.loadLibrary("git_wrapper")
        }
    }

    private val uiHelper = MyApp.appModule.uiHelper

    private val locker = Mutex()
    var isRepoInitialized = false
        private set
    private var isLibInitialized = false

    private suspend fun <T> safelyAccessLibGit2(f: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            locker.withLock {
                try {
                    if (!isLibInitialized) {
                        val res = initLib()
                        Log.d(TAG, "res on init = $res")
                        if (res < 0) {
                            throw GitException(GitExceptionType.InitLib)
                        }
                        isLibInitialized = true
                    }
                    success(f())
                } catch (e: Exception) {
                    e.printStackTrace()
                    failure(e)
                }
            }
        }


    suspend fun createRepo(repoPath: String): Result<Unit> = safelyAccessLibGit2 {
        Log.d(TAG, "create repo: $repoPath")

        if (isRepoInitialized) throw GitException(GitExceptionType.RepoAlreadyInit)

        val res = createRepoLib(repoPath)
        if (res < 0) {
            throw GitException(uiHelper.getString(R.string.error_create_repo, res.toString()))
        }
        isRepoInitialized = true
    }


    suspend fun openRepo(repoPath: String): Result<Unit> = safelyAccessLibGit2 {
        Log.d(TAG, "open repo: $repoPath")
        if (isRepoInitialized) return@safelyAccessLibGit2

        val res = openRepoLib(repoPath)
        if (res < 0) {
            throw GitException(uiHelper.getString(R.string.error_open_repo, res))
        }
        isRepoInitialized = true
    }

    private var actualCb: ((Int, Int) -> Boolean)? = null

    /**
     * This function is called from native code
     */
    @Keep
    fun progressCb(current: Int, total: Int): Boolean {
        return actualCb?.invoke(current, total) != false
    }

    suspend fun cloneRepo(
        repoPath: String,
        repoUrl: String,
        cred: Cred?,
        progressCallback: (Int) -> Boolean
    ): Result<Unit> = safelyAccessLibGit2 {
        Log.d(TAG, "clone repo: $repoPath, $repoUrl, $cred")

        if (isRepoInitialized) throw GitException(GitExceptionType.RepoAlreadyInit)

        actualCb = { current, total ->
            val percent = if (total > 0) current * 100 / total else 0
            progressCallback(percent)
        }

        val res = cloneRepoLib(
            repoPath = repoPath,
            remoteUrl = repoUrl,
            cred = cred,
            progressCallback = this
        )

        actualCb = null

        if (res < 0) {
            throw GitException(uiHelper.getString(R.string.error_clone_repo, res))
        }

        isRepoInitialized = true

    }

    // todo: update this shit
    suspend fun lastCommit(): String = safelyAccessLibGit2 {
        Log.d(TAG, "last commit")
        if (!isRepoInitialized) throw GitException(GitExceptionType.RepoNotInit)
        lastCommitLib()
    }.getOrDefault("") ?: ""

    suspend fun commitAll(author: GitAuthor, message: String): Result<Unit> = safelyAccessLibGit2 {
        Log.d(TAG, "commit all: ${author.name}")
        if (!isRepoInitialized) throw GitException(GitExceptionType.RepoNotInit)

        var res = isChangeLib()

        if (res < 0) {
            throw GitException(uiHelper.getString(R.string.error_commit_file_change, res))
        }

        if (res == 0) {
            // nothing to commit
            Log.d(TAG, "nothing to commit")
            return@safelyAccessLibGit2
        }

        res = commitAllLib(author.name, author.email, message)
        if (res < 0) {
            throw GitException(uiHelper.getString(R.string.error_commit_repo, res.toString()))
        }

    }

    suspend fun currentSignature(): GitAuthor? = safelyAccessLibGit2 {
        Log.d(TAG, "currentSignature")
        if (!isRepoInitialized) throw GitException(GitExceptionType.RepoNotInit)

        currentSignatureLib()
    }.getOrNull()?.let { GitAuthor(name = it.first, email = it.second) }

    suspend fun push(
        cred: Cred?,
        progressCallback: (current: Int, total: Int) -> Unit = { _, _ -> },
    ): Result<Unit> = safelyAccessLibGit2 {
        Log.d(TAG, "push: $cred")
        if (!isRepoInitialized) throw GitException(GitExceptionType.RepoNotInit)
        val res = try {
            actualCb = { current, total -> progressCallback(current, total); true }
            pushLib(cred, this)
        } finally {
            actualCb = null
        }

        if (res < 0) {
            Log.d(TAG, "push: $res")
            val msg = uiHelper.getString(R.string.error_push_repo, res.toString())
            Log.d(TAG, "push: $msg")

            throw Exception(uiHelper.getString(R.string.error_push_repo, res.toString()))
        }

    }

    suspend fun pull(
        cred: Cred?,
        author: GitAuthor,
        progressCallback: (current: Int, total: Int) -> Unit = { _, _ -> },
    ): Result<Unit> = safelyAccessLibGit2 {
        Log.d(TAG, "pull: $cred")
        if (!isRepoInitialized) throw GitException(GitExceptionType.RepoNotInit)

        val res = try {
            actualCb = { current, total -> progressCallback(current, total); true }
            pullLib(cred, author.name, author.email, this)
        } finally {
            actualCb = null
        }

        if (res < 0) {
            throw Exception(uiHelper.getString(R.string.error_pull_repo, res.toString()))
        }
    }

    suspend fun fetch(cred: Cred?): Result<Unit> = safelyAccessLibGit2 {
        Log.d(TAG, "fetch")
        if (!isRepoInitialized) throw GitException(GitExceptionType.RepoNotInit)
        val res = fetchLib(cred)
        if (res < 0) throw GitException("fetch error $res")
    }

    suspend fun aheadBehind(): Result<Pair<Int, Int>> = safelyAccessLibGit2 {
        if (!isRepoInitialized) throw GitException(GitExceptionType.RepoNotInit)
        val values = aheadBehindLib().split(':').map(String::toInt)
        check(values.size == 2) { "invalid ahead/behind response" }
        values[0] to values[1]
    }

    suspend fun status(): Result<List<GitWorkingTreeChange>> = safelyAccessLibGit2 {
        if (!isRepoInitialized) throw GitException(GitExceptionType.RepoNotInit)
        statusLib().lineSequence().filter(String::isNotEmpty).map { line ->
            val (kind, path) = line.split('|', limit = 2)
            GitWorkingTreeChange(path = path.fromNativeHex(), kind = kind)
        }.toList()
    }

    suspend fun recentCommits(n: Int = 5): Result<List<GitRecentCommit>> = safelyAccessLibGit2 {
        if (!isRepoInitialized) throw GitException(GitExceptionType.RepoNotInit)
        recentCommitsLib(n).lineSequence().filter(String::isNotEmpty).map { line ->
            val fields = line.split('|', limit = 5)
            check(fields.size == 5) { "invalid recent commit response" }
            GitRecentCommit(
                shortHash = fields[0],
                author = fields[1].fromNativeHex(),
                timestamp = fields[2].toLong(),
                message = fields[3].fromNativeHex(),
                files = fields[4].split(',').filter(String::isNotEmpty).map(String::fromNativeHex),
            )
        }.toList()
    }

    suspend fun syncSnapshot(cred: Cred?): Result<GitSyncSnapshot> {
        return safelyAccessLibGit2 {
            if (!isRepoInitialized) throw GitException(GitExceptionType.RepoNotInit)
            val fetchResult = fetchLib(cred)
            if (fetchResult < 0) throw GitException("fetch error $fetchResult")
            val values = aheadBehindLib().split(':').map(String::toInt)
            check(values.size == 2) { "invalid ahead/behind response" }
            val changes = statusLib().lineSequence().filter(String::isNotEmpty).map { line ->
                val (kind, path) = line.split('|', limit = 2)
                GitWorkingTreeChange(path = path.fromNativeHex(), kind = kind)
            }.toList()
            val commits = recentCommitsLib(5).lineSequence().filter(String::isNotEmpty).map { line ->
                val fields = line.split('|', limit = 5)
                check(fields.size == 5) { "invalid recent commit response" }
                GitRecentCommit(
                    shortHash = fields[0],
                    author = fields[1].fromNativeHex(),
                    timestamp = fields[2].toLong(),
                    message = fields[3].fromNativeHex(),
                    files = fields[4].split(',').filter(String::isNotEmpty).map(String::fromNativeHex),
                )
            }.toList()
            GitSyncSnapshot(values[0], values[1], changes, commits)
        }
    }

    suspend fun getTimestamps(): Result<HashMap<String, Long>> = safelyAccessLibGit2 {
        Log.d(TAG, "getTimestamps")

        val h: HashMap<String, Long> = HashMap()

        val res = getTimestampsLib(h)

        if (res < 0) {
            throw Exception("getTimestampsLib error $res")
        }
        h
    }


    fun closeRepoWithoutLock() {
        if (isRepoInitialized) closeRepoLib()
        isRepoInitialized = false
    }

    suspend fun closeRepo() = safelyAccessLibGit2 {
        closeRepoWithoutLock()
    }

    suspend fun shutdown() = safelyAccessLibGit2 {
        closeRepoWithoutLock()
        if (isLibInitialized) freeLib()
        isLibInitialized = false
    }

}

private external fun initLib(
    homePath: String = MyApp.appModule.context.filesDir.toPath().toString()
): Int

private external fun createRepoLib(repoPath: String): Int

private external fun openRepoLib(repoPath: String): Int

private external fun cloneRepoLib(
    repoPath: String,
    remoteUrl: String,
    cred: Cred?,
    progressCallback: GitManager
): Int


private external fun lastCommitLib(): String?

private external fun commitAllLib(name: String, email: String, message: String): Int
private external fun currentSignatureLib(): Pair<String, String>?
private external fun pushLib(cred: Cred?, progressCallback: GitManager): Int
private external fun pullLib(
    cred: Cred?,
    name: String,
    email: String,
    progressCallback: GitManager,
): Int
private external fun fetchLib(cred: Cred?): Int
private external fun aheadBehindLib(): String
private external fun statusLib(): String
private external fun recentCommitsLib(n: Int): String

private external fun freeLib()


private external fun closeRepoLib()

private external fun isChangeLib(): Int

private external fun getTimestampsLib(timestamps: HashMap<String, Long>): Int

external fun generateSshKeysLib(): Pair<String, String>

// return true if url is ssh
external fun getUrlInfoLib(url: String): Boolean?

private fun String.fromNativeHex(): String {
    require(length % 2 == 0) { "invalid native string" }
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray().toString(Charsets.UTF_8)
}

