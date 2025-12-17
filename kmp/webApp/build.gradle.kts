@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import java.net.URL
import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "fastbreak-web.js"
            }
        }
        binaries.executable()
    }

    sourceSets.all {
        languageSettings.optIn("kotlin.time.ExperimentalTime")
        languageSettings.optIn("org.jetbrains.compose.resources.ExperimentalResourceApi")
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.components.resources)
            }
        }
        val wasmJsMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)

                // Koala Plot for charts
                implementation(libs.koalaplot.core)

                // DateTime
                implementation(libs.kotlinx.datetime)

                // Kotlinx Serialization for JSON parsing
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}

// Task to pre-fetch chart data at build time
val prefetchChartData by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/chartData")
    outputs.dir(outputDir)

    doLast {
        val baseUrl = "https://d2jyizt5xogu23.cloudfront.net"
        val registryUrl = "$baseUrl/registry"

        val outputDirFile = outputDir.get().asFile
        outputDirFile.mkdirs()

        var registryJson = "[]"
        var chartFiles = emptyList<File>()

        try {
            println("Fetching registry from $registryUrl")
            registryJson = URL(registryUrl).readText()

            // Save registry
            File(outputDirFile, "registry.json").writeText(registryJson)
            println("Saved registry.json")

            // Parse registry (it's a map where keys are file paths)
            @Suppress("UNCHECKED_CAST")
            val registry = JsonSlurper().parseText(registryJson) as Map<String, Any>

            registry.keys.forEach { fileKey ->
                val chartUrl = "$baseUrl/$fileKey"

                try {
                    println("Fetching chart: $fileKey")
                    val chartJson = URL(chartUrl).readText()
                    // Use just the filename without path for storage
                    val fileName = fileKey.substringAfterLast("/")
                    File(outputDirFile, fileName).writeText(chartJson)
                    println("Saved $fileName")
                } catch (e: Exception) {
                    println("Warning: Failed to fetch $fileKey: ${e.message}")
                }
            }

            chartFiles = outputDirFile.listFiles()?.filter { it.extension == "json" && it.name != "registry.json" } ?: emptyList()
        } catch (e: Exception) {
            println("Warning: Failed to fetch registry: ${e.message}")
            println("Building with empty chart data - charts will be loaded at runtime")
        }

        // Generate a Kotlin file with chart data embedded
        val generatedDir = File(outputDirFile, "kotlin/com/joebad/fastbreak/web")
        generatedDir.mkdirs()

        val chartDataCode = buildString {
            appendLine("package com.joebad.fastbreak.web")
            appendLine()
            appendLine("// Auto-generated file - do not edit")
            appendLine("object BundledChartData {")
            appendLine("    val registry: String = \"\"\"")
            appendLine(registryJson.replace("\"\"\"", "\\\"\\\"\\\""))
            appendLine("\"\"\"")
            appendLine()
            appendLine("    val charts: Map<String, String> = mapOf(")
            chartFiles.forEachIndexed { index, file ->
                val comma = if (index < chartFiles.size - 1) "," else ""
                appendLine("        \"${file.name}\" to \"\"\"")
                appendLine(file.readText().replace("\"\"\"", "\\\"\\\"\\\""))
                appendLine("\"\"\"$comma")
            }
            appendLine("    )")
            appendLine("}")
        }

        File(generatedDir, "BundledChartData.kt").writeText(chartDataCode)
        println("Generated BundledChartData.kt with ${chartFiles.size} charts")
    }
}

// Make wasmJs compilation depend on prefetch task
tasks.named("compileKotlinWasmJs") {
    dependsOn(prefetchChartData)
}

// Add generated sources to compilation
kotlin.sourceSets.getByName("wasmJsMain") {
    kotlin.srcDir(layout.buildDirectory.dir("generated/chartData/kotlin"))
}
