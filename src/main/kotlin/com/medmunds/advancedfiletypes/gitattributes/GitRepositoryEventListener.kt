package com.medmunds.advancedfiletypes.gitattributes

import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.openapi.project.Project
import com.medmunds.advancedfiletypes.settings.AdvancedFileTypeSettings
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener

class GitRepositoryEventListener(private val project: Project) : VcsRepositoryMappingListener, GitRepositoryChangeListener {

    override fun mappingChanged() {
        onRepositoryStateChanged()
    }

    override fun repositoryChanged(repository: GitRepository) {
        onRepositoryStateChanged()
    }

    private fun onRepositoryStateChanged() {
        if (project.isDisposed || !project.isInitialized) return
        val service = project.getService(GitAttributesService::class.java)
        service?.clearCache()
        AdvancedFileTypeSettings.notifyFileTypesChanged(project)
    }
}
