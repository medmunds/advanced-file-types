package com.medmunds.advancedfiletypes.gitattributes

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.medmunds.advancedfiletypes.settings.AdvancedFileTypeSettings

class GitAttributesFileListener : BulkFileListener {
    override fun after(events: List<VFileEvent>) {
        val openProjects = ProjectManager.getInstance().openProjects
        if (openProjects.isEmpty()) return

        var shouldNotify = false

        for (project in openProjects) {
            if (project.isDisposed || !project.isInitialized || project.isDefault) {
                continue
            }

            val projectDir = project.guessProjectDir() ?: continue
            val projectDirPath = projectDir.path.replace('\\', '/')

            var projectAffected = false
            for (event in events) {
                val path = event.path.replace('\\', '/')
                val oldPath = (event as? VFilePropertyChangeEvent)?.oldPath?.replace('\\', '/')
                    ?: (event as? VFileMoveEvent)?.oldPath?.replace('\\', '/')

                if (isGitAttributesEvent(path, projectDirPath) ||
                    (oldPath != null && isGitAttributesEvent(oldPath, projectDirPath))
                ) {
                    val service = project.getService(GitAttributesService::class.java)
                    service?.clearCache()
                    projectAffected = true
                    break
                }
            }

            if (projectAffected) {
                val settings = AdvancedFileTypeSettings.getInstance(project)
                if (settings.isLoadFromGitAttributes()) {
                    shouldNotify = true
                }
            }
        }

        if (shouldNotify) {
            val fileTypeManager = FileTypeManager.getInstance()
            if (fileTypeManager is FileTypeManagerEx) {
                fileTypeManager.fireFileTypesChanged()
            }
        }
    }

    private fun isGitAttributesEvent(path: String, projectDirPath: String): Boolean {
        if (!path.startsWith(projectDirPath)) return false
        val fileName = path.substringAfterLast('/')
        return fileName == ".gitattributes" || path.endsWith("/.git/info/attributes")
    }
}
