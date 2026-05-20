package com.joebad.fastbreak.db

import app.cash.sqldelight.db.SqlDriver

/**
 * Factory for creating platform-specific SQLite drivers.
 * Uses expect/actual pattern for KMP compatibility.
 */
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
