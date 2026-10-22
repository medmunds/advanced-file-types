package com.medmunds.advancedfiletypes

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.LanguageFileType
import de.marhali.json5.Json5
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

object ExternalLanguageResolver {

    private val LOG = Logger.getInstance(ExternalLanguageResolver::class.java)
    private const val NO_MATCH_SENTINEL = ""

    sealed class Resolution {
        data class Resolved(val fileType: FileType) : Resolution()
        data class Unsupported(val fileTypeName: String) : Resolution()
        object Unmapped : Resolution()
    }

    private val staticAliasMap: Map<String, String> = loadStaticAliasMap()
    private val fallbackCache = ConcurrentHashMap<String, String>()

    private fun loadStaticAliasMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val resourcePath = "/com/medmunds/advancedfiletypes/language-map.json5"
        val stream = ExternalLanguageResolver::class.java.getResourceAsStream(resourcePath)
        if (stream == null) {
            LOG.error("Failed to find language mapping resource at '$resourcePath'")
            return emptyMap()
        }
        try {
            val text = InputStreamReader(stream, StandardCharsets.UTF_8).use { it.readText() }
            val rootNode = Json5().parse(text)
            if (rootNode != null && rootNode.isJson5Object) {
                for ((fileTypeName, aliasesElem) in rootNode.asJson5Object.asMap()) {
                    val targetName = fileTypeName.trim()
                    if (targetName.isEmpty() || aliasesElem == null || !aliasesElem.isJson5Array) continue
                    for (elem in aliasesElem.asJson5Array) {
                        if (elem != null && elem.isJson5Primitive && elem.asJson5Primitive.isString) {
                            val alias = elem.asJson5Primitive.asString.trim().lowercase()
                            if (alias.isNotEmpty()) {
                                map[alias] = targetName
                            }
                        }
                    }
                }
            } else {
                LOG.error("Language mapping resource at '$resourcePath' did not contain a valid root JSON5 object")
            }
        } catch (e: Exception) {
            LOG.error("Failed to parse language mapping resource at '$resourcePath'", e)
        }
        return map
    }

    /**
     * Resolves an external language identifier (VS Code or Linguist) to an IntelliJ [FileType].
     * Returns null if no matching or installed [FileType] is found in the current IDE.
     */
    fun resolveFileType(
        languageId: String,
        customMappings: Map<String, String> = emptyMap()
    ): FileType? = (resolve(languageId, customMappings) as? Resolution.Resolved)?.fileType

    /**
     * Resolves an external language identifier while retaining whether a known target type is unavailable in this IDE.
     */
    fun resolve(
        languageId: String,
        customMappings: Map<String, String> = emptyMap()
    ): Resolution {
        val trimmed = languageId.trim()
        val normalizedId = trimmed.lowercase()
        if (normalizedId.isEmpty()) return Resolution.Unmapped

        val fileTypeManager = FileTypeManager.getInstance()

        // 1. Check user-defined custom mappings from settings storage (if any).
        // Custom mappings match against the exact external language ID and map directly to exact FileType names.
        val customTargetName = customMappings[trimmed]
        if (customTargetName != null) {
            val customFileType = fileTypeManager.findFileTypeByName(customTargetName)
            val res = if (customFileType != null) {
                Resolution.Resolved(customFileType)
            } else {
                Resolution.Unsupported(customTargetName)
            }
            if (LOG.isDebugEnabled) {
                LOG.debug("Resolved custom mapping '$trimmed' -> $res")
            }
            return res
        }

        // 2. Check static predefined alias map from resource.
        // Known mappings target exact IntelliJ FileType names, resolved in O(1) without iterating registered types.
        val staticTargetName = staticAliasMap[normalizedId]
        if (staticTargetName != null) {
            val fileType = fileTypeManager.findFileTypeByName(staticTargetName)
            val res = if (fileType != null) {
                Resolution.Resolved(fileType)
            } else {
                Resolution.Unsupported(staticTargetName)
            }
            if (LOG.isDebugEnabled) {
                LOG.debug("Resolved static mapping '$trimmed' ($staticTargetName) -> $res")
            }
            return res
        }

        // 3. Fallback: Search registered IntelliJ file types for unmapped external identifiers.
        // This provides zero-maintenance fallback so external IDs that happen to match a registered
        // IntelliJ FileType name, display name, or language ID (such as new file types added by future
        // IDE plugins or releases) can resolve automatically. Results are cached to avoid repeated searches.
        val fallbackFileType = findRegisteredFileType(fileTypeManager, normalizedId)
        val res = if (fallbackFileType != null) {
            Resolution.Resolved(fallbackFileType)
        } else {
            Resolution.Unmapped
        }
        if (LOG.isDebugEnabled) {
            LOG.debug("Resolved fallback search for '$trimmed' -> $res")
        }
        return res
    }

    /**
     * Returns the static mapped IntelliJ FileType name for an external language name or alias, if mapped.
     */
    internal fun getStaticTargetName(languageName: String): String? {
        val normalizedName = languageName.trim().lowercase()
        return staticAliasMap[normalizedName]
    }

    internal fun getStaticAliasMap(): Map<String, String> = staticAliasMap

    internal fun clearFallbackCache() {
        fallbackCache.clear()
    }

    private fun findRegisteredFileType(fileTypeManager: FileTypeManager, normalizedId: String): FileType? {
        val cachedName = fallbackCache[normalizedId]
        if (cachedName != null) {
            return if (cachedName == NO_MATCH_SENTINEL) null else fileTypeManager.findFileTypeByName(cachedName)
        }

        val matched = searchRegisteredFileTypes(fileTypeManager, normalizedId)
        if (matched != null) {
            fallbackCache[normalizedId] = matched.name
            return matched
        } else {
            fallbackCache[normalizedId] = NO_MATCH_SENTINEL
            return null
        }
    }

    private fun searchRegisteredFileTypes(fileTypeManager: FileTypeManager, normalizedId: String): FileType? {
        val registered = fileTypeManager.registeredFileTypes

        // 1. Direct case-insensitive match against name, displayName, or language ID
        for (fileType in registered) {
            if (fileType.name.equals(normalizedId, ignoreCase = true)) return fileType
        }
        for (fileType in registered) {
            if (fileType.displayName.equals(normalizedId, ignoreCase = true)) return fileType
        }
        for (fileType in registered) {
            if (fileType is LanguageFileType && fileType.language.id.equals(normalizedId, ignoreCase = true)) {
                return fileType
            }
        }

        // 2. Normalized variations (hyphens, underscores, spaces) against name, displayName, and language ID
        val variations = listOf(
            normalizedId.replace('-', ' '),
            normalizedId.replace('_', ' '),
            normalizedId.replace(" ", ""),
            normalizedId.replace(" ", "-"),
            normalizedId.replace(" ", "_")
        ).filter { it != normalizedId }.distinct()

        for (variant in variations) {
            for (fileType in registered) {
                if (fileType.name.equals(variant, ignoreCase = true)) return fileType
                if (fileType.displayName.equals(variant, ignoreCase = true)) return fileType
                if (fileType is LanguageFileType && fileType.language.id.equals(variant, ignoreCase = true)) {
                    return fileType
                }
            }
        }

        return null
    }
}
