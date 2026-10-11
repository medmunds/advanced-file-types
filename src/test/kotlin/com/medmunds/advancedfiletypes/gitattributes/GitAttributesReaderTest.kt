package com.medmunds.advancedfiletypes.gitattributes

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class GitAttributesReaderTest {

    @BeforeEach
    fun setUp() {
        GitAttributesReader.clearCache()
    }

    @Test
    fun `test parse empty and blank content`() {
        assertTrue(GitAttributesReader.parseRules("").isEmpty())
        assertTrue(GitAttributesReader.parseRules("   \n\t\n  ").isEmpty())
    }

    @Test
    fun `test comments and non-linguist lines ignored`() {
        val content = """
            # This is a comment
               # Indented comment
            *.png binary
            *.txt text eol=lf
            *.md diff=markdown
        """.trimIndent()

        val rules = GitAttributesReader.parseRules(content)
        assertTrue(rules.isEmpty())
    }

    @Test
    fun `test attr macro lines ignored`() {
        val content = """
            [attr]md linguist-language=Markdown
            [attr]custom -diff -merge linguist-language=JSON
        """.trimIndent()

        val rules = GitAttributesReader.parseRules(content)
        assertTrue(rules.isEmpty())
    }

    @Test
    fun `test standard linguist-language assignments`() {
        val content = """
            *.txt linguist-language=Markdown
            docs/*.dat linguist-language=JSON
            src/**/*.ts linguist-language=TypeScript
        """.trimIndent()

        val rules = GitAttributesReader.parseRules(content)
        assertEquals(3, rules.size)

        assertEquals(GitAttributesRule("*.txt", "Markdown", isUnset = false), rules[0])
        assertEquals(GitAttributesRule("docs/*.dat", "JSON", isUnset = false), rules[1])
        assertEquals(GitAttributesRule("src/**/*.ts", "TypeScript", isUnset = false), rules[2])
    }

    @Test
    fun `test quoted language values`() {
        val content = """
            *.dat linguist-language="C++"
            *.doc linguist-language='Jupyter Notebook'
            *.erb linguist-language="HTML+ERB"
            *.custom linguist-language="Language \"With\" Quotes"
        """.trimIndent()

        val rules = GitAttributesReader.parseRules(content)
        assertEquals(4, rules.size)

        assertEquals(GitAttributesRule("*.dat", "C++", isUnset = false), rules[0])
        assertEquals(GitAttributesRule("*.doc", "Jupyter Notebook", isUnset = false), rules[1])
        assertEquals(GitAttributesRule("*.erb", "HTML+ERB", isUnset = false), rules[2])
        assertEquals(GitAttributesRule("*.custom", "Language \"With\" Quotes", isUnset = false), rules[3])
    }

    @Test
    fun `test attribute negation and unsetting`() {
        val content = """
            docs/raw.txt -linguist-language
            docs/reset.txt !linguist-language
            docs/empty.txt linguist-language=""
        """.trimIndent()

        val rules = GitAttributesReader.parseRules(content)
        assertEquals(3, rules.size)

        assertEquals(GitAttributesRule("docs/raw.txt", null, isUnset = true), rules[0])
        assertEquals(GitAttributesRule("docs/reset.txt", null, isUnset = true), rules[1])
        assertEquals(GitAttributesRule("docs/empty.txt", null, isUnset = true), rules[2])
    }

    @Test
    fun `test quoted and unquoted path patterns with escapes`() {
        val content = """
            "path with spaces/*.txt" linguist-language=JSON
            "path/with/\"quotes\"/*.txt" linguist-language=JSON
            "caf\303\251/*" linguist-language=Python
            "📁/*.txt" linguist-language=JSON
            "🎉/notes.txt" linguist-language=Markdown
            " leading and trailing " linguist-language=Ruby
            "\a\b\t\n\v\f\r\\\"" linguist-language=Kotlin
            \!escaped_exclamation.txt linguist-language=Markdown
            foo\*.txt linguist-language=Markdown
            path\ with\ spaces/*.md linguist-language=Markdown
        """.trimIndent()

        val rules = GitAttributesReader.parseRules(content)
        // In Git, unquoted patterns take raw text up to first whitespace (no backslash escape processing).
        // 'path\ with\ spaces/*.md linguist-language=Markdown' has unquoted pattern 'path\'
        // followed by subsequent attribute tokens on that line.
        assertEquals(10, rules.size)

        assertEquals(GitAttributesRule("path with spaces/*.txt", "JSON", isUnset = false), rules[0])
        assertEquals(GitAttributesRule("path/with/\"quotes\"/*.txt", "JSON", isUnset = false), rules[1])
        assertEquals(GitAttributesRule("café/*", "Python", isUnset = false), rules[2])
        assertEquals(GitAttributesRule("📁/*.txt", "JSON", isUnset = false), rules[3])
        assertEquals(GitAttributesRule("🎉/notes.txt", "Markdown", isUnset = false), rules[4])
        assertEquals(GitAttributesRule(" leading and trailing ", "Ruby", isUnset = false), rules[5])
        assertEquals(GitAttributesRule("\u0007\b\t\n\u000B\u000C\r\\\"", "Kotlin", isUnset = false), rules[6])
        assertEquals(GitAttributesRule("\\!escaped_exclamation.txt", "Markdown", isUnset = false), rules[7])
        assertEquals(GitAttributesRule("foo\\*.txt", "Markdown", isUnset = false), rules[8])
        assertEquals(GitAttributesRule("path\\", "Markdown", isUnset = false), rules[9])

        assertTrue(rules[5].matches(" leading and trailing ", " leading and trailing "))
        assertTrue(rules[5].matches("docs/ leading and trailing ", " leading and trailing "))
        assertFalse(rules[5].matches("leading and trailing", "leading and trailing"))
    }

    @Test
    fun `test octal escapes in quoted patterns`() {
        val content = """
            "caf\303\251/*" linguist-language=Python
            "raw\377byte" linguist-language=Binary
            "short\30" linguist-language=Invalid
            "out_of_range\400" linguist-language=Invalid
            "out_of_range\777" linguist-language=Invalid
            "single_digit\4" linguist-language=Invalid
            "invalid_digit\389" linguist-language=Invalid
        """.trimIndent()

        val rules = GitAttributesReader.parseRules(content)
        assertEquals(2, rules.size)
        assertEquals(GitAttributesRule("café/*", "Python", isUnset = false), rules[0])
        // \377 is raw byte 0xFF, which decodes to unicode replacement character \uFFFD in UTF-8
        assertEquals(GitAttributesRule("raw\uFFFDbyte", "Binary", isUnset = false), rules[1])
    }

    @Test
    fun `test raw attribute tokenization with quotes apostrophes and backslashes`() {
        val content = """
            *.rpy linguist-language=Ren'Py
            *.rpy2 linguist-language=Ren'Py -diff
            *.x diff=it's linguist-language=JSON
            *.y foo=a\ linguist-language=JSON
            *.z linguist-language="C++" -diff
        """.trimIndent()

        val rules = GitAttributesReader.parseRules(content)
        assertEquals(5, rules.size)

        assertEquals(GitAttributesRule("*.rpy", "Ren'Py", isUnset = false), rules[0])
        assertEquals(GitAttributesRule("*.rpy2", "Ren'Py", isUnset = false), rules[1])
        assertEquals(GitAttributesRule("*.x", "JSON", isUnset = false), rules[2])
        assertEquals(GitAttributesRule("*.y", "JSON", isUnset = false), rules[3])
        assertEquals(GitAttributesRule("*.z", "C++", isUnset = false), rules[4])
    }

    @Test
    fun `test bare and prefixed unset attribute variants`() {
        val content = """
            *.a linguist-language
            *.b linguist-language=JSON linguist-language
            *.c linguist-language=JSON -linguist-language=foo
            *.d linguist-language=JSON !linguist-language=foo
        """.trimIndent()

        val rules = GitAttributesReader.parseRules(content)
        assertEquals(4, rules.size)

        assertEquals(GitAttributesRule("*.a", null, isUnset = true), rules[0])
        assertEquals(GitAttributesRule("*.b", null, isUnset = true), rules[1])
        assertEquals(GitAttributesRule("*.c", null, isUnset = true), rules[2])
        assertEquals(GitAttributesRule("*.d", null, isUnset = true), rules[3])
    }

    @Test
    fun `test multiple attributes on single line`() {
        val content = """
            *.data text diff=json linguist-language=JSON eol=lf
            *.override linguist-language=Python -linguist-language
            *.restore -linguist-language linguist-language=Kotlin
            *.both -linguist-language !linguist-language
        """.trimIndent()

        val rules = GitAttributesReader.parseRules(content)
        assertEquals(4, rules.size)

        assertEquals(GitAttributesRule("*.data", "JSON", isUnset = false), rules[0])
        assertEquals(GitAttributesRule("*.override", null, isUnset = true), rules[1])
        assertEquals(GitAttributesRule("*.restore", "Kotlin", isUnset = false), rules[2])
        assertEquals(GitAttributesRule("*.both", null, isUnset = true), rules[3])
    }

    @Test
    fun `test invalid or negative patterns are ignored`() {
        val content = """
            !invalid.txt linguist-language=Markdown
            "" linguist-language=JSON
            "unclosed_quote.txt linguist-language=JSON
            "invalid\escape.txt" linguist-language=JSON
            *.only_pattern
        """.trimIndent()

        val rules = GitAttributesReader.parseRules(content)
        assertTrue(rules.isEmpty())
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "*.txt linguist-language=Markdown",
            "  *.txt   linguist-language=Markdown  ",
            "\t*.txt\t\tlinguist-language=Markdown\t",
            "*.txt\tlinguist-language=\"Markdown\"",
            "*.txt linguist-language='Markdown'"
        ]
    )
    fun `test whitespace variations`(line: String) {
        val rule = GitAttributesReader.parseLine(line)
        assertNotNull(rule)
        assertEquals("*.txt", rule?.pattern)
        assertEquals("Markdown", rule?.language)
        assertFalse(rule?.isUnset == true)
    }

    @Test
    fun `test attribute name is case sensitive`() {
        val content = """
            *.a LINGUIST-LANGUAGE=Ruby
            *.b -Linguist-Language
            *.c !LINGUIST-LANGUAGE
            *.d linguist-LANGUAGE=Python
        """.trimIndent()

        val rules = GitAttributesReader.parseRules(content)
        assertTrue(rules.isEmpty(), "Attribute names in Git must match case-sensitively")
    }

    @Test
    fun `test malformed lines handling`() {
        val content = """
            "unclosed pattern linguist-language=JSON
            *.empty linguist-language=
            *.unclosed_attr linguist-language="C++
            !negative.txt linguist-language=JSON
        """.trimIndent()

        val rules = GitAttributesReader.parseRules(content)
        assertEquals(2, rules.size)

        // *.empty linguist-language= should unset
        assertEquals(GitAttributesRule("*.empty", null, isUnset = true), rules[0])
        // *.unclosed_attr linguist-language="C++ should gracefully extract C++
        assertEquals(GitAttributesRule("*.unclosed_attr", "C++", isUnset = false), rules[1])
    }
}
