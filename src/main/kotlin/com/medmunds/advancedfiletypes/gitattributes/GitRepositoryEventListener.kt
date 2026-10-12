package com.medmunds.advancedfiletypes.gitattributes

import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.openapi.project.Project
import com.medmunds.advancedfiletypes.settings.AdvancedFileTypeSettings

class GitRepositoryEventListener(private val project: Project) : VcsRepositoryMappingListener {

    override fun mappingChanged() {
        if (project.isDisposed || !project.isInitialized) return
        val service = project.getService(GitAttributesService::class.java) ?: return
        val availabilityChanged = service.onRepositoryStateChanged()
        if (availabilityChanged) {
            val settings = AdvancedFileTypeSettings.getInstance(project)
            if (settings.isLoadFromGitAttributes()) {
                AdvancedFileTypeSettings.notifyFileTypesChanged(project)
            }
        }
    }
}
