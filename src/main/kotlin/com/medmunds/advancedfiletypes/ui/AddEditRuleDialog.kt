package com.medmunds.advancedfiletypes.ui

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.ComboboxSpeedSearch
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import com.medmunds.advancedfiletypes.MyMessageBundle
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
        renderer = SimpleListCellRenderer.create { label, value, _ ->
            if (value != null) {
                label.text = value.displayName
            }
        }
        selectedItem = existingRule?.syntax ?: PatternSyntax.GLOB
    }
    private val availableFileTypes: List<FileType> = FileTypeManager.getInstance().registeredFileTypes
        .filter { it != UnknownFileType.INSTANCE && it.name.isNotEmpty() }
        .sortedBy { it.displayName.ifBlank { it.name }.lowercase() }

    internal val fileTypeCombo = ComboBox(availableFileTypes.toTypedArray()).apply {
        renderer = SimpleListCellRenderer.create { label, value, _ ->
            if (value != null) {
                label.icon = value.icon
                val desc = value.description
                label.text = if (desc.isNotBlank()) {
                    MyMessageBundle.message("fileType.display.withDescription", value.displayName, desc)
                } else {
                    value.displayName
                }
            }
        }
        ComboboxSpeedSearch.installSpeedSearch(this) { fileType ->
            val desc = fileType?.description ?: ""
            if (desc.isNotBlank()) {
                MyMessageBundle.message("fileType.display.withDescription", fileType?.displayName ?: "", desc)
            } else {
                fileType?.displayName ?: ""
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

    init {
        title = if (existingRule == null) {
            MyMessageBundle.message("dialog.title.add")
        } else {
            MyMessageBundle.message("dialog.title.edit")
        }
        init()
    }

    override fun createCenterPanel(): JComponent {
        val helpText = MyMessageBundle.message("dialog.pattern.help")

        return panel {
            row(MyMessageBundle.message("dialog.pattern.label")) {
                cell(patternField)
                    .focused()
                contextHelp(helpText)
            }
            row(MyMessageBundle.message("dialog.syntax.label")) {
                cell(syntaxCombo)
            }
            row(MyMessageBundle.message("dialog.fileType.label")) {
                cell(fileTypeCombo)
            }
        }
    }

    override fun doValidate(): ValidationInfo? {
        val pattern = patternField.text.trim()
        if (pattern.isEmpty()) {
            return ValidationInfo(MyMessageBundle.message("dialog.validation.pattern.empty"), patternField)
        }
        val syntax = syntaxCombo.selectedItem as? PatternSyntax ?: PatternSyntax.GLOB
        try {
            PathPatternMatcher.compile(pattern, syntax)
        } catch (e: Exception) {
            return ValidationInfo(MyMessageBundle.message("dialog.validation.pattern.invalid", e.message ?: ""), patternField)
        }
        if (fileTypeCombo.selectedItem == null) {
            return ValidationInfo(MyMessageBundle.message("dialog.validation.fileType.empty"), fileTypeCombo)
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
