package com.medmunds.advancedfiletypes.vscode

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import de.marhali.json5.Json5
import java.util.concurrent.ConcurrentHashMap

data class VsCodeAssociation(
    val pattern: String,
    val languageId: String
)

object VsCodeSettingsReader {

    private val json5 = Json5()

    private data class CachedAssociations(
        val modificationStamp: Long,
        val associations: List<VsCodeAssociation>
    )

    private val cache = ConcurrentHashMap<String, CachedAssociations>()

    /**
     * Clears the cache for a given project or all cached settings.
     */
    fun clearCache(projectPath: String? = null) {
        if (projectPath != null) {
            cache.remove(projectPath)
        } else {
            cache.clear()
        }
    }

    /**
     * Reads and parses `files.associations` from the `.vscode/settings.json` file in the project.
     */
    fun readAssociations(project: Project): List<VsCodeAssociation> {
        val settingsFile = findSettingsFile(project) ?: return emptyList()
        val path = settingsFile.path
        val currentStamp = settingsFile.modificationStamp

        val cached = cache[path]
        if (cached != null && cached.modificationStamp == currentStamp) {
            return cached.associations
        }

        val text = try {
            VfsUtilCore.loadText(settingsFile)
        } catch (_: Exception) {
            return emptyList()
        }

        val associations = parseAssociations(text)
        cache[path] = CachedAssociations(currentStamp, associations)
        return associations
    }

    /**
     * Locates the `.vscode/settings.json` VirtualFile for the given project.
     */
    fun findSettingsFile(project: Project): VirtualFile? {
        if (project.isDisposed || !project.isInitialized || project.isDefault) {
            return null
        }
        val projectDir = project.guessProjectDir() ?: return null
        val vscodeDir = projectDir.findChild(".vscode") ?: return null
        val settingsFile = vscodeDir.findChild("settings.json") ?: return null
        if (!settingsFile.isValid || settingsFile.isDirectory) {
            return null
        }
        return settingsFile
    }

    /**
     * Parses `files.associations` from raw JSON/JSONC text.
     * Tolerates comments (`//`, `/* */`, `#`), unquoted keys, and trailing commas.
     */
    fun parseAssociations(jsonText: String): List<VsCodeAssociation> {
        if (jsonText.isBlank()) return emptyList()

        try {
            val rootNode = json5.parse(jsonText) ?: return emptyList()
            if (!rootNode.isJson5Object) return emptyList()

            val associationsNode = rootNode.asJson5Object.get("files.associations") ?: return emptyList()
            if (!associationsNode.isJson5Object) return emptyList()

            val result = mutableListOf<VsCodeAssociation>()
            for ((pattern, langElem) in associationsNode.asJson5Object.asMap()) {
                if (langElem != null && langElem.isJson5Primitive && langElem.asJson5Primitive.isString) {
                    val langId = langElem.asJson5Primitive.asString.trim()
                    val trimmedPattern = pattern.trim()
                    if (trimmedPattern.isNotEmpty() && langId.isNotEmpty()) {
                        result.add(VsCodeAssociation(trimmedPattern, langId))
                    }
                }
            }
            return result
        } catch (_: Exception) {
            return emptyList()
        }
    }
}
