package com.joebad.fastbreak.ui.theme

import platform.UIKit.UIScreen
import platform.UIKit.UITraitCollection
import platform.UIKit.UIUserInterfaceStyle

actual class SystemThemeDetector {
    actual fun isSystemInDarkMode(): Boolean {
        val traitCollection = UIScreen.mainScreen.traitCollection
        return traitCollection.userInterfaceStyle == UIUserInterfaceStyle.UIUserInterfaceStyleDark
    }
}
