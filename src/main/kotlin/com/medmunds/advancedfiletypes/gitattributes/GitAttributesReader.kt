package com.medmunds.advancedfiletypes.gitattributes

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.medmunds.advancedfiletypes.model.GlobDialect
import com.medmunds.advancedfiletypes.model.PathPatternMatcher
import com.medmunds.advancedfiletypes.model.PatternSyntax
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * Represents a parsed rule from a `.gitattributes` file for the `linguist-language` attribute.
 *
 * @property pattern The file glob/path pattern.
 * @property language The assigned Linguist language name, or null if unset/cleared.
 * @property isUnset True if the attribute was explicitly unset (`-linguist-language`) or unspecified (`!linguist-language`).
 */
data class GitAttributesRule(
    val pattern: String,
    val language: String? = null,
    val isUnset: Boolean = false
) {
    val compiledPattern: Pattern? = try {
        PathPatternMatcher.compile(pattern, PatternSyntax.GLOB, GlobDialect.GIT_ATTRIBUTES)
    } catch (_: Exception) {
        null
    }

    /**
     * Checks if this rule matches the given relative path and/or filename.
     */
    fun matches(relativePath: String?, fileName: String): Boolean {
        val pat = compiledPattern ?: return false
        if (relativePath != null) {
            val normalizedRelative = relativePath.replace('\\', '/')
                .removePrefix("./")
                .removePrefix("/")
            return pat.matcher(normalizedRelative).matches()
        }
        return pat.matcher(fileName).matches()
    }
}

/**
 * Reads and parses `.gitattributes` files, extracting `linguist-language` attributes with
 * caching based on the virtual file's modification stamp.
 *
 * Attribute parsing supports GitHub Linguist overrides including quoted language names
 * (e.g. `linguist-language="C++"`, `linguist-language='Jupyter Notebook'`), which is a plugin
 * compatibility convenience extension.
 */
object GitAttributesReader {

    private const val LINGUIST_LANGUAGE_ATTR = "linguist-language"

    private data class CachedRules(
        val modificationStamp: Long,
        val rules: List<GitAttributesRule>
    )

    private val cache = ConcurrentHashMap<String, CachedRules>()

    /**
     * Clears the cache for a given file path or all cached gitattributes.
     */
    fun clearCache(filePath: String? = null) {
        if (filePath != null) {
            cache.remove(filePath)
        } else {
            cache.clear()
        }
    }

    /**
     * Returns true if a cached entry exists for the given file path.
     * Useful for testing caching behavior.
     */
    internal fun isCached(filePath: String): Boolean = cache.containsKey(filePath)

    /**
     * Reads and parses `linguist-language` rules from the `.gitattributes` file in the project root.
     */
    fun readRules(project: Project): List<GitAttributesRule> {
        val gitAttributesFile = findGitAttributesFile(project) ?: return emptyList()
        val path = gitAttributesFile.path
        val currentStamp = gitAttributesFile.modificationStamp

        val cached = cache[path]
        if (cached != null && cached.modificationStamp == currentStamp) {
            return cached.rules
        }

        val text = try {
            VfsUtilCore.loadText(gitAttributesFile)
        } catch (_: Exception) {
            return emptyList()
        }

        val rules = parseRules(text)
        cache[path] = CachedRules(currentStamp, rules)
        return rules
    }

    /**
     * Locates the `.gitattributes` VirtualFile in the project root.
     */
    fun findGitAttributesFile(project: Project): VirtualFile? {
        if (project.isDisposed || !project.isInitialized || project.isDefault) {
            return null
        }
        val projectDir = project.guessProjectDir() ?: return null
        val gitAttributesFile = projectDir.findChild(".gitattributes") ?: return null
        if (!gitAttributesFile.isValid || gitAttributesFile.isDirectory) {
            return null
        }
        return gitAttributesFile
    }

    /**
     * Parses `.gitattributes` file content into a list of [GitAttributesRule]s.
     */
    fun parseRules(content: String): List<GitAttributesRule> {
        if (content.isBlank()) return emptyList()
        val rules = mutableListOf<GitAttributesRule>()
        for (line in content.lineSequence()) {
            val rule = parseLine(line)
            if (rule != null) {
                rules.add(rule)
            }
        }
        return rules
    }

