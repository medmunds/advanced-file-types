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
    useJUnitPlatform()
}

tasks.processResources {
    from("LICENSE") {
        into("META-INF")
    }
}
