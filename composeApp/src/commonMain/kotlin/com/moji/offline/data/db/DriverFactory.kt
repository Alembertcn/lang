package com.moji.offline.data.db

import app.cash.sqldelight.db.SqlDriver

/** Platform SQLite driver for the local user database (`moji.db`). */
expect class DriverFactory() {
    fun createDriver(): SqlDriver
}
