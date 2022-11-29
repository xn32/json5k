rootProject.name = "json5k"


@Suppress("UnstableApiUsage") // centralised repositories are incubating
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)

  repositories {
    mavenCentral()

    // workaround for https://youtrack.jetbrains.com/issue/KT-51379
    exclusiveContent {
      forRepository {
        ivy("https://download.jetbrains.com/kotlin/native/builds") {
          name = "Kotlin Native"
          patternLayout {
            listOf(
              "macos-x86_64",
              "macos-aarch64",
              "osx-x86_64",
              "osx-aarch64",
              "linux-x86_64",
              "windows-x86_64",
            ).forEach { os ->
              listOf("dev", "releases").forEach { stage ->
                artifact("$stage/[revision]/$os/[artifact]-[revision].[ext]")
              }
            }
          }
          metadataSources { artifact() }
        }
      }
      filter { includeModuleByRegex(".*", ".*kotlin-native-prebuilt.*") }
    }
  }

  pluginManagement {
    repositories {
      gradlePluginPortal()
      mavenCentral()
    }
  }
}
