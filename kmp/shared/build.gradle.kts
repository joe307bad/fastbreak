import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import org.jetbrains.kotlin.konan.properties.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    id("com.codingfeline.buildkonfig")
    kotlin("plugin.serialization") version "2.1.0"
    kotlin("native.cocoapods")
}

tasks {
    withType<Test> {
        enabled = false
    }
    register("testClasses") {
        enabled = false
    }
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }


    cocoapods {
        version = "1.0"
        summary = "Kotbase Getting Started Compose Multiplatform"
        homepage = "https://kotbase.dev/"
        ios.deploymentTarget = "14.1"
        podfile = project.file("../iosApp/Podfile")
        pod("CouchbaseLite") {
            version = libs.versions.couchbase.lite.c.get()
            linkOnly = true
        }
    }

    iosArm64()
    iosSimulatorArm64()

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "shared"
            isStatic = false  // Try setting this to false if it's currently true
            linkerOpts("-ObjC") // Ensure Obj-C symbols are linked
            export(libs.kotbase)
            binaryOption("bundleId", "com.joebad.fastbreak.shared")

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
            implementation("com.arkivanov.decompose:decompose:3.2.2")
            implementation("com.arkivanov.decompose:extensions-compose:3.2.2")
            implementation(libs.cupertino.adaptive)
            implementation(libs.cupertino.iconsExtended)
            implementation(libs.kotbase)
        }

        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting

        val iosMain by creating {
            dependsOn(commonMain.get())
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)

            dependencies {
                implementation("org.jetbrains.compose.ui:ui:1.7.3")
                implementation(compose.ui)
            }
        }

        androidMain {
            dependsOn(commonMain.get())

            dependencies {
                implementation("io.github.mirzemehdi:kmpauth-google:2.0.0")
                implementation("io.github.mirzemehdi:kmpauth-uihelper:2.0.0")
                implementation("com.arkivanov.decompose:extensions-android:3.2.2")
            }
        }

        iosMain {
            dependsOn(commonMain.get())

            dependencies {
                api(libs.kotbase)
            }
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
dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
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