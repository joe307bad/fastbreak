import java.io.File

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// Generate AppConfig from gradle.properties
val devMode = project.findProperty("dev_mode")?.toString()?.toBoolean() ?: true

val generateAppConfig = tasks.register("generateAppConfig") {
    val outputDir = layout.buildDirectory.dir("generated/appconfig")
    outputs.dir(outputDir)
    doLast {
        val dir = outputDir.get().asFile.resolve("com/joebad/fastbreak/config")
        dir.mkdirs()
        dir.resolve("AppConfig.kt").writeText("""
            package com.joebad.fastbreak.config

            object AppConfig {
                const val DEV_MODE: Boolean = $devMode
            }
        """.trimIndent())
    }
}

kotlin {
    androidTarget {
        compilations.all {
            compilerOptions.configure {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
            }
        }
    }

    sourceSets.all {
        languageSettings.optIn("androidx.compose.material3.ExperimentalMaterial3Api")
        languageSettings.optIn("com.mohamedrejeb.calf.permissions.ExperimentalPermissionsApi")
        languageSettings.optIn("kotlin.time.ExperimentalTime")
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateAppConfig.map { it.outputs.files.singleFile })
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.animation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)

            // Decompose
            implementation(libs.decompose)
            implementation(libs.decompose.compose)

            // Orbit MVI
            implementation(libs.orbit.core)
            implementation(libs.orbit.compose)

            // Koala Plot
            implementation(libs.koalaplot.core)

            // Multiplatform Settings
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.no.arg)

            // DateTime
            implementation(libs.kotlinx.datetime)

            // Kotlinx Serialization
            implementation(libs.kotlinx.serialization.json)

            // Ktor Client
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            // Calf Permissions
            implementation(libs.calf.permissions)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sentry.android)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

android {
    namespace = "com.example.kmpapp"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
