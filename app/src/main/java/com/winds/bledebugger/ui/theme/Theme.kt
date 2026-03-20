package com.winds.bledebugger.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Blue80,
    onPrimary = AppOnPrimaryDark,
    secondary = Cyan80,
    onSecondary = AppOnSecondaryDark,
    tertiary = Mint80,
    onTertiary = AppOnTertiaryDark,
    background = AppBackgroundDark,
    onBackground = AppOnBackgroundDark,
    surface = AppSurfaceDark,
    onSurface = AppOnSurfaceDark,
    surfaceVariant = AppSurfaceVariantDark,
    onSurfaceVariant = AppOnSurfaceVariantDark,
    outline = AppOutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = Blue40,
    onPrimary = AppOnPrimaryLight,
    secondary = Cyan40,
    onSecondary = AppOnSecondaryLight,
    tertiary = Mint40,
    onTertiary = AppOnTertiaryLight,
    background = AppBackgroundLight,
    onBackground = AppOnBackgroundLight,
    surface = AppSurfaceLight,
    onSurface = AppOnSurfaceLight,
    surfaceVariant = AppSurfaceVariantLight,
    onSurfaceVariant = AppOnSurfaceVariantLight,
    outline = AppOutlineLight
)

@Composable
fun BLEdebuggerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
