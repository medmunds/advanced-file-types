package com.medmunds.advancedfiletypes.model

import java.util.regex.Pattern

enum class PatternSyntax {
    GLOB,
    REGEX
}

object PathPatternMatcher {

    /**
     * Converts a glob pattern to a standard regular expression string.
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
            // Pattern like "*.txt" should match any file in any directory or at root
            sb.append("(?:.*/)?")
        }

        val len = pattern.length
        var i = 0
        while (i < len) {
            when (val c = pattern[i]) {
                '*' -> {
                    if (i + 1 < len && pattern[i + 1] == '*') {
                        // Double asterisk: **
                        val afterDouble = i + 2
                        if (afterDouble < len && pattern[afterDouble] == '/') {
                            // "foo/**/bar" or "**/bar"
                            sb.append("(?:.*/)?")
                            i += 3
                            continue
                        } else if (i > 0 && pattern[i - 1] == '/') {
                            // "foo/**"
                            sb.append(".*")
                            i += 2
                            continue
                        } else {
                            sb.append(".*")
                            i += 2
                            continue
                        }
                    } else {
                        // Single asterisk: * matches anything except '/'
                        sb.append("[^/]*")
                        i++
                    }
                }
                '?' -> {
                    sb.append("[^/]")
                    i++
                }
                '{' -> {
                    val closing = pattern.indexOf('}', i)
                    if (closing != -1) {
                        val choices = pattern.substring(i + 1, closing).split(',')
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
                '[' -> {
                    val closing = pattern.indexOf(']', i)
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
                '.', '(', ')', '+', '^', '$', '|' -> {
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

    private fun globToRegexFragment(fragment: String): String {
        val sb = StringBuilder()
        for (c in fragment) {
            when (c) {
                '*' -> sb.append("[^/]*")
                '?' -> sb.append("[^/]")
                '.', '(', ')', '+', '^', '$', '|', '{', '}', '[', ']' -> sb.append('\\').append(c)
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /**
     * Compiles a pattern into a java.util.regex.Pattern.
     * Throws PatternSyntaxException if invalid.
     */
    fun compile(patternText: String, syntax: PatternSyntax): Pattern {
        val regex = when (syntax) {
            PatternSyntax.GLOB -> globToRegex(patternText)
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
