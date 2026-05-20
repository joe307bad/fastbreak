package com.joebad.fastbreak.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

/**
 * iOS implementation of DatabaseDriverFactory.
 * Uses NativeSqliteDriver for native SQLite access.
 */
actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(
            schema = FastbreakDatabase.Schema,
            name = "fastbreak.db"
        )
    }
}
