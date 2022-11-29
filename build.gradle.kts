import org.jetbrains.dokka.gradle.DokkaTask
import java.net.URL

plugins {
    kotlin("multiplatform") version "1.7.20"
    kotlin("plugin.serialization") version "1.7.20"
    id("org.jetbrains.kotlinx.kover") version "0.6.1"
    id("org.jetbrains.dokka") version "1.7.20"
    `maven-publish`
    signing
}

group = "io.github.xn32"
version = "0.2.1"

kotlin {
    jvm {
        compilations.configureEach {
            kotlinOptions {
                jvmTarget = "1.8"
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


val sonatypeUsername: String? by project
val sonatypePassword: String? by project

val isReleaseVersion = !version.toString().endsWith("SNAPSHOT")

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("json5k")
            description.set("JSON5 library for Kotlin")
            url.set("https://github.com/xn32/json5k")

            scm {
                url.set("https://github.com/xn32/json5k")
                connection.set("scm:git:git://github.com/xn32/json5k.git")
                developerConnection.set("scm:git:ssh://git@github.com/xn32/json5k.git")
            }

            developers {
                developer {
                    id.set("xn32")
                    url.set("https://github.com/xn32")
                }
            }

            licenses {
                license {
                    name.set("The Apache Software License, Version 2.0")
                    url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
        }
    }

    repositories {
        maven {
            url = if (isReleaseVersion) {
                uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            } else {
                uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            }

            credentials {
                username = sonatypeUsername ?: ""
                password = sonatypePassword ?: ""
            }
        }
    }
}

signing {
    isRequired = isReleaseVersion && gradle.taskGraph.hasTask("publish")
    // TODO updating signing
//    sign(publishing.publications["mavenJava"])
}

tasks.withType<GenerateModuleMetadata>().configureEach {
    enabled = false
}

tasks.withType<DokkaTask>().configureEach {
    val githubRepo = "https://github.com/xn32/json5k"
    val footerMsg = "<a href='$githubRepo'>json5k on GitHub</a>"

    dokkaSourceSets {
        configureEach {
            externalDocumentationLink {
                url.set(URL("https://kotlinlang.org/api/kotlinx.serialization/"))
            }

            includes.from("dokka/index.md")

            sourceLink {
                val gitVersion = if (isReleaseVersion) {
                    "v$version"
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
