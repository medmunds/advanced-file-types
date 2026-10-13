package com.medmunds.advancedfiletypes.gitattributes

import com.intellij.openapi.project.Project
import com.medmunds.advancedfiletypes.settings.AdvancedFileTypeSettings
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener

class GitRepositoryEventListener(private val project: Project) : GitRepositoryChangeListener {

    override fun repositoryChanged(repository: GitRepository) {
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
