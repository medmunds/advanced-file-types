package com.medmunds.advancedfiletypes.ui

import com.intellij.driver.client.ProjectRef
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.client.utility
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.settings.settingsDialog
import com.intellij.driver.sdk.waitFor
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.Starter
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

@Remote("com.intellij.openapi.options.ShowSettingsUtil")
interface RemoteShowSettingsUtil {
    fun showSettingsDialog(project: ProjectRef?, nameToSelect: String)
}

@Remote("com.medmunds.advancedfiletypes.ui.ScreenshotHelper", plugin = "com.medmunds.advancedfiletypes.testhelper")
interface RemoteScreenshotHelper {
    fun prepareEnvironment(): Boolean
    fun isConfigurableReady(): Boolean
    fun getAvailableThemeNames(): Array<String>
    fun setTheme(themeName: String): Boolean
    fun captureComponentScreenshots(outputDirPath: String, testPath: String, suffix: String): Array<String>
}

@Tag("screenshot")
class SettingsScreenshotTest {

    @Test
    fun captureScreenshots() {
        val pluginZipPath = System.getProperty("path.to.build.plugin")
            ?: error("System property 'path.to.build.plugin' is not set")
        val testHelperPluginPath = System.getProperty("path.to.test.helper.plugin")
            ?: error("System property 'path.to.test.helper.plugin' is not set")

        // Set up test project with Git and initial settings
        val tempProjectDir = kotlin.io.path.createTempDirectory("test-demo-project")
        ProcessBuilder("git", "init").directory(tempProjectDir.toFile()).start().waitFor()

        val ideaDir = tempProjectDir.resolve(".idea")
        Files.createDirectories(ideaDir)
        val xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project version="4">
              <component name="AdvancedFileTypeSettings">
                <rules>
                  <overrideRule pattern="help/LICENSE.txt" fileTypeName="PLAIN_TEXT" />
                  <overrideRule pattern="help/**/*.txt" fileTypeName="Markdown" />
                  <overrideRule pattern="vendor/components/**/*.html" fileTypeName="Angular20Html" />
                  <overrideRule pattern="(?!public/).*\.html" fileTypeName="Handlebars/Mustache" syntax="REGEX" />
                  <overrideRule pattern="tsconfig.json" fileTypeName="JSON5" />
                </rules>
              </component>
            </project>
        """.trimIndent()
        Files.writeString(ideaDir.resolve("advancedFileTypes.xml"), xmlContent)
        Files.writeString(tempProjectDir.resolve("README.md"), "# Demo Project\n")

        val testCase = TestCase(IdeProductProvider.WS, projectInfo = LocalProjectInfo(tempProjectDir)).useRelease()
        val testContext = Starter.newContext("settingsScreenshot", testCase).apply {
            PluginConfigurator(this).installPluginFromPath(Path.of(pluginZipPath))
            PluginConfigurator(this).installPluginFromPath(Path.of(testHelperPluginPath))
        }

        val outputDir = File("build/screenshots").apply { mkdirs() }

        testContext.runIdeWithDriver().useDriverAndCloseIde {
            val helper = utility<RemoteScreenshotHelper>()
            helper.prepareEnvironment()

            ideFrame {
                waitForIndicators()
                
                driver.withContext(OnDispatcher.EDT) {
                    val showSettingsUtil = driver.service<RemoteShowSettingsUtil>()
                    showSettingsUtil.showSettingsDialog(project, "Advanced File Types")
                }
                
                settingsDialog {
                    waitFor(timeout = 15.seconds) {
                        xx { contains(byVisibleText("Project-specific file type overrides")) }.list().isNotEmpty()
                    }
                }
            }

            val availableThemes = helper.getAvailableThemeNames()

            // 1. Capture Light theme screenshots (Islands Light)
            val lightThemeName = availableThemes.firstOrNull { it.equals("Islands Light", ignoreCase = true) }
                ?: availableThemes.firstOrNull { it.contains("Light", ignoreCase = true) }
                ?: "Light"
            helper.setTheme(lightThemeName)

            ideFrame {
                settingsDialog {
                    waitFor(timeout = 15.seconds) {
                        helper.isConfigurableReady()
                    }
                }
            }

            helper.captureComponentScreenshots(outputDir.absolutePath, "src/home.html", "")

            // 2. Capture Dark theme screenshots (Islands Dark)
            val darkThemeName = availableThemes.firstOrNull { it.equals("Islands Dark", ignoreCase = true) }
                ?: availableThemes.firstOrNull { it.contains("Dark", ignoreCase = true) }
                ?: "Dark"
            helper.setTheme(darkThemeName)

            ideFrame {
                settingsDialog {
                    waitFor(timeout = 15.seconds) {
                        helper.isConfigurableReady()
                    }
                }
            }

            helper.captureComponentScreenshots(outputDir.absolutePath, "src/home.html", "_dark")

            // Copy all generated assets to img/ directory
            val imgDir = File("img").apply { mkdirs() }
            listOf(
                "settings-dialog.png", "settings-dialog_dark.png",
                "settings-panel.png", "settings-panel_dark.png",
                "pattern-matching-rules.png", "pattern-matching-rules_dark.png",
                "vscode-and-gitattributes.png", "vscode-and-gitattributes_dark.png",
                "test-pattern-matching.png", "test-pattern-matching_dark.png"
            ).forEach { filename ->
                val srcFile = File(outputDir, filename)
                if (srcFile.exists()) {
                    srcFile.copyTo(File(imgDir, filename), overwrite = true)
                }
            }
        }
    }
}
