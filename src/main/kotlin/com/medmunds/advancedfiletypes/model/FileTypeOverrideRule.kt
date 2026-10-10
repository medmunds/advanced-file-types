package com.medmunds.advancedfiletypes.model

import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.Transient
import java.util.regex.Pattern

@Tag("overrideRule")
data class FileTypeOverrideRule(
    @Attribute("pattern")
    var pattern: String = "",

    @Attribute("fileTypeName")
    var fileTypeName: String = "",

    @Attribute("syntax")
    var syntax: PatternSyntax = PatternSyntax.GLOB
) {
    @Transient
    @Volatile
    private var cachedPattern: Pattern? = null

    @Transient
    @Volatile
    private var cachedPatternKey: String? = null

    fun matches(relativePath: String?, fileName: String): Boolean {
        if (pattern.isBlank()) return false
        val p = getOrCompilePattern() ?: return false
        return PathPatternMatcher.matches(p, relativePath, fileName)
    }

    fun getOrCompilePattern(): Pattern? {
        val key = "$syntax:$pattern"
        val existing = cachedPattern
        if (existing != null && cachedPatternKey == key) {
            return existing
        }
        return try {
            val compiled = PathPatternMatcher.compile(pattern, syntax)
            cachedPattern = compiled
            cachedPatternKey = key
            compiled
        } catch (_: Exception) {
            null
        }
    }
}
