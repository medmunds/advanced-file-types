package com.medmunds.advancedfiletypes.ui

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.EditableModel
import com.medmunds.advancedfiletypes.AdvancedFileTypesBundle
import com.medmunds.advancedfiletypes.gitattributes.GitAttributesService
import com.medmunds.advancedfiletypes.help.AdvancedFileTypeWebHelpProvider
import com.medmunds.advancedfiletypes.model.FileTypeOverrideRule
import com.medmunds.advancedfiletypes.model.PatternSyntax
import com.medmunds.advancedfiletypes.settings.AdvancedFileTypeSettings
import com.medmunds.advancedfiletypes.settings.MatchResult
import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

class AdvancedFileTypeConfigurable(private val project: Project) : SearchableConfigurable {

    private companion object {
        // NBSP reserves height in the DSL label/comment to prevent layout shifts when clearing text
        private const val NBSP = "\u00A0"
    }

    private val rules = mutableListOf<FileTypeOverrideRule>()
    private val tableModel = RuleTableModel(rules)
    internal val table = JBTable(tableModel)
    internal val includeVsCodeCheckBox = JBCheckBox(AdvancedFileTypesBundle.message("settings.includeVsCode"))
    internal val includeGitAttributesCheckBox = JBCheckBox(AdvancedFileTypesBundle.message("settings.includeGitAttributes"))

    internal val testPathField = JBTextField(30)
    internal lateinit var testResultCell: Cell<JLabel>

    override fun getId(): String = "com.medmunds.advancedfiletypes.settings"

    override fun getDisplayName(): String = AdvancedFileTypesBundle.message("settings.displayName")

    override fun getHelpTopic(): String = AdvancedFileTypeWebHelpProvider.HELP_TOPIC

