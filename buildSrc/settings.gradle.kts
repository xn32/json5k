rootProject.name = "buildSrc"


@Suppress("UnstableApiUsage") // centralised repositories are incubating
dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }

    pluginManagement {
        repositories {
            gradlePluginPortal()
            mavenCentral()
        }
    }
}
