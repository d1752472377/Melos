package com.aesthetic.music.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.aesthetic.music.ui.components.ScaleIndication

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AestheticMusicTheme(content: @Composable () -> Unit) {
    val isDark = Palette.isDark
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDark
            }
        }
    }
    CompositionLocalProvider(
        LocalIndication provides ScaleIndication,
        LocalRippleConfiguration provides null,
        LocalAppIsDark provides isDark,
    ) {
        MaterialTheme(
            colorScheme = if (isDark) GzDarkColorScheme else GzLightColorScheme,
            typography = MiSansTypography,
            content = content,
        )
    }
}
