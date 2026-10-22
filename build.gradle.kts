import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    implementation(libs.json5)

    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2025.3.6.1")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.JUnit5)
        testFramework(TestFrameworkType.Starter)

        bundledPlugin("Git4Idea")
    }
}

intellijPlatform {
    pluginVerification {
        ides {
            val isMinimal = providers.gradleProperty("minimalVerification")
                .map { it.isEmpty() || it.toBoolean() }
                .getOrElse(false)

            if (isMinimal) {
                // Verify only against the current target IDEA version (local development)
                current()
            } else {
                // Verify against the full IDE matrix from minimum supported build through latest EAP
                select {
                    types = listOf(
                        IntelliJPlatformType.IntellijIdea,
                        IntelliJPlatformType.PyCharm,
                        IntelliJPlatformType.WebStorm,
                        IntelliJPlatformType.RustRover,
                    )
                    channels = listOf(
                        ProductRelease.Channel.RELEASE,
                        ProductRelease.Channel.EAP,
                    )
                    sinceBuild = "253"
                }
            }
        }
    }
}

tasks.test {
    useJUnitPlatform {
        excludeTags("screenshot")
    }
    testLogging {
        showStandardStreams = true
    }
}

val testHelperJar = tasks.register<Jar>("testHelperJar") {
    archiveBaseName.set("advanced-file-types-test-helper")
    archiveClassifier.set("")
    archiveVersion.set("")
    from(sourceSets.test.get().output) {
        include("com/medmunds/advancedfiletypes/ui/ScreenshotHelper*")
    }
    from("src/test/resources/test-helper-plugin") {
        include("META-INF/plugin.xml")
    }
    destinationDirectory.set(layout.buildDirectory.dir("test-helper-plugin/lib"))
}

val buildTestHelperPlugin = tasks.register<Zip>("buildTestHelperPlugin") {
    archiveBaseName.set("advanced-file-types-test-helper")
    archiveClassifier.set("")
    archiveVersion.set("")
    from(testHelperJar) {
        into("advanced-file-types-test-helper/lib")
    }
    destinationDirectory.set(layout.buildDirectory.dir("test-helper-plugin"))
}

val generateScreenshots = intellijPlatformTesting.testIde.register("generateScreenshots") {
    testFramework(TestFrameworkType.Platform)
    testFramework(TestFrameworkType.JUnit5)
    testFramework(TestFrameworkType.Starter)
    task {
        group = "documentation"
        description = "Generates documentation screenshots into img/"
        useJUnitPlatform {
            includeTags("screenshot")
        }
        testLogging {
            showStandardStreams = true
        }
        dependsOn(tasks.buildPlugin, buildTestHelperPlugin)
        systemProperty("path.to.build.plugin", tasks.buildPlugin.flatMap { it.archiveFile }.get().asFile.absolutePath)
        systemProperty("path.to.test.helper.plugin", buildTestHelperPlugin.flatMap { it.archiveFile }.get().asFile.absolutePath)
        systemProperty("allure.results.directory", layout.buildDirectory.dir("allure-results").get().asFile.absolutePath)
        outputs.dir("img")
    }
}

tasks.processResources {
    from("LICENSE") {
        into("META-INF")
    }
}

val generateBuildConfig = tasks.register("generateBuildConfig") {
    val versionStr = project.version.toString()
    val branch = if (versionStr.endsWith("-SNAPSHOT")) "main" else versionStr
    val readmeUrl = "https://github.com/medmunds/advanced-file-types/blob/$branch/README.md#usage"

    val outputDir = layout.buildDirectory.dir("generated/sources/buildconfig/kotlin/main")
    outputs.dir(outputDir)

    doLast {
        val configFile = outputDir.get().file("com/medmunds/advancedfiletypes/BuildConfig.kt").asFile
        configFile.parentFile.mkdirs()
        configFile.writeText("""
            package com.medmunds.advancedfiletypes

            object BuildConfig {
                const val README_URL = "$readmeUrl"
            }
        """.trimIndent())
    }
}

kotlin {
    sourceSets.main {
        kotlin.srcDir(generateBuildConfig.map { it.outputs.files })
    }
}
