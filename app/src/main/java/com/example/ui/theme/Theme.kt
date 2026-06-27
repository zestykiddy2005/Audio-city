package com.example.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    secondary = VocalLavender,
    tertiary = LuxeGold,
    background = DeepMidnight,
    surface = SlateDark,
    onPrimary = DeepMidnight,
    onSecondary = DeepMidnight,
    onBackground = TextLight,
    onSurface = TextWhite
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force Dark mode for a premium dark music experience
    dynamicColor: Boolean = false, // Use our handcrafted luxury palette instead of system colors
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
