package com.medmunds.advancedfiletypes.gitattributes

import com.intellij.openapi.diagnostic.Logger
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
                    if (LOG.isDebugEnabled) {
                        LOG.debug("Git attributes change detected for project '${project.name}'")
                    }
                    shouldNotify = true
                }
            }
        }

        if (shouldNotify) {
            if (LOG.isDebugEnabled) {
                LOG.debug("Notifying file type changes following .gitattributes modification")
            }
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

    companion object {
        private val LOG = Logger.getInstance(GitAttributesFileListener::class.java)
    }
}
