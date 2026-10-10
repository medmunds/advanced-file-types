package com.medmunds.advancedfiletypes

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.medmunds.advancedfiletypes.model.FileTypeOverrideRule
import com.medmunds.advancedfiletypes.model.PatternSyntax
import com.medmunds.advancedfiletypes.settings.AdvancedFileTypeSettings
import com.medmunds.advancedfiletypes.ui.AdvancedFileTypeConfigurable
import org.junit.Assert

class AdvancedFileTypeTest : BasePlatformTestCase() {

    private lateinit var settings: AdvancedFileTypeSettings
    private val overrider = AdvancedFileTypeOverrider()

    override fun setUp() {
        super.setUp()
        settings = AdvancedFileTypeSettings.getInstance(project)
        settings.setRules(emptyList())
    }

    override fun tearDown() {
        try {
            settings.setRules(emptyList())
        } finally {
            super.tearDown()
        }
    }

    fun testDirectoryAndFilenamePatternOverride() {
        // e.g., all .txt files in docs directory could be treated as HTML (or Markdown/XML/etc.)
        val htmlFileType = FileTypeManager.getInstance().findFileTypeByName("HTML")
        Assert.assertNotNull("HTML file type should be available in platform", htmlFileType)

        val docsFile = myFixture.addFileToProject("docs/guide.txt", "<h1>Hello Docs</h1>").virtualFile
        val srcFile = myFixture.addFileToProject("src/guide.txt", "Plain text in src").virtualFile

        // Initially without rules, both files should not be overridden by the plugin
        Assert.assertNull(overrider.getOverriddenFileType(docsFile))
        Assert.assertNull(overrider.getOverriddenFileType(srcFile))

        // Configure rule: docs/*.txt -> HTML
        val rule = FileTypeOverrideRule(
            pattern = "docs/*.txt",
            fileTypeName = "HTML",
            syntax = PatternSyntax.GLOB,
            isEnabled = true
        )
        settings.setRules(listOf(rule))

        // Test overrider
        val overriddenDocs = overrider.getOverriddenFileType(docsFile)
        val overriddenSrc = overrider.getOverriddenFileType(srcFile)

        Assert.assertEquals("docs/guide.txt should be overridden as HTML", htmlFileType, overriddenDocs)
        Assert.assertNull("src/guide.txt should not be overridden", overriddenSrc)
    }

    fun testFirstMatchDeterminesFileType() {
        val htmlFileType = FileTypeManager.getInstance().findFileTypeByName("HTML")
        val xmlFileType = FileTypeManager.getInstance().findFileTypeByName("XML")
        Assert.assertNotNull(htmlFileType)
        Assert.assertNotNull(xmlFileType)

        val docsFile = myFixture.addFileToProject("docs/api.txt", "<root/>").virtualFile

        // Rule 1: docs/**/*.txt -> HTML
        // Rule 2: docs/*.txt -> XML
        settings.setRules(listOf(
            FileTypeOverrideRule("docs/**/*.txt", "HTML", PatternSyntax.GLOB, true),
            FileTypeOverrideRule("docs/*.txt", "XML", PatternSyntax.GLOB, true)
        ))

        Assert.assertEquals("First rule should win", htmlFileType, overrider.getOverriddenFileType(docsFile))

        // Swap order
        settings.setRules(listOf(
            FileTypeOverrideRule("docs/*.txt", "XML", PatternSyntax.GLOB, true),
            FileTypeOverrideRule("docs/**/*.txt", "HTML", PatternSyntax.GLOB, true)
        ))

        Assert.assertEquals("First rule should win after swap", xmlFileType, overrider.getOverriddenFileType(docsFile))
    }

    fun testDisabledRuleIsSkipped() {
        val xmlFileType = FileTypeManager.getInstance().findFileTypeByName("XML")

        val docsFile = myFixture.addFileToProject("docs/sub/guide.txt", "content").virtualFile

        settings.setRules(listOf(
            FileTypeOverrideRule("docs/**/*.txt", "HTML", PatternSyntax.GLOB, false), // disabled
            FileTypeOverrideRule("**/*.txt", "XML", PatternSyntax.GLOB, true)          // enabled
        ))

        Assert.assertEquals("Disabled rule should be skipped in favor of next matching rule", xmlFileType, overrider.getOverriddenFileType(docsFile))
    }

    fun testRegexPatternOverride() {
        val xmlFileType = FileTypeManager.getInstance().findFileTypeByName("XML")
        Assert.assertNotNull(xmlFileType)

        val specialFile = myFixture.addFileToProject("config/service-123.cfg", "<config/>").virtualFile
        val otherFile = myFixture.addFileToProject("config/other.txt", "text").virtualFile

        settings.setRules(listOf(
            FileTypeOverrideRule("config/service-\\d+\\.cfg", "XML", PatternSyntax.REGEX, true)
        ))

        Assert.assertEquals(xmlFileType, overrider.getOverriddenFileType(specialFile))
        Assert.assertNull(overrider.getOverriddenFileType(otherFile))
    }

    fun testSettingsStatePersistence() {
        val state = AdvancedFileTypeSettings.State()
        state.rules.add(FileTypeOverrideRule("templates/**/*.tpl", "HTML", PatternSyntax.GLOB, true))
        state.rules.add(FileTypeOverrideRule(".*\\.custom", "XML", PatternSyntax.REGEX, false))

        settings.loadState(state)

        val loaded = settings.getRules()
        Assert.assertEquals(2, loaded.size)
        Assert.assertEquals("templates/**/*.tpl", loaded[0].pattern)
        Assert.assertEquals("HTML", loaded[0].fileTypeName)
        Assert.assertEquals(PatternSyntax.GLOB, loaded[0].syntax)
        Assert.assertTrue(loaded[0].isEnabled)

        Assert.assertEquals(".*\\.custom", loaded[1].pattern)
        Assert.assertEquals("XML", loaded[1].fileTypeName)
        Assert.assertEquals(PatternSyntax.REGEX, loaded[1].syntax)
        Assert.assertFalse(loaded[1].isEnabled)
    }

    fun testConfigurableLifecycle() {
        val configurable = AdvancedFileTypeConfigurable(project)
        val component = configurable.createComponent()
        Assert.assertNotNull("Configurable component should not be null", component)

        settings.setRules(listOf(
            FileTypeOverrideRule("docs/*.txt", "HTML", PatternSyntax.GLOB, true)
        ))

        configurable.reset()
        Assert.assertFalse("Configurable should not be modified after reset", configurable.isModified())

        configurable.disposeUIResources()
    }
}
