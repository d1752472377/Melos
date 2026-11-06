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
val Background get() = Color.Transparent      // 默认通透，透出全局底层 iOS 拟态环境光
val Surface get() = light(Color(0xFFFFFFFF), Color(0xFF1B1C22))         // 卡片纯白 / 质感深灰
val SurfaceSoft get() = light(Color(0xFFEFF2F6), Color(0xFF24262E))     // 浅层容器背景

// ===== 活力主色与光感强调色 =====
val PrimaryBrown get() = light(Color(0xFF5352ED), Color(0xFF7052FF))    // 活力电光蓝紫（主强调色）
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

val AmberLight = Color(0xFFFFEAA7)
val Rose = AccentCoral

// ===== 精致灰阶体系 =====
val TextPrimary get() = light(Color(0xFF12141A), Color(0xFFF7F9FC))     // 一级主标题（极黑/高光白）
val TextMuted get() = light(Color(0xFF5E6778), Color(0xFF9AA4B5))       // 次级说明
val TextSoft get() = light(Color(0xFF8893A7), Color(0xFF6F798B))        // 辅助文字/副标题
val TextFaint get() = light(Color(0xFFCCD4E0), Color(0xFF404654))       // 极淡图标/占位/微弱边框

val Stone200 get() = light(Color(0xFFE2E7ED), Color(0xFF2A2D38))        // 1dp 精致线框描边
val Stone100 get() = light(Color(0xFFF1F4F8), Color(0xFF1F222B))        // 按钮浅底色/次级卡片

// 卡片统一描边：浅色为白色亮边（与白卡融为一体，只在浅灰背景上留一道极浅边界），
// 深色为同色系深灰。配合无阴影，得到与「我的收藏」一致的干净卡片。
val CardBorder get() = light(Color.White.copy(alpha = 0.9f), Color(0xFF33304A))

// ===== 沉浸式页面（FM 电台页）主题色 =====
// 深色沿用原本的墨黑氛围；浅色切成白底深字，整体就是黑 / 白两套。
val FmBackground get() = light(Color(0xFFF6F8FA), Color(0xFF0D0D11))
val FmScrimTop get() = light(Color(0x99FFFFFF), Color(0x990A0A0E))
val FmScrimMid get() = light(Color(0xCCF3F5F9), Color(0xCC0D0D12))
val FmScrimBottom get() = light(Color(0xFAF6F8FA), Color(0xFA09090C))
val FmSheetContainer get() = light(Color(0xFFFFFFFF), Color(0xFF16161B))

/** FM 页前景色：深色=白，浅色=近黑（文字 / 图标 / 进度条） */
fun fmOn(alpha: Float = 1f): Color =
    if (Palette.isDark) Color.White.copy(alpha = alpha) else Color(0xFF12141A).copy(alpha = alpha)

/** FM 页「玻璃」叠色：深色=白叠加，浅色=黑叠加（胶囊 / 按钮底 / 分隔） */
fun fmGlass(alpha: Float): Color =
    if (Palette.isDark) Color.White.copy(alpha = alpha) else Color.Black.copy(alpha = alpha)

// FM 卡片（玻璃感）：浅色用白色玻璃 + 柔阴影区分，深色用淡白叠加
val FmCardBg get() = light(Color.White.copy(alpha = 0.88f), Color.White.copy(alpha = 0.10f))
val FmCardBorder get() = light(Color.Transparent, Color.White.copy(alpha = 0.16f))

// FM 抽屉里的卡片：抽屉底色是纯白时靠一条浅描边区分
val FmSheetCardBg get() = light(Color.White, Color.White.copy(alpha = 0.10f))
val FmSheetCardBorder get() = light(Stone200, Color.White.copy(alpha = 0.14f))

// FM 实心圆按钮：浅色纯黑、深色纯白（图标取反色）
val FmSolidFill get() = light(Color(0xFF12141A), Color.White)
val FmOnSolid get() = light(Color.White, Color(0xFF12141A))

// ===== 听歌统计页专用（毛玻璃 + 渐变） =====
// 页面背景：顶部淡蓝紫 → 下方米白/浅灰（深色下改为墨蓝 → 近黑，保持同样氛围）
val StatsGradientTop get() = light(Color(0xFFE8ECFF), Color(0xFF1A1B2E))
val StatsGradientMid get() = light(Color(0xFFF3F1FB), Color(0xFF16171F))
val StatsGradientBottom get() = light(Color(0xFFFAF8F4), Color(0xFF0F1013))

// 毛玻璃卡片：半透明白 + 淡边框（深色下半透明白黑）
val GlassSurface get() = light(Color(0xE6FFFFFF), Color(0xE61C1E26))
val GlassSurfaceSoft get() = light(Color(0x99FFFFFF), Color(0x1AFFFFFF))
val GlassBorder get() = light(Color(0x66FFFFFF), Color(0x1FFFFFFF))

// 数据可视化：蓝紫渐变（时段分布进度条）、青蓝渐变（柱状图）
val AccentViolet get() = light(Color(0xFF7C6BFF), Color(0xFF8E80FF))
val AccentTeal get() = light(Color(0xFF14B8A6), Color(0xFF2DD4BF))

// 排行榜前三名（金银铜）
val RankGold get() = light(Color(0xFFE0A020), Color(0xFFF0B429))
val RankSilver get() = light(Color(0xFF8E9AAF), Color(0xFFAAB4C6))
val RankBronze get() = light(Color(0xFFC07A45), Color(0xFFD08C55))

