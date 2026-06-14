package com.joebad.fastbreak.platform

import com.example.kmpapp.BuildConfig

actual object BuildFlags {
    actual val isDebugBuild: Boolean = BuildConfig.DEBUG
}
