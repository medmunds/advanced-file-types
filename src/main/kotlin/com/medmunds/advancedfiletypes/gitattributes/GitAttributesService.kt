package com.medmunds.advancedfiletypes.gitattributes

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.util.concurrency.AppExecutorUtil
import com.medmunds.advancedfiletypes.settings.AdvancedFileTypeSettings
import git4idea.config.GitExecutableDetector
import git4idea.config.GitExecutableManager
import git4idea.config.GitVcsApplicationSettings
import git4idea.config.GitVcsSettings
import git4idea.repo.GitRepositoryManager
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Project-level service that manages a long-lived `git check-attr --stdin -z linguist-language`
 * process for evaluating .gitattributes rules.
 */
@Service(Service.Level.PROJECT)
class GitAttributesService(private val project: Project) : Disposable {

    private val LOG = Logger.getInstance(GitAttributesService::class.java)

    private val lock = ReentrantLock()
    private val cache = ConcurrentHashMap<String, String>()
    private val NULL_SENTINEL = "\u0000__NULL__\u0000"

    private var currentSession: ProcessSession? = null
    private var isGitUnavailable = false
    private var lastKnownGitAvailable: Boolean = false
    private var lastAccessTime = System.currentTimeMillis()

    @Volatile
    private var cachedGitExecutablePath: String? = null

    private val idleCheckFuture: Future<*>? = try {
        AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay({
            checkIdleTimeout()
        }, IDLE_CHECK_INTERVAL_SECONDS, IDLE_CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS)
    } catch (_: Exception) {
        null
    }

    init {
        lastKnownGitAvailable = isGitAvailable()
        prefetchGitExecutableAsync()
    }

    private class ProcessSession(
        val process: Process,
        val stdin: OutputStream,
        val stdout: InputStream
    ) {
        fun isAlive(): Boolean = process.isAlive

        fun close() {
            try {
                stdin.close()
            } catch (_: Exception) {}
            try {
                stdout.close()
            } catch (_: Exception) {}
            try {
                process.destroyForcibly()
            } catch (_: Exception) {}
        }
    }

    /**
     * Checks whether Git and a Git repository are available for this project.
     */
    fun isGitAvailable(): Boolean {
        if (project.isDisposed || project.isDefault) {
            return false
        }
        if (customProjectDirectory != null) {
            val custom = customProjectDirectory ?: return false
            return File(custom, ".git").exists()
        }
        try {
            val repoManager = GitRepositoryManager.getInstance(project)
            if (repoManager.repositories.isNotEmpty()) {
                return true
            }
        } catch (_: Exception) {}

        var dir = getProjectDirectory()
        while (dir != null) {
            if (File(dir, ".git").exists()) {
                return true
            }
            dir = dir.parentFile
        }
        return false
    }

    /**
     * Notifies the service that VCS or Git repository mapping state may have changed.
     * If Git availability transitioned (e.g. from unavailable to available or vice versa),
     * resets cache and returns true so callers can notify file type changes.
     */
    fun onRepositoryStateChanged(): Boolean {
        val currentlyAvailable = isGitAvailable()
        val availabilityChanged = (currentlyAvailable != lastKnownGitAvailable) || (currentlyAvailable && isGitUnavailable)
        if (availabilityChanged) {
            lastKnownGitAvailable = currentlyAvailable
            clearCache()
            if (currentlyAvailable) {
                prefetchGitExecutableAsync()
            }
            return true
        }
        return false
    }

    /**
     * Retrieves the Linguist language assigned to the given relative path via .gitattributes,
     * or null if no language is assigned or Git attributes evaluation is unavailable.
     */
    fun getLanguage(relativePath: String): String? {
        if (project.isDisposed || !project.isInitialized || project.isDefault) {
            return null
        }
        if (!isGitAvailable()) {
            return null
        }

        val normalized = normalizePath(relativePath)
        val cached = cache[normalized]
        if (cached != null) {
            return if (cached == NULL_SENTINEL) null else cached
        }

        lastAccessTime = System.currentTimeMillis()

        val language = lock.withLock {
            // Double-check cache inside lock
            val cachedInLock = cache[normalized]
            if (cachedInLock != null) {
                return@withLock if (cachedInLock == NULL_SENTINEL) null else cachedInLock
            }

            if (isGitUnavailable || !isGitAvailable()) {
                return@withLock null
            }

            val session = getOrStartProcessSession() ?: return@withLock null
            queryProcessWithTimeout(session, normalized)
        }

        cache[normalized] = language ?: NULL_SENTINEL
        return language
    }

    /**
     * Clears cached attributes and stops the running Git process.
     */
    fun clearCache() {
        cache.clear()
        lock.withLock {
            isGitUnavailable = false
            currentSession?.close()
            currentSession = null
            lastKnownGitAvailable = isGitAvailable()
        }
    }

    /**
     * Returns true if a cached entry exists for the given relative path.
     */
    internal fun isCached(relativePath: String): Boolean {
        val normalized = normalizePath(relativePath)
        return cache.containsKey(normalized)
    }

