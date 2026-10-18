package com.medmunds.advancedfiletypes.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.util.xmlb.annotations.XMap
import com.medmunds.advancedfiletypes.ExternalLanguageResolver
import com.medmunds.advancedfiletypes.gitattributes.GitAttributesService
import com.medmunds.advancedfiletypes.model.FileTypeOverrideRule
import com.medmunds.advancedfiletypes.model.PathPatternMatcher
import com.medmunds.advancedfiletypes.model.PatternSyntax
import com.medmunds.advancedfiletypes.vscode.VsCodeSettingsReader

sealed class MatchResult {
    data class PluginRuleMatch(val rule: FileTypeOverrideRule, val fileType: FileType) : MatchResult()
    data class PluginRuleUnsupportedMatch(val rule: FileTypeOverrideRule, val fileTypeName: String) : MatchResult()
    data class VsCodeMatch(val pattern: String, val languageId: String, val fileType: FileType) : MatchResult()
    data class VsCodeUnsupportedMatch(
        val pattern: String,
        val languageId: String,
        val fileTypeName: String? = null
    ) : MatchResult()
    data class GitAttributesMatch(val pattern: String, val language: String, val fileType: FileType) : MatchResult()
    data class GitAttributesUnsupportedMatch(
        val pattern: String,
        val language: String,
        val fileTypeName: String? = null
    ) : MatchResult()
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

        var loadFromGitAttributes: Boolean = true

        @XMap(entryTagName = "mapping", keyAttributeName = "language", valueAttributeName = "fileTypeName")
        var customLanguageMappings: MutableMap<String, String> = mutableMapOf()
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

    fun isLoadFromGitAttributes(): Boolean {
        return myState.loadFromGitAttributes
    }

    fun setLoadFromGitAttributes(value: Boolean) {
        if (myState.loadFromGitAttributes != value) {
            myState.loadFromGitAttributes = value
            notifyFileTypesChanged()
        }
    }

    fun notifyFileTypesChanged() {
        notifyFileTypesChanged(project)
    }

    /**
     * Resolves the relative path of a virtual file with respect to the project directory or base path.
     */
    fun getProjectRelativePath(file: VirtualFile): String? {
        val projectDir = project.guessProjectDir()
        if (projectDir != null) {
            val rel = VfsUtilCore.getRelativePath(file, projectDir, '/')
            if (rel != null) return rel
        }

        val basePath = project.basePath
        if (!basePath.isNullOrBlank()) {
            val normalizedBasePath = basePath.replace('\\', '/').trimEnd('/')
            val filePath = file.path
            if (filePath.startsWith("$normalizedBasePath/")) {
                return filePath.removePrefix("$normalizedBasePath/")
            }
        }

        return null
    }

    /**
     * Resolves the relative path of a virtual file with respect to the project directory containing .gitattributes.
     */
    fun getProjectDirRelativePath(file: VirtualFile): String? {
        return getProjectRelativePath(file)
    }

    /**
     * Evaluates a relative path and filename against plugin rules, VS Code associations, and .gitattributes.
     */
    @JvmOverloads
    fun evaluateMatch(
        relativePath: String?,
        fileName: String,
        effectiveRules: List<FileTypeOverrideRule> = myState.rules,
        includeVsCode: Boolean = myState.loadFromVsCodeSettings,
        includeGitAttributes: Boolean = myState.loadFromGitAttributes,
        gitAttributesRelativePath: String? = relativePath
    ): MatchResult {
        val fileTypeManager = FileTypeManager.getInstance()

        // 1. Check plugin override rules first
        for (rule in effectiveRules) {
            if (rule.matches(relativePath, fileName)) {
                val fileType = fileTypeManager.findFileTypeByName(rule.fileTypeName)
                return if (fileType != null) {
                    MatchResult.PluginRuleMatch(rule, fileType)
                } else {
                    MatchResult.PluginRuleUnsupportedMatch(rule, rule.fileTypeName)
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
                    return when (val resolution = ExternalLanguageResolver.resolve(
                        association.languageId,
                        myState.customLanguageMappings
                    )) {
                        is ExternalLanguageResolver.Resolution.Resolved ->
                            MatchResult.VsCodeMatch(association.pattern, association.languageId, resolution.fileType)
                        is ExternalLanguageResolver.Resolution.Unsupported ->
                            MatchResult.VsCodeUnsupportedMatch(
                                association.pattern,
                                association.languageId,
                                resolution.fileTypeName
                            )
                        ExternalLanguageResolver.Resolution.Unmapped ->
                            MatchResult.VsCodeUnsupportedMatch(association.pattern, association.languageId)
                    }
                }
            }
        }

        // 3. Check .gitattributes linguist-language if enabled
        if (includeGitAttributes && gitAttributesRelativePath != null) {
            val service = project.getService(GitAttributesService::class.java)
            val language = service?.getLanguage(gitAttributesRelativePath)
            if (language != null) {
                return when (val resolution = ExternalLanguageResolver.resolve(
                    language,
                    myState.customLanguageMappings
                )) {
                    is ExternalLanguageResolver.Resolution.Resolved ->
                        MatchResult.GitAttributesMatch(gitAttributesRelativePath, language, resolution.fileType)
                    is ExternalLanguageResolver.Resolution.Unsupported ->
                        MatchResult.GitAttributesUnsupportedMatch(
                            gitAttributesRelativePath,
                            language,
                            resolution.fileTypeName
                        )
                    ExternalLanguageResolver.Resolution.Unmapped ->
                        MatchResult.GitAttributesUnsupportedMatch(gitAttributesRelativePath, language)
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
        val gitAttributesRelativePath = getProjectDirRelativePath(file)
        val fileName = file.name
        return when (val match = evaluateMatch(
            relativePath = relativePath,
            fileName = fileName,
            effectiveRules = myState.rules,
            includeVsCode = myState.loadFromVsCodeSettings,
            includeGitAttributes = myState.loadFromGitAttributes,
            gitAttributesRelativePath = gitAttributesRelativePath
        )) {
            is MatchResult.PluginRuleMatch -> match.fileType
            is MatchResult.VsCodeMatch -> match.fileType
            is MatchResult.GitAttributesMatch -> match.fileType
            is MatchResult.PluginRuleUnsupportedMatch,
            is MatchResult.VsCodeUnsupportedMatch,
            is MatchResult.GitAttributesUnsupportedMatch,
            MatchResult.NoMatch -> null
        }
    }

    companion object {
        fun getInstance(project: Project): AdvancedFileTypeSettings {
            return project.getService(AdvancedFileTypeSettings::class.java)
        }

        fun notifyFileTypesChanged(project: Project? = null) {
            if (project != null && (project.isDisposed || !project.isInitialized)) return
            val app = ApplicationManager.getApplication() ?: return
            val isExpired = Condition<Any?> {
                project != null && (project.isDisposed || !project.isInitialized)
            }
            val runnable = Runnable {
                if (isExpired.value(null)) return@Runnable
                val fileTypeManager = FileTypeManager.getInstance()
                if (fileTypeManager is FileTypeManagerEx) {
                    fileTypeManager.fireFileTypesChanged()
                }
            }
            if (app.isDispatchThread && TransactionGuard.getInstance().isWritingAllowed) {
                runnable.run()
            } else {
                app.invokeLater(runnable, ModalityState.nonModal(), isExpired)
            }
        }
    }
}
