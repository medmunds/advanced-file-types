package com.medmunds.advancedfiletypes.gitattributes

import com.intellij.openapi.project.Project
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

                if (affectsProjectAttributes(path, projectDirPath, project) ||
                    (oldPath != null && affectsProjectAttributes(oldPath, projectDirPath, project))
                ) {
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
            AdvancedFileTypeSettings.notifyFileTypesChanged()
        }
    }

    private fun affectsProjectAttributes(path: String, projectDirPath: String, project: Project): Boolean {
        if (!path.startsWith(projectDirPath)) return false
        val relPath = path.removePrefix(projectDirPath).removePrefix("/")
        val fileName = path.substringAfterLast('/')

        if (fileName == ".gitattributes" || relPath == ".git/info/attributes" || relPath.endsWith("/.git/info/attributes")) {
            val service = project.getService(GitAttributesService::class.java)
            service?.clearCache()
            return true
        }

        if (relPath == ".git" || relPath.endsWith("/.git")) {
            val service = project.getService(GitAttributesService::class.java)
            return service?.onRepositoryStateChanged() ?: false
        }

        return false
    }
}
