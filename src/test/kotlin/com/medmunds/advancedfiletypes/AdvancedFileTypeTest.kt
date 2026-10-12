package com.medmunds.advancedfiletypes

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.speedSearch.SpeedSearchSupply
import com.medmunds.advancedfiletypes.gitattributes.GitAttributesService
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
import java.io.File

class AdvancedFileTypeTest : BasePlatformTestCase() {

    private lateinit var settings: AdvancedFileTypeSettings
    private val overrider = AdvancedFileTypeOverrider()
    private lateinit var tempGitDir: File

    override fun setUp() {
        super.setUp()
        settings = AdvancedFileTypeSettings.getInstance(project)
        settings.setRules(emptyList())

        val tempDir = kotlin.io.path.createTempDirectory("adv-git-test").toFile()
        tempGitDir = tempDir
        val pb = ProcessBuilder("git", "init")
        pb.directory(tempDir)
        val proc = pb.start()
        proc.waitFor()

        val service = GitAttributesService.getInstance(project)
        service.customProjectDirectory = tempGitDir
        service.clearCache()
    }

    override fun tearDown() {
        try {
            settings.setRules(emptyList())
            GitAttributesService.getInstance(project).clearCache()
            tempGitDir.deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    private fun addGitAttributes(content: String, relativePath: String = ".gitattributes"): com.intellij.psi.PsiFile {
        val file = File(tempGitDir, relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
        GitAttributesService.getInstance(project).clearCache()
        return myFixture.addFileToProject(relativePath, content)
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

    fun testPluginRuleUnsupportedFileTypeGracefulFallback() {
        val file = myFixture.addFileToProject("docs/config.txt", "content").virtualFile

        // Rule with non-existent file type should be silently skipped
        settings.setRules(listOf(
            FileTypeOverrideRule("docs/*.txt", "NonExistentFileType123", PatternSyntax.GLOB)
        ))
        Assert.assertNull("Unsupported file type rule should be silently skipped", overrider.getOverriddenFileType(file))

        // When a valid rule follows an unsupported one, the valid rule should still match
        val htmlType = FileTypeManager.getInstance().findFileTypeByName("HTML")
        settings.setRules(listOf(
            FileTypeOverrideRule("docs/*.txt", "NonExistentFileType123", PatternSyntax.GLOB),
            FileTypeOverrideRule("docs/*.txt", "HTML", PatternSyntax.GLOB)
        ))
        Assert.assertEquals("Second valid rule should match after unsupported rule is skipped", htmlType, overrider.getOverriddenFileType(file))

        // evaluateMatch should return NoMatch when all matching rules have unsupported types
        val noMatchResult = settings.evaluateMatch("docs/config.txt", "config.txt", listOf(
            FileTypeOverrideRule("docs/*.txt", "NonExistentFileType123", PatternSyntax.GLOB)
        ), includeVsCode = false, includeGitAttributes = false)
        Assert.assertTrue("Should return NoMatch when all rules reference unsupported types", noMatchResult is MatchResult.NoMatch)
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

        val typeLabel = configurable.testResultCell.component
        val detailLabel = configurable.testResultCell.comment

        // Test matched state: type label shows file type with icon, detail label shows match info
        val htmlType = FileTypeManager.getInstance().findFileTypeByName("HTML")
        configurable.testPathField.text = "docs/test.txt"
        configurable.updateTestResult()
        Assert.assertTrue("Type label should show HTML", typeLabel.text.contains("HTML"))
        Assert.assertEquals(htmlType?.icon, typeLabel.icon)
        Assert.assertTrue("Detail label should contain pattern", (detailLabel?.text ?: "").contains("docs/*.txt"))

        // Test unmatched state: type label shows IDE default, detail label shows no override
        configurable.testPathField.text = "unknown/file.abc"
        configurable.updateTestResult()
        Assert.assertTrue("Type label should indicate IDE default", typeLabel.text.contains("default", true))
        Assert.assertNull(typeLabel.icon)
        Assert.assertTrue("Detail label should indicate no override", (detailLabel?.text ?: "").contains("No override", true))

        // Test empty path clears both labels (uses NBSP to prevent layout shifts)
        configurable.testPathField.text = ""
        configurable.updateTestResult()
        Assert.assertEquals("\u00A0", typeLabel.text)
        Assert.assertNull(typeLabel.icon)
        // Detail label is cleared to NBSP; the DSL comment label wraps it in HTML with CSS styles
        Assert.assertTrue("Detail label should contain NBSP entity", (detailLabel?.text ?: "").contains("&#160;"))

        configurable.disposeUIResources()
    }

    fun testTestPatternMatchingHtmlEscaping() {
        // Regression test: patterns containing HTML-sensitive characters must be escaped
        // to prevent HTML injection in the detail label (DSL comment renders HTML)

        val configurable = AdvancedFileTypeConfigurable(project)
        val component = configurable.createComponent()
        Assert.assertNotNull(component)

        val detailLabel = configurable.testResultCell.comment

        settings.setRules(listOf(
            FileTypeOverrideRule(
                pattern = "[<>&]*.y",
                fileTypeName = "HTML",
                syntax = PatternSyntax.GLOB
            )
        ))
        configurable.reset()
        configurable.testPathField.text = "&foo.y"
        configurable.updateTestResult()
        val detailText = detailLabel?.text ?: ""
        Assert.assertTrue(
            "Pattern characters should be escaped in HTML-rendering label",
            detailText.contains("[&lt;&gt;&amp;]*.y")
        )
        Assert.assertFalse(
            "Raw <>& should not appear unescaped (would indicate HTML injection)",
            detailText.contains("[<>&]")
        )

        configurable.disposeUIResources()
    }

    fun testTestPatternMatchingHtmlEscapingInVsCodeLanguageId() {
        // Regression test: VS Code language IDs containing HTML-sensitive characters
        // must be escaped to prevent HTML injection in the detail label
        // Simulates: { "*.html": "<&script>" } in .vscode/settings.json

        val configurable = AdvancedFileTypeConfigurable(project)
        val component = configurable.createComponent()
        Assert.assertNotNull(component)

        val detailLabel = configurable.testResultCell.comment

        myFixture.addFileToProject(
            ".vscode/settings.json",
            """
            {
              "files.associations": {
                "*.vsc-test": "<&script>"
              }
            }
            """.trimIndent()
        )
        VsCodeSettingsReader.clearCache()
        configurable.reset()

        configurable.testPathField.text = "test.vsc-test"
        configurable.updateTestResult()
        val vscodeDetail = detailLabel?.text ?: ""
        Assert.assertTrue(
            "VS Code language ID HTML characters should be escaped",
            vscodeDetail.contains("&lt;&amp;script&gt;")
        )
        Assert.assertFalse(
            "Raw <&script> should not appear unescaped in VS Code language ID",
            vscodeDetail.contains("<&script>")
        )

        configurable.disposeUIResources()
    }

    fun testTestPatternMatchingHtmlEscapingInGitattributesLanguage() {
        // Regression test: gitattributes linguist language values containing HTML-sensitive
        // characters must be escaped to prevent HTML injection in the detail label

        val configurable = AdvancedFileTypeConfigurable(project)
        val component = configurable.createComponent()
        Assert.assertNotNull(component)

        val detailLabel = configurable.testResultCell.comment

        addGitAttributes("*.git-test linguist-language=<&data>\n")
        configurable.reset()

        configurable.testPathField.text = "test.git-test"
        configurable.updateTestResult()
        val gitDetail = detailLabel?.text ?: ""
        Assert.assertTrue(
            "Gitattributes linguist language HTML characters should be escaped",
            gitDetail.contains("&lt;&amp;data&gt;")
        )
        Assert.assertFalse(
            "Raw <&data> should not appear unescaped in gitattributes language",
            gitDetail.contains("<&data>")
        )

        configurable.disposeUIResources()
    }

    fun testGitAttributesServiceIntegration() {
        val service = GitAttributesService.getInstance(project)
        service.clearCache()

        // Initially without .gitattributes
        Assert.assertNull(service.getLanguage("sample.data"))

        // Add .gitattributes to project
        addGitAttributes(
            """
            # Git attributes config
            *.data linguist-language=JSON
            docs/*.txt linguist-language=Markdown
            raw/* -linguist-language
            """.trimIndent()
        )

        Assert.assertEquals("JSON", service.getLanguage("sample.data"))
        Assert.assertEquals("Markdown", service.getLanguage("docs/readme.txt"))
        Assert.assertNull("raw/* should be unset", service.getLanguage("raw/file.txt"))

        // Verify caching
        Assert.assertTrue(service.isCached("sample.data"))

        // Modify .gitattributes file on disk
        val gitAttrFile = File(tempGitDir, ".gitattributes")
        gitAttrFile.writeText("*.updated linguist-language=Kotlin\n")
        service.clearCache()

        Assert.assertEquals("Kotlin", service.getLanguage("sample.updated"))
        Assert.assertNull(service.getLanguage("sample.data"))
    }

    fun testGitAttributesLinguistLanguageOverride() {
        val jsonType = FileTypeManager.getInstance().findFileTypeByName("JSON")
        Assert.assertNotNull(jsonType)

        addGitAttributes("*.data linguist-language=JSON\n")

        val dataFile = myFixture.addFileToProject("src/payload.data", "{}").virtualFile
        val overridden = overrider.getOverriddenFileType(dataFile)
        Assert.assertEquals("payload.data should be overridden as JSON via .gitattributes", jsonType, overridden)
    }

    fun testGitAttributesLastMatchWinsAndNegation() {
        val htmlType = FileTypeManager.getInstance().findFileTypeByName("HTML")
        val xmlType = FileTypeManager.getInstance().findFileTypeByName("XML")
        Assert.assertNotNull(htmlType)
        Assert.assertNotNull(xmlType)

        addGitAttributes(
            """
            *.txt linguist-language=HTML
            docs/*.txt linguist-language=XML
            docs/raw.txt -linguist-language
            """.trimIndent()
        )

        val rootText = myFixture.addFileToProject("readme.txt", "text").virtualFile
        val docsGuide = myFixture.addFileToProject("docs/guide.txt", "text").virtualFile
        val docsRaw = myFixture.addFileToProject("docs/raw.txt", "text").virtualFile

        Assert.assertEquals("readme.txt should match *.txt (HTML)", htmlType, overrider.getOverriddenFileType(rootText))
        Assert.assertEquals("docs/guide.txt should match docs/*.txt (XML, last match wins)", xmlType, overrider.getOverriddenFileType(docsGuide))
        Assert.assertNull("docs/raw.txt was unset with -linguist-language and should fall back to default", overrider.getOverriddenFileType(docsRaw))
    }

    fun testPrecedencePluginOverVsCodeOverGitAttributes() {
        val htmlType = FileTypeManager.getInstance().findFileTypeByName("HTML")
        val xmlType = FileTypeManager.getInstance().findFileTypeByName("XML")
        val jsonType = FileTypeManager.getInstance().findFileTypeByName("JSON")
        Assert.assertNotNull(htmlType)
        Assert.assertNotNull(xmlType)
        Assert.assertNotNull(jsonType)

        // 1. .gitattributes sets *.custom -> JSON
        addGitAttributes("*.custom linguist-language=JSON\n")

        // 2. .vscode/settings.json sets *.custom -> XML
        myFixture.addFileToProject(
            ".vscode/settings.json",
            """
            {
              "files.associations": {
                "*.custom": "xml"
              }
            }
            """.trimIndent()
        )
        VsCodeSettingsReader.clearCache()

        val customFile = myFixture.addFileToProject("sample.custom", "<content/>").virtualFile

        // With no plugin rule, VS Code association takes precedence over .gitattributes
        Assert.assertEquals("VS Code association should override .gitattributes", xmlType, overrider.getOverriddenFileType(customFile))

        // When VS Code is disabled, .gitattributes takes effect
        settings.setLoadFromVsCodeSettings(false)
        Assert.assertEquals(".gitattributes should apply when VS Code is disabled", jsonType, overrider.getOverriddenFileType(customFile))

        // When a plugin rule is added, plugin rule takes highest precedence
        settings.setRules(listOf(FileTypeOverrideRule("*.custom", "HTML", PatternSyntax.GLOB)))
        Assert.assertEquals("Plugin rule should have highest precedence", htmlType, overrider.getOverriddenFileType(customFile))

        // Re-enable VS Code settings, plugin rule still wins
        settings.setLoadFromVsCodeSettings(true)
        Assert.assertEquals("Plugin rule still wins when VS Code is re-enabled", htmlType, overrider.getOverriddenFileType(customFile))
    }

    fun testGitAttributesDisabledViaSettings() {
        val jsonType = FileTypeManager.getInstance().findFileTypeByName("JSON")
        Assert.assertNotNull(jsonType)

        addGitAttributes("*.data linguist-language=JSON\n")

        val dataFile = myFixture.addFileToProject("sample.data", "{}").virtualFile
        Assert.assertEquals(jsonType, overrider.getOverriddenFileType(dataFile))

        settings.setLoadFromGitAttributes(false)
        Assert.assertNull("When loadFromGitAttributes is false, .gitattributes should not override", overrider.getOverriddenFileType(dataFile))

        settings.setLoadFromGitAttributes(true)
        Assert.assertEquals(jsonType, overrider.getOverriddenFileType(dataFile))
    }

    fun testGitAttributesUnsupportedLanguageGracefulFallback() {
        addGitAttributes("*.xyz linguist-language=CompletelyUnsupportedLanguageXYZ\n")

        val xyzFile = myFixture.addFileToProject("sample.xyz", "data").virtualFile
        Assert.assertNull("Unsupported linguist language should fall through to default without error", overrider.getOverriddenFileType(xyzFile))

        val match = settings.evaluateMatch("sample.xyz", "sample.xyz", includeGitAttributes = true)
        Assert.assertTrue("evaluateMatch should return GitAttributesUnsupportedMatch", match is MatchResult.GitAttributesUnsupportedMatch)
        val unsupported = match as MatchResult.GitAttributesUnsupportedMatch
        Assert.assertEquals("sample.xyz", unsupported.pattern)
        Assert.assertEquals("CompletelyUnsupportedLanguageXYZ", unsupported.language)
    }

    fun testGitAttributesFileListenerLiveChanges() {
        val jsonType = FileTypeManager.getInstance().findFileTypeByName("JSON")
        val xmlType = FileTypeManager.getInstance().findFileTypeByName("XML")
        Assert.assertNotNull(jsonType)
        Assert.assertNotNull(xmlType)

        val gitAttrPsi = addGitAttributes("*.payload linguist-language=JSON\n")
        val gitAttrVirtualFile = gitAttrPsi.virtualFile
        val payloadFile = myFixture.addFileToProject("test.payload", "data").virtualFile

        Assert.assertEquals("Initial .gitattributes gives JSON", jsonType, overrider.getOverriddenFileType(payloadFile))

        // Modify .gitattributes content
        val diskFile = File(tempGitDir, ".gitattributes")
        diskFile.writeText("*.payload linguist-language=XML\n")
        WriteCommandAction.runWriteCommandAction(project) {
            VfsUtil.saveText(gitAttrVirtualFile, "*.payload linguist-language=XML\n")
        }

        Assert.assertEquals("Modified .gitattributes dynamically gives XML", xmlType, overrider.getOverriddenFileType(payloadFile))

        // Delete .gitattributes file
        diskFile.delete()
        WriteCommandAction.runWriteCommandAction(project) {
            gitAttrVirtualFile.delete(this)
        }

        Assert.assertNull("Deleted .gitattributes removes override", overrider.getOverriddenFileType(payloadFile))
    }

    fun testVsCodeUnsupportedFallsThroughToGitAttributes() {
        val jsonType = FileTypeManager.getInstance().findFileTypeByName("JSON")
        Assert.assertNotNull(jsonType)

        // VS Code maps *.data to unsupported language
        myFixture.addFileToProject(
            ".vscode/settings.json",
            """
            {
              "files.associations": {
                "*.data": "unsupported-unknown-lang"
              }
            }
            """.trimIndent()
        )
        VsCodeSettingsReader.clearCache()

        // .gitattributes maps *.data to JSON
        addGitAttributes("*.data linguist-language=JSON\n")

        val dataFile = myFixture.addFileToProject("sample.data", "{}").virtualFile

        // Both findMatchingFileType and evaluateMatch should fall through to GitAttributes
        Assert.assertEquals("Overrider should fall through from unsupported VS Code to GitAttributes", jsonType, overrider.getOverriddenFileType(dataFile))

        val match = settings.evaluateMatch("sample.data", "sample.data", includeVsCode = true, includeGitAttributes = true)
        Assert.assertTrue("evaluateMatch should fall through and return GitAttributesMatch", match is MatchResult.GitAttributesMatch)
        val gitMatch = match as MatchResult.GitAttributesMatch
        Assert.assertEquals("sample.data", gitMatch.pattern)
        Assert.assertEquals("JSON", gitMatch.language)
        Assert.assertEquals(jsonType, gitMatch.fileType)
    }

    fun testGitAttributesOnlyAppliesWithinProject() {
        val jsonType = FileTypeManager.getInstance().findFileTypeByName("JSON")
        Assert.assertNotNull(jsonType)

        addGitAttributes("*.data linguist-language=JSON\n")

        // When relativePath is null (outside project), .gitattributes does not apply
        val matchOutside = settings.evaluateMatch(null, "sample.data", includeGitAttributes = true)
        Assert.assertEquals("Files outside project should not match gitattributes", MatchResult.NoMatch, matchOutside)

        val lightFile = com.intellij.testFramework.LightVirtualFile("sample.data", "{}")
        Assert.assertNull("Virtual files outside project root should not match gitattributes", overrider.getOverriddenFileType(lightFile))
    }

    fun testGitAttributesListenerNotifiesOnGitAttributesChanges() {
        val topic = FileTypeManager.TOPIC
        var notificationCount = 0
        val connection = com.intellij.openapi.application.ApplicationManager.getApplication().messageBus.connect(testRootDisposable)
        connection.subscribe(topic, object : com.intellij.openapi.fileTypes.FileTypeListener {
            override fun fileTypesChanged(event: com.intellij.openapi.fileTypes.FileTypeEvent) {
                notificationCount++
            }
        })

        val gitAttrPsi = addGitAttributes("*.payload linguist-language=JSON\n")
        val gitAttrVirtualFile = gitAttrPsi.virtualFile

        val countAfterAdd = notificationCount

        // 1. Modifying root .gitattributes should trigger notification
        val diskFile = File(tempGitDir, ".gitattributes")
        diskFile.writeText("*.payload linguist-language=XML\n")
        WriteCommandAction.runWriteCommandAction(project) {
            VfsUtil.saveText(gitAttrVirtualFile, "*.payload linguist-language=XML\n")
        }
        Assert.assertTrue("Notification count should increase after root .gitattributes change", notificationCount > countAfterAdd)

        // 2. Modifying a nested .gitattributes (e.g. sub/.gitattributes) should ALSO trigger notification
        val countBeforeNested = notificationCount
        val nestedGitAttr = addGitAttributes("*.txt text\n", "sub/.gitattributes").virtualFile

        val nestedDiskFile = File(tempGitDir, "sub/.gitattributes")
        nestedDiskFile.writeText("*.txt linguist-language=JSON\n")
        WriteCommandAction.runWriteCommandAction(project) {
            VfsUtil.saveText(nestedGitAttr, "*.txt linguist-language=JSON\n")
        }
        Assert.assertTrue("Nested .gitattributes change should trigger fileTypesChanged notification", notificationCount > countBeforeNested)
    }

    fun testGitAttributesCheckboxLifecycle() {
        // Verify default state is enabled
        Assert.assertTrue("GitAttributes should be enabled by default", settings.isLoadFromGitAttributes())

        // Disable via settings
        settings.setLoadFromGitAttributes(false)
        Assert.assertFalse("GitAttributes should be disabled", settings.isLoadFromGitAttributes())

        // Re-enable
        settings.setLoadFromGitAttributes(true)
        Assert.assertTrue("GitAttributes should be re-enabled", settings.isLoadFromGitAttributes())

        // Verify persistence through getState/loadState
        val state = settings.state
        Assert.assertTrue("State should persist enabled value", state.loadFromGitAttributes)

        settings.setLoadFromGitAttributes(false)
        val disabledState = settings.state
        Assert.assertFalse("State should persist disabled value", disabledState.loadFromGitAttributes)
    }

    fun testGitAttributesLiveTesterWithCheckbox() {
        addGitAttributes("*.data linguist-language=JSON\n")

        val configurable = AdvancedFileTypeConfigurable(project)
        configurable.createComponent()
        configurable.reset()

        // Verify checkbox is checked by default
        Assert.assertTrue("GitAttributes checkbox should be checked by default", configurable.includeGitAttributesCheckBox.isSelected)

        // Test with checkbox enabled - should match
        configurable.testPathField.text = "test.data"
        configurable.updateTestResult()
        val typeText1 = configurable.testResultCell.component.text
        val detailText1 = configurable.testResultCell.comment?.text ?: ""
        Assert.assertTrue(
            "Test result should show gitattributes match when enabled",
            typeText1.contains("JSON", true) ||
            detailText1.contains(".gitattributes", true)
        )

        // Disable checkbox and verify no match
        configurable.includeGitAttributesCheckBox.isSelected = false
        configurable.updateTestResult()
        val typeText2 = configurable.testResultCell.component.text
        val detailText2 = configurable.testResultCell.comment?.text ?: ""
        Assert.assertTrue(
            "Test result should show no match when gitattributes is disabled",
            typeText2.contains("default", true) ||
            detailText2.contains("No override", true)
        )

        // Re-enable and verify match again
        configurable.includeGitAttributesCheckBox.isSelected = true
        configurable.updateTestResult()
        val typeText3 = configurable.testResultCell.component.text
        val detailText3 = configurable.testResultCell.comment?.text ?: ""
        Assert.assertTrue(
            "Test result should show gitattributes match when re-enabled",
            typeText3.contains("JSON", true) ||
            detailText3.contains(".gitattributes", true)
        )

        configurable.disposeUIResources()
    }

    fun testGitAttributesEvaluateMatchToggle() {
        addGitAttributes("*.data linguist-language=JSON\n")

        // With gitattributes enabled
        val matchEnabled = settings.evaluateMatch("test.data", "test.data", includeGitAttributes = true)
        Assert.assertTrue("Should match when gitattributes enabled", matchEnabled is MatchResult.GitAttributesMatch)

        // With gitattributes disabled
        val matchDisabled = settings.evaluateMatch("test.data", "test.data", includeGitAttributes = false)
        Assert.assertEquals("Should not match when gitattributes disabled", MatchResult.NoMatch, matchDisabled)
    }

    fun testGitAttributesDisabledWhenNoGitRepo() {
        val nonGitDir = kotlin.io.path.createTempDirectory("non-git-ui-test").toFile()
        try {
            val service = GitAttributesService.getInstance(project)
            service.customProjectDirectory = nonGitDir
            service.clearCache()

            val configurable = AdvancedFileTypeConfigurable(project)
            configurable.createComponent()
            configurable.reset()

            // When no git repository is present, checkbox should be unchecked and disabled
            Assert.assertFalse("Checkbox should be disabled when no git repository", configurable.includeGitAttributesCheckBox.isEnabled)
            Assert.assertFalse("Checkbox should be unchecked when no git repository", configurable.includeGitAttributesCheckBox.isSelected)

            // isModified should be false
            Assert.assertFalse(configurable.isModified)

            configurable.disposeUIResources()
        } finally {
            nonGitDir.deleteRecursively()
            val service = GitAttributesService.getInstance(project)
            service.customProjectDirectory = tempGitDir
            service.clearCache()
        }
    }
}
