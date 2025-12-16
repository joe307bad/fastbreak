import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(localPropertiesFile.inputStream())
    }
}

android {
    namespace = "com.joebad.fastbreak"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.joebad.fastbreak"
        minSdk = 24
        targetSdk = 35
        versionCode = (project.findProperty("VERSION_CODE") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("VERSION_NAME") as String?) ?: "1.0"
    }

    val releaseStoreFile = localProperties.getProperty("RELEASE_STORE_FILE", "")
    if (releaseStoreFile.isNotEmpty()) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD", "")
                keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS", "")
                keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD", "")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseStoreFile.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.decompose)
    implementation(libs.essenty.lifecycle)
    implementation(libs.multiplatform.settings.no.arg)
    implementation(libs.ktor.client.core)
    implementation(libs.sentry.android)
}
