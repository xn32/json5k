import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URL

plugins {
    kotlin("jvm") version "1.7.10"
    kotlin("plugin.serialization") version "1.7.10"
    id("org.jetbrains.dokka") version "1.7.10"
    `maven-publish`
    `java-library`
}

group = "io.github.xn32"
version = "0.2.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    api("org.jetbrains.kotlinx:kotlinx-serialization-core:1.4.0")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("lib") {
            from(components["java"])
        }
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.withType<GenerateModuleMetadata> {
    enabled = false
}

tasks.withType<DokkaTask>() {
    val githubRepo = "https://github.com/xn32/json5k"
    val footerMsg = "Find the <a href='$githubRepo'>json5k repository</a> on GitHub."

    dokkaSourceSets {
        configureEach {
            externalDocumentationLink {
                url.set(URL("https://kotlinlang.org/api/kotlinx.serialization/"))
            }

            sourceLink {
                val gradleVersion = version.toString()

                val gitVersion = if (!gradleVersion.contains("SNAPSHOT")) {
                    gradleVersion
                } else {
                    "main"
                }

                localDirectory.set(file("src/main/kotlin"))
                remoteUrl.set(URL("$githubRepo/blob/$gitVersion/src/main/kotlin"))
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
