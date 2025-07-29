plugins {
    id("com.android.application")
    kotlin("android")
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.joebad.fastbreak.android"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.joebad.fastbreak.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField(
            "String",
            "GOOGLE_AUTH_SERVER_ID",
            "\"" + (project.findProperty("GOOGLE_AUTH_SERVER_ID") ?: "") + "\""
        )
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    signingConfigs {
        create("release") {
            keyAlias = project.findProperty("RELEASE_KEY_ALIAS")?.toString()
            keyPassword = project.findProperty("RELEASE_KEY_PASSWORD")?.toString()
            storeFile = project.findProperty("RELEASE_STORE_FILE")?.toString()?.let { file(it) }
            storePassword = project.findProperty("RELEASE_STORE_PASSWORD")?.toString()
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    implementation(projects.shared)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    debugImplementation(libs.compose.ui.tooling)

    implementation("com.arkivanov.decompose:decompose:3.2.2")
    implementation("com.arkivanov.decompose:extensions-compose:3.2.2")
    implementation("com.arkivanov.decompose:extensions-android:3.2.2")
    implementation("com.liftric:kvault:1.12.0")
}