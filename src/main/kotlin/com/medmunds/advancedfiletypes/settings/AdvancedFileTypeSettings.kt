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
import com.medmunds.advancedfiletypes.model.FileTypeOverrideRule

@Service(Service.Level.PROJECT)
@State(
    name = "AdvancedFileTypeSettings",
    storages = [Storage("advancedFileTypes.xml")]
)
class AdvancedFileTypeSettings(private val project: Project) : PersistentStateComponent<AdvancedFileTypeSettings.State> {

    class State {
        @XCollection(style = XCollection.Style.v2)
        var rules: MutableList<FileTypeOverrideRule> = mutableListOf()
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

        if (!project.isDisposed) {
            val contentRoot = ProjectFileIndex.getInstance(project).getContentRootForFile(file)
            if (contentRoot != null) {
                val rel = VfsUtilCore.getRelativePath(file, contentRoot, '/')
                if (rel != null) return rel
            }
        }

        return null
    }

    /**
     * Finds the matching rule and resulting FileType for a given relative path and filename.
     */
    fun findMatchingRule(relativePath: String?, fileName: String): Pair<FileTypeOverrideRule, FileType>? {
        val fileTypeManager = FileTypeManager.getInstance()
        for (rule in myState.rules) {
            if (rule.isEnabled && rule.matches(relativePath, fileName)) {
                val fileType = fileTypeManager.findFileTypeByName(rule.fileTypeName)
                if (fileType != null) {
                    return Pair(rule, fileType)
                }
            }
        }
        return null
    }

    /**
     * Finds the overridden FileType for a virtual file, or null if no rule matches.
     */
    fun findMatchingFileType(file: VirtualFile): FileType? {
        val relativePath = getProjectRelativePath(file)
        val match = findMatchingRule(relativePath, file.name)
        return match?.second
    }

    companion object {
        fun getInstance(project: Project): AdvancedFileTypeSettings {
            return project.getService(AdvancedFileTypeSettings::class.java)
        }
    }
}
