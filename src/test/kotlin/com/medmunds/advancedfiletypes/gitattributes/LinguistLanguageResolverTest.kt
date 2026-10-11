package com.medmunds.advancedfiletypes.gitattributes

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Properties

class LinguistLanguageResolverTest : BasePlatformTestCase() {

    fun testPropertiesFileParsingAndIntegrity() {
        val resourcePath = "/com/medmunds/advancedfiletypes/linguist-language-map.properties"
        val stream = LinguistLanguageResolver::class.java.getResourceAsStream(resourcePath)
        Assert.assertNotNull("linguist-language-map.properties must exist", stream)

        val properties = Properties()
        InputStreamReader(stream!!, StandardCharsets.UTF_8).use { reader ->
            properties.load(reader)
        }

        Assert.assertTrue("Properties should contain at least 50 mappings", properties.size >= 50)

        for (key in properties.stringPropertyNames()) {
            Assert.assertFalse("Property key '$key' must not contain unescaped '='", key.contains('='))
            Assert.assertFalse("Property key '$key' must not contain unescaped ':'", key.contains(':'))
            val value = properties.getProperty(key)
            Assert.assertNotNull("Value for '$key' must not be null", value)
            Assert.assertTrue("Value for '$key' must not be blank", value.isNotBlank())
        }

        // Assert multi-word keys with escaped spaces are loaded correctly by Properties
        Assert.assertEquals("JSON5", properties.getProperty("json with comments"))
        Assert.assertEquals("PLAIN_TEXT", properties.getProperty("plain text"))
        Assert.assertEquals("Properties", properties.getProperty("java properties"))
        Assert.assertEquals("Protocol Buffer", properties.getProperty("protocol buffer"))
        Assert.assertEquals("Jupyter Notebook", properties.getProperty("jupyter notebook"))
        Assert.assertEquals("GitIgnore file", properties.getProperty("ignore list"))
        Assert.assertEquals("GitConfig file", properties.getProperty("git config"))
        Assert.assertEquals("Visual Basic", properties.getProperty("visual basic"))
        Assert.assertEquals("JSONL", properties.getProperty("json lines"))
    }

    fun testStaticTargetNameLookup() {
        Assert.assertEquals("JSON5", LinguistLanguageResolver.getStaticTargetName("JSON with Comments"))
        Assert.assertEquals("JSON5", LinguistLanguageResolver.getStaticTargetName("json-with-comments"))
        Assert.assertEquals("JSON5", LinguistLanguageResolver.getStaticTargetName("jsonc"))

        Assert.assertEquals("Shell Script", LinguistLanguageResolver.getStaticTargetName("Shell"))
        Assert.assertEquals("Shell Script", LinguistLanguageResolver.getStaticTargetName("bash"))
        Assert.assertEquals("Shell Script", LinguistLanguageResolver.getStaticTargetName("sh"))

        Assert.assertEquals("GitIgnore file", LinguistLanguageResolver.getStaticTargetName("Ignore List"))
        Assert.assertEquals("GitIgnore file", LinguistLanguageResolver.getStaticTargetName("ignore-list"))
        Assert.assertEquals("GitIgnore file", LinguistLanguageResolver.getStaticTargetName("gitignore"))

        Assert.assertEquals("GitConfig file", LinguistLanguageResolver.getStaticTargetName("Git Config"))
        Assert.assertEquals("GitConfig file", LinguistLanguageResolver.getStaticTargetName("gitconfig"))

        Assert.assertEquals("ReST", LinguistLanguageResolver.getStaticTargetName("reStructuredText"))
        Assert.assertEquals("ReST", LinguistLanguageResolver.getStaticTargetName("rst"))

        Assert.assertEquals("C++", LinguistLanguageResolver.getStaticTargetName("C++"))
        Assert.assertEquals("C++", LinguistLanguageResolver.getStaticTargetName("cpp"))
        Assert.assertEquals("C++", LinguistLanguageResolver.getStaticTargetName("cplusplus"))

        Assert.assertEquals("Jupyter Notebook", LinguistLanguageResolver.getStaticTargetName("Jupyter Notebook"))
        Assert.assertEquals("Jupyter Notebook", LinguistLanguageResolver.getStaticTargetName("ipynb"))

        Assert.assertEquals("Protocol Buffer", LinguistLanguageResolver.getStaticTargetName("Protocol Buffer"))
        Assert.assertEquals("Protocol Buffer", LinguistLanguageResolver.getStaticTargetName("proto"))
    }

    fun testExcludedAndFabricatedLanguagesNotMapped() {
        // C should not map to C++ via map
        Assert.assertNull(LinguistLanguageResolver.getStaticTargetName("c"))

        // Raku / Perl 6 should not map to Perl
        Assert.assertNull(LinguistLanguageResolver.getStaticTargetName("raku"))
        Assert.assertNull(LinguistLanguageResolver.getStaticTargetName("perl 6"))
        Assert.assertNull(LinguistLanguageResolver.getStaticTargetName("perl6"))

        // Git attributes should not map to GitIgnore
        Assert.assertNull(LinguistLanguageResolver.getStaticTargetName("git attributes"))
        Assert.assertNull(LinguistLanguageResolver.getStaticTargetName("gitattributes"))

        // Console/terminal sessions should not map to Shell Script
        Assert.assertNull(LinguistLanguageResolver.getStaticTargetName("shellsession"))
        Assert.assertNull(LinguistLanguageResolver.getStaticTargetName("console"))

        // Fabricated aliases not in official Linguist
        Assert.assertNull(LinguistLanguageResolver.getStaticTargetName("kt"))
        Assert.assertNull(LinguistLanguageResolver.getStaticTargetName("py"))
        Assert.assertNull(LinguistLanguageResolver.getStaticTargetName("txt"))
        Assert.assertNull(LinguistLanguageResolver.getStaticTargetName("mdown"))
        Assert.assertNull(LinguistLanguageResolver.getStaticTargetName("cmd"))
        Assert.assertNull(LinguistLanguageResolver.getStaticTargetName(".env"))
    }

