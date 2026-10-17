package com.medmunds.advancedfiletypes.ui

import com.intellij.ide.GeneralSettings
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProgressManager
import java.awt.Component
import java.awt.Container
import java.awt.Dialog
import java.awt.Frame
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Window
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities

@Suppress("unused")
object ScreenshotHelper {

    @JvmStatic
    fun prepareEnvironment(): Boolean {
        ApplicationManager.getApplication().invokeAndWait({
            try {
                val generalSettings = GeneralSettings.getInstance()
                generalSettings.isAutoSaveIfInactive = false
                generalSettings.isSaveOnFrameDeactivation = false
            } catch (_: Throwable) {}

            try {
                val saveHandler = SaveAndSyncHandler.getInstance()
                saveHandler.blockSaveOnFrameDeactivation()
            } catch (_: Throwable) {}
        }, ModalityState.any())
        return true
    }

    @JvmStatic
    fun isConfigurableReady(): Boolean {
        val configurable = AdvancedFileTypeConfigurableTracker.currentInstance ?: return false
        val rootPanel = configurable.rootPanel ?: return false
        val tablePanel = configurable.tableDecoratorPanel ?: return false
        if (!rootPanel.isShowing || !tablePanel.isShowing || rootPanel.width <= 50 || rootPanel.height <= 50) {
            return false
        }

        // Check if any modal progress dialog or "Saving" dialog is currently active
        val settingsDialogWindow = SwingUtilities.getWindowAncestor(rootPanel)
        val allWindows = Window.getWindows()
        for (w in allWindows) {
            if (w.isShowing && w != settingsDialogWindow) {
                if (w is Dialog && w.isModal) {
                    return false
                }
                val title = when (w) {
                    is Dialog -> w.title ?: ""
                    is Frame -> w.title ?: ""
                    else -> ""
                }
                if (title.contains("Saving", ignoreCase = true) || title.contains("Progress", ignoreCase = true)) {
                    return false
                }
            }
        }

        try {
            if (ProgressManager.getInstance().hasModalProgressIndicator()) {
                return false
            }
        } catch (_: Throwable) {}

        return true
    }

    @JvmStatic
    @Suppress("UnstableApiUsage")
    fun getAvailableThemeNames(): Array<String> {
        val lafManager = LafManager.getInstance()
        return lafManager.installedThemes.map { it.name }.toList().toTypedArray()
    }

    @JvmStatic
    @Suppress("UnstableApiUsage")
    fun setTheme(themeName: String): Boolean {
        val lafManager = LafManager.getInstance()
        val target: UIThemeLookAndFeelInfo = lafManager.installedThemes.firstOrNull {
            it.name.equals(themeName, ignoreCase = true)
        } ?: lafManager.installedThemes.firstOrNull {
            it.name.contains(themeName, ignoreCase = true)
        } ?: return false

        ApplicationManager.getApplication().invokeAndWait({
            lafManager.setCurrentLookAndFeel(target, false)
            lafManager.updateUI()
        }, ModalityState.any())
        return true
    }

