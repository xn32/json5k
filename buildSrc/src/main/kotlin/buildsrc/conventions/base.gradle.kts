package buildsrc.conventions

import buildsrc.config.Json5kBuildProps

plugins {
    base
}

// common config for all projects

if (project != rootProject) {
    project.version = rootProject.version
    project.group = rootProject.group
}

extensions.create(Json5kBuildProps.EXTENSION_NAME, Json5kBuildProps::class, project)

tasks.withType<AbstractArchiveTask>().configureEach {
    // https://docs.gradle.org/current/userguide/working_with_files.html#sec:reproducible_archives
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
