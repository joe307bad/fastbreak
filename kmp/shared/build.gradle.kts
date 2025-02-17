import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.konan.properties.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    id("com.codingfeline.buildkonfig")
    kotlin("plugin.serialization") version "2.1.0"
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

    iosX64 {
        binaries.framework {
            baseName = "shared"
            isStatic = true
            binaryOption("bundleId", "com.joebad.fastbreak.shared")  // Replace with your bundle ID
        }
    }
    iosArm64 {
        binaries.framework {
            baseName = "shared"
            isStatic = true
            binaryOption("bundleId", "com.joebad.fastbreak.shared")  // Replace with your bundle ID
        }
    }
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "shared"
            isStatic = true
            binaryOption("bundleId", "com.joebad.fastbreak.shared")  // Replace with your bundle ID
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        binaries.library()
        browser {
            testTask {
                enabled = false
            }
        }
    }
    
//    listOf(
//        iosX64(),
//        iosArm64(),
//        iosSimulatorArm64()
//    ).forEach {
//        it.binaries.framework {
//            baseName = "shared"
//            isStatic = true
//        }
//    }

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
        }

        val nonWasmMain by creating {
            dependsOn(commonMain.get())

            dependencies {
                implementation("io.github.mirzemehdi:kmpauth-google:2.0.0")
                implementation("io.github.mirzemehdi:kmpauth-uihelper:2.0.0")
            }
        }

        // Create a new iosMain source set
        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting

        create("iosMain") {
            dependsOn(nonWasmMain)
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
        }

        androidMain {
            dependsOn(nonWasmMain)

            dependencies {
                implementation("com.arkivanov.decompose:extensions-android:3.2.2")
            }
        }

        iosMain {
            dependsOn(nonWasmMain)
        }

        wasmJsMain {
            dependencies {
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