    /**
     * Parses a single line from a `.gitattributes` file.
     * Returns a [GitAttributesRule] if the line contains a valid pattern and `linguist-language` attribute,
     * or null if the line is empty, a comment, a macro definition, or does not contain `linguist-language`.
     */
    fun parseLine(line: String): GitAttributesRule? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("[attr]")) {
            return null
        }

        val len = trimmed.length
        val pattern: String
        var idx: Int

        if (trimmed.startsWith('"')) {
            val unquoted = unquoteCStylePattern(trimmed) ?: return null
            pattern = unquoted.first
            idx = unquoted.second
        } else {
            // Unquoted pattern in Git is raw text up to the first whitespace
            var end = 0
            while (end < len && !trimmed[end].isWhitespace()) {
                end++
            }
            pattern = trimmed.substring(0, end)
            idx = end
        }

        if (pattern.isEmpty() || pattern.startsWith("!")) {
            // Negative path patterns (!pattern) are invalid in .gitattributes per git spec
            return null
        }

        var lastLanguage: String? = null
        var lastIsUnset = false
        var hasLinguistAttr = false

        while (idx < len) {
            // Skip whitespace
            while (idx < len && trimmed[idx].isWhitespace()) {
                idx++
            }
            if (idx >= len) break

            // Quoted language value compatibility extension: linguist-language="..." or linguist-language='...'
            if (trimmed.startsWith("$LINGUIST_LANGUAGE_ATTR=\"", idx) || trimmed.startsWith("$LINGUIST_LANGUAGE_ATTR='", idx)) {
                val quoteChar = trimmed[idx + "$LINGUIST_LANGUAGE_ATTR=".length]
                idx += "$LINGUIST_LANGUAGE_ATTR=".length + 1 // advance past linguist-language="

                val langBuilder = StringBuilder()
                var escaped = false

                while (idx < len) {
                    val c = trimmed[idx]
                    if (escaped) {
                        langBuilder.append(c)
                        escaped = false
                        idx++
                    } else if (c == '\\') {
                        escaped = true
                        idx++
                    } else if (c == quoteChar) {
                        idx++
                        break
                    } else {
                        langBuilder.append(c)
                        idx++
                    }
                }

                val value = langBuilder.toString().trim()
                if (value.isNotEmpty()) {
                    lastLanguage = value
                    lastIsUnset = false
                    hasLinguistAttr = true
                } else {
                    lastLanguage = null
                    lastIsUnset = true
                    hasLinguistAttr = true
                }
            } else {
                // Raw attribute token up to the next whitespace
                val tokenStart = idx
                while (idx < len && !trimmed[idx].isWhitespace()) {
                    idx++
                }
                val token = trimmed.substring(tokenStart, idx)
                if (token.isEmpty()) continue

                // Git and Linguist attribute names are case-sensitive
                if (token == "-$LINGUIST_LANGUAGE_ATTR" || token.startsWith("-$LINGUIST_LANGUAGE_ATTR=") ||
                    token == "!$LINGUIST_LANGUAGE_ATTR" || token.startsWith("!$LINGUIST_LANGUAGE_ATTR=") ||
                    token == LINGUIST_LANGUAGE_ATTR
                ) {
                    lastLanguage = null
                    lastIsUnset = true
                    hasLinguistAttr = true
                } else if (token.startsWith("$LINGUIST_LANGUAGE_ATTR=")) {
                    val value = token.substring("$LINGUIST_LANGUAGE_ATTR=".length).trim()
                    if (value.isNotEmpty()) {
                        lastLanguage = value
                        lastIsUnset = false
                        hasLinguistAttr = true
                    } else {
                        lastLanguage = null
                        lastIsUnset = true
                        hasLinguistAttr = true
                    }
                }
            }
        }

        if (hasLinguistAttr) {
            return GitAttributesRule(
                pattern = pattern,
                language = lastLanguage,
                isUnset = lastIsUnset
            )
        }

        return null
    }

    /**
     * Unquotes a C-style quoted pattern string according to Git's `unquote_c_style` rules.
     * Supports C escapes (`\a`, `\b`, `\t`, `\n`, `\v`, `\f`, `\r`, `\"`, `\\`), octal byte escapes
     * (`\ddd`) decoded as UTF-8, and preserves quoted whitespace without trimming.
     *
     * Returns a pair of (unquotedPattern, nextIndexInLine) or null if quotes are malformed or invalid escapes are present.
     */
    private fun unquoteCStylePattern(input: String): Pair<String, Int>? {
        if (!input.startsWith('"')) return null
        var idx = 1
        val len = input.length
        val byteStream = ByteArrayOutputStream()
        var closed = false

        while (idx < len) {
            val c = input[idx]
            if (c == '"') {
                closed = true
                idx++
                break
            } else if (c == '\\') {
                idx++
                if (idx >= len) return null
                val esc = input[idx]
                when (esc) {
                    'a' -> byteStream.write(0x07)
                    'b' -> byteStream.write(0x08)
                    't' -> byteStream.write(0x09)
                    'n' -> byteStream.write(0x0A)
                    'v' -> byteStream.write(0x0B)
                    'f' -> byteStream.write(0x0C)
                    'r' -> byteStream.write(0x0D)
                    '"' -> byteStream.write('"'.code)
                    '\\' -> byteStream.write('\\'.code)
                    '0', '1', '2', '3' -> {
                        if (idx + 2 >= len) return null
                        val d2 = input[idx + 1]
                        val d3 = input[idx + 2]
                        if (d2 !in '0'..'7' || d3 !in '0'..'7') return null
                        val octVal = ((esc - '0') shl 6) or ((d2 - '0') shl 3) or (d3 - '0')
                        byteStream.write(octVal)
                        idx += 2
                    }
                    else -> {
                        // Unknown escape sequence fails C-style unquoting
                        return null
                    }
                }
                idx++
            } else {
                val start = idx
                while (idx < len && input[idx] != '"' && input[idx] != '\\') {
                    idx++
                }
                val textRun = input.substring(start, idx)
                val bytes = textRun.toByteArray(Charsets.UTF_8)
                byteStream.write(bytes)
            }
        }

        if (!closed) return null
        if (idx < len && !input[idx].isWhitespace()) {
            return null
        }

        val pattern = byteStream.toString(Charsets.UTF_8.name())
        return Pair(pattern, idx)
    }
}
