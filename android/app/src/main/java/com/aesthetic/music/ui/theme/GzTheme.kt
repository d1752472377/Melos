package com.aesthetic.music.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import java.time.LocalTime

val LocalAppIsDark = staticCompositionLocalOf<Boolean?> { null }

@Composable
fun appIsDark(): Boolean {
    LocalAppIsDark.current?.let { return it }
    return (LocalConfiguration.current.uiMode and 0x30) == 0x20
}

const val GZ_THEME_MODE_SYSTEM = "system"

const val GZ_THEME_MODE_DARK = "dark"

const val GZ_THEME_MODE_LIGHT = "light"

const val GZ_THEME_MODE_SCHEDULE = "schedule"

const val GZ_DEFAULT_DAY_START_MINUTES = 360

const val GZ_DEFAULT_NIGHT_START_MINUTES = 1080

fun gzThemeModeName(mode: com.aesthetic.music.data.settings.ThemeMode): String = when (mode) {
    com.aesthetic.music.data.settings.ThemeMode.SYSTEM -> GZ_THEME_MODE_SYSTEM
    com.aesthetic.music.data.settings.ThemeMode.LIGHT -> GZ_THEME_MODE_LIGHT
    com.aesthetic.music.data.settings.ThemeMode.DARK -> GZ_THEME_MODE_DARK
}

fun resolveScheduledDark(
    minuteOfDay: Int,
    dayStartMinutes: Int = GZ_DEFAULT_DAY_START_MINUTES,
    nightStartMinutes: Int = GZ_DEFAULT_NIGHT_START_MINUTES,
): Boolean {
    val inDayWindow = if (nightStartMinutes > dayStartMinutes) {
        minuteOfDay >= dayStartMinutes && minuteOfDay < nightStartMinutes
    } else {
        minuteOfDay >= dayStartMinutes || minuteOfDay < nightStartMinutes
    }
    return !inDayWindow
}

fun resolveScheduledDarkNow(
    dayStartMinutes: Int = GZ_DEFAULT_DAY_START_MINUTES,
    nightStartMinutes: Int = GZ_DEFAULT_NIGHT_START_MINUTES,
): Boolean {
    val now = LocalTime.now()
    return resolveScheduledDark(
        minuteOfDay = now.minute + now.hour * 60,
        dayStartMinutes = dayStartMinutes,
        nightStartMinutes = nightStartMinutes,
    )
}
