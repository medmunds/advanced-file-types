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

    @ParameterizedTest(name = "Windows path separator normalization in Standard dialect: 'src\\*.txt' against path=''{0}'', file=''{1}'' -> {2}")
    @CsvSource(
        "src/file.txt, file.txt, true",
        "src\\file.txt, file.txt, true",
        "docs/file.txt, file.txt, false",
        "src/sub/file.txt, file.txt, false"
    )
    fun testWindowsBackslashNormalizationStandardDialect(relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile("src\\*.txt", PatternSyntax.GLOB, GlobDialect.STANDARD)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "Windows path with subdirectories 'foo\\bar\\baz.json' against path=''{0}'', file=''{1}'' -> {2}")
    @CsvSource(
        "foo/bar/baz.json, baz.json, true",
        "foo\\bar\\baz.json, baz.json, true",
        "other/bar/baz.json, baz.json, false"
    )
    fun testWindowsMultiDirectoryStandardDialect(relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile("foo\\bar\\baz.json", PatternSyntax.GLOB, GlobDialect.STANDARD)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "Git dialect escaped asterisk 'foo\\*.txt' against path=''{0}'', file=''{1}'' -> {2}")
    @CsvSource(
        "foo*.txt, foo*.txt, true",
        "dir/foo*.txt, foo*.txt, true",
        "foobar.txt, foobar.txt, false",
        "foo123.txt, foo123.txt, false"
    )
    fun testGitDialectEscapedAsterisk(relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile("foo\\*.txt", PatternSyntax.GLOB, GlobDialect.GIT_ATTRIBUTES)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "Git dialect literal braces '*.{js,ts}' against path=''{0}'', file=''{1}'' -> {2}")
    @CsvSource(
        "'file.{js,ts}', 'file.{js,ts}', true",
        "'dir/file.{js,ts}', 'file.{js,ts}', true",
        "'file.js', 'file.js', false",
        "'file.ts', 'file.ts', false"
    )
    fun testGitDialectLiteralBraces(relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile("*.{js,ts}", PatternSyntax.GLOB, GlobDialect.GIT_ATTRIBUTES)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "Git dialect embedded '**' acts as single '*' in 'foo**bar' against path=''{0}'', file=''{1}'' -> {2}")
    @CsvSource(
        "foobar, foobar, true",
        "fooxxxbar, fooxxxbar, true",
        "dir/fooxxxbar, fooxxxbar, true",
        "foo/sub/bar, bar, false"
    )
    fun testGitDialectEmbeddedDoubleAsterisk(relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile("foo**bar", PatternSyntax.GLOB, GlobDialect.GIT_ATTRIBUTES)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "Git dialect slash-bounded '**' crosses directories 'foo/**/bar' against path=''{0}'', file=''{1}'' -> {2}")
    @CsvSource(
        "foo/bar, bar, true",
        "foo/sub/bar, bar, true",
        "foo/a/b/c/bar, bar, true",
        "other/sub/bar, bar, false"
    )
    fun testGitDialectSlashBoundedDoubleAsterisk(relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile("foo/**/bar", PatternSyntax.GLOB, GlobDialect.GIT_ATTRIBUTES)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "Git dialect root anchoring 'docs/*.txt' against path=''{0}'', file=''{1}'' -> {2}")
    @CsvSource(
        "docs/file.txt, file.txt, true",
        "sub/docs/file.txt, file.txt, false"
    )
    fun testGitDialectRootAnchoring(relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile("docs/*.txt", PatternSyntax.GLOB, GlobDialect.GIT_ATTRIBUTES)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "Glob 'foo\\[bar\\].txt' against path=''{0}'', file=''{1}'' -> {2}")
    @CsvSource(
        "foo[bar].txt, foo[bar].txt, true",
        "dir/foo[bar].txt, foo[bar].txt, true",
        "foob.txt, foob.txt, false",
        "fooa.txt, fooa.txt, false"
    )
    fun testEscapedBracketsGlob(relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile("foo\\[bar\\].txt", PatternSyntax.GLOB, GlobDialect.GIT_ATTRIBUTES)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "Glob 'foo\\ bar.txt' against path=''{0}'', file=''{1}'' -> {2}")
    @CsvSource(
        "foo bar.txt, foo bar.txt, true",
        "dir/foo bar.txt, foo bar.txt, true",
        "foobar.txt, foobar.txt, false"
    )
    fun testEscapedSpaceGlob(relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile("foo\\ bar.txt", PatternSyntax.GLOB, GlobDialect.GIT_ATTRIBUTES)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "Standard glob '/*.txt' normalizes leading slash and matches anywhere: path=''{0}'', file=''{1}'' -> {2}")
    @CsvSource(
        "file.txt, file.txt, true",
        "docs/file.txt, file.txt, true",
        "docs/sub/file.txt, file.txt, true"
    )
    fun testStandardGlobLeadingSlashNormalization(relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile("/*.txt", PatternSyntax.GLOB, GlobDialect.STANDARD)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "Git wildmatch '/*.txt' is root-anchored: path=''{0}'', file=''{1}'' -> {2}")
    @CsvSource(
        "file.txt, file.txt, true",
        "docs/file.txt, file.txt, false",
        "docs/sub/file.txt, file.txt, false"
    )
    fun testGitDialectRootAnchoredGlob(relativePath: String, fileName: String, expectedMatch: Boolean) {
        val rule = com.medmunds.advancedfiletypes.gitattributes.GitAttributesRule("/*.txt", "Markdown")
        val result = rule.matches(relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "Regex filename fallback: pattern=''{0}'', path=''{1}'', file=''{2}'' -> {3}")
    @CsvSource(
        "'^Dockerfile$', 'sub/Dockerfile', 'Dockerfile', true",
        "'^Dockerfile$', 'sub/other/Dockerfile', 'Dockerfile', true",
        "'^Dockerfile$', 'sub/Dockerfile.bak', 'Dockerfile.bak', false"
    )
    fun testRegexFilenameFallback(patternText: String, relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile(patternText, PatternSyntax.REGEX)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "Git dialect spaces preserved in pattern: path=''{0}'', file=''{1}'' -> {2}")
    @CsvSource(
        "' leading and trailing ', ' leading and trailing ', true",
        "'dir/ leading and trailing ', ' leading and trailing ', true",
        "'leading and trailing', 'leading and trailing', false"
    )
    fun testGitDialectSpacesPreserved(relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile(" leading and trailing ", PatternSyntax.GLOB, GlobDialect.GIT_ATTRIBUTES)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    // Tests for glob bugs fixed during .gitattributes refactoring

    @ParameterizedTest(name = "Standard glob nested braces 'src/{a/{b,c},d}.txt' against path=''{0}'', file=''{1}'' -> {2}")
    @CsvSource(
        "src/a/b.txt, b.txt, true",
        "src/a/c.txt, c.txt, true",
        "src/d.txt, d.txt, true",
        "src/a/e.txt, e.txt, false",
        "src/x.txt, x.txt, false"
    )
    fun testStandardGlobNestedBraces(relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile("src/{a/{b,c},d}.txt", PatternSyntax.GLOB, GlobDialect.STANDARD)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "Standard glob character class with negation '[!a-z].txt' against path=''{0}'', file=''{1}'' -> {2}")
    @CsvSource(
        "1.txt, 1.txt, true",
        "!.txt, !.txt, true",
        "a.txt, a.txt, false",
        "z.txt, z.txt, false",
        "A.txt, A.txt, false"
    )
    fun testStandardGlobNegatedCharacterClass(relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile("[!a-z].txt", PatternSyntax.GLOB, GlobDialect.STANDARD)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "Standard glob character class with range '[a-z0-9].txt' against path=''{0}'', file=''{1}'' -> {2}")
    @CsvSource(
        "a.txt, a.txt, true",
        "z.txt, z.txt, true",
        "0.txt, 0.txt, true",
        "9.txt, 9.txt, true",
        "A.txt, A.txt, true"
    )
    fun testStandardGlobCharacterClassRange(relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile("[a-z0-9].txt", PatternSyntax.GLOB, GlobDialect.STANDARD)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "Standard glob alternation with commas 'src/{test,spec,config}/*.kt' against path=''{0}'', file=''{1}'' -> {2}")
    @CsvSource(
        "src/test/Main.kt, Main.kt, true",
        "src/spec/Main.kt, Main.kt, true",
        "src/config/Main.kt, Main.kt, true",
        "src/lib/Main.kt, Main.kt, false"
    )
    fun testStandardGlobAlternationWithMultipleChoices(relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile("src/{test,spec,config}/*.kt", PatternSyntax.GLOB, GlobDialect.STANDARD)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }
}
