import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.7.22"))

    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.22")
    implementation("org.jetbrains.kotlin:kotlin-serialization:1.7.22")
}

val buildLogicJvmTarget = "11"


kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(buildLogicJvmTarget))
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = buildLogicJvmTarget
    }
}

kotlinDslPluginOptions {
    jvmTarget.set(buildLogicJvmTarget)
}
