plugins {
    id("com.android.application")
    kotlin("android")
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)

    id("io.sentry.android.gradle") version "5.8.0"
}

android {
    namespace = "com.joebad.fastbreak.android"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.joebad.fastbreak.android"
        minSdk = 26
        targetSdk = 34
        versionCode = (project.findProperty("VERSION_CODE") as? String)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("VERSION_NAME") as? String) ?: "1.0.0"
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
    
    // Sentry SDK
    implementation("io.sentry:sentry-android:7.21.0")
}

sentry {
    org.set("joe-badaczewski")
    projectName.set("fastbreak")

    // this will upload your source code to Sentry to show it as part of the stack traces
    // disable if you don't want to expose your sources
    includeSourceContext.set(true)
}
