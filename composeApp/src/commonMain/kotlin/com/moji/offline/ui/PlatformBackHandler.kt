package com.moji.offline.ui

import androidx.compose.runtime.Composable

/** System back (Android) / no-op (iOS, which uses the on-screen back). */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
