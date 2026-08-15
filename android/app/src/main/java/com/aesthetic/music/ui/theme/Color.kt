package com.aesthetic.music.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * 全局调色板状态：深色/浅色切换（三档主题模式在入口处写入 isDark）。
 * 所有颜色通过 getter 动态返回，Compose 中读取时自动参与重组。
 */
object Palette {
    var isDark by mutableStateOf(false)
}

/** 应用三档主题模式（systemDark 为系统当前是否深色） */
fun applyThemeMode(mode: com.aesthetic.music.data.settings.ThemeMode, systemDark: Boolean) {
    Palette.isDark = when (mode) {
        com.aesthetic.music.data.settings.ThemeMode.SYSTEM -> systemDark
        com.aesthetic.music.data.settings.ThemeMode.LIGHT -> false
        com.aesthetic.music.data.settings.ThemeMode.DARK -> true
    }
}

private fun light(c: Color, d: Color) = if (Palette.isDark) d else c

// ===== 从参考 HTML 提取的配色（浅色） + 深色对应值 =====
val Background get() = light(Color(0xFFF6F5F2), Color(0xFF141416))      // 页面背景
val Surface get() = light(Color(0xFFFFFFFF), Color(0xFF1E1E22))         // 卡片白
val SurfaceSoft get() = light(Color(0xFFF5F5F4), Color(0xFF26262B))     // stone-100

val PrimaryBrown get() = light(Color(0xFF534332), Color(0xFF8A7354))    // 主按钮深棕（深色下提亮）
val PrimaryBrownDark get() = light(Color(0xFF3D3124), Color(0xFF6B5942))
val PlayerBg get() = light(Color(0xFF3A2E24), Color(0xFF2A211B))        // 播放页深棕
val PlayerBgDark get() = light(Color(0xFF271F18), Color(0xFF1B1511))

val TagBg get() = light(Color(0xFFEFEAD8), Color(0xFF2E2A22))           // 标签背景
val TagText get() = light(Color(0xFF786244), Color(0xFFD8C9A8))         // 标签文字

val Amber = Color(0xFFF59E0B)
val AmberLight = Color(0xFFFDE68A)
val AmberDark = Color(0xFFB45309)

// stone 灰阶
val TextPrimary get() = light(Color(0xFF1C1917), Color(0xFFF2F1EE))     // 主文字
val TextStrong get() = light(Color(0xFF292524), Color(0xFFE7E5E4))
val TextMuted get() = light(Color(0xFF78716C), Color(0xFF9E9891))
val TextSoft get() = light(Color(0xFFA8A29E), Color(0xFF7C756E))        // 次要文字
val TextFaint get() = light(Color(0xFFD6D3D1), Color(0xFF4A453F))
val Stone200 get() = light(Color(0xFFE7E5E4), Color(0xFF333238))        // 边框/图标底
val Stone100 get() = light(Color(0xFFF5F5F4), Color(0xFF2A2A2F))

val Rose = Color(0xFFF43F5E)
