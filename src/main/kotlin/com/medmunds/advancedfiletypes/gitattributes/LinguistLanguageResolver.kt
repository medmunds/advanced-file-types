package com.medmunds.advancedfiletypes.gitattributes

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.LanguageFileType
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Properties

object LinguistLanguageResolver {

    private val staticAliasMap: Map<String, String> = loadStaticAliasMap()

    private fun loadStaticAliasMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val resourcePath = "/com/medmunds/advancedfiletypes/linguist-language-map.properties"
        val stream = LinguistLanguageResolver::class.java.getResourceAsStream(resourcePath)
        if (stream != null) {
            try {
                InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
                    val properties = Properties()
                    properties.load(reader)
                    for (name in properties.stringPropertyNames()) {
                        val value = properties.getProperty(name)?.trim()
                        if (!value.isNullOrEmpty()) {
                            map[name.trim().lowercase()] = value
                        }
                    }
                }
            } catch (_: Exception) {
                // Fall back to empty map if resource read fails
            }
        }
        return map
    }

    /**
     * Resolves a GitHub Linguist language name or alias to an IntelliJ FileType.
     * Returns null if no matching or installed FileType is found in the current IDE.
     */
    fun resolveFileType(
        languageName: String,
        customMappings: Map<String, String> = emptyMap()
    ): FileType? {
        val trimmed = languageName.trim()
        val normalizedName = trimmed.lowercase()
        if (normalizedName.isEmpty()) return null

        val fileTypeManager = FileTypeManager.getInstance()

        // 1. Check user-defined custom mappings from settings storage (if any)
        val customTargetName = customMappings[normalizedName]
            ?: customMappings[trimmed]
            ?: customMappings[normalizedName.replace('-', ' ')]
        if (!customTargetName.isNullOrBlank()) {
            findFileType(fileTypeManager, customTargetName)?.let { return it }
        }

        // 2. Check static predefined alias map from resource
        val staticTargetName = getStaticTargetName(normalizedName)
        if (!staticTargetName.isNullOrBlank()) {
            findFileType(fileTypeManager, staticTargetName)?.let { return it }
        }

        // 3. Check direct resolution against registered file types
        return findFileType(fileTypeManager, trimmed)
    }

    /**
     * Returns the static mapped IntelliJ FileType name for a Linguist language name or alias, if mapped.
     */
    internal fun getStaticTargetName(languageName: String): String? {
        val normalizedName = languageName.trim().lowercase()
        if (normalizedName.isEmpty()) return null
        return staticAliasMap[normalizedName]
            ?: staticAliasMap[normalizedName.replace('-', ' ')]
            ?: staticAliasMap[normalizedName.replace('_', ' ')]
            ?: staticAliasMap[normalizedName.replace(" ", "")]
    }

    private fun findFileType(fileTypeManager: FileTypeManager, targetName: String): FileType? {
        // Direct exact lookup by name
        val exact = fileTypeManager.findFileTypeByName(targetName)
        if (exact != null) return exact

        // Search through registered file types by name, display name, or language ID
        val registered = fileTypeManager.registeredFileTypes
        for (fileType in registered) {
            if (fileType.name.equals(targetName, ignoreCase = true)) {
                return fileType
            }
        }
        for (fileType in registered) {
            if (fileType.displayName.equals(targetName, ignoreCase = true)) {
                return fileType
            }
        }
        for (fileType in registered) {
            if (fileType is LanguageFileType && fileType.language.id.equals(targetName, ignoreCase = true)) {
                return fileType
            }
        }

        // Try normalized variations against display name (e.g., spaces vs hyphens)
        val spaceNormalized = targetName.replace('-', ' ')
        for (fileType in registered) {
            if (fileType.displayName.equals(spaceNormalized, ignoreCase = true)) {
                return fileType
            }
        }

        return null
    }
}
