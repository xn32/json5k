package buildsrc.config

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import javax.inject.Inject

/**
 * Common properties used to build json5k.
 *
 * The properties need to be accessible during configuration, so they should be static across all subprojects.
 * They can be sourced from Gradle properties or environment variables.
 */
abstract class Json5kBuildProps @Inject constructor(
    project: Project,
    private val providers: ProviderFactory,
) {

    val jvmTarget: Provider<String> = json5kSetting("jvmTarget", "1.8")


    // can be set with environment variables: e.g. ORG_GRADLE_PROJECT_ossrhUsername=my-username
    val sonatypeUsername: Provider<String> = providers.gradleProperty("sonatypeUsername")
    val sonatypePassword: Provider<String> = providers.gradleProperty("sonatypePassword")
    val signingKey: Provider<String> = providers.gradleProperty("signingKey")
    val signingPassword: Provider<String> = providers.gradleProperty("signingPassword")

    val projectVersion = providers.provider { project.version }
    val isReleaseVersion = projectVersion.map { version ->
        version.toString().matches(Json5kBuildProps.releaseVersionRegex)
    }

    /**
     * Helper function to create a Gradle compatible [Provider] that maps both [projectVersion] and [isReleaseVersion]
     * into a new value.
     */
    fun <T> projectVersion(map: (version: String, isRelease: Boolean) -> T): Provider<T> =
        providers.zip(projectVersion, isReleaseVersion) { version, isRelease ->
            map(version.toString(), isRelease)
        }

    /**
     * Comma separated list of MavenPublication names that will have the publishing task enabled.
     * The provided names will be matched ignoring case, and by prefix, so `iOS` will match
     * `iosArm64`, `iosX64`, and `iosSimulatorArm64`.
     *
     * This is used to avoid duplicate publications, which can occur when a Kotlin Multiplatform
     * project is published in CI/CD on different host machines (Linux, Windows, and macOS).
     *
     * For example, by including `jvm` in the values when publishing on Linux, but omitting `jvm` on
     * Windows and macOS, this results in any Kotlin/JVM publications only being published once.
     */
    val enabledPublicationNamePrefixes: Provider<Set<String>> =
        json5kSetting("enabledPublicationNamePrefixes", "KotlinMultiplatform,Jvm,Js,iOS,macOS,watchOS,tvOS,mingw")
            .map { enabledPlatforms ->
                enabledPlatforms
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toSet()
            }

    private fun json5kSetting(name: String, default: String? = null): Provider<String> =
        providers.gradleProperty("${JSON5K_PROP_PREFIX}_$name")
            .orElse(providers.provider { default }) // workaround for https://github.com/gradle/gradle/issues/12388

    private fun json5kFlag(name: String, default: Boolean): Provider<Boolean> =
        providers.gradleProperty("${JSON5K_PROP_PREFIX}_$name").map { it.toBoolean() }.orElse(default)

    companion object {
        const val EXTENSION_NAME = "json5kBuildProps"
        private const val JSON5K_PROP_PREFIX = "json5k"

        /**
         * Regex for matching the release version.
         *
         * If a version does not match it should be treated as a SNAPSHOT version.
         */
        val releaseVersionRegex = Regex("\\d\\+.\\d\\+.\\d+")
    }
}
