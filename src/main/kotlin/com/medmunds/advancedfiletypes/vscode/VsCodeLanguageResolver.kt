package com.medmunds.advancedfiletypes.vscode

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.LanguageFileType
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Properties

object VsCodeLanguageResolver {

    sealed class Resolution {
        data class Resolved(val fileType: FileType) : Resolution()
        data class Unsupported(val fileTypeName: String) : Resolution()
        object Unmapped : Resolution()
    }

    private val staticAliasMap: Map<String, String> = loadStaticAliasMap()

    private fun loadStaticAliasMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val resourcePath = "/com/medmunds/advancedfiletypes/vscode-language-map.properties"
        val stream = VsCodeLanguageResolver::class.java.getResourceAsStream(resourcePath)
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
     * Resolves a VS Code language identifier to an IntelliJ FileType.
     * Returns null if no matching or installed FileType is found in the current IDE.
     */
    fun resolveFileType(
        languageId: String,
        customMappings: Map<String, String> = emptyMap()
    ): FileType? = (resolve(languageId, customMappings) as? Resolution.Resolved)?.fileType

    /**
     * Resolves a VS Code language identifier while retaining whether a known target type is unavailable.
     */
    fun resolve(
        languageId: String,
        customMappings: Map<String, String> = emptyMap()
    ): Resolution {
        val normalizedId = languageId.trim().lowercase()
        if (normalizedId.isEmpty()) return Resolution.Unmapped

        val fileTypeManager = FileTypeManager.getInstance()
        var unavailableTarget: String? = null

        // 1. Check user-defined custom mappings from settings storage (if any)
        val customTargetName = customMappings[normalizedId] ?: customMappings[languageId.trim()]
        if (!customTargetName.isNullOrBlank()) {
            findFileType(fileTypeManager, customTargetName)?.let { return Resolution.Resolved(it) }
            unavailableTarget = customTargetName
        }

        // 2. Check static predefined alias map from resource
        val staticTargetName = staticAliasMap[normalizedId]
        if (!staticTargetName.isNullOrBlank()) {
            findFileType(fileTypeManager, staticTargetName)?.let { return Resolution.Resolved(it) }
            unavailableTarget = staticTargetName
        }

        // 3. Check direct resolution against registered file types
        findFileType(fileTypeManager, languageId.trim())?.let { return Resolution.Resolved(it) }

        return unavailableTarget?.let(Resolution::Unsupported) ?: Resolution.Unmapped
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

        return null
    }
}