    override fun createComponent(): JComponent {
        setupTable()

        val tablePanel = ToolbarDecorator.createDecorator(table)
            .setAddAction {
                val dialog = AddEditRuleDialog(project, null)
                if (dialog.showAndGet()) {
                    rules.add(dialog.getRule())
                    tableModel.fireTableRowsInserted(rules.size - 1, rules.size - 1)
                    updateTestResult()
                }
            }
            .setEditAction {
                val selectedRow = table.selectedRow
                if (selectedRow in rules.indices) {
                    val dialog = AddEditRuleDialog(project, rules[selectedRow])
                    if (dialog.showAndGet()) {
                        rules[selectedRow] = dialog.getRule()
                        tableModel.fireTableRowsUpdated(selectedRow, selectedRow)
                        updateTestResult()
                    }
                }
            }
            .setRemoveAction {
                val selectedRow = table.selectedRow
                if (selectedRow in rules.indices) {
                    rules.removeAt(selectedRow)
                    tableModel.fireTableRowsDeleted(selectedRow, selectedRow)
                    updateTestResult()
                }
            }
            .createPanel()

        includeVsCodeCheckBox.addActionListener {
            updateTestResult()
        }

        includeGitAttributesCheckBox.addActionListener {
            updateTestResult()
        }

        testPathField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = updateTestResult()
            override fun removeUpdate(e: DocumentEvent?) = updateTestResult()
            override fun changedUpdate(e: DocumentEvent?) = updateTestResult()
        })

        tableModel.addTableModelListener {
            updateTestResult()
        }

        val component = panel {
            row {
                label(AdvancedFileTypesBundle.message("settings.description"))
                    .comment(AdvancedFileTypesBundle.message("settings.explanation"))
            }
            row {
                cell(tablePanel).align(Align.FILL)
            }.resizableRow()
            row {
                cell(includeVsCodeCheckBox)
            }
            row {
                val gitCell = cell(includeGitAttributesCheckBox)
                val isGitAvailable = GitAttributesService.getInstance(project).isGitAvailable()
                if (!isGitAvailable) {
                    gitCell.comment(AdvancedFileTypesBundle.message("settings.includeGitAttributes.unavailable"))
                }
            }
            group(AdvancedFileTypesBundle.message("settings.test.group")) {
                row(AdvancedFileTypesBundle.message("settings.test.path.label")) {
                    cell(testPathField)
                        .comment(AdvancedFileTypesBundle.message("settings.test.path.comment"))
                }
                row(AdvancedFileTypesBundle.message("settings.test.result.label")) {
                    testResultCell = label(NBSP)
                        .comment(NBSP)
                }
            }
        }
        reset()
        return component
    }

    private fun setupTable() {
        table.setRowSelectionAllowed(true)
        table.setShowGrid(false)

        table.columnModel.getColumn(0).apply {
            preferredWidth = 250
            headerValue = AdvancedFileTypesBundle.message("settings.table.column.pattern")
        }
        table.columnModel.getColumn(1).apply {
            preferredWidth = 80
            maxWidth = 100
            headerValue = AdvancedFileTypesBundle.message("settings.table.column.syntax")
            cellRenderer = object : DefaultTableCellRenderer() {
                override fun getTableCellRendererComponent(
                    table: JTable?,
                    value: Any?,
                    isSelected: Boolean,
                    hasFocus: Boolean,
                    row: Int,
                    column: Int
                ): Component {
                    val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
                    val syntax = value as? PatternSyntax
                    c.text = syntax?.displayName ?: (value?.toString() ?: "")
                    return c
                }
            }
        }
        table.columnModel.getColumn(2).apply {
            preferredWidth = 200
            headerValue = AdvancedFileTypesBundle.message("settings.table.column.fileType")
            cellRenderer = object : DefaultTableCellRenderer() {
                override fun getTableCellRendererComponent(
                    table: JTable?,
                    value: Any?,
                    isSelected: Boolean,
                    hasFocus: Boolean,
                    row: Int,
                    column: Int
                ): Component {
                    val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
                    val typeName = value as? String ?: ""
                    c.icon = FileTypeDisplayHelper.iconForName(typeName)
                    c.text = FileTypeDisplayHelper.displayTextForName(typeName, UnknownFileTypeOrigin.NO_IDE_SUPPORT)
                    return c
                }
            }
        }

        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean {
                val row = table.rowAtPoint(event.point)
                if (row in rules.indices) {
                    val dialog = AddEditRuleDialog(project, rules[row])
                    if (dialog.showAndGet()) {
                        rules[row] = dialog.getRule()
                        tableModel.fireTableRowsUpdated(row, row)
                        updateTestResult()
                        return true
                    }
                }
                return false
            }
        }.installOn(table)
    }

    private fun setResult(typeText: String, typeIcon: javax.swing.Icon? = null, detailText: String = "") {
        // Use NBSP for empty text to reserve height and prevent layout shifts
        testResultCell.component.text = if (typeText.isBlank()) NBSP else typeText
        testResultCell.component.icon = typeIcon // TODO: somehow force height if null to avoid layout shifts
        testResultCell.comment?.text = if (detailText.isBlank()) NBSP else detailText
    }

    internal fun updateTestResult() {
        table.clearSelection()
        val path = testPathField.text.trim()
        if (path.isEmpty()) {
            setResult("")
            return
        }

        val fileName = path.substringAfterLast('/').substringAfterLast('\\')
        val settings = AdvancedFileTypeSettings.getInstance(project)
        val matchResult = settings.evaluateMatch(
            relativePath = path,
            fileName = fileName,
            effectiveRules = rules,
            includeVsCode = includeVsCodeCheckBox.isSelected,
            includeGitAttributes = includeGitAttributesCheckBox.isSelected
        )

        when (matchResult) {
            is MatchResult.PluginRuleMatch -> {
                val modelRow = rules.indexOf(matchResult.rule)
                if (modelRow >= 0) {
                    val viewRow = table.convertRowIndexToView(modelRow)
                    if (viewRow >= 0) {
                        table.setRowSelectionInterval(viewRow, viewRow)
                        table.scrollRectToVisible(table.getCellRect(viewRow, 0, true))
                    }
                }
                setResult(
                    typeText = FileTypeDisplayHelper.displayText(matchResult.fileType),
                    typeIcon = FileTypeDisplayHelper.iconFor(matchResult.fileType),
                    detailText = AdvancedFileTypesBundle.message(
                        "settings.test.matched.pattern",
                        escapeHtml(matchResult.rule.pattern)
                    )
                )
            }
            is MatchResult.VsCodeMatch -> {
                setResult(
                    typeText = FileTypeDisplayHelper.displayText(matchResult.fileType),
                    typeIcon = FileTypeDisplayHelper.iconFor(matchResult.fileType),
                    detailText = AdvancedFileTypesBundle.message(
                        "settings.test.matched.vscode.pattern",
                        escapeHtml(matchResult.pattern),
                        escapeHtml(matchResult.languageId)
                    )
                )
            }
            is MatchResult.VsCodeUnsupportedMatch -> {
                setResult(
                    typeText = FileTypeDisplayHelper.displayTextForUnknown(
                        matchResult.languageId,
                        UnknownFileTypeOrigin.VS_CODE_IDENTIFIER
                    ),
                    typeIcon = FileTypeDisplayHelper.iconForUnknown(),
                    detailText = AdvancedFileTypesBundle.message(
                        "settings.test.matched.vscode.pattern",
                        escapeHtml(matchResult.pattern),
                        escapeHtml(matchResult.languageId)
                    )
                )
            }
            is MatchResult.GitAttributesMatch -> {
                setResult(
                    typeText = FileTypeDisplayHelper.displayText(matchResult.fileType),
                    typeIcon = FileTypeDisplayHelper.iconFor(matchResult.fileType),
                    detailText = AdvancedFileTypesBundle.message(
                        "settings.test.matched.gitattributes.pattern",
                        escapeHtml(matchResult.language)
                    )
                )
            }
            is MatchResult.GitAttributesUnsupportedMatch -> {
                setResult(
                    typeText = FileTypeDisplayHelper.displayTextForUnknown(
                        matchResult.language,
                        UnknownFileTypeOrigin.LINGUIST_IDENTIFIER
                    ),
                    typeIcon = FileTypeDisplayHelper.iconForUnknown(),
                    detailText = AdvancedFileTypesBundle.message(
                        "settings.test.matched.gitattributes.pattern",
                        escapeHtml(matchResult.language)
                    )
                )
            }
            is MatchResult.NoMatch -> {
                val defaultFileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName)
                val detailText = if (defaultFileType is UnknownFileType || defaultFileType == UnknownFileType.INSTANCE) {
                    AdvancedFileTypesBundle.message("settings.test.matched.none")
                } else {
                    AdvancedFileTypesBundle.message("settings.test.matched.global")
                }
                setResult(
                    typeText = FileTypeDisplayHelper.displayText(defaultFileType),
                    typeIcon = FileTypeDisplayHelper.iconFor(defaultFileType),
                    detailText = detailText
                )
            }
        }
    }

    override fun isModified(): Boolean {
        val settings = AdvancedFileTypeSettings.getInstance(project)
        val isGitAvailable = GitAttributesService.getInstance(project).isGitAvailable()
        val gitModified = if (isGitAvailable) {
            includeGitAttributesCheckBox.isSelected != settings.isLoadFromGitAttributes()
        } else {
            false
        }
        return rules != settings.getRules() 
            || includeVsCodeCheckBox.isSelected != settings.isLoadFromVsCodeSettings()
            || gitModified
    }

    override fun apply() {
        val settings = AdvancedFileTypeSettings.getInstance(project)
        settings.setRules(rules)
        settings.setLoadFromVsCodeSettings(includeVsCodeCheckBox.isSelected)
        val isGitAvailable = GitAttributesService.getInstance(project).isGitAvailable()
        if (isGitAvailable) {
            settings.setLoadFromGitAttributes(includeGitAttributesCheckBox.isSelected)
        }
    }

    override fun reset() {
        val settings = AdvancedFileTypeSettings.getInstance(project)
        rules.clear()
        rules.addAll(settings.getRules().map { it.copy() })
        tableModel.fireTableDataChanged()
        includeVsCodeCheckBox.isSelected = settings.isLoadFromVsCodeSettings()
        val isGitAvailable = GitAttributesService.getInstance(project).isGitAvailable()
        if (isGitAvailable) {
            includeGitAttributesCheckBox.isEnabled = true
            includeGitAttributesCheckBox.isSelected = settings.isLoadFromGitAttributes()
        } else {
            includeGitAttributesCheckBox.isEnabled = false
            includeGitAttributesCheckBox.isSelected = false
        }
        updateTestResult()
    }

    private class RuleTableModel(
        private val rules: MutableList<FileTypeOverrideRule>
    ) : AbstractTableModel(), EditableModel {

        override fun getRowCount(): Int = rules.size

        override fun getColumnCount(): Int = 3

        override fun getColumnClass(columnIndex: Int): Class<*> {
            return when (columnIndex) {
                0 -> String::class.java
                1 -> PatternSyntax::class.java
                2 -> String::class.java
                else -> Any::class.java
            }
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val rule = rules[rowIndex]
            return when (columnIndex) {
                0 -> rule.pattern
                1 -> rule.syntax
                2 -> rule.fileTypeName
                else -> ""
            }
        }

        override fun addRow() {
            rules.add(FileTypeOverrideRule())
            fireTableRowsInserted(rules.size - 1, rules.size - 1)
        }

        override fun exchangeRows(oldIndex: Int, newIndex: Int) {
            val moved = rules.removeAt(oldIndex)
            rules.add(newIndex, moved)
            fireTableDataChanged()
        }

        override fun canExchangeRows(oldIndex: Int, newIndex: Int): Boolean = true

        override fun removeRow(idx: Int) {
            if (idx in rules.indices) {
                rules.removeAt(idx)
                fireTableRowsDeleted(idx, idx)
            }
        }
    }
}

// Escape HTML entities for safe insertion into HTML-rendering labels (e.g., DSL comment labels)
private fun escapeHtml(text: String): String {
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