    @JvmStatic
    fun captureComponentScreenshots(outputDirPath: String, testPath: String, suffix: String = ""): Array<String> {
        val createdFiles = mutableListOf<String>()
        val outputDir = File(outputDirPath).apply { mkdirs() }

        ApplicationManager.getApplication().invokeAndWait({
            val configurable = AdvancedFileTypeConfigurableTracker.currentInstance
                ?: error("AdvancedFileTypeConfigurable instance is not currently active")

            if (testPath.isNotBlank()) {
                configurable.testPathField.text = testPath
                configurable.updateTestResult()
            }

            val rootPanel = configurable.rootPanel
                ?: error("Configurable root panel is null")

            check(rootPanel.isShowing) { "Configurable rootPanel is not showing (width=${rootPanel.width}, height=${rootPanel.height})" }

            val splitter = findAncestorOfType<com.intellij.openapi.ui.Splitter>(rootPanel)
            val settingsContentPanel = splitter?.secondComponent ?: rootPanel

            settingsContentPanel.validate()
            settingsContentPanel.doLayout()
            settingsContentPanel.repaint()

            val scale = 2.0

            // 0. Entire IDE settings dialog screenshot (cropped to content pane, omitting empty titlebar)
            val dialogWindow = SwingUtilities.getWindowAncestor(rootPanel)
            val dialogRoot = (dialogWindow as? javax.swing.RootPaneContainer)?.rootPane
            if (dialogRoot != null && dialogRoot.isShowing) {
                // Suppress dynamic overlay scrollbars for consistent screenshots
                val dialogImage = withHiddenScrollBars(dialogRoot) {
                    dialogRoot.validate()
                    dialogRoot.doLayout()
                    dialogRoot.repaint()
                    renderComponentToImage(dialogRoot, scale)
                }
                val settingsDialogFile = File(outputDir, if (suffix.isNotEmpty()) "settings-dialog$suffix.png" else "settings-dialog.png")
                val dialogBounds = getDialogBounds(dialogRoot)
                saveCroppedBounds(dialogBounds, dialogImage, scale, settingsDialogFile, margin = 0)
                createdFiles.add(settingsDialogFile.absolutePath)
            }

            val fullImage = renderComponentToImage(settingsContentPanel, scale)

            // 1. Full settings panel screenshot (including breadcrumbs above the configurable)
            val settingsPanelFile = File(outputDir, if (suffix.isNotEmpty()) "settings-panel$suffix.png" else "settings-panel.png")
            ImageIO.write(fullImage, "PNG", settingsPanelFile)
            createdFiles.add(settingsPanelFile.absolutePath)

            // 2. Rules table with toolbar decorator and description header
            val rulesBounds = getRulesGroupBounds(configurable, settingsContentPanel)
            if (rulesBounds != null) {
                val rulesFile = File(outputDir, if (suffix.isNotEmpty()) "pattern-matching-rules$suffix.png" else "pattern-matching-rules.png")
                saveCroppedBounds(rulesBounds, fullImage, scale, rulesFile, margin = 4)
                createdFiles.add(rulesFile.absolutePath)
            }

            // 3. VS Code and GitAttributes checkboxes
            val vsCodeBox = configurable.includeVsCodeCheckBox
            val gitBox = configurable.includeGitAttributesCheckBox
            val vsGitBounds = getCombinedBounds(listOf(vsCodeBox, gitBox), settingsContentPanel)
            if (vsGitBounds != null) {
                val vsGitFile = File(outputDir, if (suffix.isNotEmpty()) "vscode-and-gitattributes$suffix.png" else "vscode-and-gitattributes.png")
                saveCroppedBounds(vsGitBounds, fullImage, scale, vsGitFile, margin = 6)
                createdFiles.add(vsGitFile.absolutePath)
            }

            // 4. Test matching section
            val testGroupBounds = getTestGroupBounds(configurable, settingsContentPanel)
            if (testGroupBounds != null) {
                val testMatchingFile = File(outputDir, if (suffix.isNotEmpty()) "test-pattern-matching$suffix.png" else "test-pattern-matching.png")
                saveCroppedBounds(testGroupBounds, fullImage, scale, testMatchingFile, margin = 4)
                createdFiles.add(testMatchingFile.absolutePath)
            }
        }, ModalityState.any())

        return createdFiles.toTypedArray()
    }

