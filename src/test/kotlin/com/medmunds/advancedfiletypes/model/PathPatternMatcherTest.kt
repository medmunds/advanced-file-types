package com.medmunds.advancedfiletypes.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PathPatternMatcherTest {

    @Test
    fun testExtensionOnlyGlob() {
        val pattern = PathPatternMatcher.compile("*.txt", PatternSyntax.GLOB)
        assertTrue(PathPatternMatcher.matches(pattern, "file.txt", "file.txt"))
        assertTrue(PathPatternMatcher.matches(pattern, "docs/file.txt", "file.txt"))
        assertTrue(PathPatternMatcher.matches(pattern, "docs/sub/file.txt", "file.txt"))
        assertFalse(PathPatternMatcher.matches(pattern, "file.md", "file.md"))
        assertFalse(PathPatternMatcher.matches(pattern, "docs/file.txt.bak", "file.txt.bak"))
    }

    @Test
    fun testSingleDirectoryGlob() {
        val pattern = PathPatternMatcher.compile("docs/*.txt", PatternSyntax.GLOB)
        assertTrue(PathPatternMatcher.matches(pattern, "docs/readme.txt", "readme.txt"))
        assertTrue(PathPatternMatcher.matches(pattern, "/docs/readme.txt", "readme.txt"))
        assertTrue(PathPatternMatcher.matches(pattern, "./docs/readme.txt", "readme.txt"))
        assertFalse(PathPatternMatcher.matches(pattern, "src/readme.txt", "readme.txt"))
        assertFalse(PathPatternMatcher.matches(pattern, "docs/sub/readme.txt", "readme.txt"))
    }

    @Test
    fun testRecursiveDirectoryGlob() {
        val pattern = PathPatternMatcher.compile("docs/**/*.txt", PatternSyntax.GLOB)
        assertTrue(PathPatternMatcher.matches(pattern, "docs/readme.txt", "readme.txt"))
        assertTrue(PathPatternMatcher.matches(pattern, "docs/sub/readme.txt", "readme.txt"))
        assertTrue(PathPatternMatcher.matches(pattern, "docs/a/b/c/readme.txt", "readme.txt"))
        assertFalse(PathPatternMatcher.matches(pattern, "src/docs/readme.txt", "readme.txt"))
        assertFalse(PathPatternMatcher.matches(pattern, "docs/readme.md", "readme.md"))
    }

    @Test
    fun testAnywhereDirectoryGlob() {
        val pattern = PathPatternMatcher.compile("**/docs/*.txt", PatternSyntax.GLOB)
        assertTrue(PathPatternMatcher.matches(pattern, "docs/readme.txt", "readme.txt"))
        assertTrue(PathPatternMatcher.matches(pattern, "sub/docs/readme.txt", "readme.txt"))
        assertTrue(PathPatternMatcher.matches(pattern, "a/b/docs/readme.txt", "readme.txt"))
        assertFalse(PathPatternMatcher.matches(pattern, "docs/sub/readme.txt", "readme.txt"))
    }

    @Test
    fun testAlternationGlob() {
        val pattern = PathPatternMatcher.compile("docs/*.{txt,md}", PatternSyntax.GLOB)
        assertTrue(PathPatternMatcher.matches(pattern, "docs/readme.txt", "readme.txt"))
        assertTrue(PathPatternMatcher.matches(pattern, "docs/readme.md", "readme.md"))
        assertFalse(PathPatternMatcher.matches(pattern, "docs/readme.html", "readme.html"))
    }

    @Test
    fun testRegexSyntax() {
        val pattern = PathPatternMatcher.compile("docs/.*\\.txt", PatternSyntax.REGEX)
        assertTrue(PathPatternMatcher.matches(pattern, "docs/readme.txt", "readme.txt"))
        assertTrue(PathPatternMatcher.matches(pattern, "docs/sub/readme.txt", "readme.txt"))
        assertFalse(PathPatternMatcher.matches(pattern, "src/readme.txt", "readme.txt"))
    }
}
