package com.medmunds.advancedfiletypes.model

import com.medmunds.advancedfiletypes.AdvancedFileTypesBundle
import java.util.regex.Pattern

enum class PatternSyntax(val bundleKey: String) {
    GLOB("syntax.glob"),
    REGEX("syntax.regex");

    val displayName: String
        get() = AdvancedFileTypesBundle.message(bundleKey)
}

object PathPatternMatcher {
    // Reference implementation: https://github.com/microsoft/vscode/blob/main/src/vs/base/common/glob.ts

    /**
     * Converts a glob pattern to a standard regular expression string compatible with VS Code glob syntax.
     */
    fun globToRegex(glob: String): String {
        var pattern = glob.trim().replace('\\', '/')
        if (pattern.startsWith("./")) {
            pattern = pattern.substring(2)
        }
        if (pattern.startsWith("/")) {
            pattern = pattern.substring(1)
        }

        val hasSlash = pattern.contains('/')
        val sb = StringBuilder("^")
        if (!hasSlash) {
            sb.append("(?:.*/)?")
        }

        convertGlobFragment(pattern, sb)
        sb.append("$")
        return sb.toString()
    }

    private fun convertGlobFragment(fragment: String, sb: StringBuilder) {
        val len = fragment.length
        var i = 0
        while (i < len) {
            when (val c = fragment[i]) {
                '*' -> {
                    if (i + 1 < len && fragment[i + 1] == '*') {
                        // Collapse consecutive '*' characters
                        var j = i + 2
                        while (j < len && fragment[j] == '*') {
                            j++
                        }
                        // Collapse consecutive /**/ sequences
                        while (j < len && fragment[j] == '/') {
                            var k = j + 1
                            if (k + 1 < len && fragment[k] == '*' && fragment[k + 1] == '*') {
                                k += 2
                                while (k < len && fragment[k] == '*') k++
                                j = k
                            } else {
                                break
                            }
                        }
                        if (j < len && fragment[j] == '/') {
                            // **/
                            if (!sb.endsWith("(?:.*/)?")) {
                                sb.append("(?:.*/)?")
                            }
                            i = j + 1
                        } else if (j == len) {
                            // ** at the end of pattern
                            if (sb.endsWith("/")) {
                                sb.setLength(sb.length - 1)
                                sb.append("(?:/.*)?")
                            } else if (sb.endsWith("(?:.*/)?")) {
                                sb.append(".*")
                            } else if (!sb.endsWith(".*")) {
                                sb.append(".*")
                            }
                            i = j
                        } else {
                            // ** followed by non-slash (e.g., **foo)
                            sb.append(".*")
                            i = j
                        }
                    } else {
                        sb.append("[^/]*")
                        i++
                    }
                }
                '?' -> {
                    sb.append("[^/]")
                    i++
                }
                '{' -> {
                    val closing = findClosingBrace(fragment, i)
                    if (closing != -1) {
                        val choices = splitChoices(fragment.substring(i + 1, closing))
                        sb.append("(?:")
                        for ((idx, choice) in choices.withIndex()) {
                            if (idx > 0) sb.append("|")
                            convertGlobFragment(choice, sb)
                        }
                        sb.append(")")
                        i = closing + 1
                    } else {
                        sb.append("\\{")
                        i++
                    }
                }
                '[' -> {
                    val closing = findClosingBracket(fragment, i)
                    if (closing != -1) {
                        val classContent = fragment.substring(i + 1, closing)
                        sb.append(convertCharacterClass(classContent))
                        i = closing + 1
                    } else {
                        sb.append("\\[")
                        i++
                    }
                }
                in setOf('.', '(', ')', '+', '^', '$', '|', '}', ']') -> {
                    sb.append('\\').append(c)
                    i++
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
    }

    private fun convertCharacterClass(classContent: String): String {
        val isNegated = (classContent.startsWith('!') || classContent.startsWith('^')) && classContent.length > 1
        val rawContent = if (isNegated) classContent.substring(1) else classContent

        val sb = StringBuilder("[")
        if (isNegated) {
            sb.append("^")
        }

        var j = 0
        while (j < rawContent.length) {
            when (val ch = rawContent[j]) {
                '\\' -> {
                    if (j + 1 < rawContent.length) {
                        sb.append('\\').append(rawContent[j + 1])
                        j += 2
                    } else {
                        sb.append("\\\\")
                        j++
                    }
                }
                '[' -> {
                    sb.append("\\[")
                    j++
                }
                ']' -> {
                    sb.append("\\]")
                    j++
                }
                '^' if j == 0 && !isNegated -> {
                    sb.append("\\^")
                    j++
                }
                '&' if j + 1 < rawContent.length && rawContent[j + 1] == '&' -> {
                    sb.append("\\&\\&")
                    j += 2
                }
                else -> {
                    sb.append(ch)
                    j++
                }
            }
        }
        sb.append("]")
        return sb.toString()
    }

    private fun findClosingBrace(pattern: String, openIdx: Int): Int {
        var depth = 0
        var insideBracket = false
        var i = openIdx
        while (i < pattern.length) {
            if (pattern[i] == '\\' && i + 1 < pattern.length) {
                i += 2
                continue
            }
            val c = pattern[i]
            when (c) {
                '[' if !insideBracket -> insideBracket = true
                ']' if insideBracket -> insideBracket = false
                '{' if !insideBracket -> depth++
                '}' if !insideBracket -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
    }

    private fun splitChoices(content: String): List<String> {
        val choices = mutableListOf<String>()
        val sb = StringBuilder()
        var braceDepth = 0
        var insideBracket = false
        var i = 0
        while (i < content.length) {
            if (content[i] == '\\' && i + 1 < content.length) {
                sb.append(content[i]).append(content[i + 1])
                i += 2
                continue
            }
            when (val c = content[i]) {
                '[' if !insideBracket -> {
                    insideBracket = true
                    sb.append(c)
                    i++
                }
                ']' if insideBracket -> {
                    insideBracket = false
                    sb.append(c)
                    i++
                }
                '{' if !insideBracket -> {
                    braceDepth++
                    sb.append(c)
                    i++
                }
                '}' if !insideBracket -> {
                    if (braceDepth > 0) braceDepth--
                    sb.append(c)
                    i++
                }
                ',' if braceDepth == 0 && !insideBracket -> {
                    choices.add(sb.toString())
                    sb.clear()
                    i++
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        choices.add(sb.toString())
        return choices
    }

    private fun findClosingBracket(pattern: String, openIdx: Int): Int {
        var i = openIdx + 1
        if (i < pattern.length && (pattern[i] == '!' || pattern[i] == '^')) {
            if (i + 1 < pattern.length && pattern[i + 1] == ']' && i + 2 < pattern.length && pattern[i + 2] == ']') {
                // [!]] or [^]] -> literal ']' as first char of negated class
                i += 2
            } else if (i + 1 < pattern.length && pattern[i + 1] == ']') {
                // [!] or [^] -> literal '!' or '^' inside class, closing bracket is at i + 1
                return i + 1
            } else {
                i++
            }
        } else if (i < pattern.length && pattern[i] == ']') {
            if (i + 1 < pattern.length && pattern[i + 1] == ']') {
                // []] -> literal ']' as first char of class
                i++
            } else {
                // [] -> empty class, closing bracket at i
                return i
            }
        }
        while (i < pattern.length) {
            if (pattern[i] == '\\' && i + 1 < pattern.length) {
                i += 2
                continue
            }
            if (pattern[i] == ']') return i
            i++
        }
        return -1
    }

    /**
     * Compiles a pattern into a java.util.regex.Pattern.
     * Throws PatternSyntaxException if invalid.
     */
    @JvmOverloads
    fun compile(
        patternText: String,
        syntax: PatternSyntax,
        ignoreCase: Boolean = true
    ): Pattern {
        val regex = when (syntax) {
            PatternSyntax.GLOB -> globToRegex(patternText)
            PatternSyntax.REGEX -> patternText
        }
        val flags = if (ignoreCase) Pattern.CASE_INSENSITIVE else 0
        return Pattern.compile(regex, flags)
    }

    /**
     * Matches a relative path (and/or fileName) against a pattern.
     */
    fun matches(pattern: Pattern, relativePath: String?, fileName: String): Boolean {
        val normalizedRelative = relativePath?.replace('\\', '/')
            ?.removePrefix("./")
            ?.removePrefix("/")

        val target = normalizedRelative ?: fileName
        return pattern.matcher(target).matches()
    }
}
