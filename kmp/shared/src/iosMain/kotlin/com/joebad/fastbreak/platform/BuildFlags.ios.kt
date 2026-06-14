package com.joebad.fastbreak.platform

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

@OptIn(ExperimentalNativeApi::class)
actual object BuildFlags {
    actual val isDebugBuild: Boolean = Platform.isDebugBinary
}
