package com.joebad.fastbreak.data.model

// Declaration order is tab order on the home screen, and the first entry is the
// tab the app opens on (see HomeComponent.SavedState).
enum class Sport(val displayName: String) {
    NFL("NFL"),
    MLB("MLB"),
    NBA("NBA"),
    NHL("NHL"),
    CBB("CBB")
}
