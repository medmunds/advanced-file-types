package com.medmunds.advancedfiletypes.vscode

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
                        settings.notifyFileTypesChanged()
                    }
                }
            }
        }
    }
}
