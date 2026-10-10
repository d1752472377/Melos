package com.aesthetic.music.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import java.time.LocalTime

/**
 * GZ Music 主题骨架 —— 逐语义还原自
 * `gz_decompiled/sources/com/gzmusic/app/ui/theme/ThemeKt.java`
 *
 * 还原要点：
 *  - `ThemeKt.java:50`  `c = new if1(new uy7(21))`
 *      → `staticCompositionLocalOf<Boolean?> { null }`，即「当前是否深色」的本地覆盖值，
 *        默认 null 表示没有覆盖、回落到系统 Configuration。
 *  - `ThemeKt.java:633-645`  `appIsDark()`
 *      → 先读本地覆盖；为 null 时判断 `(Configuration.uiMode and 0x30) == 0x20`（UI_MODE_NIGHT_YES）。
 *  - `ThemeKt.java:133`  `GZMusicTheme(themeMode, dayStart, nightStart, content)`
 *      → 主题模式是字符串："system" / "light" / "dark" / "schedule"，
 *        schedule 用「一天内的分钟数」判定，默认 dayStart=360(06:00)、nightStart=1080(18:00)。
 *
 * 本项目集成方式：Melos 已有唯一的主题入口 `AestheticMusicTheme`（见 Theme.kt）与
 * 主题模式三档状态 `Palette.isDark`。这里不另建第二个主题入口，只提供 GZ 的
 * `LocalAppIsDark` + `appIsDark()`，由 `AestheticMusicTheme` 负责提供覆盖值，
 * 这样全部 GZ 样式 token 跟随同一个深浅色来源，不会出现两套主题打架。
 */

/** 对应 ThemeKt.java:50 —— 「当前是否深色」的本地覆盖，null 表示回落到系统 Configuration */
val LocalAppIsDark = staticCompositionLocalOf<Boolean?> { null }

/** 对应 ThemeKt.java:633 `appIsDark()` */
@Composable
fun appIsDark(): Boolean {
    LocalAppIsDark.current?.let { return it }
    // ThemeKt.java:642 —— (uiMode & 48) == 32 即 Configuration.UI_MODE_NIGHT_YES
    return (LocalConfiguration.current.uiMode and 0x30) == 0x20
}

// ===== 主题模式（对应 GZMusicTheme 的字符串模式） =====

/** ThemeKt.java:199 默认值 —— GZMusicTheme(themeMode = "system") */
const val GZ_THEME_MODE_SYSTEM = "system"

/** ThemeKt.java:217 —— themeMode == "dark" 强制深色 */
const val GZ_THEME_MODE_DARK = "dark"

/** ThemeKt.java:214 —— themeMode == "light" 强制浅色 */
const val GZ_THEME_MODE_LIGHT = "light"

/** ThemeKt.java:220 —— themeMode == "schedule" 按时间自动切换 */
const val GZ_THEME_MODE_SCHEDULE = "schedule"

/** ThemeKt.java:204 —— GZMusicTheme(dayStart = 360)，单位「一天内的分钟数」= 06:00 */
const val GZ_DEFAULT_DAY_START_MINUTES = 360

/** ThemeKt.java:207 —— GZMusicTheme(nightStart = 1080)，单位「一天内的分钟数」= 18:00 */
const val GZ_DEFAULT_NIGHT_START_MINUTES = 1080

/**
 * 把 GZ 的字符串主题模式映射到本项目已有的三档 [com.aesthetic.music.data.settings.ThemeMode]。
 * Melos 目前没有 schedule 档，schedule 由 [resolveScheduledDark] 在别处独立处理。
 */
fun gzThemeModeName(mode: com.aesthetic.music.data.settings.ThemeMode): String = when (mode) {
    com.aesthetic.music.data.settings.ThemeMode.SYSTEM -> GZ_THEME_MODE_SYSTEM
    com.aesthetic.music.data.settings.ThemeMode.LIGHT -> GZ_THEME_MODE_LIGHT
    com.aesthetic.music.data.settings.ThemeMode.DARK -> GZ_THEME_MODE_DARK
}

/**
 * `schedule` 模式的时间判定（ThemeKt.java:220-228）。
 *
 * 注意：JADX 在这里把三元表达式反编译坏了（ThemeKt.java:223 出现
 * `if (i8 > i6 ? minute < i8 || minute >= i6 : minute >= i6 && minute < i8)` 这种在
 * i8 > i6 时恒为真的写法，同一函数在 ThemeKt.java:303/401/488 处更是退化成了两个分支
 * 都赋值 false 的死代码块）。反编译结果自相矛盾，无法作为真值，因此这里采用唯一自洽的
 * 语义：**白天窗口 [dayStart, nightStart) 内为浅色，窗口外为深色**；跨夜窗口
 * （dayStart > nightStart）则取补集。这一处是显式标注的推断，不是从反编译文件抠出的值。
 */
fun resolveScheduledDark(
    minuteOfDay: Int,
    dayStartMinutes: Int = GZ_DEFAULT_DAY_START_MINUTES,
    nightStartMinutes: Int = GZ_DEFAULT_NIGHT_START_MINUTES,
): Boolean {
    val inDayWindow = if (nightStartMinutes > dayStartMinutes) {
        minuteOfDay >= dayStartMinutes && minuteOfDay < nightStartMinutes
    } else {
        // 跨夜窗口：白天跨过 00:00
        minuteOfDay >= dayStartMinutes || minuteOfDay < nightStartMinutes
    }
    return !inDayWindow
}

/** 便捷入口：按当前本地时间解析 schedule 模式 */
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
