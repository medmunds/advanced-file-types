package com.medmunds.advancedfiletypes.ui

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.ComboboxSpeedSearch
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.medmunds.advancedfiletypes.AdvancedFileTypesBundle
import com.medmunds.advancedfiletypes.model.FileTypeOverrideRule
import com.medmunds.advancedfiletypes.model.PathPatternMatcher
import com.medmunds.advancedfiletypes.model.PatternSyntax
import javax.swing.JComponent

class AddEditRuleDialog(
    project: Project?,
    private val existingRule: FileTypeOverrideRule?
) : DialogWrapper(project, true) {

    private val patternField = JBTextField(existingRule?.pattern ?: "", 30)
    private val syntaxCombo = ComboBox(PatternSyntax.entries.toTypedArray()).apply {
        renderer = listCellRenderer {
            value?.let { text(it.displayName) }
        }
        selectedItem = existingRule?.syntax ?: PatternSyntax.GLOB
    }
    private val availableFileTypes: List<FileType> = FileTypeManager.getInstance().registeredFileTypes
        .filter { it != UnknownFileType.INSTANCE && it.name.isNotEmpty() }
        .sortedBy { it.displayName.ifBlank { it.name }.lowercase() }

    internal val fileTypeCombo = ComboBox(availableFileTypes.toTypedArray()).apply {
        renderer = listCellRenderer {
            value?.let { fileType ->
                FileTypeDisplayHelper.iconFor(fileType)?.let { icon(it) }
                text(FileTypeDisplayHelper.displayText(fileType))
            }
        }
        ComboboxSpeedSearch.installSpeedSearch(this) { fileType ->
            fileType?.let { FileTypeDisplayHelper.displayText(it) } ?: ""
        }
        val targetName = existingRule?.fileTypeName
        if (targetName != null) {
            val matched = availableFileTypes.find { it.name.equals(targetName, ignoreCase = true) }
            if (matched != null) {
                selectedItem = matched
            }
        }
    }

    init {
        title = if (existingRule == null) {
            AdvancedFileTypesBundle.message("dialog.title.add")
        } else {
            AdvancedFileTypesBundle.message("dialog.title.edit")
        }
        init()
    }

    override fun createCenterPanel(): JComponent {
        val helpText = AdvancedFileTypesBundle.message("dialog.pattern.help")

        return panel {
            row(AdvancedFileTypesBundle.message("dialog.pattern.label")) {
                cell(patternField)
                    .focused()
                contextHelp(helpText)
            }
            row(AdvancedFileTypesBundle.message("dialog.syntax.label")) {
                cell(syntaxCombo)
            }
            row(AdvancedFileTypesBundle.message("dialog.fileType.label")) {
                cell(fileTypeCombo)
            }
        }
    }

    override fun doValidate(): ValidationInfo? {
        val pattern = patternField.text.trim()
        if (pattern.isEmpty()) {
            return ValidationInfo(AdvancedFileTypesBundle.message("dialog.validation.pattern.empty"), patternField)
        }
        val syntax = syntaxCombo.selectedItem as? PatternSyntax ?: PatternSyntax.GLOB
        try {
            PathPatternMatcher.compile(pattern, syntax)
        } catch (e: Exception) {
            return ValidationInfo(AdvancedFileTypesBundle.message("dialog.validation.pattern.invalid", e.message ?: ""), patternField)
        }
        if (fileTypeCombo.selectedItem == null) {
            return ValidationInfo(AdvancedFileTypesBundle.message("dialog.validation.fileType.empty"), fileTypeCombo)
        }
        return null
    }

    fun getRule(): FileTypeOverrideRule {
        val pattern = patternField.text.trim()
        val syntax = syntaxCombo.selectedItem as? PatternSyntax ?: PatternSyntax.GLOB
        val fileType = fileTypeCombo.selectedItem as FileType
        return FileTypeOverrideRule(
            pattern = pattern,
            fileTypeName = fileType.name,
            syntax = syntax
        )
    }
}
