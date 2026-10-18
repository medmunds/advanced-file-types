package com.medmunds.advancedfiletypes

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.impl.FileTypeOverrider
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.medmunds.advancedfiletypes.settings.AdvancedFileTypeSettings

class AdvancedFileTypeOverrider : FileTypeOverrider {

    private val isResolving = ThreadLocal.withInitial { false }

    override fun getOverriddenFileType(file: VirtualFile): FileType? {
        if (file.isDirectory || !file.isValid) {
            return null
        }
        if (isResolving.get()) {
            return null
        }
        return try {
            val project = findProjectForFile(file)
            if (project == null || project.isDisposed || !project.isInitialized || project.isDefault) {
                return null
            }

            isResolving.set(true)
            try {
                val settings = AdvancedFileTypeSettings.getInstance(project)
                settings.findMatchingFileType(file)
            } finally {
                isResolving.set(false)
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Resolves the open project containing the virtual file without querying WorkspaceFileIndex
     * or ProjectLocator to prevent lock contention on VFS threads.
     */
    internal fun findProjectForFile(file: VirtualFile): Project? {
        val openProjects = try {
            ProjectManager.getInstance().openProjects
        } catch (_: Throwable) {
            return null
        }
        if (openProjects.isEmpty()) return null

        var bestProject: Project? = null
        var bestLength = -1

        for (project in openProjects) {
            if (project.isDisposed || !project.isInitialized || project.isDefault) continue

            val projectDir = project.guessProjectDir()
            if (projectDir != null && VfsUtilCore.isAncestor(projectDir, file, false)) {
                val pathLength = projectDir.path.length
                if (pathLength > bestLength) {
                    bestLength = pathLength
                    bestProject = project
                }
                continue
            }

            val basePath = project.basePath
            if (!basePath.isNullOrBlank()) {
                val normalizedBase = basePath.replace('\\', '/').trimEnd('/')
                if (file.path.startsWith("$normalizedBase/")) {
                    val pathLength = normalizedBase.length
                    if (pathLength > bestLength) {
                        bestLength = pathLength
                        bestProject = project
                    }
                }
            }
        }

        return bestProject
    }
}
