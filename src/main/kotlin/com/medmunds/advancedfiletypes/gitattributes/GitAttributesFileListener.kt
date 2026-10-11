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
            val projectGitAttrPath = "$projectDirPath/.gitattributes"

            var projectAffected = false
            for (event in events) {
                val path = event.path.replace('\\', '/')
                val oldPath = (event as? VFilePropertyChangeEvent)?.oldPath?.replace('\\', '/')
                    ?: (event as? VFileMoveEvent)?.oldPath?.replace('\\', '/')

                if (isMatchingPath(path, projectGitAttrPath) ||
                    (oldPath != null && isMatchingPath(oldPath, projectGitAttrPath))
                ) {
                    GitAttributesReader.clearCache(projectGitAttrPath)
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

    private fun isMatchingPath(eventPath: String, projectGitAttrPath: String): Boolean {
        return eventPath == projectGitAttrPath || projectGitAttrPath.startsWith("$eventPath/")
    }
}
