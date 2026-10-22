package com.medmunds.advancedfiletypes.vscode

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.medmunds.advancedfiletypes.settings.AdvancedFileTypeSettings

class VsCodeSettingsFileListener : BulkFileListener {
    override fun after(events: List<VFileEvent>) {
        var relevantChange = false
        for (event in events) {
            val path = event.path.replace('\\', '/')
            if (path.endsWith("/.vscode/settings.json")) {
                if (LOG.isDebugEnabled) {
                    LOG.debug("Detected modification to .vscode/settings.json at '$path'")
                }
                VsCodeSettingsReader.clearCache(path)
                relevantChange = true
            }
        }

        if (relevantChange) {
            val openProjects = ProjectManager.getInstance().openProjects
            for (project in openProjects) {
                if (!project.isDisposed && project.isInitialized && !project.isDefault) {
                    val settings = AdvancedFileTypeSettings.getInstance(project)
                    if (settings.isLoadFromVsCodeSettings()) {
                        if (LOG.isDebugEnabled) {
                            LOG.debug("Notifying file type changes for project '${project.name}' following .vscode/settings.json update")
                        }
                        settings.notifyFileTypesChanged()
                    }
                }
            }
        }
    }

    companion object {
        private val LOG = Logger.getInstance(VsCodeSettingsFileListener::class.java)
    }
}
