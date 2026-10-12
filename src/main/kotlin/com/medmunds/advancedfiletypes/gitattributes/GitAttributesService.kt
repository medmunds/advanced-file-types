package com.medmunds.advancedfiletypes.gitattributes

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.util.concurrency.AppExecutorUtil
import git4idea.config.GitExecutableManager
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
    private var lastAccessTime = System.currentTimeMillis()

    private val idleCheckFuture: Future<*>? = try {
        AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay({
            checkIdleTimeout()
        }, IDLE_CHECK_INTERVAL_SECONDS, IDLE_CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS)
    } catch (_: Exception) {
        null
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
     * Retrieves the Linguist language assigned to the given relative path via .gitattributes,
     * or null if no language is assigned or Git attributes evaluation is unavailable.
     */
    fun getLanguage(relativePath: String): String? {
        if (project.isDisposed || !project.isInitialized || project.isDefault) {
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

            if (isGitUnavailable) {
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
        if (customProjectDirectory != null) {
            return customProjectDirectory
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

    private fun getOrStartProcessSession(): ProcessSession? {
        val existing = currentSession
        if (existing != null && existing.isAlive()) {
            return existing
        }

        if (existing != null && !existing.isAlive()) {
            val exitCode = try { existing.process.exitValue() } catch (_: Exception) { -1 }
            LOG.warn("git check-attr process died unexpectedly (exitValue=$exitCode). Restarting process for project '${project.name}'.")
            existing.close()
            currentSession = null
        }

        val workDir = getProjectDirectory()
        if (workDir == null) {
            LOG.debug("Cannot determine working directory for project '${project.name}'")
            return null
        }

        val gitPath = try {
            GitExecutableManager.getInstance().getPathToGit(project)
        } catch (e: Exception) {
            LOG.warn("Failed to obtain Git executable path from Git4Idea: ${e.message}", e)
            "git"
        }

        val effectiveGit = if (gitPath.isBlank()) "git" else gitPath

        return try {
            val pb = ProcessBuilder(effectiveGit, "check-attr", "--stdin", "-z", LINGUIST_ATTR)
            pb.directory(workDir)
            pb.redirectError(ProcessBuilder.Redirect.DISCARD)

            val proc = pb.start()
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
                val respPath = readNulTerminatedString(session.stdout)
                val respAttr = readNulTerminatedString(session.stdout)
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
            val exitValue = if (!session.isAlive()) try { session.process.exitValue() } catch (_: Exception) { -1 } else "alive"
            LOG.warn("git check-attr process communication failed for path '$normalizedPath' (process state=$exitValue): ${cause.message}", cause)
            session.close()
            currentSession = null
            null
        }
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
        private const val LINGUIST_ATTR = "linguist-language"
        private const val QUERY_TIMEOUT_MS = 2000L
        private const val IDLE_TIMEOUT_MS = 60_000L
        private const val IDLE_CHECK_INTERVAL_SECONDS = 30L

        fun getInstance(project: Project): GitAttributesService {
            return project.getService(GitAttributesService::class.java)
        }
    }
}