    @Suppress("KotlinMisorderedAssertEqualsArguments")
    fun testStaticPredefinedMappings() {
        val htmlType = LinguistLanguageResolver.resolveFileType("HTML")
        Assert.assertNotNull(htmlType)
        Assert.assertEquals("HTML", htmlType?.name)

        val htmlAlias = LinguistLanguageResolver.resolveFileType("xhtml")
        Assert.assertNotNull(htmlAlias)
        Assert.assertEquals("HTML", htmlAlias?.name)

        val xmlType = LinguistLanguageResolver.resolveFileType("XML")
        Assert.assertNotNull(xmlType)
        Assert.assertEquals("XML", xmlType?.name)

        val xmlAlias = LinguistLanguageResolver.resolveFileType("rss")
        Assert.assertNotNull(xmlAlias)
        Assert.assertEquals("XML", xmlAlias?.name)

        val jsonType = LinguistLanguageResolver.resolveFileType("JSON")
        Assert.assertNotNull(jsonType)
        Assert.assertEquals("JSON", jsonType?.name)

        val jsonGeo = LinguistLanguageResolver.resolveFileType("geojson")
        Assert.assertNotNull(jsonGeo)
        Assert.assertEquals("JSON", jsonGeo?.name)

        val javaType = LinguistLanguageResolver.resolveFileType("Java")
        Assert.assertNotNull(javaType)
        Assert.assertEquals("JAVA", javaType?.name)

        val plainTextType = LinguistLanguageResolver.resolveFileType("Plain Text")
        Assert.assertNotNull(plainTextType)
        Assert.assertEquals(PlainTextFileType.INSTANCE, plainTextType)

        val fundamentalType = LinguistLanguageResolver.resolveFileType("fundamental")
        Assert.assertNotNull(fundamentalType)
        Assert.assertEquals(PlainTextFileType.INSTANCE, fundamentalType)

        val shellFileType = FileTypeManager.getInstance().findFileTypeByName("Shell Script")
        if (shellFileType != null) {
            val shellType = LinguistLanguageResolver.resolveFileType("Shell")
            Assert.assertEquals(shellFileType, shellType)
            val bashType = LinguistLanguageResolver.resolveFileType("bash")
            Assert.assertEquals(shellFileType, bashType)
        }
    }

    fun testCaseInsensitiveAndAliasResolution() {
        val jsonLower = LinguistLanguageResolver.resolveFileType("json")
        val jsonUpper = LinguistLanguageResolver.resolveFileType("JSON")
        val jsonGeo = LinguistLanguageResolver.resolveFileType("geojson")

        Assert.assertNotNull(jsonLower)
        Assert.assertEquals(jsonLower, jsonUpper)
        Assert.assertEquals(jsonLower, jsonGeo)

        val xmlLower = LinguistLanguageResolver.resolveFileType("xml")
        val xmlUpper = LinguistLanguageResolver.resolveFileType("XML")
        val xmlAtom = LinguistLanguageResolver.resolveFileType("atom")
        Assert.assertNotNull(xmlLower)
        Assert.assertEquals(xmlLower, xmlUpper)
        Assert.assertEquals(xmlLower, xmlAtom)

        val plainText = LinguistLanguageResolver.resolveFileType("Plain Text")
        val fundamental = LinguistLanguageResolver.resolveFileType("fundamental")
        Assert.assertNotNull(plainText)
        Assert.assertEquals(PlainTextFileType.INSTANCE, plainText)
        Assert.assertEquals(PlainTextFileType.INSTANCE, fundamental)
    }

    fun testUnsupportedAndUnknownLanguages() {
        Assert.assertNull(LinguistLanguageResolver.resolveFileType(""))
        Assert.assertNull(LinguistLanguageResolver.resolveFileType("   "))
        Assert.assertNull(LinguistLanguageResolver.resolveFileType("CompletelyNonExistentLanguage12345"))
        Assert.assertNull(LinguistLanguageResolver.resolveFileType("unknown-custom-xyz"))
    }

    fun testCustomMappingsOverride() {
        val customMap = mapOf("customlang" to "XML", "python" to "HTML")
        val customResolved = LinguistLanguageResolver.resolveFileType("customlang", customMap)
        Assert.assertNotNull(customResolved)
        Assert.assertEquals("XML", customResolved?.name)

        val overriddenPython = LinguistLanguageResolver.resolveFileType("python", customMap)
        Assert.assertNotNull(overriddenPython)
        Assert.assertEquals("HTML", overriddenPython?.name)
    }

    @Suppress("KotlinMisorderedAssertEqualsArguments")
    fun testDirectRegisteredFileTypeFallback() {
        val xmlType = FileTypeManager.getInstance().findFileTypeByName("XML")
        Assert.assertNotNull(xmlType)
        val resolved = LinguistLanguageResolver.resolveFileType("XML")
        Assert.assertEquals(xmlType, resolved)
    }
}
