import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import org.jetbrains.kotlin.konan.properties.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    id("com.codingfeline.buildkonfig")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
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
            //put your multiplatform dependencies here
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation("io.github.mirzemehdi:kmpauth-google:2.0.0")
            implementation("io.github.mirzemehdi:kmpauth-uihelper:2.0.0")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "com.joebad.fastbreak"
    compileSdk = 34
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

buildkonfig {
    val propertiesProj = Properties()
    packageName = "com.joebad.fastbreak"

    defaultConfigs {
        propertiesProj.load(project.rootProject.file("local.properties").inputStream())
        val apiKey: String = propertiesProj.getProperty("GOOGLE_AUTH_SERVER_ID")

        require(apiKey.isNotEmpty()) {
            "Register your GOOGLE_AUTH_SERVER_ID from developer and place it in local.properties as `GOOGLE_AUTH_SERVER_ID`"
        }

        buildConfigField(STRING, "GOOGLE_AUTH_SERVER_ID", apiKey)
    }
}
