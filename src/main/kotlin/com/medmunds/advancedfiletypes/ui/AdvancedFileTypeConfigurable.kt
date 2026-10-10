package com.medmunds.advancedfiletypes.ui

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.EditableModel
import com.medmunds.advancedfiletypes.MyMessageBundle
import com.medmunds.advancedfiletypes.help.AdvancedFileTypeWebHelpProvider
import com.medmunds.advancedfiletypes.model.FileTypeOverrideRule
import com.medmunds.advancedfiletypes.model.PatternSyntax
import com.medmunds.advancedfiletypes.settings.AdvancedFileTypeSettings
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

    private val rules = mutableListOf<FileTypeOverrideRule>()
    private val tableModel = RuleTableModel(rules)
    private val table = JBTable(tableModel)

    private val testPathField = JBTextField(30)
    private val testResultLabel = JBLabel(MyMessageBundle.message("settings.test.no.path"))

    override fun getId(): String = "com.medmunds.advancedfiletypes.settings"

    override fun getDisplayName(): String = MyMessageBundle.message("settings.displayName")

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

        testPathField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = updateTestResult()
            override fun removeUpdate(e: DocumentEvent?) = updateTestResult()
            override fun changedUpdate(e: DocumentEvent?) = updateTestResult()
        })

        tableModel.addTableModelListener {
            updateTestResult()
        }

        return panel {
            row {
                label(MyMessageBundle.message("settings.description"))
            }
            row {
                comment(MyMessageBundle.message("settings.explanation"))
            }
            row {
                cell(tablePanel).align(Align.FILL)
            }.resizableRow()
            group(MyMessageBundle.message("settings.test.group")) {
                row(MyMessageBundle.message("settings.test.path.label")) {
                    cell(testPathField)
                        .comment(MyMessageBundle.message("settings.test.path.comment"))
                }
                row(MyMessageBundle.message("settings.test.result.label")) {
                    cell(testResultLabel)
                }
            }
        }
    }

    private fun setupTable() {
        table.setRowSelectionAllowed(true)
        table.setShowGrid(false)

        table.columnModel.getColumn(0).apply {
            preferredWidth = 250
            headerValue = "Pattern"
        }
        table.columnModel.getColumn(1).apply {
            preferredWidth = 80
            maxWidth = 100
            headerValue = "Syntax"
        }
        table.columnModel.getColumn(2).apply {
            preferredWidth = 200
            headerValue = "File Type"
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
                    val fileType = FileTypeManager.getInstance().findFileTypeByName(typeName)
                    if (fileType != null) {
                        c.icon = fileType.icon
                        val desc = fileType.description
                        c.text = if (desc.isNotBlank()) "${fileType.displayName} ($desc)" else fileType.displayName
                    } else {
                        c.icon = null
                        c.text = typeName
                    }
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

    private fun updateTestResult() {
        val path = testPathField.text.trim()
        if (path.isEmpty()) {
            testResultLabel.text = MyMessageBundle.message("settings.test.no.path")
            testResultLabel.icon = null
            return
        }

        val fileName = path.substringAfterLast('/').substringAfterLast('\\')
        val fileTypeManager = FileTypeManager.getInstance()

        for (rule in rules) {
            if (rule.matches(path, fileName)) {
                val fileType = fileTypeManager.findFileTypeByName(rule.fileTypeName)
                if (fileType != null) {
                    testResultLabel.text = MyMessageBundle.message(
                        "settings.test.matched",
                        rule.pattern,
                        fileType.displayName,
                        rule.fileTypeName
                    )
                    testResultLabel.icon = fileType.icon
                    return
                }
            }
        }

        testResultLabel.text = MyMessageBundle.message("settings.test.no.match")
        testResultLabel.icon = null
    }

    override fun isModified(): Boolean {
        val savedRules = AdvancedFileTypeSettings.getInstance(project).getRules()
        return rules != savedRules
    }

    override fun apply() {
        AdvancedFileTypeSettings.getInstance(project).setRules(rules)
    }

    override fun reset() {
        rules.clear()
        rules.addAll(AdvancedFileTypeSettings.getInstance(project).getRules().map { it.copy() })
        tableModel.fireTableDataChanged()
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
