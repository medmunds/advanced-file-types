package com.medmunds.advancedfiletypes

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.impl.FileTypeOverrider
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.VirtualFile
import com.medmunds.advancedfiletypes.settings.AdvancedFileTypeSettings

class AdvancedFileTypeOverrider : FileTypeOverrider {
    override fun getOverriddenFileType(file: VirtualFile): FileType? {
        if (file.isDirectory || !file.isValid) {
            return null
        }
        val project = ProjectLocator.getInstance().guessProjectForFile(file)
        if (project == null || project.isDisposed || !project.isInitialized || project.isDefault) {
            return null
        }
        val settings = AdvancedFileTypeSettings.getInstance(project)
        return settings.findMatchingFileType(file)
    }
}
