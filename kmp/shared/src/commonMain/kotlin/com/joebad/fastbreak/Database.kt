package com.joebad.fastbreak

import kotbase.Database

class FastBreakDatabase {

    private var database: Database? = null

    // Create a database
    fun createDb() {
        database = Database("FastBreakDatabase")
        Log.i(TAG, "Database created: $database")
    }
    private companion object {
        private const val TAG = "SHARED_KOTLIN"
    }
}