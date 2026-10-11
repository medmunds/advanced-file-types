package com.medmunds.advancedfiletypes.ui

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.EditableModel
import com.intellij.util.ui.JBUI
import com.medmunds.advancedfiletypes.AdvancedFileTypesBundle
import com.medmunds.advancedfiletypes.help.AdvancedFileTypeWebHelpProvider
import com.medmunds.advancedfiletypes.model.FileTypeOverrideRule
import com.medmunds.advancedfiletypes.model.PatternSyntax
import com.medmunds.advancedfiletypes.settings.AdvancedFileTypeSettings
import com.medmunds.advancedfiletypes.settings.MatchResult
import java.awt.Component
import java.awt.Dimension
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

class AdvancedFileTypeConfigurable(private val project: Project) : SearchableConfigurable {

    private val rules = mutableListOf<FileTypeOverrideRule>()
    private val tableModel = RuleTableModel(rules)
    private val table = JBTable(tableModel)
    internal val includeVsCodeCheckBox = JBCheckBox(AdvancedFileTypesBundle.message("settings.includeVsCode"))

    internal val testPathField = JBTextField(30)
    internal val testResultLabel = object : JBLabel() {
        init {
            verticalAlignment = SwingConstants.TOP
        }

        override fun getPreferredSize(): Dimension {
            val size = super.getPreferredSize()
            val fm = getFontMetrics(font)
            val lineHeight = fm?.height ?: JBUI.scale(16)
            val twoLinesHeight = lineHeight * 2 + insets.top + insets.bottom
            val iconHeight = (icon?.iconHeight ?: 0) + insets.top + insets.bottom
            val targetHeight = maxOf(twoLinesHeight, iconHeight)
            return Dimension(0, maxOf(size.height, targetHeight))
        }

        override fun getMinimumSize(): Dimension {
            val fm = getFontMetrics(font)
            val lineHeight = fm?.height ?: JBUI.scale(16)
            val twoLinesHeight = lineHeight * 2 + insets.top + insets.bottom
            val iconHeight = (icon?.iconHeight ?: 0) + insets.top + insets.bottom
            val targetHeight = maxOf(twoLinesHeight, iconHeight)
            return Dimension(0, targetHeight)
        }
    }

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

        testPathField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = updateTestResult()
            override fun removeUpdate(e: DocumentEvent?) = updateTestResult()
            override fun changedUpdate(e: DocumentEvent?) = updateTestResult()
        })

        tableModel.addTableModelListener {
            updateTestResult()
        }

        reset()

        return panel {
            row {
                label(AdvancedFileTypesBundle.message("settings.description"))
            }
            row {
                comment(AdvancedFileTypesBundle.message("settings.explanation"))
            }
            row {
                cell(tablePanel).align(Align.FILL)
            }.resizableRow()
            row {
                cell(includeVsCodeCheckBox)
            }
            group(AdvancedFileTypesBundle.message("settings.test.group")) {
                row(AdvancedFileTypesBundle.message("settings.test.path.label")) {
                    cell(testPathField)
                        .comment(AdvancedFileTypesBundle.message("settings.test.path.comment"))
                }
                row(AdvancedFileTypesBundle.message("settings.test.result.label")) {
                    cell(testResultLabel)
                        .align(AlignX.FILL)
                        .align(AlignY.TOP)
                }
            }
        }
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
                    val fileType = FileTypeManager.getInstance().findFileTypeByName(typeName)
                    if (fileType != null) {
                        c.icon = fileType.icon
                        val desc = fileType.description
                        c.text = if (desc.isNotBlank()) {
                            AdvancedFileTypesBundle.message("fileType.display.withDescription", fileType.displayName, desc)
                        } else {
                            fileType.displayName
                        }
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

    private fun setResult(text: String, icon: Icon? = null) {
        testResultLabel.text = if (text.isBlank()) "" else "<html>${StringUtil.escapeXmlEntities(text)}</html>"
        testResultLabel.icon = icon
    }

    private fun updateTestResult() {
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
            includeVsCode = includeVsCodeCheckBox.isSelected
        )

        when (matchResult) {
            is MatchResult.PluginRuleMatch -> {
                setResult(
                    AdvancedFileTypesBundle.message(
                        "settings.test.matched",
                        matchResult.rule.pattern,
                        matchResult.fileType.displayName,
                        matchResult.rule.fileTypeName
                    ),
                    matchResult.fileType.icon
                )
            }
            is MatchResult.VsCodeMatch -> {
                setResult(
                    AdvancedFileTypesBundle.message(
                        "settings.test.matched.vscode",
                        matchResult.pattern,
                        matchResult.fileType.displayName,
                        matchResult.fileType.name
                    ),
                    matchResult.fileType.icon
                )
            }
            is MatchResult.VsCodeUnsupportedMatch -> {
                setResult(
                    AdvancedFileTypesBundle.message(
                        "settings.test.matched.vscode.unsupported",
                        matchResult.pattern,
                        matchResult.languageId
                    )
                )
            }
            is MatchResult.NoMatch -> {
                setResult(AdvancedFileTypesBundle.message("settings.test.no.match"))
            }
        }
    }

    override fun isModified(): Boolean {
        val settings = AdvancedFileTypeSettings.getInstance(project)
        return rules != settings.getRules() || includeVsCodeCheckBox.isSelected != settings.isLoadFromVsCodeSettings()
    }

    override fun apply() {
        val settings = AdvancedFileTypeSettings.getInstance(project)
        settings.setRules(rules)
        settings.setLoadFromVsCodeSettings(includeVsCodeCheckBox.isSelected)
    }

    override fun reset() {
        val settings = AdvancedFileTypeSettings.getInstance(project)
        rules.clear()
        rules.addAll(settings.getRules().map { it.copy() })
        tableModel.fireTableDataChanged()
        includeVsCodeCheckBox.isSelected = settings.isLoadFromVsCodeSettings()
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
