plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
                freeCompilerArgs += listOf("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
            }
        }
    }

    sourceSets.all {
        languageSettings.optIn("androidx.compose.material3.ExperimentalMaterial3Api")
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
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)

            // Decompose
            implementation(libs.decompose)
            implementation(libs.decompose.compose)

            // Orbit MVI
            implementation(libs.orbit.core)
            implementation(libs.orbit.compose)

            // Koala Plot
            implementation(libs.koalaplot.core)

            // Cupertino
            implementation(libs.cupertino)
            implementation(libs.cupertino.adaptive)

            // Multiplatform Settings
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.no.arg)

            // DateTime
            implementation(libs.kotlinx.datetime)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
    }
}

android {
    namespace = "com.example.kmpapp"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
