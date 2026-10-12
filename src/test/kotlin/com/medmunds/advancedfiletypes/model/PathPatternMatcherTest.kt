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

    @ParameterizedTest(name = "Windows path separator normalization: 'src\\*.txt' against path=''{0}'', file=''{1}'' -> {2}")
    @CsvSource(
        "src/file.txt, file.txt, true",
        "src\\file.txt, file.txt, true",
        "docs/file.txt, file.txt, false",
        "src/sub/file.txt, file.txt, false"
    )
    fun testWindowsBackslashNormalization(relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile("src\\*.txt", PatternSyntax.GLOB)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "Windows path with subdirectories 'foo\\bar\\baz.json' against path=''{0}'', file=''{1}'' -> {2}")
    @CsvSource(
        "foo/bar/baz.json, baz.json, true",
        "foo\\bar\\baz.json, baz.json, true",
        "other/bar/baz.json, baz.json, false"
    )
    fun testWindowsMultiDirectory(relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile("foo\\bar\\baz.json", PatternSyntax.GLOB)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "Standard glob '/*.txt' anchors to project root directory: path=''{0}'', file=''{1}'' -> {2}")
    @CsvSource(
        "file.txt, file.txt, true",
        "docs/file.txt, file.txt, false",
        "docs/sub/file.txt, file.txt, false"
    )
    fun testStandardGlobLeadingSlashRootAnchored(relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile("/*.txt", PatternSyntax.GLOB)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "Standard glob './*.txt' anchors to project root directory: path=''{0}'', file=''{1}'' -> {2}")
    @CsvSource(
        "file.txt, file.txt, true",
        "docs/file.txt, file.txt, false",
        "docs/sub/file.txt, file.txt, false"
    )
    fun testStandardGlobDotSlashRootAnchored(relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile("./*.txt", PatternSyntax.GLOB)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "Standard glob root anchored with alternatives '/*.(txt|html)': pattern=''{0}'', path=''{1}'', file=''{2}'' -> {3}")
    @CsvSource(
        "'/*.{txt,html}', 'file.txt', 'file.txt', true",
        "'/*.{txt,html}', 'index.html', 'index.html', true",
        "'/*.{txt,html}', 'docs/file.txt', 'file.txt', false",
        "'/*.{txt,html}', 'docs/index.html', 'index.html', false",
        "'{/*.txt,/*.html}', 'file.txt', 'file.txt', true",
        "'{/*.txt,/*.html}', 'index.html', 'index.html', true",
        "'{/*.txt,/*.html}', 'docs/file.txt', 'file.txt', false",
        "'{/*.txt,/*.html}', 'docs/index.html', 'index.html', false"
    )
    fun testStandardGlobRootAnchoredAlternatives(patternText: String, relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile(patternText, PatternSyntax.GLOB)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "Regex path matching: pattern=''{0}'', path=''{1}'', file=''{2}'' -> {3}")
    @CsvSource(
        "'^Dockerfile$', 'Dockerfile', 'Dockerfile', true",
        "'^Dockerfile$', 'sub/Dockerfile', 'Dockerfile', false",
        "'^Dockerfile$', 'sub/other/Dockerfile', 'Dockerfile', false",
        "'(?:.*/)?Dockerfile', 'sub/Dockerfile', 'Dockerfile', true",
        "'^sub/.*', 'sub/Dockerfile', 'Dockerfile', true",
        "'^sub/.*', 'other/Dockerfile', 'Dockerfile', false"
    )
    fun testRegexPathMatching(patternText: String, relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile(patternText, PatternSyntax.REGEX)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "Regex negative lookahead: pattern=''{0}'', path=''{1}'', file=''{2}'' -> {3}")
    @CsvSource(
        "'(?!public/).+\\.html', 'public/index.html', 'index.html', false",
        "'(?!public/).+\\.html', 'src/index.html', 'index.html', true",
        "'(?!public/).+\\.html', 'index.html', 'index.html', true",
        "'^(?!public/).+\\.html$', 'public/index.html', 'index.html', false",
        "'^(?!public/).+\\.html$', 'src/index.html', 'index.html', true",
        "'^(?!public/).+\\.html$', 'index.html', 'index.html', true"
    )
    fun testRegexNegativeLookahead(patternText: String, relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile(patternText, PatternSyntax.REGEX)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "Regex fallback on fileName when relativePath is null: pattern=''{0}'', file=''{1}'' -> {2}")
    @CsvSource(
        "'^Dockerfile$', 'Dockerfile', true",
        "'^Dockerfile$', 'Dockerfile.bak', false",
        "'(?!public/).+\\.html', 'index.html', true"
    )
    fun testRegexNullRelativePathFallback(patternText: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile(patternText, PatternSyntax.REGEX)
        val result = PathPatternMatcher.matches(pattern, null, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "Regex case-insensitivity: pattern=''{0}'', path=''{1}'', file=''{2}'' -> {3}")
    @CsvSource(
        "'docs/.*\\.TXT', 'docs/readme.txt', 'readme.txt', true",
        "'DOCS/.*\\.txt', 'docs/readme.txt', 'readme.txt', true",
        "'docs/.*\\.txt', 'DOCS/README.TXT', 'README.TXT', true",
        "'docs/.*\\.txt', 'docs/readme.md', 'readme.md', false"
    )
    fun testRegexCaseInsensitivity(patternText: String, relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile(patternText, PatternSyntax.REGEX)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "Regex path normalization (Windows separators, leading slash and ./): pattern=''{0}'', path=''{1}'', file=''{2}'' -> {3}")
    @CsvSource(
        "'docs/.*\\.txt', 'docs/file.txt', 'file.txt', true",
        "'docs/.*\\.txt', '/docs/file.txt', 'file.txt', true",
        "'docs/.*\\.txt', './docs/file.txt', 'file.txt', true",
        "'docs/.*\\.txt', 'docs\\file.txt', 'file.txt', true",
        "'docs/.*\\.txt', 'docs\\sub\\file.txt', 'file.txt', true",
        "'docs/sub/.*\\.txt', 'docs\\sub\\file.txt', 'file.txt', true",
        "'docs/.*\\.txt', 'src\\sub\\file.txt', 'file.txt', false"
    )
    fun testRegexPathNormalization(patternText: String, relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile(patternText, PatternSyntax.REGEX)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "Standard glob nested braces 'src/{a/{b,c},d}.txt' against path=''{0}'', file=''{1}'' -> {2}")
    @CsvSource(
        "src/a/b.txt, b.txt, true",
        "src/a/c.txt, c.txt, true",
        "src/d.txt, d.txt, true",
        "src/a/e.txt, e.txt, false",
        "src/x.txt, x.txt, false"
    )
    fun testStandardGlobNestedBraces(relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile("src/{a/{b,c},d}.txt", PatternSyntax.GLOB)
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
        val pattern = PathPatternMatcher.compile("[!a-z].txt", PatternSyntax.GLOB)
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
        val pattern = PathPatternMatcher.compile("[a-z0-9].txt", PatternSyntax.GLOB)
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
        val pattern = PathPatternMatcher.compile("src/{test,spec,config}/*.kt", PatternSyntax.GLOB)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    // Reference tests: https://github.com/microsoft/vscode/blob/main/src/vs/base/test/common/glob.test.ts
    @ParameterizedTest(name = "VS Code glob simple wildcards: pattern=''{0}'', path=''{1}'', file=''{2}'' -> {3}")
    @CsvSource(
        "'*.js', 'foo.js', 'foo.js', true",
        "'*.js', 'bar/foo.js', 'foo.js', true",
        "'*.js', 'foo.txt', 'foo.txt', false",
        "'?bar.js', 'xbar.js', 'xbar.js', true",
        "'?bar.js', 'foo/xbar.js', 'xbar.js', true",
        "'?bar.js', 'bar.js', 'bar.js', false",
        "'?bar.js', 'xxbar.js', 'xxbar.js', false",
        "'foo?bar.js', 'foo1bar.js', 'foo1bar.js', true",
        "'foo?bar.js', 'fooabar.js', 'fooabar.js', true",
        "'foo?bar.js', 'foobar.js', 'foobar.js', false",
        "'foo?bar.js', 'foo12bar.js', 'foo12bar.js', false",
        "'foo.js', 'foo.js', 'foo.js', true",
        "'foo.js', 'sub/foo.js', 'foo.js', true",
        "'foo.js', 'bar.js', 'bar.js', false"
    )
    fun testVsCodeSimpleAndWildcardGlobs(patternText: String, relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile(patternText, PatternSyntax.GLOB)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "VS Code recursive globstars: pattern=''{0}'', path=''{1}'', file=''{2}'' -> {3}")
    @CsvSource(
        "'**/*.js', 'foo.js', 'foo.js', true",
        "'**/*.js', 'bar/foo.js', 'foo.js', true",
        "'**/*.js', 'bar/baz/foo.js', 'foo.js', true",
        "'**/*.js', 'foo.ts', 'foo.ts', false",
        "'foo/**/*.js', 'foo/bar.js', 'bar.js', true",
        "'foo/**/*.js', 'foo/bar/baz.js', 'baz.js', true",
        "'foo/**/*.js', 'bar/foo.js', 'foo.js', false",
        "'**/foo', 'foo', 'foo', true",
        "'**/foo', 'bar/foo', 'foo', true",
        "'**/foo', 'bar/baz/foo', 'foo', true",
        "'**/foo', 'bar/foo/baz', 'baz', false",
        "'a/**/b', 'a/b', 'b', true",
        "'a/**/b', 'a/x/b', 'b', true",
        "'a/**/b', 'a/x/y/b', 'b', true",
        "'a/**/b', 'a/b/c', 'c', false",
        "'a/**/b/*.txt', 'a/b/file.txt', 'file.txt', true",
        "'a/**/b/*.txt', 'a/x/b/file.txt', 'file.txt', true",
        "'a/**/b/*.txt', 'a/x/y/b/file.txt', 'file.txt', true",
        "'a/**/b/*.txt', 'a/file.txt', 'file.txt', false",
        "'**/**', 'file.txt', 'file.txt', true",
        "'**/**', 'a/b/c.txt', 'c.txt', true",
        "'foo/**', 'foo/bar', 'bar', true",
        "'foo/**', 'foo/a/b', 'b', true",
        "'foo/**', 'other/bar', 'bar', false"
    )
    fun testVsCodeGlobstars(patternText: String, relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile(patternText, PatternSyntax.GLOB)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "VS Code dot files: pattern=''{0}'', path=''{1}'', file=''{2}'' -> {3}")
    @CsvSource(
        "'.*', '.gitignore', '.gitignore', true",
        "'.*', 'sub/.gitignore', '.gitignore', true",
        "'.*', 'file.txt', 'file.txt', false",
        "'**/.*', '.git', '.git', true",
        "'**/.*', 'foo/.git', '.git', true",
        "'**/.*', 'foo/bar/.gitignore', '.gitignore', true",
        "'**/.*', 'foo/bar/baz.txt', 'baz.txt', false",
        "'.{foo,bar}', '.foo', '.foo', true",
        "'.{foo,bar}', '.bar', '.bar', true",
        "'.{foo,bar}', '.baz', '.baz', false",
        "'.git/**', '.git/config', 'config', true",
        "'.git/**', '.git/objects/abc', 'abc', true",
        "'.git/**', 'foo/.git/config', 'config', false"
    )
    fun testVsCodeDotFiles(patternText: String, relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile(patternText, PatternSyntax.GLOB)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "VS Code brace expansion: pattern=''{0}'', path=''{1}'', file=''{2}'' -> {3}")
    @CsvSource(
        "'{foo,bar}', 'foo', 'foo', true",
        "'{foo,bar}', 'bar', 'bar', true",
        "'{foo,bar}', 'baz', 'baz', false",
        "'foo.{dir1,dir2}bar', 'foo.dir1bar', 'foo.dir1bar', true",
        "'foo.{dir1,dir2}bar', 'foo.dir2bar', 'foo.dir2bar', true",
        "'foo.{dir1,dir2}bar', 'foo.dir3bar', 'foo.dir3bar', false",
        "'{**/*.html,**/*.txt}', 'index.html', 'index.html', true",
        "'{**/*.html,**/*.txt}', 'public/index.html', 'index.html', true",
        "'{**/*.html,**/*.txt}', 'docs/readme.txt', 'readme.txt', true",
        "'{**/*.html,**/*.txt}', 'readme.md', 'readme.md', false",
        "'src/{test,spec}/**/*.kt', 'src/test/a.kt', 'a.kt', true",
        "'src/{test,spec}/**/*.kt', 'src/spec/sub/b.kt', 'b.kt', true",
        "'src/{test,spec}/**/*.kt', 'src/main/c.kt', 'c.kt', false",
        "'foo/{a,b}/{c,d}.txt', 'foo/a/c.txt', 'c.txt', true",
        "'foo/{a,b}/{c,d}.txt', 'foo/b/d.txt', 'd.txt', true",
        "'foo/{a,b}/{c,d}.txt', 'foo/a/e.txt', 'e.txt', false",
        "'foo/{[0-9],abc}/*.txt', 'foo/1/a.txt', 'a.txt', true",
        "'foo/{[0-9],abc}/*.txt', 'foo/abc/b.txt', 'b.txt', true",
        "'foo/{[0-9],abc}/*.txt', 'foo/xyz/c.txt', 'c.txt', false"
    )
    fun testVsCodeBraceExpansions(patternText: String, relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile(patternText, PatternSyntax.GLOB)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "VS Code character classes: pattern=''{0}'', path=''{1}'', file=''{2}'' -> {3}")
    @CsvSource(
        "'yam[a-z]', 'yama', 'yama', true",
        "'yam[a-z]', 'yamz', 'yamz', true",
        "'yam[a-z]', 'yam1', 'yam1', false",
        "'yam[!a-z]', 'yam1', 'yam1', true",
        "'yam[!a-z]', 'yam-', 'yam-', true",
        "'yam[!a-z]', 'yama', 'yama', false",
        "'yam[^a-z]', 'yam1', 'yam1', true",
        "'yam[^a-z]', 'yam-', 'yam-', true",
        "'yam[^a-z]', 'yama', 'yama', false",
        "'test-[0-9][0-9].txt', 'test-01.txt', 'test-01.txt', true",
        "'test-[0-9][0-9].txt', 'test-99.txt', 'test-99.txt', true",
        "'test-[0-9][0-9].txt', 'test-1a.txt', 'test-1a.txt', false",
        "'[a-zA-Z0-9].txt', 'a.txt', 'a.txt', true",
        "'[a-zA-Z0-9].txt', 'Z.txt', 'Z.txt', true",
        "'[a-zA-Z0-9].txt', '9.txt', '9.txt', true",
        "'[a-zA-Z0-9].txt', '-.txt', '-.txt', false"
    )
    fun testVsCodeCharacterClassesAndRanges(patternText: String, relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile(patternText, PatternSyntax.GLOB)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "VS Code bracket escaping: pattern=''{0}'', path=''{1}'', file=''{2}'' -> {3}")
    @CsvSource(
        "'yam[[]', 'yam[', 'yam[', true",
        "'yam[[]', 'yama', 'yama', false",
        "'yam[[]', 'yam]', 'yam]', false",
        "'yam[]]', 'yam]', 'yam]', true",
        "'yam[]]', 'yama', 'yama', false",
        "'yam[]]', 'yam[', 'yam[', false",
        "'yam[*]', 'yam*', 'yam*', true",
        "'yam[*]', 'yama', 'yama', false",
        "'yam[*]', 'yamfoo', 'yamfoo', false",
        "'yam[?]', 'yam?', 'yam?', true",
        "'yam[?]', 'yama', 'yama', false",
        "'yam[?]', 'yam1', 'yam1', false",
        "'yam[!]', 'yam!', 'yam!', true",
        "'yam[!]', 'yama', 'yama', false",
        "'yam[[][a-z][]]', 'yam[a]', 'yam[a]', true",
        "'yam[[][a-z][]]', 'yam[z]', 'yam[z]', true",
        "'yam[[][a-z][]]', 'yama', 'yama', false"
    )
    fun testVsCodeBracketEscaping(patternText: String, relativePath: String, fileName: String, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile(patternText, PatternSyntax.GLOB)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }

    @ParameterizedTest(name = "Case sensitivity control: pattern=''{0}'', path=''{1}'', file=''{2}'', ignoreCase={3} -> {4}")
    @CsvSource(
        "'docs/*.txt', 'docs/file.txt', 'file.txt', true, true",
        "'docs/*.txt', 'docs/FILE.TXT', 'FILE.TXT', true, true",
        "'docs/*.txt', 'DOCS/file.txt', 'file.txt', true, true",
        "'docs/*.txt', 'docs/file.txt', 'file.txt', false, true",
        "'docs/*.txt', 'docs/FILE.TXT', 'FILE.TXT', false, false",
        "'docs/*.txt', 'DOCS/file.txt', 'file.txt', false, false"
    )
    fun testCaseSensitivityControl(patternText: String, relativePath: String, fileName: String, ignoreCase: Boolean, expectedMatch: Boolean) {
        val pattern = PathPatternMatcher.compile(patternText, PatternSyntax.GLOB, ignoreCase = ignoreCase)
        val result = PathPatternMatcher.matches(pattern, relativePath, fileName)
        assertEquals(expectedMatch, result)
    }
}
