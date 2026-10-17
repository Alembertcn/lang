package com.moji.offline.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.moji.offline.AppContext
import com.moji.offline.db.MojiDatabase

actual class DriverFactory actual constructor() {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(MojiDatabase.Schema, AppContext.context, "moji.db")
}
