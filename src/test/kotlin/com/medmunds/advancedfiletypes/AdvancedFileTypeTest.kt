package com.medmunds.advancedfiletypes

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.speedSearch.SpeedSearchSupply
import com.medmunds.advancedfiletypes.help.AdvancedFileTypeWebHelpProvider
import com.medmunds.advancedfiletypes.model.FileTypeOverrideRule
import com.medmunds.advancedfiletypes.model.PatternSyntax
import com.medmunds.advancedfiletypes.settings.AdvancedFileTypeSettings
import com.medmunds.advancedfiletypes.settings.MatchResult
import com.medmunds.advancedfiletypes.ui.AdvancedFileTypeConfigurable
import com.medmunds.advancedfiletypes.vscode.VsCodeAssociation
import com.medmunds.advancedfiletypes.vscode.VsCodeLanguageResolver
import com.medmunds.advancedfiletypes.vscode.VsCodeSettingsReader
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
        Assert.assertEquals("File Type Matching", AdvancedFileTypesBundle.message("settings.displayName"))
        Assert.assertEquals("Pattern", AdvancedFileTypesBundle.message("settings.table.column.pattern"))
        Assert.assertEquals("Syntax", AdvancedFileTypesBundle.message("settings.table.column.syntax"))
        Assert.assertEquals("File Type", AdvancedFileTypesBundle.message("settings.table.column.fileType"))
        Assert.assertEquals("Include file associations from .vscode/settings.json", AdvancedFileTypesBundle.message("settings.includeVsCode"))
        Assert.assertEquals("Glob", AdvancedFileTypesBundle.message("syntax.glob"))
        Assert.assertEquals("Regex", AdvancedFileTypesBundle.message("syntax.regex"))
        Assert.assertEquals("Glob", PatternSyntax.GLOB.displayName)
        Assert.assertEquals("Regex", PatternSyntax.REGEX.displayName)
        Assert.assertEquals("Add File Type Override Rule", AdvancedFileTypesBundle.message("dialog.title.add"))
        Assert.assertEquals("Edit File Type Override Rule", AdvancedFileTypesBundle.message("dialog.title.edit"))
        Assert.assertEquals("Pattern:", AdvancedFileTypesBundle.message("dialog.pattern.label"))
        Assert.assertEquals("Syntax:", AdvancedFileTypesBundle.message("dialog.syntax.label"))
        Assert.assertEquals("File Type:", AdvancedFileTypesBundle.message("dialog.fileType.label"))
        Assert.assertEquals("Pattern cannot be empty", AdvancedFileTypesBundle.message("dialog.validation.pattern.empty"))
        Assert.assertEquals("Invalid pattern syntax: bad", AdvancedFileTypesBundle.message("dialog.validation.pattern.invalid", "bad"))
        Assert.assertEquals("Please select a file type", AdvancedFileTypesBundle.message("dialog.validation.fileType.empty"))
        Assert.assertEquals("HTML (HTML files)", AdvancedFileTypesBundle.message("fileType.display.withDescription", "HTML", "HTML files"))
        Assert.assertTrue(AdvancedFileTypesBundle.message("dialog.pattern.help").contains("Glob patterns"))
    }

    fun testVsCodeSettingsReaderJsoncParsing() {
        val jsonc = """
            {
              // Comment line
              /* Multi-line
                 comment */
              "editor.tabSize": 2,
              "files.associations": {
                "**/docs/**/*.txt": "html",
                "*.special": "xml", // inline comment
                "bad": 123,
              },
            }
        """.trimIndent()

        val parsed = VsCodeSettingsReader.parseAssociations(jsonc)
        Assert.assertEquals(2, parsed.size)
        Assert.assertEquals(VsCodeAssociation("**/docs/**/*.txt", "html"), parsed[0])
        Assert.assertEquals(VsCodeAssociation("*.special", "xml"), parsed[1])

        val emptyParsed = VsCodeSettingsReader.parseAssociations("")
        Assert.assertTrue(emptyParsed.isEmpty())

        val invalidParsed = VsCodeSettingsReader.parseAssociations("{ not valid json: }}")
        Assert.assertTrue(invalidParsed.isEmpty())
    }

    @Suppress("KotlinMisorderedAssertEqualsArguments")
    fun testVsCodeLanguageResolver() {
        val htmlType = FileTypeManager.getInstance().findFileTypeByName("HTML")
        val xmlType = FileTypeManager.getInstance().findFileTypeByName("XML")
        Assert.assertNotNull(htmlType)
        Assert.assertNotNull(xmlType)

        // Predefined aliases
        Assert.assertEquals(htmlType, VsCodeLanguageResolver.resolveFileType("html"))
        Assert.assertEquals(xmlType, VsCodeLanguageResolver.resolveFileType("xml"))
        val plainTextType = FileTypeManager.getInstance().findFileTypeByName("PLAIN_TEXT")
        Assert.assertEquals(plainTextType, VsCodeLanguageResolver.resolveFileType("plaintext"))

        // Direct name matching
        Assert.assertEquals(htmlType, VsCodeLanguageResolver.resolveFileType("HTML"))

        // Unsupported language returns null
        Assert.assertNull(VsCodeLanguageResolver.resolveFileType("unknown_nonexistent_language_id_xyz"))

        // Custom mappings escape hatch
        val customType = VsCodeLanguageResolver.resolveFileType(
            "custom_lang",
            mapOf("custom_lang" to "HTML")
        )
        Assert.assertEquals(htmlType, customType)
    }

    fun testVsCodeSettingsIntegrationAndPrecedence() {
        val htmlType = FileTypeManager.getInstance().findFileTypeByName("HTML")
        val xmlType = FileTypeManager.getInstance().findFileTypeByName("XML")
        Assert.assertNotNull(htmlType)
        Assert.assertNotNull(xmlType)

        // Create .vscode/settings.json
        myFixture.addFileToProject(
            ".vscode/settings.json",
            """
            {
              // Project associations
              "files.associations": {
                "**/docs/**/*.txt": "html",
                "*.data": "xml"
              }
            }
            """.trimIndent()
        )
        VsCodeSettingsReader.clearCache()

        val docsFile = myFixture.addFileToProject("docs/guide.txt", "content").virtualFile
        val dataFile = myFixture.addFileToProject("src/sample.data", "<data/>").virtualFile
        val otherFile = myFixture.addFileToProject("src/plain.txt", "plain").virtualFile

        // With no plugin rules, VS Code associations take effect
        Assert.assertEquals(htmlType, overrider.getOverriddenFileType(docsFile))
        Assert.assertEquals(xmlType, overrider.getOverriddenFileType(dataFile))
        Assert.assertNull(overrider.getOverriddenFileType(otherFile))

        // Plugin rule takes precedence over VS Code setting
        settings.setRules(listOf(
            FileTypeOverrideRule("docs/*.txt", "XML", PatternSyntax.GLOB)
        ))
        Assert.assertEquals("Plugin rule must take precedence over VS Code setting", xmlType, overrider.getOverriddenFileType(docsFile))

        // When VS Code loading is disabled via setting, VS Code associations are ignored
        settings.setRules(emptyList())
        settings.setLoadFromVsCodeSettings(false)
        Assert.assertNull("VS Code settings should be ignored when disabled", overrider.getOverriddenFileType(docsFile))
        Assert.assertNull("VS Code settings should be ignored when disabled", overrider.getOverriddenFileType(dataFile))

        // Re-enable
        settings.setLoadFromVsCodeSettings(true)
        Assert.assertEquals(htmlType, overrider.getOverriddenFileType(docsFile))
    }

    fun testVsCodeSettingsJsonSelfOverride() {
        val htmlType = FileTypeManager.getInstance().findFileTypeByName("HTML")
        val xmlType = FileTypeManager.getInstance().findFileTypeByName("XML")
        Assert.assertNotNull(htmlType)
        Assert.assertNotNull(xmlType)

        // Create .vscode/settings.json that matches all json in .vscode/ as html
        val settingsFile = myFixture.addFileToProject(
            ".vscode/settings.json",
            """
            {
              // Self association rule
              "files.associations": {
                ".vscode/**/*.json": "html",
              }
            }
            """.trimIndent()
        ).virtualFile
        VsCodeSettingsReader.clearCache()

        val otherJsonFile = myFixture.addFileToProject(".vscode/other.json", "{}").virtualFile

        // Both .vscode/settings.json and .vscode/other.json should be overridden to HTML
        Assert.assertEquals(htmlType, overrider.getOverriddenFileType(otherJsonFile))
        Assert.assertEquals(htmlType, overrider.getOverriddenFileType(settingsFile))

        // Plugin rule should also be able to override .vscode/settings.json directly
        settings.setRules(listOf(
            FileTypeOverrideRule(".vscode/**/*.json", "XML", PatternSyntax.GLOB)
        ))
        Assert.assertEquals(xmlType, overrider.getOverriddenFileType(settingsFile))
        Assert.assertEquals(xmlType, overrider.getOverriddenFileType(otherJsonFile))
    }

    fun testVsCodeUnsupportedFileTypeGracefulFallback() {
        myFixture.addFileToProject(
            ".vscode/settings.json",
            """
            {
              "files.associations": {
                "docs/unsupported.txt": "unsupported_language_identifier_123",
                "docs/*.txt": "html"
              }
            }
            """.trimIndent()
        )
        VsCodeSettingsReader.clearCache()

        val unsupportedFile = myFixture.addFileToProject("docs/unsupported.txt", "content").virtualFile
        val otherFile = myFixture.addFileToProject("docs/other.txt", "content").virtualFile

        // Unsupported rule should fall through to subsequent rule
        val htmlType = FileTypeManager.getInstance().findFileTypeByName("HTML")
        Assert.assertEquals(htmlType, overrider.getOverriddenFileType(unsupportedFile))
        Assert.assertEquals(htmlType, overrider.getOverriddenFileType(otherFile))
    }

    fun testConfigurableWithVsCodeCheckboxAndDiagnostics() {
        myFixture.addFileToProject(
            ".vscode/settings.json",
            """
            {
              "files.associations": {
                "docs/*.txt": "html",
                "special/*.xyz": "unsupported_xyz_lang"
              }
            }
            """.trimIndent()
        )
        VsCodeSettingsReader.clearCache()

        val configurable = AdvancedFileTypeConfigurable(project)
        val component = configurable.createComponent()
        Assert.assertNotNull(component)

        // Checkbox initial state
        Assert.assertTrue(configurable.includeVsCodeCheckBox.isSelected)

        // Match result evaluation for plugin rule
        val pluginMatch = settings.evaluateMatch("docs/test.txt", "test.txt", listOf(
            FileTypeOverrideRule("docs/*.txt", "XML", PatternSyntax.GLOB)
        ), includeVsCode = true)
        Assert.assertTrue(pluginMatch is MatchResult.PluginRuleMatch)

        // Match result evaluation for VS Code setting
        val vscodeMatch = settings.evaluateMatch("docs/test.txt", "test.txt", emptyList(), includeVsCode = true)
        Assert.assertTrue(vscodeMatch is MatchResult.VsCodeMatch)

        // Match result evaluation for unsupported VS Code setting
        val unsupportedMatch = settings.evaluateMatch("special/file.xyz", "file.xyz", emptyList(), includeVsCode = true)
        Assert.assertTrue(unsupportedMatch is MatchResult.VsCodeUnsupportedMatch)

        // Match result evaluation when VS Code is disabled
        val noMatchWhenDisabled = settings.evaluateMatch("docs/test.txt", "test.txt", emptyList(), includeVsCode = false)
        Assert.assertTrue(noMatchWhenDisabled is MatchResult.NoMatch)

        // Toggle checkbox and verify isModified
        configurable.includeVsCodeCheckBox.isSelected = false
        Assert.assertTrue(configurable.isModified())

        configurable.apply()
        Assert.assertFalse(settings.isLoadFromVsCodeSettings())

        configurable.reset()
        Assert.assertFalse(configurable.includeVsCodeCheckBox.isSelected)

        settings.setLoadFromVsCodeSettings(true)
        configurable.reset()
        Assert.assertTrue(configurable.includeVsCodeCheckBox.isSelected)

        configurable.disposeUIResources()
    }

    fun testConfigurablePatternTesterWrappingAndTwoLineHeight() {
        myFixture.addFileToProject(
            ".vscode/settings.json",
            """
            {
              "files.associations": {
                "docs/*.txt": "html"
              }
            }
            """.trimIndent()
        )
        VsCodeSettingsReader.clearCache()

        val configurable = AdvancedFileTypeConfigurable(project)
        val component = configurable.createComponent()
        Assert.assertNotNull(component)

        val resultLabel = configurable.testResultLabel
        Assert.assertEquals(javax.swing.SwingConstants.TOP, resultLabel.verticalAlignment)

        // Preferred and minimum height should allocate space for at least 2 lines of text
        val fontMetrics = resultLabel.getFontMetrics(resultLabel.font)
        val twoLinesHeight = (fontMetrics?.height ?: 16) * 2
        Assert.assertTrue(
            "Preferred height should be at least two lines",
            resultLabel.preferredSize.height >= twoLinesHeight
        )
        Assert.assertTrue(
            "Minimum height should be at least two lines",
            resultLabel.minimumSize.height >= twoLinesHeight
        )
        Assert.assertEquals(
            "Preferred width should be 0 to avoid forcing horizontal scrolling",
            0,
            resultLabel.preferredSize.width
        )
        Assert.assertEquals(
            "Minimum width should be 0 to avoid forcing horizontal scrolling",
            0,
            resultLabel.minimumSize.width
        )

        // Test matched state uses HTML wrapping and displays icon
        val htmlType = FileTypeManager.getInstance().findFileTypeByName("HTML")
        configurable.testPathField.text = "docs/test.txt"
        Assert.assertTrue("Result text should be wrapped in HTML", resultLabel.text.startsWith("<html>"))
        Assert.assertTrue(resultLabel.text.contains("docs/*.txt"))
        Assert.assertTrue(resultLabel.text.contains("HTML"))
        Assert.assertEquals(htmlType?.icon, resultLabel.icon)

        // Space remains allocated for two lines when text is populated
        Assert.assertTrue(resultLabel.preferredSize.height >= twoLinesHeight)

        // Test unmatched state uses HTML wrapping without icon
        configurable.testPathField.text = "unknown/file.abc"
        Assert.assertTrue("Unmatched result text should be wrapped in HTML", resultLabel.text.startsWith("<html>"))
        Assert.assertNull(resultLabel.icon)

        // Verify left edges of testPathField and testResultLabel are aligned
        component.setSize(800, 600)
        component.validate()
        val pathPos = javax.swing.SwingUtilities.convertPoint(configurable.testPathField, 0, 0, component)
        val resultPos = javax.swing.SwingUtilities.convertPoint(resultLabel, 0, 0, component)
        Assert.assertEquals("Left edges of sample path input and result label should be aligned", pathPos.x, resultPos.x)

        configurable.disposeUIResources()
    }
}
