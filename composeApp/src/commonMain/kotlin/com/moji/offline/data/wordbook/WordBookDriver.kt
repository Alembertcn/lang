package com.moji.offline.data.wordbook

import app.cash.sqldelight.db.SqlDriver

/** Opens the read-only word book copied out of the app resources. */
expect fun openWordBookDriver(): SqlDriver
