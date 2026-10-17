package com.medmunds.advancedfiletypes

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.marhali.json5.Json5
import org.junit.Assert
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class ExternalLanguageResolverTest : BasePlatformTestCase() {

    fun testJson5FileParsingAndIntegrity() {
        val resourcePath = "/com/medmunds/advancedfiletypes/language-map.json5"
        val stream = ExternalLanguageResolver::class.java.getResourceAsStream(resourcePath)
        Assert.assertNotNull("language-map.json5 must exist", stream)

        val text = InputStreamReader(stream!!, StandardCharsets.UTF_8).use { it.readText() }
        val rootNode = Json5().parse(text)
        Assert.assertNotNull("Root node must not be null", rootNode)
        Assert.assertTrue("Root node must be a JSON5 object", rootNode.isJson5Object)

        val map = rootNode.asJson5Object.asMap()
        Assert.assertTrue("Language map should contain at least 100 target FileTypes", map.size >= 100)

        val aliasMap = ExternalLanguageResolver.getStaticAliasMap()
        Assert.assertTrue("Flattened alias map should contain at least 200 aliases", aliasMap.size >= 200)

        var previousTargetKey = ""
        for ((targetTypeName, aliasesElem) in map) {
            Assert.assertFalse("Target FileType name '$targetTypeName' must not be blank", targetTypeName.isBlank())
            Assert.assertEquals("Target FileType name '$targetTypeName' must be trimmed", targetTypeName.trim(), targetTypeName)
            if (previousTargetKey.isNotEmpty()) {
                Assert.assertTrue(
                    "Target FileType names must be sorted alphabetically: '$targetTypeName' should be after '$previousTargetKey'",
                    targetTypeName.compareTo(previousTargetKey, ignoreCase = true) > 0
                )
            }
            previousTargetKey = targetTypeName

            Assert.assertNotNull("Aliases element for '$targetTypeName' must not be null", aliasesElem)
            Assert.assertTrue("Aliases element for '$targetTypeName' must be an array", aliasesElem.isJson5Array)

            var previousAlias = ""
            for (elem in aliasesElem.asJson5Array) {
                Assert.assertTrue("Alias must be a primitive string", elem.isJson5Primitive && elem.asJson5Primitive.isString)
                val alias = elem.asJson5Primitive.asString
                Assert.assertFalse("Alias '$alias' under '$targetTypeName' must not be blank", alias.isBlank())
                Assert.assertEquals("Alias '$alias' under '$targetTypeName' must be trimmed", alias.trim(), alias)
                Assert.assertEquals("Alias '$alias' under '$targetTypeName' must be stored in lowercase", alias.lowercase(), alias)
                if (previousAlias.isNotEmpty()) {
                    Assert.assertTrue(
                        "Aliases under '$targetTypeName' must be sorted alphabetically: '$alias' should be after '$previousAlias'",
                        alias.compareTo(previousAlias) > 0
                    )
                }
                previousAlias = alias
            }
        }

        // Assert multi-word and symbol keys are loaded correctly
        Assert.assertEquals("JSON5", aliasMap["json with comments"])
        Assert.assertEquals("JSON5", aliasMap["jsonc"])
        Assert.assertEquals("JSON5", aliasMap["json5"])
        Assert.assertEquals("PLAIN_TEXT", aliasMap["plain text"])
        Assert.assertEquals("PLAIN_TEXT", aliasMap["plaintext"])
        Assert.assertEquals("Properties", aliasMap["java properties"])
        Assert.assertEquals("Properties", aliasMap["properties"])
        Assert.assertEquals("Protocol Buffer", aliasMap["protocol buffer"])
        Assert.assertEquals("Protocol Buffer", aliasMap["protocol buffers"])
        Assert.assertEquals("Protocol Buffer", aliasMap["proto"])
        Assert.assertEquals("Jupyter Notebook", aliasMap["jupyter notebook"])
        Assert.assertEquals("Jupyter Notebook", aliasMap["ipython notebook"])
        Assert.assertEquals("GitIgnore file", aliasMap["ignore list"])
        Assert.assertEquals("GitIgnore file", aliasMap["gitignore"])
        Assert.assertEquals("GitIgnore file", aliasMap["ignore"])
        Assert.assertEquals("GitIgnore file", aliasMap["git-ignore"])
        Assert.assertEquals("GitConfig file", aliasMap["git config"])
        Assert.assertEquals("GitConfig file", aliasMap["gitconfig"])
        Assert.assertEquals("Visual Basic", aliasMap["visual basic"])
        Assert.assertEquals("Visual Basic", aliasMap["visual basic .net"])
        Assert.assertEquals("JSONL", aliasMap["json lines"])
        Assert.assertEquals("JSONL", aliasMap["jsonl"])
        Assert.assertEquals("Shell Script", aliasMap["shellscript"])
        Assert.assertEquals("Shell Script", aliasMap["shell"])
        Assert.assertEquals("Shell Script", aliasMap["bash"])
        Assert.assertEquals("C#", aliasMap["c#"])
        Assert.assertEquals("C#", aliasMap["csharp"])
        Assert.assertEquals("C++", aliasMap["c++"])
        Assert.assertEquals("C++", aliasMap["c"])
        Assert.assertEquals("C++", aliasMap["cpp"])
    }

    fun testStaticTargetNameLookup() {
        Assert.assertEquals("JSON5", ExternalLanguageResolver.getStaticTargetName("JSON with Comments"))
        Assert.assertEquals("JSON5", ExternalLanguageResolver.getStaticTargetName("jsonc"))
        Assert.assertEquals("JSON5", ExternalLanguageResolver.getStaticTargetName("json5"))

        Assert.assertEquals("Shell Script", ExternalLanguageResolver.getStaticTargetName("Shell"))
        Assert.assertEquals("Shell Script", ExternalLanguageResolver.getStaticTargetName("shellscript"))
        Assert.assertEquals("Shell Script", ExternalLanguageResolver.getStaticTargetName("bash"))
        Assert.assertEquals("Shell Script", ExternalLanguageResolver.getStaticTargetName("sh"))
        Assert.assertEquals("Shell Script", ExternalLanguageResolver.getStaticTargetName("zsh"))

        Assert.assertEquals("GitIgnore file", ExternalLanguageResolver.getStaticTargetName("Ignore List"))
        Assert.assertEquals("GitIgnore file", ExternalLanguageResolver.getStaticTargetName("gitignore"))
        Assert.assertEquals("GitIgnore file", ExternalLanguageResolver.getStaticTargetName("ignore"))
        Assert.assertEquals("GitIgnore file", ExternalLanguageResolver.getStaticTargetName("git-ignore"))

        Assert.assertEquals("GitConfig file", ExternalLanguageResolver.getStaticTargetName("Git Config"))
        Assert.assertEquals("GitConfig file", ExternalLanguageResolver.getStaticTargetName("gitconfig"))
        Assert.assertEquals("GitConfig file", ExternalLanguageResolver.getStaticTargetName("gitmodules"))

        Assert.assertEquals("ReST", ExternalLanguageResolver.getStaticTargetName("reStructuredText"))
        Assert.assertEquals("ReST", ExternalLanguageResolver.getStaticTargetName("rst"))

        Assert.assertEquals("C++", ExternalLanguageResolver.getStaticTargetName("C++"))
        Assert.assertEquals("C++", ExternalLanguageResolver.getStaticTargetName("c"))
        Assert.assertEquals("C++", ExternalLanguageResolver.getStaticTargetName("cpp"))

        Assert.assertEquals("C#", ExternalLanguageResolver.getStaticTargetName("C#"))
        Assert.assertEquals("C#", ExternalLanguageResolver.getStaticTargetName("csharp"))

        Assert.assertEquals("Jupyter Notebook", ExternalLanguageResolver.getStaticTargetName("Jupyter Notebook"))
        Assert.assertEquals("Jupyter Notebook", ExternalLanguageResolver.getStaticTargetName("ipython notebook"))

        Assert.assertEquals("Protocol Buffer", ExternalLanguageResolver.getStaticTargetName("Protocol Buffer"))
        Assert.assertEquals("Protocol Buffer", ExternalLanguageResolver.getStaticTargetName("proto"))
        Assert.assertEquals("Protocol Buffer", ExternalLanguageResolver.getStaticTargetName("protocol buffers"))

        // Expanded language mappings
        Assert.assertEquals("Astro", ExternalLanguageResolver.getStaticTargetName("astro"))
        Assert.assertEquals("Svelte", ExternalLanguageResolver.getStaticTargetName("svelte"))
        Assert.assertEquals("Zig", ExternalLanguageResolver.getStaticTargetName("zig"))
        Assert.assertEquals("HCL", ExternalLanguageResolver.getStaticTargetName("hcl"))
        Assert.assertEquals("HCL", ExternalLanguageResolver.getStaticTargetName("terraform"))
        Assert.assertEquals("HCL", ExternalLanguageResolver.getStaticTargetName("opentofu"))
        Assert.assertEquals("AsciiDoc", ExternalLanguageResolver.getStaticTargetName("asciidoc"))
        Assert.assertEquals("Prisma", ExternalLanguageResolver.getStaticTargetName("prisma"))
        Assert.assertEquals("Solidity file", ExternalLanguageResolver.getStaticTargetName("solidity"))
        Assert.assertEquals("Elm file", ExternalLanguageResolver.getStaticTargetName("elm"))
        Assert.assertEquals("Nix", ExternalLanguageResolver.getStaticTargetName("nix"))
        Assert.assertEquals("Nix", ExternalLanguageResolver.getStaticTargetName("nixos"))
        Assert.assertEquals("Mermaid", ExternalLanguageResolver.getStaticTargetName("mermaid"))
        Assert.assertEquals("Elixir", ExternalLanguageResolver.getStaticTargetName("elixir"))
        Assert.assertEquals("Erlang", ExternalLanguageResolver.getStaticTargetName("erlang"))
        Assert.assertEquals("Haskell", ExternalLanguageResolver.getStaticTargetName("haskell"))
        Assert.assertEquals("Julia", ExternalLanguageResolver.getStaticTargetName("julia"))
        Assert.assertEquals("Lua", ExternalLanguageResolver.getStaticTargetName("lua"))
        Assert.assertEquals("OCaml", ExternalLanguageResolver.getStaticTargetName("ocaml"))
        Assert.assertEquals("R", ExternalLanguageResolver.getStaticTargetName("r"))
        Assert.assertEquals("Scala", ExternalLanguageResolver.getStaticTargetName("scala"))
        Assert.assertEquals("Dart", ExternalLanguageResolver.getStaticTargetName("dart"))
        Assert.assertEquals("Rust", ExternalLanguageResolver.getStaticTargetName("rust"))
        Assert.assertEquals("Go", ExternalLanguageResolver.getStaticTargetName("go"))
        Assert.assertEquals("PHP", ExternalLanguageResolver.getStaticTargetName("php"))
        Assert.assertEquals("Ruby", ExternalLanguageResolver.getStaticTargetName("ruby"))
        Assert.assertEquals("Swift", ExternalLanguageResolver.getStaticTargetName("swift"))
        Assert.assertEquals("Handlebars/Mustache", ExternalLanguageResolver.getStaticTargetName("handlebars"))
        Assert.assertEquals("Handlebars/Mustache", ExternalLanguageResolver.getStaticTargetName("hbs"))
    }

    fun testExcludedAndFabricatedLanguagesNotMapped() {
        // Git attributes should not map to GitIgnore
        Assert.assertNull(ExternalLanguageResolver.getStaticTargetName("git attributes"))
        Assert.assertNull(ExternalLanguageResolver.getStaticTargetName("gitattributes"))

        // Console/terminal sessions should not map to Shell Script
        Assert.assertNull(ExternalLanguageResolver.getStaticTargetName("shellsession"))
        Assert.assertNull(ExternalLanguageResolver.getStaticTargetName("console"))

        // Fabricated aliases not in official sets
        Assert.assertNull(ExternalLanguageResolver.getStaticTargetName("kt"))
        Assert.assertNull(ExternalLanguageResolver.getStaticTargetName("py"))
        Assert.assertNull(ExternalLanguageResolver.getStaticTargetName("txt"))
        Assert.assertNull(ExternalLanguageResolver.getStaticTargetName("mdown"))
        Assert.assertNull(ExternalLanguageResolver.getStaticTargetName("cmd"))
        Assert.assertNull(ExternalLanguageResolver.getStaticTargetName(".env"))
        Assert.assertNull(ExternalLanguageResolver.getStaticTargetName("docker-compose"))
        Assert.assertNull(ExternalLanguageResolver.getStaticTargetName("plain-text"))
        Assert.assertNull(ExternalLanguageResolver.getStaticTargetName("java-properties"))
        Assert.assertNull(ExternalLanguageResolver.getStaticTargetName("visual-basic-.net"))
        Assert.assertNull(ExternalLanguageResolver.getStaticTargetName("ignore-list"))
        Assert.assertNull(ExternalLanguageResolver.getStaticTargetName("git-config"))
        Assert.assertNull(ExternalLanguageResolver.getStaticTargetName("cs"))
        Assert.assertNull(ExternalLanguageResolver.getStaticTargetName("cplusplus"))
        Assert.assertNull(ExternalLanguageResolver.getStaticTargetName("ipynb"))
        Assert.assertNull(ExternalLanguageResolver.getStaticTargetName("proto3"))
    }

    @Suppress("KotlinMisorderedAssertEqualsArguments")
    fun testStaticPredefinedMappings() {
        val htmlType = ExternalLanguageResolver.resolveFileType("HTML")
        Assert.assertNotNull(htmlType)
        Assert.assertEquals("HTML", htmlType?.name)

        val htmlAlias = ExternalLanguageResolver.resolveFileType("xhtml")
        Assert.assertNotNull(htmlAlias)
        Assert.assertEquals("HTML", htmlAlias?.name)

        val xmlType = ExternalLanguageResolver.resolveFileType("XML")
        Assert.assertNotNull(xmlType)
        Assert.assertEquals("XML", xmlType?.name)

        val xmlAlias = ExternalLanguageResolver.resolveFileType("rss")
        Assert.assertNotNull(xmlAlias)
        Assert.assertEquals("XML", xmlAlias?.name)

        val jsonType = ExternalLanguageResolver.resolveFileType("JSON")
        Assert.assertNotNull(jsonType)
        Assert.assertEquals("JSON", jsonType?.name)

        val jsonGeo = ExternalLanguageResolver.resolveFileType("geojson")
        Assert.assertNotNull(jsonGeo)
        Assert.assertEquals("JSON", jsonGeo?.name)

        val javaType = ExternalLanguageResolver.resolveFileType("Java")
        Assert.assertNotNull(javaType)
        Assert.assertEquals("JAVA", javaType?.name)

        val plainTextType = ExternalLanguageResolver.resolveFileType("Plain Text")
        Assert.assertNotNull(plainTextType)
        Assert.assertEquals(PlainTextFileType.INSTANCE, plainTextType)

        val fundamentalType = ExternalLanguageResolver.resolveFileType("fundamental")
        Assert.assertNotNull(fundamentalType)
        Assert.assertEquals(PlainTextFileType.INSTANCE, fundamentalType)

        val shellFileType = FileTypeManager.getInstance().findFileTypeByName("Shell Script")
        if (shellFileType != null) {
            val shellType = ExternalLanguageResolver.resolveFileType("Shell")
            Assert.assertEquals(shellFileType, shellType)
            val bashType = ExternalLanguageResolver.resolveFileType("bash")
            Assert.assertEquals(shellFileType, bashType)
            val vsCodeShellType = ExternalLanguageResolver.resolveFileType("shellscript")
            Assert.assertEquals(shellFileType, vsCodeShellType)
        }
    }

    fun testCaseInsensitiveAndAliasResolution() {
        val jsonLower = ExternalLanguageResolver.resolveFileType("json")
        val jsonUpper = ExternalLanguageResolver.resolveFileType("JSON")
        val jsonGeo = ExternalLanguageResolver.resolveFileType("geojson")

        Assert.assertNotNull(jsonLower)
        Assert.assertEquals(jsonLower, jsonUpper)
        Assert.assertEquals(jsonLower, jsonGeo)

        val xmlLower = ExternalLanguageResolver.resolveFileType("xml")
        val xmlUpper = ExternalLanguageResolver.resolveFileType("XML")
        val xmlRss = ExternalLanguageResolver.resolveFileType("rss")
        Assert.assertNotNull(xmlLower)
        Assert.assertEquals(xmlLower, xmlUpper)
        Assert.assertEquals(xmlLower, xmlRss)

        val plainText = ExternalLanguageResolver.resolveFileType("Plain Text")
        val fundamental = ExternalLanguageResolver.resolveFileType("fundamental")
        Assert.assertNotNull(plainText)
        Assert.assertEquals(PlainTextFileType.INSTANCE, plainText)
        Assert.assertEquals(PlainTextFileType.INSTANCE, fundamental)
    }

    fun testUnsupportedAndUnknownLanguages() {
        Assert.assertNull(ExternalLanguageResolver.resolveFileType(""))
        Assert.assertNull(ExternalLanguageResolver.resolveFileType("   "))
        Assert.assertNull(ExternalLanguageResolver.resolveFileType("CompletelyNonExistentLanguage12345"))
        Assert.assertNull(ExternalLanguageResolver.resolveFileType("unknown-custom-xyz"))

        Assert.assertEquals(ExternalLanguageResolver.Resolution.Unmapped, ExternalLanguageResolver.resolve(""))
        Assert.assertEquals(ExternalLanguageResolver.Resolution.Unmapped, ExternalLanguageResolver.resolve("CompletelyNonExistentLanguage12345"))

        // Static mapping for a language whose plugin is not installed should return Unsupported
        val unsupportedChecks = listOf(
            "rust" to "Rust",
            "zig" to "Zig",
            "svelte" to "Svelte",
            "astro" to "Astro",
            "elixir" to "Elixir",
            "terraform" to "HCL",
            "solidity" to "Solidity file"
        )
        for ((externalId, targetFileType) in unsupportedChecks) {
            val res = ExternalLanguageResolver.resolve(externalId)
            val installed = FileTypeManager.getInstance().findFileTypeByName(targetFileType)
            if (installed == null) {
                Assert.assertEquals(ExternalLanguageResolver.Resolution.Unsupported(targetFileType), res)
            } else {
                Assert.assertEquals(ExternalLanguageResolver.Resolution.Resolved(installed), res)
            }
        }
    }

    @Suppress("KotlinMisorderedAssertEqualsArguments")
    fun testFallbackCaching() {
        ExternalLanguageResolver.clearFallbackCache()

        // First call populates cache
        val unmappedFirst = ExternalLanguageResolver.resolve("nonexistent-lang-for-cache")
        Assert.assertEquals(ExternalLanguageResolver.Resolution.Unmapped, unmappedFirst)

        // Second call should return cached unmapped resolution
        val unmappedSecond = ExternalLanguageResolver.resolve("nonexistent-lang-for-cache")
        Assert.assertEquals(ExternalLanguageResolver.Resolution.Unmapped, unmappedSecond)

        // Test whitespace tolerance at entry point
        val xmlType = FileTypeManager.getInstance().findFileTypeByName("XML")
        Assert.assertNotNull(xmlType)
        Assert.assertEquals(xmlType, ExternalLanguageResolver.resolveFileType("  XML  "))
        Assert.assertEquals(xmlType, ExternalLanguageResolver.resolveFileType("  xml  "))
    }

    fun testCustomMappingsOverride() {
        val customMap = mapOf("customlang" to "XML", "python" to "HTML", "unsupported" to "NonExistentFileType")
        val customResolved = ExternalLanguageResolver.resolveFileType("customlang", customMap)
        Assert.assertNotNull(customResolved)
        Assert.assertEquals("XML", customResolved?.name)

        val overriddenPython = ExternalLanguageResolver.resolveFileType("python", customMap)
        Assert.assertNotNull(overriddenPython)
        Assert.assertEquals("HTML", overriddenPython?.name)

        val unsupportedCustom = ExternalLanguageResolver.resolve("unsupported", customMap)
        Assert.assertEquals(ExternalLanguageResolver.Resolution.Unsupported("NonExistentFileType"), unsupportedCustom)
    }

    @Suppress("KotlinMisorderedAssertEqualsArguments")
    fun testDirectRegisteredFileTypeFallback() {
        val xmlType = FileTypeManager.getInstance().findFileTypeByName("XML")
        Assert.assertNotNull(xmlType)
        val resolved = ExternalLanguageResolver.resolveFileType("XML")
        Assert.assertEquals(xmlType, resolved)
    }
}