    private fun renderComponentToImage(component: JComponent, scale: Double): BufferedImage {
        val width = component.width
        val height = component.height
        val scaledWidth = (width * scale).toInt()
        val scaledHeight = (height * scale).toInt()

        val image = BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB)
        val g2 = image.createGraphics()
        try {
            g2.scale(scale, scale)
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB)
            component.printAll(g2)
        } finally {
            g2.dispose()
        }
        return image
    }

    private fun saveCroppedBounds(
        bounds: Rectangle,
        fullImage: BufferedImage,
        scale: Double,
        outputFile: File,
        margin: Int = 0
    ) {
        val x = ((bounds.x - margin) * scale).toInt().coerceIn(0, fullImage.width)
        val y = ((bounds.y - margin) * scale).toInt().coerceIn(0, fullImage.height)
        val w = ((bounds.width + 2 * margin) * scale).toInt().coerceAtMost(fullImage.width - x)
        val h = ((bounds.height + 2 * margin) * scale).toInt().coerceAtMost(fullImage.height - y)

        if (w > 0 && h > 0) {
            val cropped = fullImage.getSubimage(x, y, w, h)
            ImageIO.write(cropped, "PNG", outputFile)
        }
    }

    private fun <T> withHiddenScrollBars(root: Component, block: () -> T): T {
        val scrollPanes = mutableListOf<Pair<JScrollPane, Pair<Int, Int>>>()

        fun collectAndHide(comp: Component) {
            if (comp is JScrollPane) {
                scrollPanes.add(comp to (comp.verticalScrollBarPolicy to comp.horizontalScrollBarPolicy))
                comp.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
                comp.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                comp.verticalScrollBar?.isVisible = false
                comp.horizontalScrollBar?.isVisible = false
            }
            if (comp is Container) {
                comp.components.forEach { collectAndHide(it) }
            }
        }

        collectAndHide(root)
        root.validate()
        root.doLayout()

        try {
            return block()
        } finally {
            for ((pane, policies) in scrollPanes) {
                pane.verticalScrollBarPolicy = policies.first
                pane.horizontalScrollBarPolicy = policies.second
                pane.verticalScrollBar?.isVisible = true
                pane.horizontalScrollBar?.isVisible = true
            }
            root.validate()
            root.doLayout()
        }
    }

    private fun getDialogBounds(dialogRoot: JComponent): Rectangle {
        val contentPane = (dialogRoot as? javax.swing.JRootPane)?.contentPane as? JComponent
        if (contentPane != null && contentPane.isShowing) {
            val bounds = SwingUtilities.convertRectangle(contentPane.parent, contentPane.bounds, dialogRoot)
            if (bounds.width > 0 && bounds.height > 0) {
                return bounds
            }
        }
        return Rectangle(0, 0, dialogRoot.width, dialogRoot.height)
    }

    private fun getRulesGroupBounds(configurable: AdvancedFileTypeConfigurable, root: JComponent): Rectangle? {
        val tablePanel = configurable.tableDecoratorPanel ?: return null
        val table = configurable.table
        if (!tablePanel.isShowing) return null

        val descText = com.medmunds.advancedfiletypes.AdvancedFileTypesBundle.message("settings.description")
        val explanationText = com.medmunds.advancedfiletypes.AdvancedFileTypesBundle.message("settings.explanation")

        val headerComponents = findComponentsMatching(root) { comp ->
            if (comp == root) return@findComponentsMatching false
            when (comp) {
                is javax.swing.JLabel -> {
                    val txt = comp.text ?: ""
                    txt.contains(descText) || txt.contains(explanationText)
                }
                is javax.swing.text.JTextComponent -> {
                    val txt = comp.text ?: ""
                    txt.contains(descText) || txt.contains(explanationText)
                }
                else -> false
            }
        }

        val headerBounds = if (headerComponents.isNotEmpty()) {
            getCombinedBounds(headerComponents, root)
        } else null

        val tablePanelBounds = SwingUtilities.convertRectangle(tablePanel.parent, tablePanel.bounds, root)

        val bottomY = if (table.rowCount > 0 && table.isShowing) {
            val lastRowRect = table.getCellRect(table.rowCount - 1, 0, true)
            val p = SwingUtilities.convertPoint(table, java.awt.Point(0, lastRowRect.y + lastRowRect.height), root)
            (p.y + 6).coerceAtMost(tablePanelBounds.y + tablePanelBounds.height)
        } else {
            tablePanelBounds.y + tablePanelBounds.height
        }

        val topY = headerBounds?.y ?: tablePanelBounds.y
        val minX = listOfNotNull(headerBounds?.x, tablePanelBounds.x).minOrNull() ?: tablePanelBounds.x
        val maxX = listOfNotNull(
            headerBounds?.let { it.x + it.width },
            tablePanelBounds.x + tablePanelBounds.width
        ).maxOrNull() ?: (tablePanelBounds.x + tablePanelBounds.width)

        return Rectangle(minX, topY, maxX - minX, bottomY - topY)
    }

    private fun getTestGroupBounds(configurable: AdvancedFileTypeConfigurable, root: JComponent): Rectangle? {
        val testField = configurable.testPathField
        val resultComp = configurable.testResultCell.component
        val resultComment = configurable.testResultCell.comment

        val components = mutableListOf<Component>(testField, resultComp)
        if (resultComment != null) {
            components.add(resultComment)
        }

        // Also search root panel for the group title label or separator and related labels
        val titleText = com.medmunds.advancedfiletypes.AdvancedFileTypesBundle.message("settings.test.group")
        val pathLabelText = com.medmunds.advancedfiletypes.AdvancedFileTypesBundle.message("settings.test.path.label")
        val resultLabelText = com.medmunds.advancedfiletypes.AdvancedFileTypesBundle.message("settings.test.result.label")

        findComponentsMatching(root) { comp ->
            if (comp == root) return@findComponentsMatching false
            when (comp) {
                is javax.swing.JLabel -> {
                    val txt = comp.text ?: ""
                    txt.contains(titleText) || txt.contains(pathLabelText) || txt.contains(resultLabelText)
                }
                is com.intellij.ui.TitledSeparator -> {
                    val txt = comp.text ?: ""
                    txt.contains(titleText)
                }
                else -> false
            }
        }.forEach { components.add(it) }

        return getCombinedBounds(components, root)
    }

    private fun findComponentsMatching(container: Container, predicate: (Component) -> Boolean): List<Component> {
        val result = mutableListOf<Component>()
        for (comp in container.components) {
            if (predicate(comp)) {
                result.add(comp)
            }
            if (comp is Container) {
                result.addAll(findComponentsMatching(comp, predicate))
            }
        }
        return result
    }

    private inline fun <reified T : Component> findAncestorOfType(component: Component): T? {
        var parent = component.parent
        while (parent != null) {
            if (parent is T) {
                return parent
            }
            parent = parent.parent
        }
        return null
    }

    private fun getCombinedBounds(components: List<Component>, root: JComponent): Rectangle? {
        val valid = components.filter { it.isShowing && it.parent != null }
        if (valid.isEmpty()) return null
        var rect: Rectangle? = null
        for (comp in valid) {
            val r = SwingUtilities.convertRectangle(comp.parent, comp.bounds, root)
            rect = rect?.union(r) ?: r
        }
        return rect
    }
}
