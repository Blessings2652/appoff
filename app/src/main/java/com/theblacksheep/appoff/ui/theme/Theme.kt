package com.theblacksheep.appoff.ui.theme

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import com.theblacksheep.appoff.ui.AppTheme

private val DarkColorScheme = darkColorScheme(
    primary = Accent,
    secondary = Accent,
    tertiary = Accent,
    background = TrueDark,
    surface = SurfaceDark,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
)

private val BlackColorScheme = darkColorScheme(
    primary = Accent,
    secondary = Accent,
    tertiary = Accent,
    background = Color.Black,
    surface = Color(0xFF0D1017),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color.Gray,
)

private val LightColorScheme = lightColorScheme(
    primary = Accent,
    secondary = Accent,
    tertiary = Accent,
    background = Color(0xFFF8F9FA),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black,
    onSurfaceVariant = Color(0xFF5F6368),
)

@Composable
fun AppOffTheme(
    appTheme: AppTheme = AppTheme.DARK,
    primaryColor: Color = Accent,
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (appTheme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK, AppTheme.BLACK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }

    val baseColorScheme = when {
        appTheme == AppTheme.BLACK -> BlackColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val colorScheme = baseColorScheme.copy(
        primary = primaryColor,
        secondary = primaryColor,
        tertiary = primaryColor
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val context = view.context
            val activity = context as? Activity 
                ?: (context as? ContextWrapper)?.baseContext as? Activity
            
            activity?.window?.let { window ->
                @Suppress("DEPRECATION")
                window.statusBarColor = Color.Transparent.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    val currentDensity = LocalDensity.current
    val customDensity = Density(
        density = currentDensity.density,
        fontScale = fontScale
    )

    CompositionLocalProvider(
        LocalDensity provides customDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

@Composable
fun DeepRamCleanerTheme(
    appTheme: AppTheme = AppTheme.DARK,
    primaryColor: Color = Accent,
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit,
) {
    AppOffTheme(
        appTheme = appTheme,
        primaryColor = primaryColor,
        fontScale = fontScale,
        content = content
    )
}
