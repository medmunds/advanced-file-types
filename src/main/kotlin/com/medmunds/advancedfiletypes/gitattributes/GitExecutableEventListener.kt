package com.medmunds.advancedfiletypes.gitattributes

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import git4idea.config.GitExecutableListener

/**
 * Application listener for Git executable changes (e.g. background detection completion
 * or Git executable path modifications in Settings).
 */
class GitExecutableEventListener : GitExecutableListener {

    override fun executableChanged() {
        val openProjects = try {
            ProjectManager.getInstance().openProjects
        } catch (_: Exception) {
            return
        }

        if (LOG.isDebugEnabled) {
            LOG.debug("Git executable changed or detected; updating project GitAttributesServices")
        }

        for (project in openProjects) {
            if (project.isDisposed || !project.isInitialized) continue
            val service = project.getService(GitAttributesService::class.java) ?: continue
            service.onGitExecutableChanged()
        }
    }

    companion object {
        private val LOG = Logger.getInstance(GitExecutableEventListener::class.java)
    }
}
