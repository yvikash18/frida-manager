package com.prapps.fridaserverinstaller.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = ElectricViolet,
    onPrimary = TextWhite,
    primaryContainer = ElectricViolet,
    onPrimaryContainer = TextWhite,
    secondary = CyberCyan,
    onSecondary = TextWhite,
    secondaryContainer = CyberCyan,
    onSecondaryContainer = TextWhite,
    tertiary = CyberCyan,
    onTertiary = TextWhite,
    background = MidnightVoid,
    onBackground = TextWhite,
    surface = ShieldGrey,
    onSurface = TextWhite,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = MutedSlate,
    outline = Divider,
    error = ErrorRed,
    onError = TextWhite
)

private val LightColorScheme = lightColorScheme(
    primary = ElectricViolet,
    onPrimary = TextWhite,
    primaryContainer = ElectricViolet.copy(alpha = 0.2f),
    onPrimaryContainer = Color.Black,
    secondary = CyberCyan,
    onSecondary = TextWhite,
    tertiary = CyberCyan,
    background = Color(0xFFF5F5F5),
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE0E0E0),
    onSurfaceVariant = Color.Gray,
    outline = Color.LightGray,
    error = ErrorRed,
    onError = TextWhite
)

@Composable
fun FridaServerInstallerTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val bgColor = if (darkTheme) MidnightVoid else Color(0xFFF5F5F5)
            window.statusBarColor = bgColor.toArgb()
            window.navigationBarColor = bgColor.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}