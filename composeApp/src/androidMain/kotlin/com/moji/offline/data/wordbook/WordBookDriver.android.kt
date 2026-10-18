package com.moji.offline.data.wordbook

import android.util.Log
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.moji.offline.AppContext
import com.moji.offline.wordbook.WordBookDatabase
import java.io.FileOutputStream

private const val ASSET = "composeResources/com.moji.offline.resources/files/wordbook.db"

actual fun openWordBookDriver(): SqlDriver {
    val context = AppContext.context
    val target = context.getDatabasePath("wordbook.db")
    if (!target.exists() || target.length() < 1_000_000L) {
        target.parentFile?.mkdirs()
        context.assets.open(ASSET).use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output, 1024 * 1024) }
        }
        Log.d("WordBook", "copied wordbook ${target.length()}")
    }
    return AndroidSqliteDriver(WordBookDatabase.Schema, context, "wordbook.db")
}
