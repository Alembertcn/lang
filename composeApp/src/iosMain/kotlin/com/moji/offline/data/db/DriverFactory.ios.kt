package com.moji.offline.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.moji.offline.db.MojiDatabase

actual class DriverFactory actual constructor() {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(MojiDatabase.Schema, "moji.db")
}