    private fun normalizePath(path: String): String {
        return path.replace('\\', '/')
            .removePrefix("./")
            .removePrefix("/")
    }

    internal var customProjectDirectory: File? = null

    private fun getProjectDirectory(): File? {
        val custom = customProjectDirectory
        if (custom != null) {
            return if (custom.isDirectory) custom else null
        }
        val basePath = project.basePath
        if (!basePath.isNullOrBlank()) {
            val dir = File(basePath)
            if (dir.isDirectory) return dir
        }

        val projectDir = project.guessProjectDir()
        if (projectDir != null) {
            try {
                val ioFile = VfsUtilCore.virtualToIoFile(projectDir)
                if (ioFile.isDirectory) return ioFile
            } catch (_: Exception) {}
            try {
                val nioFile = projectDir.toNioPath().toFile()
                if (nioFile.isDirectory) return nioFile
            } catch (_: Exception) {}
        }

        return null
    }

    /**
     * Asynchronously pre-fetches and caches the Git executable path on a background thread.
     */
    fun prefetchGitExecutableAsync() {
        if (project.isDisposed || project.isDefault) return
        try {
            AppExecutorUtil.getAppExecutorService().execute {
                if (project.isDisposed || !project.isInitialized) return@execute
                updateGitExecutablePath()
            }
        } catch (_: Exception) {}
    }

    /**
     * Resolves the Git executable path without blocking or performing disk detection on the EDT.
     */
    internal fun getEffectiveGitExecutable(): String {
        cachedGitExecutablePath?.let { return it }

        val isEdt = ApplicationManager.getApplication()?.isDispatchThread == true
        if (isEdt) {
            val quickPath = getQuickGitPath()
            cachedGitExecutablePath = quickPath
            prefetchGitExecutableAsync()
            return quickPath
        } else {
            return updateGitExecutablePath()
        }
    }

    /**
     * Fast, non-blocking check for saved or detected Git executable path.
     */
    internal fun getQuickGitPath(): String {
        try {
            val projectSettingsPath = GitVcsSettings.getInstance(project).pathToGit
            if (isValidGitExecutable(projectSettingsPath)) {
                return projectSettingsPath!!
            }
        } catch (_: Exception) {}

        try {
            val appSettingsPath = GitVcsApplicationSettings.getInstance().savedPathToGit
            if (isValidGitExecutable(appSettingsPath)) {
                return appSettingsPath!!
            }
        } catch (_: Exception) {}

        try {
            val detected = GitExecutableManager.getInstance().getDetectedExecutable(project, false)
            if (isValidGitExecutable(detected)) {
                return detected!!
            }
        } catch (_: Exception) {}

        return GitExecutableDetector.getDefaultExecutable()
    }

    private fun isValidGitExecutable(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val file = File(path)
        return !file.isAbsolute || (file.isFile && file.canExecute())
    }

    /**
     * Queries GitExecutableManager on a background thread, caching the result and notifying
     * file type changes if the effective executable changed.
     */
    internal fun updateGitExecutablePath(): String {
        val detected = try {
            val path = GitExecutableManager.getInstance().getPathToGit(project)
            if (isValidGitExecutable(path)) path else null
        } catch (e: Exception) {
            LOG.debug("Unable to obtain Git path from GitExecutableManager: ${e.message}")
            null
        }
        val effective = detected ?: getQuickGitPath()
        val previous = cachedGitExecutablePath
        cachedGitExecutablePath = effective
        if (previous != null && previous != effective) {
            clearCache()
            val settings = AdvancedFileTypeSettings.getInstance(project)
            if (settings.isLoadFromGitAttributes()) {
                AdvancedFileTypeSettings.notifyFileTypesChanged(project)
            }
        }
        return effective
    }

    /**
     * Handles notifications when Git executable detection completes or settings change.
     */
    fun onGitExecutableChanged() {
        if (project.isDisposed || !project.isInitialized) return
        prefetchGitExecutableAsync()
    }

