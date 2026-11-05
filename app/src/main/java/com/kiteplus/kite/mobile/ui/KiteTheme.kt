package com.kiteplus.kite.mobile.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Deliberately plain. An editor should disappear behind the text, and the app
 * is read in daylight on a phone far more often than in a dark room — but both
 * are offered because an evening draft is a real thing.
 */
@Composable
fun KiteTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colors, content = content)
}
