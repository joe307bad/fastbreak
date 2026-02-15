package com.joebad.fastbreak.ui.bracket

import platform.UIKit.UIView

/**
 * Holds a factory function for creating the native Filament bracket UIView.
 * Set from the iOS app before creating the MainViewController.
 */
object BracketViewFactory {
    var createView: (() -> UIView)? = null
}
