package com.medmunds.advancedfiletypes

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.speedSearch.SpeedSearchSupply
import com.medmunds.advancedfiletypes.help.AdvancedFileTypeWebHelpProvider
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
            syntax = PatternSyntax.GLOB
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
            FileTypeOverrideRule("docs/**/*.txt", "HTML", PatternSyntax.GLOB),
            FileTypeOverrideRule("docs/*.txt", "XML", PatternSyntax.GLOB)
        ))

        Assert.assertEquals("First rule should win", htmlFileType, overrider.getOverriddenFileType(docsFile))

        // Swap order
        settings.setRules(listOf(
            FileTypeOverrideRule("docs/*.txt", "XML", PatternSyntax.GLOB),
            FileTypeOverrideRule("docs/**/*.txt", "HTML", PatternSyntax.GLOB)
        ))

        Assert.assertEquals("First rule should win after swap", xmlFileType, overrider.getOverriddenFileType(docsFile))
    }

    fun testRuleRemovalRevertsOverride() {
        val xmlFileType = FileTypeManager.getInstance().findFileTypeByName("XML")

        val docsFile = myFixture.addFileToProject("docs/sub/guide.txt", "content").virtualFile

        settings.setRules(listOf(
            FileTypeOverrideRule("docs/**/*.txt", "HTML", PatternSyntax.GLOB),
            FileTypeOverrideRule("**/*.txt", "XML", PatternSyntax.GLOB)
        ))

        // Initially matches first rule (HTML)
        val htmlFileType = FileTypeManager.getInstance().findFileTypeByName("HTML")
        Assert.assertEquals(htmlFileType, overrider.getOverriddenFileType(docsFile))

        // When user deletes/removes the first rule, the next matching rule applies
        settings.setRules(listOf(
            FileTypeOverrideRule("**/*.txt", "XML", PatternSyntax.GLOB)
        ))
        Assert.assertEquals("Next matching rule should take effect when previous rule is removed", xmlFileType, overrider.getOverriddenFileType(docsFile))

        // When all rules are removed, override returns null
        settings.setRules(emptyList())
        Assert.assertNull("No override should apply when all rules are deleted", overrider.getOverriddenFileType(docsFile))
    }

    fun testRegexPatternOverride() {
        val xmlFileType = FileTypeManager.getInstance().findFileTypeByName("XML")
        Assert.assertNotNull(xmlFileType)

        val specialFile = myFixture.addFileToProject("config/service-123.cfg", "<config/>").virtualFile
        val otherFile = myFixture.addFileToProject("config/other.txt", "text").virtualFile

        settings.setRules(listOf(
            FileTypeOverrideRule("config/service-\\d+\\.cfg", "XML", PatternSyntax.REGEX)
        ))

        Assert.assertEquals(xmlFileType, overrider.getOverriddenFileType(specialFile))
        Assert.assertNull(overrider.getOverriddenFileType(otherFile))
    }

    fun testSettingsStatePersistence() {
        val state = AdvancedFileTypeSettings.State()
        state.rules.add(FileTypeOverrideRule("templates/**/*.tpl", "HTML", PatternSyntax.GLOB))
        state.rules.add(FileTypeOverrideRule(".*\\.custom", "XML", PatternSyntax.REGEX))

        settings.loadState(state)

        val loaded = settings.getRules()
        Assert.assertEquals(2, loaded.size)
        Assert.assertEquals("templates/**/*.tpl", loaded[0].pattern)
        Assert.assertEquals("HTML", loaded[0].fileTypeName)
        Assert.assertEquals(PatternSyntax.GLOB, loaded[0].syntax)

        Assert.assertEquals(".*\\.custom", loaded[1].pattern)
        Assert.assertEquals("XML", loaded[1].fileTypeName)
        Assert.assertEquals(PatternSyntax.REGEX, loaded[1].syntax)
    }

    fun testConfigurableLifecycle() {
        val configurable = AdvancedFileTypeConfigurable(project)
        val component = configurable.createComponent()
        Assert.assertNotNull("Configurable component should not be null", component)
        Assert.assertEquals(
            "Configurable must declare the expected help topic ID",
            AdvancedFileTypeWebHelpProvider.HELP_TOPIC,
            configurable.helpTopic
        )

        settings.setRules(listOf(
            FileTypeOverrideRule("docs/*.txt", "HTML", PatternSyntax.GLOB)
        ))

        configurable.reset()
        Assert.assertFalse("Configurable should not be modified after reset", configurable.isModified())

        configurable.disposeUIResources()
    }

    fun testWebHelpProviderResolvesHelpUrl() {
        val provider = AdvancedFileTypeWebHelpProvider()
        Assert.assertEquals(AdvancedFileTypeWebHelpProvider.HELP_TOPIC_PREFIX, provider.helpTopicPrefix)

        val resolvedUrl = provider.getHelpPageUrl(AdvancedFileTypeWebHelpProvider.HELP_TOPIC)
        Assert.assertEquals(AdvancedFileTypeWebHelpProvider.README_URL, resolvedUrl)

        val otherTopicUrl = provider.getHelpPageUrl("some.other.plugin.topic")
        Assert.assertNull("Unrelated help topic should not be resolved by this provider", otherTopicUrl)
    }

    fun testAddEditRuleDialogCreationAndRuleExtraction() {
        val rule = FileTypeOverrideRule("docs/*.txt", "HTML", PatternSyntax.GLOB)
        val dialog = com.medmunds.advancedfiletypes.ui.AddEditRuleDialog(project, rule)
        val extracted = dialog.getRule()
        Assert.assertEquals("docs/*.txt", extracted.pattern)
        Assert.assertEquals("HTML", extracted.fileTypeName)
        Assert.assertEquals(PatternSyntax.GLOB, extracted.syntax)
        Assert.assertNotNull("fileTypeCombo should have SpeedSearch installed", SpeedSearchSupply.getSupply(dialog.fileTypeCombo, true))
        dialog.disposeIfNeeded()
    }

    fun testMessageBundleKeys() {
        Assert.assertEquals("File Type Matching", MyMessageBundle.message("settings.displayName"))
        Assert.assertEquals("Pattern", MyMessageBundle.message("settings.table.column.pattern"))
        Assert.assertEquals("Syntax", MyMessageBundle.message("settings.table.column.syntax"))
        Assert.assertEquals("File Type", MyMessageBundle.message("settings.table.column.fileType"))
        Assert.assertEquals("Glob", MyMessageBundle.message("syntax.glob"))
        Assert.assertEquals("Regex", MyMessageBundle.message("syntax.regex"))
        Assert.assertEquals("Glob", PatternSyntax.GLOB.displayName)
        Assert.assertEquals("Regex", PatternSyntax.REGEX.displayName)
        Assert.assertEquals("Add File Type Override Rule", MyMessageBundle.message("dialog.title.add"))
        Assert.assertEquals("Edit File Type Override Rule", MyMessageBundle.message("dialog.title.edit"))
        Assert.assertEquals("Pattern:", MyMessageBundle.message("dialog.pattern.label"))
        Assert.assertEquals("Syntax:", MyMessageBundle.message("dialog.syntax.label"))
        Assert.assertEquals("File Type:", MyMessageBundle.message("dialog.fileType.label"))
        Assert.assertEquals("Pattern cannot be empty", MyMessageBundle.message("dialog.validation.pattern.empty"))
        Assert.assertEquals("Invalid pattern syntax: bad", MyMessageBundle.message("dialog.validation.pattern.invalid", "bad"))
        Assert.assertEquals("Please select a file type", MyMessageBundle.message("dialog.validation.fileType.empty"))
        Assert.assertEquals("HTML (HTML files)", MyMessageBundle.message("fileType.display.withDescription", "HTML", "HTML files"))
        Assert.assertTrue(MyMessageBundle.message("dialog.pattern.help").contains("Glob patterns"))
    }
}
