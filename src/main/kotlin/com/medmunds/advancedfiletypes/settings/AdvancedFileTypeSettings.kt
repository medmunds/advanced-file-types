package com.medmunds.advancedfiletypes.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.util.xmlb.annotations.XMap
import com.medmunds.advancedfiletypes.model.FileTypeOverrideRule
import com.medmunds.advancedfiletypes.model.PathPatternMatcher
import com.medmunds.advancedfiletypes.model.PatternSyntax
import com.medmunds.advancedfiletypes.vscode.VsCodeLanguageResolver
import com.medmunds.advancedfiletypes.vscode.VsCodeSettingsReader

sealed class MatchResult {
    data class PluginRuleMatch(val rule: FileTypeOverrideRule, val fileType: FileType) : MatchResult()
    data class VsCodeMatch(val pattern: String, val languageId: String, val fileType: FileType) : MatchResult()
    data class VsCodeUnsupportedMatch(val pattern: String, val languageId: String) : MatchResult()
    object NoMatch : MatchResult()
}

@Service(Service.Level.PROJECT)
@State(
    name = "AdvancedFileTypeSettings",
    storages = [Storage("advancedFileTypes.xml")]
)
class AdvancedFileTypeSettings(private val project: Project) : PersistentStateComponent<AdvancedFileTypeSettings.State> {

    class State {
        @XCollection(style = XCollection.Style.v2)
        var rules: MutableList<FileTypeOverrideRule> = mutableListOf()

        var loadFromVsCodeSettings: Boolean = true

        @XMap(entryTagName = "mapping", keyAttributeName = "language", valueAttributeName = "fileType")
        var customVsCodeLanguageMappings: MutableMap<String, String> = mutableMapOf()
    }

    private var myState = State()

    override fun getState(): State {
        return myState
    }

    override fun loadState(state: State) {
        myState = state
    }

    fun getRules(): List<FileTypeOverrideRule> {
        return myState.rules.toList()
    }

    fun setRules(newRules: List<FileTypeOverrideRule>) {
        myState.rules = newRules.map { it.copy() }.toMutableList()
        notifyFileTypesChanged()
    }

    fun isLoadFromVsCodeSettings(): Boolean {
        return myState.loadFromVsCodeSettings
    }

    fun setLoadFromVsCodeSettings(value: Boolean) {
        if (myState.loadFromVsCodeSettings != value) {
            myState.loadFromVsCodeSettings = value
            notifyFileTypesChanged()
        }
    }

    fun notifyFileTypesChanged() {
        if (!project.isDisposed) {
            val fileTypeManager = FileTypeManager.getInstance()
            if (fileTypeManager is FileTypeManagerEx) {
                fileTypeManager.fireFileTypesChanged()
            }
        }
    }

    /**
     * Resolves the relative path of a virtual file with respect to the project or its content roots.
     */
    fun getProjectRelativePath(file: VirtualFile): String? {
        val projectDir = project.guessProjectDir()
        if (projectDir != null) {
            val rel = VfsUtilCore.getRelativePath(file, projectDir, '/')
            if (rel != null) return rel
        }

        if (!project.isDisposed && project.isInitialized && !project.isDefault) {
            try {
                val contentRoot = ProjectFileIndex.getInstance(project).getContentRootForFile(file)
                if (contentRoot != null) {
                    val rel = VfsUtilCore.getRelativePath(file, contentRoot, '/')
                    if (rel != null) return rel
                }
            } catch (_: Throwable) {
                // Ignore index/model errors during early startup or background checks
            }
        }

        return null
    }

    /**
     * Evaluates a relative path and filename against plugin rules and (if enabled) VS Code associations.
     */
    fun evaluateMatch(
        relativePath: String?,
        fileName: String,
        effectiveRules: List<FileTypeOverrideRule> = myState.rules,
        includeVsCode: Boolean = myState.loadFromVsCodeSettings
    ): MatchResult {
        val fileTypeManager = FileTypeManager.getInstance()

        // 1. Check plugin override rules first
        for (rule in effectiveRules) {
            if (rule.matches(relativePath, fileName)) {
                val fileType = fileTypeManager.findFileTypeByName(rule.fileTypeName)
                if (fileType != null) {
                    return MatchResult.PluginRuleMatch(rule, fileType)
                }
            }
        }

        // 2. Check VS Code settings.json (files.associations) if enabled
        if (includeVsCode) {
            val vsCodeAssociations = VsCodeSettingsReader.readAssociations(project)
            for (association in vsCodeAssociations) {
                val pattern = try {
                    PathPatternMatcher.compile(association.pattern, PatternSyntax.GLOB)
                } catch (_: Exception) {
                    null
                }
                if (pattern != null && PathPatternMatcher.matches(pattern, relativePath, fileName)) {
                    val fileType = VsCodeLanguageResolver.resolveFileType(
                        association.languageId,
                        myState.customVsCodeLanguageMappings
                    )
                    if (fileType != null) {
                        return MatchResult.VsCodeMatch(association.pattern, association.languageId, fileType)
                    } else {
                        return MatchResult.VsCodeUnsupportedMatch(association.pattern, association.languageId)
                    }
                }
            }
        }

        return MatchResult.NoMatch
    }

    /**
     * Finds the overridden FileType for a virtual file, or null if no rule matches.
     */
    fun findMatchingFileType(file: VirtualFile): FileType? {
        val relativePath = getProjectRelativePath(file)
        val fileName = file.name
        val fileTypeManager = FileTypeManager.getInstance()

        // 1. Plugin rules take first precedence
        for (rule in myState.rules) {
            if (rule.matches(relativePath, fileName)) {
                val fileType = fileTypeManager.findFileTypeByName(rule.fileTypeName)
                if (fileType != null) {
                    return fileType
                }
            }
        }

        // 2. VS Code settings.json associations (fallback, skipping unsupported types)
        if (myState.loadFromVsCodeSettings) {
            val vsCodeAssociations = VsCodeSettingsReader.readAssociations(project)
            for (association in vsCodeAssociations) {
                val pattern = try {
                    PathPatternMatcher.compile(association.pattern, PatternSyntax.GLOB)
                } catch (_: Exception) {
                    null
                }
                if (pattern != null && PathPatternMatcher.matches(pattern, relativePath, fileName)) {
                    val fileType = VsCodeLanguageResolver.resolveFileType(
                        association.languageId,
                        myState.customVsCodeLanguageMappings
                    )
                    if (fileType != null) {
                        return fileType
                    }
                }
            }
        }

        return null
    }

    companion object {
        fun getInstance(project: Project): AdvancedFileTypeSettings {
            return project.getService(AdvancedFileTypeSettings::class.java)
        }
    }
}
