package com.aesthetic.music.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * 全局调色板状态：深色/浅色切换。
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

// ===== 基础底色系统 =====
val Background get() = light(Color(0xFFF6F8FA), Color(0xFF101114))      // 极简通透象牙冷白 / 深邃曜石黑
val Surface get() = light(Color(0xFFFFFFFF), Color(0xFF1B1C22))         // 卡片纯白 / 质感深灰
val SurfaceSoft get() = light(Color(0xFFEFF2F6), Color(0xFF24262E))     // 浅层容器背景
val SurfaceElevated get() = light(Color(0xFFFFFFFF), Color(0xFF22242C)) // 悬浮层表面

// ===== 活力主色与光感强调色 =====
val PrimaryBrown get() = light(Color(0xFF5352ED), Color(0xFF7052FF))    // 活力电光蓝紫（主强调色）
val PrimaryBrownDark get() = light(Color(0xFF3742FA), Color(0xFF5352ED))
val PrimaryGradientStart get() = light(Color(0xFF6C5CE7), Color(0xFF8B78FF))
val PrimaryGradientEnd get() = light(Color(0xFF5352ED), Color(0xFF5D38E5))

// ===== 活泼多维度场景强调色 =====
val AccentCoral = Color(0xFFFF4757)       // 炽烈落日珊瑚红（收藏/榜一/热烈）
val AccentMint = Color(0xFF2ED573)        // 鲜亮薄荷翠绿（流行/播放中/活跃）
val AccentCyan = Color(0xFF1E90FF)        // 天青极光蓝（电音/发现/统计）
val AccentAmber = Color(0xFFFFA502)       // 暖阳琥珀金（民谣/VIP/经典）
val AccentRose = Color(0xFFFF6B81)        // 暮云玫瑰粉（摇滚/爱心）

val PlayerBg get() = light(Color(0xFF1E1F29), Color(0xFF14151B))        // 播放页雅致黑紫
val PlayerBgDark get() = light(Color(0xFF13141A), Color(0xFF0C0D11))

val TagBg get() = light(Color(0xFFEEF2FF), Color(0xFF27293D))           // 标签背景
val TagText get() = light(Color(0xFF4F46E5), Color(0xFFA5B4FC))         // 标签文字

val Amber = AccentAmber
val AmberLight = Color(0xFFFFEAA7)
val AmberDark = Color(0xFFD35400)
val Rose = AccentCoral

// ===== 精致灰阶体系 =====
val TextPrimary get() = light(Color(0xFF12141A), Color(0xFFF7F9FC))     // 一级主标题（极黑/高光白）
val TextStrong get() = light(Color(0xFF2C313E), Color(0xFFDCE2EC))      // 加强正文
val TextMuted get() = light(Color(0xFF5E6778), Color(0xFF9AA4B5))       // 次级说明
val TextSoft get() = light(Color(0xFF8893A7), Color(0xFF6F798B))        // 辅助文字/副标题
val TextFaint get() = light(Color(0xFFCCD4E0), Color(0xFF404654))       // 极淡图标/占位/微弱边框

val Stone200 get() = light(Color(0xFFE2E7ED), Color(0xFF2A2D38))        // 1dp 精致线框描边
val Stone100 get() = light(Color(0xFFF1F4F8), Color(0xFF1F222B))        // 按钮浅底色/次级卡片
