package buildsrc.conventions

import buildsrc.config.Json5kBuildProps
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper

plugins {
    signing
    `maven-publish`
}

val buildProps = extensions.getByType<Json5kBuildProps>()

val isReleaseVersion = providers.provider {
    project.version.toString().matches(Json5kBuildProps.releaseVersionRegex)
}

val sonatypeReleaseUrl: Provider<String> = isReleaseVersion.map { isRelease ->
    if (isRelease) {
        "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
    } else {
        "https://s01.oss.sonatype.org/content/repositories/snapshots/"
    }
}

val javadocJarStub by tasks.registering(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Empty Javadoc Jar (required by Maven Central)"
    archiveClassifier.set("javadoc")
}

signing {
    useGpgCmd()
    if (buildProps.signingKey.isPresent && buildProps.signingPassword.isPresent) {
        logger.lifecycle("[maven-publish convention] signing is enabled for ${project.path}")
        useInMemoryPgpKeys(buildProps.signingKey.get(), buildProps.signingPassword.get())
    }
}

afterEvaluate {
    // Gradle hasn't updated the signing plugin to be compatible with lazy-configuration, so it needs weird workarounds
    //
    // 1. Register signatures in afterEvaluate, otherwise the signing plugin creates the signing tasks
    // too early, before all the publications are added.
    //
    // 2. Use publications.all { }, not .configureEach { }, otherwise the signing plugin doesn't create the tasks soon enough.

    if (buildProps.signingKey.isPresent && buildProps.signingPassword.isPresent) {
        publishing.publications.all publication@{
            logger.lifecycle("[maven-publish convention] configuring signature for publication ${this@publication.name} in ${project.path}")
            // closureOf is a Gradle Kotlin DSL workaround: https://github.com/gradle/gradle/issues/19903
            signing.sign(closureOf<SignOperation> { signing.sign(this@publication) })
        }
    }
}

publishing {
    repositories {
        if (buildProps.sonatypeUsername.isPresent && buildProps.sonatypePassword.isPresent) {
            maven(sonatypeReleaseUrl) {
                name = "SonatypeRelease"
                credentials {
                    username = buildProps.sonatypeUsername.get()
                    password = buildProps.sonatypePassword.get()
                }
            }
        }

        // Publish to a project-local Maven directory, for verification. To test, run:
        // ./gradlew publishAllPublicationsToMavenProjectLocalRepository
        // and check $rootDir/build/maven-project-local
        maven(rootProject.layout.buildDirectory.dir("maven-project-local")) {
            name = "MavenProjectLocal"
        }
    }

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
}

tasks.withType<AbstractPublishToMaven>().configureEach {
    // Gradle warns about some signing tasks using publishing task outputs without explicit dependencies.
    // Here's a quick fix.
    dependsOn(tasks.withType<Sign>())
    mustRunAfter(tasks.withType<Sign>())

    // use vals - improves Gradle Config Cache compatibility
    val publicationGAV = publication?.run { "$group:$artifactId:$version" }

    doLast {
        if (publicationGAV != null) {
            logger.lifecycle("[task: ${path}] $publicationGAV")
        }
    }
}

// Kotlin Multiplatform specific publishing configuration
plugins.withType<KotlinMultiplatformPluginWrapper>().configureEach {

    publishing {
        publications.withType<MavenPublication>().configureEach {
            artifact(javadocJarStub)
        }
    }

    tasks.withType<AbstractPublishToMaven>().configureEach {

        // use provider - improves Gradle work-avoidance compatibility
        val publicationNameProvider = provider { publication?.name }

        val isPublicationEnabled = providers.zip(
            publicationNameProvider,
            buildProps.enabledPublicationNamePrefixes,
        ) { publicationName, enabledNames ->
            publicationName != null && enabledNames.any { it.startsWith(publicationName, ignoreCase = true) }
        }

        // register an input so Gradle can do up-to-date checks
        inputs.property("isPublicationEnabled", isPublicationEnabled)

        onlyIf {
            isPublicationEnabled.get().also { enabled ->
                if (!enabled) logger.lifecycle("[task: $path] publishing for '${publicationNameProvider.get()}' is disabled")
            }
        }
    }
}
