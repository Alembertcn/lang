package com.moji.offline.data.db

import com.moji.offline.db.MojiDatabase

/**
 * Local user database (joined plans, article progress, favorites, events).
 * Lazily opened so previews/tests that never touch it pay nothing.
 */
object MojiDb {
    val database: MojiDatabase by lazy { MojiDatabase(DriverFactory().createDriver()) }

    val userData get() = database.userDataQueries
}