    private fun getOrStartProcessSession(): ProcessSession? {
        val existing = currentSession
        if (existing != null && existing.isAlive()) {
            return existing
        }

        if (existing != null && !existing.isAlive()) {
            val stderr = readStderr(existing.process)
            val exitCode = try { existing.process.exitValue() } catch (_: Exception) { -1 }
            if (isNotGitRepo(stderr, exitCode)) {
                handleNotGitRepo(stderr)
                existing.close()
                currentSession = null
                return null
            } else {
                LOG.warn("git check-attr process died unexpectedly (exitValue=$exitCode, stderr='$stderr'). Restarting process for project '${project.name}'.")
                existing.close()
                currentSession = null
            }
        }

        val workDir = getProjectDirectory()
        if (workDir == null) {
            LOG.debug("Cannot determine working directory for project '${project.name}'")
            return null
        }

        val effectiveGit = getEffectiveGitExecutable()

        return try {
            val pb = ProcessBuilder(effectiveGit, "check-attr", "--stdin", "-z", LINGUIST_ATTR)
            pb.directory(workDir)

            val proc = pb.start()

            // Check if process terminated immediately (e.g. non-git directory or bad executable)
            if (proc.waitFor(50, TimeUnit.MILLISECONDS)) {
                val exitCode = proc.exitValue()
                val stderr = readStderr(proc)
                if (isNotGitRepo(stderr, exitCode)) {
                    handleNotGitRepo(stderr)
                } else {
                    LOG.warn("git check-attr process terminated unexpectedly on startup for project '${project.name}' (exitCode=$exitCode, stderr='$stderr').")
                    isGitUnavailable = true
                }
                proc.destroyForcibly()
                return null
            }

            val session = ProcessSession(proc, proc.outputStream, proc.inputStream)
            currentSession = session
            LOG.info("Started git check-attr process (pid=${proc.pid()}) for project '${project.name}' in '$workDir'.")
            session
        } catch (e: Exception) {
            LOG.warn("Failed to spawn git check-attr process for project '${project.name}': ${e.message}", e)
            isGitUnavailable = true
            null
        }
    }

    private fun queryProcessWithTimeout(session: ProcessSession, normalizedPath: String): String? {
        val queryFuture = CompletableFuture.supplyAsync {
            try {
                val pathBytes = normalizedPath.toByteArray(StandardCharsets.UTF_8)
                session.stdin.write(pathBytes)
                session.stdin.write(0)
                session.stdin.flush()

                // Read 3 NUL-separated fields: <path>\0<attr>\0<info>\0
                // Access to stdin/stdout is serialized under lock, so responses strictly match the queried path and attribute.
                readNulTerminatedString(session.stdout)
                readNulTerminatedString(session.stdout)
                val respInfo = readNulTerminatedString(session.stdout)

                respInfo
            } catch (e: Exception) {
                throw e
            }
        }

        return try {
            val info = queryFuture.get(QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            when {
                info == null -> null
                info == "unspecified" || info == "unset" || info == "set" -> null
                info.isBlank() -> null
                else -> info.trim()
            }
        } catch (e: TimeoutException) {
            LOG.warn("git check-attr process timed out after ${QUERY_TIMEOUT_MS}ms querying path '$normalizedPath' in project '${project.name}'. Terminating stuck process.", e)
            session.close()
            currentSession = null
            null
        } catch (e: Exception) {
            val cause = e.cause ?: e
            val stderr = readStderr(session.process)
            val exitCode = if (!session.isAlive()) try { session.process.exitValue() } catch (_: Exception) { null } else null
            if (isNotGitRepo(stderr, exitCode)) {
                handleNotGitRepo(stderr)
            } else {
                val state = exitCode?.let { "exitValue=$it" } ?: "alive"
                LOG.warn("git check-attr process communication failed for path '$normalizedPath' (process state=$state, stderr='$stderr'): ${cause.message}", cause)
            }
            session.close()
            currentSession = null
            null
        }
    }

    private fun readStderr(process: Process): String {
        return try {
            process.errorStream.bufferedReader().readText().trim()
        } catch (_: Exception) {
            ""
        }
    }

    private fun isNotGitRepo(stderr: String, exitCode: Int?): Boolean {
        return stderr.contains(NOT_A_GIT_REPO_MESSAGE, ignoreCase = true) || exitCode == 128
    }

    private fun handleNotGitRepo(stderr: String) {
        val details = if (stderr.isNotBlank()) " ($stderr)" else ""
        LOG.info("Project '${project.name}' is not a Git repository$details. Git attributes evaluation is disabled.")
        isGitUnavailable = true
    }

    private fun readNulTerminatedString(input: InputStream): String {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) {
                if (buffer.size() == 0) {
                    throw EOFException("Reached EOF on git check-attr stdout stream")
                }
                break
            }
            if (b == 0) {
                break
            }
            buffer.write(b)
        }
        return buffer.toString(StandardCharsets.UTF_8.name())
    }

    private fun checkIdleTimeout() {
        if (lock.tryLock()) {
            try {
                val session = currentSession
                if (session != null && session.isAlive()) {
                    val idleTime = System.currentTimeMillis() - lastAccessTime
                    if (idleTime > IDLE_TIMEOUT_MS) {
                        LOG.info("Stopping idle git check-attr process for project '${project.name}' (idle for ${idleTime / 1000}s).")
                        session.close()
                        currentSession = null
                    }
                }
            } finally {
                lock.unlock()
            }
        }
    }

    override fun dispose() {
        idleCheckFuture?.cancel(true)
        clearCache()
    }

    companion object {
        private const val NOT_A_GIT_REPO_MESSAGE = "not a git repository"
        private const val LINGUIST_ATTR = "linguist-language"
        private const val QUERY_TIMEOUT_MS = 2000L
        private const val IDLE_TIMEOUT_MS = 60_000L
        private const val IDLE_CHECK_INTERVAL_SECONDS = 30L

        fun getInstance(project: Project): GitAttributesService {
            return project.getService(GitAttributesService::class.java)
        }
    }
}
