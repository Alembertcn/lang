package com.moji.offline.ui

import androidx.compose.runtime.Composable

/**
 * The app switches themes in-app (MojiTheme), so the system bar icon contrast
 * has to follow LocalDarkTheme instead of the OS setting; every screen paints
 * its own top bar under the status bar (edge-to-edge, no double inset).
 */
@Composable
expect fun SystemBarAppearance(darkTheme: Boolean)
