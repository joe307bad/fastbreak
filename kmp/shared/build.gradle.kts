
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
    androidTarget()
    iosArm64()
    iosX64()
    iosSimulatorArm64()

    cocoapods {
        version = "1.0"
        summary = "Fastbreak"
        homepage = "https://joebad.com/"
        ios.deploymentTarget = "14.1"
        podfile = project.file("../iosApp/Podfile")
        pod("CouchbaseLite") {
            version = libs.versions.couchbase.lite.c.get()
            linkOnly = true
        }

        pod("GoogleSignIn", linkOnly = true)
        pod("FirebaseCore", linkOnly = true)
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotbase)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation("com.arkivanov.decompose:decompose:3.2.2")
            implementation("com.arkivanov.decompose:extensions-compose:3.2.2")
            implementation("com.arkivanov.decompose:extensions-compose-experimental:3.2.2")
            implementation(libs.cupertino.adaptive)
            implementation(libs.cupertino.iconsExtended)
            implementation(libs.kotbase)
            implementation("io.github.mirzemehdi:kmpauth-google:2.0.0")
            implementation("io.github.mirzemehdi:kmpauth-uihelper:2.0.0")
//            implementation(files("libs/tabler-icons-release.aar"))
        }

        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by creating {
            dependsOn(commonMain.get())
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
        }

        androidMain.dependencies {
            implementation(libs.compose.ui.tooling.preview)
            api(libs.androidx.activity.compose)
            implementation("com.arkivanov.decompose:extensions-android:3.2.2")

        }
    }
}

android {
    namespace = "com.joebad.fastbreak"
    compileSdk = 34
    defaultConfig {
        minSdk = 24
    }
    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    sourceSets {
        getByName("main") {
            assets.srcDir("src/main/assets")
        }
    }
}
dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.ui.text.google.fonts)
    implementation(libs.androidx.ui.text.android)
    implementation(libs.androidx.core.i18n)
    implementation(libs.androidx.ui.android)
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
        buildConfigField(com.codingfeline.buildkonfig.compiler.FieldSpec.Type.BOOLEAN, "IS_DEBUG", "false")
    }

    targetConfigs {
        create("android") {
            buildConfigField(com.codingfeline.buildkonfig.compiler.FieldSpec.Type.BOOLEAN, "IS_DEBUG", "true")
        }
    }
}

tasks.register<Copy>("copyFontsToAndroidAssets") {
    from("src/commonMain/resources/font")
    into("../androidApp/src/main/assets/font")
}

tasks.named("preBuild") {
    dependsOn("copyFontsToAndroidAssets")
}