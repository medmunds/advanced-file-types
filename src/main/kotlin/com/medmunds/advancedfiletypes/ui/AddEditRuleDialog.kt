package com.medmunds.advancedfiletypes.ui

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
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
        selectedItem = existingRule?.syntax ?: PatternSyntax.GLOB
    }
    private val availableFileTypes: List<FileType> = FileTypeManager.getInstance().registeredFileTypes
        .filter { it != UnknownFileType.INSTANCE && it.name.isNotEmpty() }
        .sortedBy { it.displayName.ifBlank { it.name }.lowercase() }

    private val fileTypeCombo = ComboBox(availableFileTypes.toTypedArray()).apply {
        renderer = SimpleListCellRenderer.create { label, value, _ ->
            if (value != null) {
                label.icon = value.icon
                val desc = value.description
                label.text = if (desc.isNotBlank()) "${value.displayName} ($desc)" else value.displayName
            }
        }
        val targetName = existingRule?.fileTypeName
        if (targetName != null) {
            val matched = availableFileTypes.find { it.name.equals(targetName, ignoreCase = true) }
            if (matched != null) {
                selectedItem = matched
            }
        }
    }

    private val enabledCheckBox = JBCheckBox("Enabled", existingRule?.isEnabled ?: true)

    init {
        title = if (existingRule == null) "Add File Type Override Rule" else "Edit File Type Override Rule"
        init()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row("Pattern:") {
                cell(patternField)
                    .comment("E.g. docs/**/*.txt, *.custom, or src/templates/*")
                    .focused()
            }
            row("Syntax:") {
                cell(syntaxCombo)
            }
            row("File Type:") {
                cell(fileTypeCombo)
            }
            row {
                cell(enabledCheckBox)
            }
        }
    }

    override fun doValidate(): ValidationInfo? {
        val pattern = patternField.text.trim()
        if (pattern.isEmpty()) {
            return ValidationInfo("Pattern cannot be empty", patternField)
        }
        val syntax = syntaxCombo.selectedItem as? PatternSyntax ?: PatternSyntax.GLOB
        try {
            PathPatternMatcher.compile(pattern, syntax)
        } catch (e: Exception) {
            return ValidationInfo("Invalid pattern syntax: ${e.message}", patternField)
        }
        if (fileTypeCombo.selectedItem == null) {
            return ValidationInfo("Please select a file type", fileTypeCombo)
        }
        return null
    }

    fun getRule(): FileTypeOverrideRule {
        val pattern = patternField.text.trim()
        val syntax = syntaxCombo.selectedItem as? PatternSyntax ?: PatternSyntax.GLOB
        val fileType = fileTypeCombo.selectedItem as FileType
        val isEnabled = enabledCheckBox.isSelected
        return FileTypeOverrideRule(
            pattern = pattern,
            fileTypeName = fileType.name,
            syntax = syntax,
            isEnabled = isEnabled
        )
    }
}
