import org.jetbrains.dokka.gradle.DokkaTask
import java.net.URL

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlinx.kover") version "0.6.1"
    id("org.jetbrains.dokka") version "1.7.20"

    buildsrc.conventions.base
    buildsrc.conventions.`maven-publish`
}

group = "io.github.xn32"
version = "0.2.1"

kotlin {
    jvm {
        withJava()
        compilations.configureEach {
            kotlinOptions {
                jvmTarget = json5kBuildProps.jvmTarget.get()
            }
        }
        testRuns.configureEach {
            executionTask.configure {
                useJUnitPlatform()
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-serialization-core:1.4.1")
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

tasks.withType<DokkaTask>().configureEach {
    val githubRepo = "https://github.com/xn32/json5k"
    val footerMsg = "<a href='$githubRepo'>json5k on GitHub</a>"
    val githubUrl = json5kBuildProps.projectVersion { version, isRelease ->
        val gitVersion = if (isRelease) {
            "v$version"
        } else {
            "main"
        }
        URL("$githubRepo/blob/$gitVersion/src/main/kotlin")
    }

    dokkaSourceSets {
        configureEach {
            externalDocumentationLink {
                url.set(URL("https://kotlinlang.org/api/kotlinx.serialization/"))
            }

            includes.from("dokka/index.md")

            sourceLink {
                localDirectory.set(file("src/main/kotlin"))
                remoteUrl.set(githubUrl)
                remoteLineSuffix.set("#L")
            }
        }
    }

    outputDirectory.set(buildDir.resolve("dokka"))
    suppressInheritedMembers.set(true)

    pluginsMapConfiguration.set(
        mapOf(
            "org.jetbrains.dokka.base.DokkaBase" to """
                {
                    "footerMessage": "$footerMsg",
                    "customStyleSheets": [ "${file("dokka/custom.css")}" ]
                }
            """.trimIndent()
        )
    )
}
