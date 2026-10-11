package com.medmunds.advancedfiletypes.model

import com.medmunds.advancedfiletypes.AdvancedFileTypesBundle
import java.util.regex.Pattern

enum class PatternSyntax(val bundleKey: String) {
    GLOB("syntax.glob"),
    REGEX("syntax.regex");

    val displayName: String
        get() = AdvancedFileTypesBundle.message(bundleKey)
}

enum class GlobDialect {
    /**
     * Standard glob dialect used for plugin override rules and VS Code settings.
     * Backslashes '\' are normalized to directory separators '/', and braces '{a,b}' expand to alternations.
     */
    STANDARD,

    /**
     * Git wildmatch dialect used for .gitattributes files.
     * Backslashes '\' escape literal characters, braces are literal, and '**' only crosses directories when bounded by slashes.
     */
    GIT_ATTRIBUTES
}

object PathPatternMatcher {

    /**
     * Converts a glob pattern to a standard regular expression string.
     */
    fun globToRegex(glob: String, dialect: GlobDialect = GlobDialect.STANDARD): String {
        return when (dialect) {
            GlobDialect.STANDARD -> globToRegexStandard(glob)
            GlobDialect.GIT_ATTRIBUTES -> globToRegexGitAttributes(glob)
        }
    }

    private fun globToRegexStandard(glob: String): String {
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

        val len = pattern.length
        var i = 0
        while (i < len) {
            val c = pattern[i]
            when {
                c == '*' -> {
                    if (i + 1 < len && pattern[i + 1] == '*') {
                        val afterDouble = i + 2
                        if (afterDouble < len && pattern[afterDouble] == '/') {
                            sb.append("(?:.*/)?")
                            i += 3
                        } else if (i > 0 && pattern[i - 1] == '/') {
                            sb.append(".*")
                            i += 2
                        } else {
                            sb.append(".*")
                            i += 2
                        }
                    } else {
                        sb.append("[^/]*")
                        i++
                    }
                }
                c == '?' -> {
                    sb.append("[^/]")
                    i++
                }
                c == '{' -> {
                    val closing = findClosingBrace(pattern, i)
                    if (closing != -1) {
                        val choices = splitChoices(pattern.substring(i + 1, closing))
                        sb.append("(?:")
                        sb.append(choices.joinToString("|") { choice ->
                            globToRegexFragment(choice)
                        })
                        sb.append(")")
                        i = closing + 1
                    } else {
                        sb.append("\\{")
                        i++
                    }
                }
                c == '[' -> {
                    val closing = findClosingBracket(pattern, i)
                    if (closing != -1) {
                        var classContent = pattern.substring(i + 1, closing)
                        if (classContent.startsWith('!')) {
                            classContent = "^" + classContent.substring(1)
                        }
                        sb.append("[").append(classContent).append("]")
                        i = closing + 1
                    } else {
                        sb.append("\\[")
                        i++
                    }
                }
                c in setOf('.', '(', ')', '+', '^', '$', '|') -> {
                    sb.append('\\').append(c)
                    i++
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }

        sb.append("$")
        return sb.toString()
    }

    private fun globToRegexGitAttributes(glob: String): String {
        var pattern = glob
        var hasSlash = false
        var idx = 0
        while (idx < pattern.length) {
            if (pattern[idx] == '\\') {
                idx += 2
                continue
            }
            if (pattern[idx] == '/') {
                hasSlash = true
                break
            }
            idx++
        }

        if (pattern.startsWith("./")) {
            pattern = pattern.substring(2)
            hasSlash = true
        } else if (pattern.startsWith("/")) {
            pattern = pattern.substring(1)
            hasSlash = true
        }

        val sb = StringBuilder("^")
        if (!hasSlash) {
            sb.append("(?:.*/)?")
        }

        val len = pattern.length
        var i = 0
        while (i < len) {
            val c = pattern[i]
            when {
                c == '\\' -> {
                    if (i + 1 < len) {
                        val next = pattern[i + 1]
                        when (next) {
                            '*', '?', '[', ']', '{', '}', '!', '\\', ' ', '#', '.', '(', ')', '+', '^', '$', '|' -> {
                                sb.append('\\').append(next)
                            }
                            else -> {
                                sb.append(next)
                            }
                        }
                        i += 2
                    } else {
                        sb.append('\\')
                        i++
                    }
                }
                c == '*' -> {
                    if (i + 1 < len && pattern[i + 1] == '*') {
                        val prevSlash = (i == 0 || pattern[i - 1] == '/')
                        val afterDouble = i + 2
                        val nextSlash = (afterDouble >= len || pattern[afterDouble] == '/')
                        if (prevSlash && nextSlash) {
                            if (afterDouble < len && pattern[afterDouble] == '/') {
                                sb.append("(?:.*/)?")
                                i = afterDouble + 1
                            } else if (i > 0 && pattern[i - 1] == '/') {
                                sb.append(".*")
                                i = afterDouble
                            } else {
                                sb.append(".*")
                                i = afterDouble
                            }
                        } else {
                            // Embedded ** behaves as single * in wildmatch
                            sb.append("[^/]*")
                            i = afterDouble
                        }
                    } else {
                        sb.append("[^/]*")
                        i++
                    }
                }
                c == '?' -> {
                    sb.append("[^/]")
                    i++
                }
                c == '[' -> {
                    val closing = findClosingBracketGit(pattern, i)
                    if (closing != -1) {
                        var classContent = pattern.substring(i + 1, closing)
                        if (classContent.startsWith('!') || classContent.startsWith('^')) {
                            classContent = "^" + classContent.substring(1)
                        }
                        sb.append("[").append(classContent).append("]")
                        i = closing + 1
                    } else {
                        sb.append("\\[")
                        i++
                    }
                }
                c in setOf('.', '(', ')', '+', '^', '$', '|', '{', '}') -> {
                    sb.append('\\').append(c)
                    i++
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }

        sb.append("$")
        return sb.toString()
    }

    private fun findClosingBrace(pattern: String, openIdx: Int): Int {
        var depth = 0
        var i = openIdx
        while (i < pattern.length) {
            if (pattern[i] == '\\' && i + 1 < pattern.length) {
                i += 2
                continue
            }
            if (pattern[i] == '{') depth++
            else if (pattern[i] == '}') {
                depth--
                if (depth == 0) return i
            }
            i++
        }
        return -1
    }

    private fun splitChoices(content: String): List<String> {
        val choices = mutableListOf<String>()
        val sb = StringBuilder()
        var depth = 0
        var i = 0
        while (i < content.length) {
            if (content[i] == '\\' && i + 1 < content.length) {
                sb.append(content[i]).append(content[i + 1])
                i += 2
                continue
            }
            if (content[i] == '{') depth++
            else if (content[i] == '}') depth--
            else if (content[i] == ',' && depth == 0) {
                choices.add(sb.toString())
                sb.clear()
                i++
                continue
            }
            sb.append(content[i])
            i++
        }
        choices.add(sb.toString())
        return choices
    }

    private fun findClosingBracket(pattern: String, openIdx: Int): Int {
        var i = openIdx + 1
        if (i < pattern.length && pattern[i] == '!') i++
        if (i < pattern.length && pattern[i] == ']') i++
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

    private fun findClosingBracketGit(pattern: String, openIdx: Int): Int {
        var i = openIdx + 1
        if (i < pattern.length && (pattern[i] == '!' || pattern[i] == '^')) i++
        if (i < pattern.length && pattern[i] == ']') i++
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

    private fun globToRegexFragment(fragment: String): String {
        val sb = StringBuilder()
        var i = 0
        val len = fragment.length
        while (i < len) {
            val c = fragment[i]
            when {
                c == '*' -> {
                    sb.append("[^/]*")
                    i++
                }
                c == '?' -> {
                    sb.append("[^/]")
                    i++
                }
                c == '{' -> {
                    // Handle nested brace alternations recursively
                    val closing = findClosingBrace(fragment, i)
                    if (closing != -1) {
                        val choices = splitChoices(fragment.substring(i + 1, closing))
                        sb.append("(?:")
                        sb.append(choices.joinToString("|") { choice ->
                            globToRegexFragment(choice)
                        })
                        sb.append(")")
                        i = closing + 1
                    } else {
                        sb.append("\\{")
                        i++
                    }
                }
                c in setOf('.', '(', ')', '+', '^', '$', '|', '}', '[', ']', '\\') -> {
                    sb.append('\\').append(c)
                    i++
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return sb.toString()
    }

    /**
     * Compiles a pattern into a java.util.regex.Pattern.
     * Throws PatternSyntaxException if invalid.
     */
    fun compile(
        patternText: String,
        syntax: PatternSyntax,
        dialect: GlobDialect = GlobDialect.STANDARD
    ): Pattern {
        val regex = when (syntax) {
            PatternSyntax.GLOB -> globToRegex(patternText, dialect)
            PatternSyntax.REGEX -> patternText
        }
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
    }

    /**
     * Matches a relative path (and/or fileName) against a pattern.
     */
    fun matches(pattern: Pattern, relativePath: String?, fileName: String): Boolean {
        val normalizedRelative = relativePath?.replace('\\', '/')
            ?.removePrefix("./")
            ?.removePrefix("/")

        if (normalizedRelative != null && pattern.matcher(normalizedRelative).matches()) {
            return true
        }

        return pattern.matcher(fileName).matches()
    }
}
