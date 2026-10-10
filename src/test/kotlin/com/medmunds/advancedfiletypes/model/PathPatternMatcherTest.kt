package com.medmunds.advancedfiletypes.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class PathPatternMatcherTest {

    @ParameterizedTest(name = "Glob '*.txt' against path=''{0}'', file=''{1}'' -> {2}")
    @CsvSource(
        "file.txt, file.txt, true",
        "docs/file.txt, file.txt, true",
        "docs/sub/file.txt, file.txt, true",
        "file.md, file.md, false",
        "docs/file.txt.bak, file.txt.bak, false"
    )
    fun testExtensionOnlyGlob(relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile("*.txt", PatternSyntax.GLOB)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "Glob 'docs/*.txt' against path=''{0}'', file=''{1}'' -> {2}")
    @CsvSource(
        "docs/readme.txt, readme.txt, true",
        "/docs/readme.txt, readme.txt, true",
        "./docs/readme.txt, readme.txt, true",
        "docs\\readme.txt, readme.txt, true",
        "src/readme.txt, readme.txt, false",
        "docs/sub/readme.txt, readme.txt, false"
    )
    fun testSingleDirectoryGlob(relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile("docs/*.txt", PatternSyntax.GLOB)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "Glob 'docs/**/*.txt' against path=''{0}'', file=''{1}'' -> {2}")
    @CsvSource(
        "docs/readme.txt, readme.txt, true",
        "docs/sub/readme.txt, readme.txt, true",
        "docs/a/b/c/readme.txt, readme.txt, true",
        "src/docs/readme.txt, readme.txt, false",
        "docs/readme.md, readme.md, false"
    )
    fun testRecursiveDirectoryGlob(relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile("docs/**/*.txt", PatternSyntax.GLOB)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "Glob '**/docs/*.txt' against path=''{0}'', file=''{1}'' -> {2}")
    @CsvSource(
        "docs/readme.txt, readme.txt, true",
        "sub/docs/readme.txt, readme.txt, true",
        "a/b/docs/readme.txt, readme.txt, true",
        "docs/sub/readme.txt, readme.txt, false"
    )
    fun testAnywhereDirectoryGlob(relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile("**/docs/*.txt", PatternSyntax.GLOB)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "Glob 'docs/*.{txt,md}' against path=''{0}'', file=''{1}'' -> {2}")
    @CsvSource(
        "docs/readme.txt, readme.txt, true",
        "docs/readme.md, readme.md, true",
        "docs/readme.html, readme.html, false"
    )
    fun testAlternationGlob(relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile("docs/*.{txt,md}", PatternSyntax.GLOB)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "Glob 'src/test-[0-9]?.kt' against path=''{0}'', file=''{1}'' -> {2}")
    @CsvSource(
        "src/test-1a.kt, test-1a.kt, true",
        "src/test-9z.kt, test-9z.kt, true",
        "src/test-12.kt, test-12.kt, true",
        "src/test-a1.kt, test-a1.kt, false",
        "src/test-1.kt, test-1.kt, false"
    )
    fun testCharacterClassAndSingleWildcardGlob(relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile("src/test-[0-9]?.kt", PatternSyntax.GLOB)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "Regex 'docs/.*\\.txt' against path=''{0}'', file=''{1}'' -> {2}")
    @CsvSource(
        "docs/readme.txt, readme.txt, true",
        "docs/sub/readme.txt, readme.txt, true",
        "src/readme.txt, readme.txt, false"
    )
    fun testRegexSyntax(relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile("docs/.*\\.txt", PatternSyntax.REGEX)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "Glob '*.txt' fallback on fileName when relativePath is null for '{0}'")
    @ValueSource(strings = ["file.txt", "README.TXT", "notes.txt"])
    fun testNullRelativePathFallbackMatches(fileName: String) {
        val pattern = PathPatternMatcher.compile("*.txt", PatternSyntax.GLOB)
        assertTrue(PathPatternMatcher.matches(pattern, null, fileName))
    }
}
