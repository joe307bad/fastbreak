import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
  alias(libs.plugins.jetbrainsCompose)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlinMultiplatform)
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
  @OptIn(ExperimentalWasmDsl::class)
  wasmJs {
    moduleName = "app"
    browser {
      commonWebpackConfig {
        outputFileName = "app.js"
      }
    }
    binaries.executable()
  }

  sourceSets {
    commonMain.dependencies {
      implementation(compose.runtime)
      implementation(compose.foundation)
      implementation(compose.material)
      implementation(compose.ui)
      @OptIn(ExperimentalComposeLibrary::class)
      implementation(compose.components.resources)
      implementation(project(":shared"))
    }
  }
}

compose.experimental {
  web.application {}
}