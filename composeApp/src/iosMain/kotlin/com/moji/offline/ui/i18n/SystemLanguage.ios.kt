package com.moji.offline.ui.i18n

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

actual fun systemLanguageCode(): String {
    val tag = NSLocale.preferredLanguages.firstOrNull() as? String ?: return "en"
    return tag.substringBefore("-").substringBefore("_")
}
