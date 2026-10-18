package com.moji.offline.ui

import android.content.Intent
import com.moji.offline.AppContext

actual fun sharePlainText(text: String) {
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(share, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    AppContext.context.startActivity(chooser)
}
