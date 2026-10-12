package com.medmunds.advancedfiletypes.gitattributes

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert
import java.io.File
import kotlin.io.path.createTempDirectory

class GitAttributesServiceTest : BasePlatformTestCase() {

    private lateinit var tempGitDir: File

    override fun setUp() {
        super.setUp()
        val tempDir = createTempDirectory("git-test-repo").toFile()
        tempGitDir = tempDir

        val pb = ProcessBuilder("git", "init")
        pb.directory(tempDir)
        val proc = pb.start()
        proc.waitFor()
    }

    override fun tearDown() {
        try {
            GitAttributesService.getInstance(project).clearCache()
            tempGitDir.deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    fun testGitCheckAttrResolution() {
        val gitattributes = File(tempGitDir, ".gitattributes")
        gitattributes.writeText(
            """
            *.dat linguist-language=Markdown
            docs/*.xyz linguist-language=JSON
            raw/* -linguist-language
            "path with spaces/*.txt" linguist-language=Python
            sub/**/nested.foo linguist-language=Ruby
            café/* linguist-language=Kotlin
            """.trimIndent()
        )

        // Nested .gitattributes in subdirectory
        val subDir = File(tempGitDir, "sub")
        subDir.mkdirs()
        val subGitAttributes = File(subDir, ".gitattributes")
        subGitAttributes.writeText("override.dat linguist-language=Kotlin\n")

        val service = GitAttributesService.getInstance(project)
        service.customProjectDirectory = tempGitDir
        service.clearCache()

        Assert.assertTrue(service.isGitAvailable())

        // Root rules
        Assert.assertEquals("Markdown", service.getLanguage("file.dat"))
        Assert.assertEquals("JSON", service.getLanguage("docs/test.xyz"))
        Assert.assertNull("Negated attribute should return null", service.getLanguage("raw/data.txt"))
        Assert.assertEquals("Python", service.getLanguage("path with spaces/file.txt"))
        Assert.assertEquals("Ruby", service.getLanguage("sub/a/b/nested.foo"))
        Assert.assertEquals("Kotlin", service.getLanguage("café/main.kt"))
        Assert.assertNull("Unmatched file should return null", service.getLanguage("unmatched.txt"))

        // Nested .gitattributes takes precedence
        Assert.assertEquals("Kotlin", service.getLanguage("sub/override.dat"))
        Assert.assertEquals("Markdown", service.getLanguage("other/override.dat"))

        // Caching verification
        Assert.assertTrue(service.isCached("file.dat"))
        Assert.assertTrue(service.isCached("unmatched.txt"))

        // Cache clear
        service.clearCache()
        Assert.assertFalse(service.isCached("file.dat"))
    }

    fun testDynamicFileChangeAndCacheClearing() {
        val gitattributes = File(tempGitDir, ".gitattributes")
        gitattributes.writeText("*.foo linguist-language=JSON\n")

        val service = GitAttributesService.getInstance(project)
        service.customProjectDirectory = tempGitDir
        service.clearCache()

        Assert.assertEquals("JSON", service.getLanguage("sample.foo"))
        Assert.assertTrue(service.isCached("sample.foo"))

        // Update .gitattributes file on disk
        gitattributes.writeText("*.foo linguist-language=Kotlin\n")
        service.clearCache()

        Assert.assertEquals("Kotlin", service.getLanguage("sample.foo"))
    }

    fun testNonGitDirectoryGracefulHandling() {
        val nonGitDir = createTempDirectory("non-git-dir").toFile()
        try {
            val service = GitAttributesService.getInstance(project)
            service.customProjectDirectory = nonGitDir
            service.clearCache()

            Assert.assertFalse("isGitAvailable should be false for non-git directory", service.isGitAvailable())

            val result = service.getLanguage("file.txt")
            Assert.assertNull("Non-git repo should return null without throwing or warning", result)
        } finally {
            nonGitDir.deleteRecursively()
        }
    }

    fun testProcessDeathRecovery() {
        val gitattributes = File(tempGitDir, ".gitattributes")
        gitattributes.writeText("*.dat linguist-language=Markdown\n*.json linguist-language=JSON\n")

        val service = GitAttributesService.getInstance(project)
        service.customProjectDirectory = tempGitDir
        service.clearCache()

        // 1. Initial query starts process and succeeds
        Assert.assertEquals("Markdown", service.getLanguage("file.dat"))

        // 2. Forcibly kill the running process session via reflection to simulate sudden death
        val currentSessionField = GitAttributesService::class.java.getDeclaredField("currentSession")
        currentSessionField.isAccessible = true
        val session = currentSessionField.get(service)
        Assert.assertNotNull("Process session should be running", session)

        val processField = session.javaClass.getDeclaredField("process")
        processField.isAccessible = true
        val process = processField.get(session) as Process
        process.destroyForcibly()
        process.waitFor()
        Assert.assertFalse(process.isAlive)

        // 3. Querying another uncached file should detect dead process, restart, and succeed
        Assert.assertEquals("JSON", service.getLanguage("data.json"))
    }

    fun testGitRepositoryEventListener() {
        val gitattributes = File(tempGitDir, ".gitattributes")
        gitattributes.writeText("*.foo linguist-language=JSON\n")

        val service = GitAttributesService.getInstance(project)
        service.customProjectDirectory = tempGitDir
        service.clearCache()

        Assert.assertEquals("JSON", service.getLanguage("sample.foo"))
        Assert.assertTrue(service.isCached("sample.foo"))

        // Trigger listener
        val listener = GitRepositoryEventListener(project)
        listener.mappingChanged()

        // Cache should be cleared
        Assert.assertFalse("Cache should be cleared after mapping change event", service.isCached("sample.foo"))
    }
}
