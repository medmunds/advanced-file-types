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
            // Always verify against IDEA (the platform dependency target)
            current()
            // Verify against additional IDE products in CI (-PfullVerification=true)
            if (providers.gradleProperty("fullVerification").orNull.toBoolean()) {
                select {
                    types = listOf(
                        IntelliJPlatformType.PyCharm,
                        IntelliJPlatformType.WebStorm,
                        IntelliJPlatformType.RustRover,
                    )
                    channels = listOf(ProductRelease.Channel.RELEASE)
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
