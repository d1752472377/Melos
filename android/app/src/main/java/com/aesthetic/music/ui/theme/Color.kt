package com.aesthetic.music.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * 全局调色板状态：深色/浅色切换。
 * 配色基于 Apple HIG system colors：一个主色 + 中性灰阶，无渐变。
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
val Background get() = Color.Transparent                                  // 保持通透，透出全局底层环境光
val Surface get() = light(Color(0xFFFFFFFF), Color(0xFF1C1C1E))           // secondarySystemGroupedBackground
val SurfaceSoft get() = light(Color(0xFFE5E5EA), Color(0xFF2C2C2E))       // systemGray5 / tertiary

// ===== 唯一主色（Apple Music 红粉）=====
// 渐变起止色取同一个值，原有渐变处自动变成纯色
val PrimaryBrown get() = light(Color(0xFFFF2D55), Color(0xFFFF375F))
val PrimaryGradientStart get() = light(Color(0xFFFF2D55), Color(0xFFFF375F))
val PrimaryGradientEnd get() = light(Color(0xFFFF2D55), Color(0xFFFF375F))

// ===== 辅助强调色（仅用于少量语义场景）=====
val AccentCoral get() = light(Color(0xFFFF2D55), Color(0xFFFF375F))       // 收藏 / 榜一（与主色一致）
val AccentMint get() = light(Color(0xFF34C759), Color(0xFF30D158))        // 播放中 / 活跃
val AccentCyan get() = light(Color(0xFF007AFF), Color(0xFF0A84FF))        // 发现 / 统计
val AccentAmber get() = light(Color(0xFFFF9500), Color(0xFFFF9F0A))       // VIP / 经典
val AccentRose get() = light(Color(0xFFFF6482), Color(0xFFFF6482))        // 爱心

val PlayerBg get() = light(Color(0xFF1C1C1E), Color(0xFF0A0A0A))          // 播放页底
val PlayerBgDark get() = light(Color(0xFF0F0F10), Color(0xFF000000))

val AmberLight = Color(0xFFFFE8C2)
val Rose get() = AccentCoral

// ===== 灰阶体系（Apple label / gray）=====
val TextPrimary get() = light(Color(0xFF000000), Color(0xFFFFFFFF))       // label
val TextMuted get() = light(Color(0x993C3C43), Color(0x99EBEBF5))         // secondaryLabel（60%）
val TextSoft get() = light(Color(0xFF8E8E93), Color(0xFF8E8E93))          // systemGray
val TextFaint get() = light(Color(0xFFC7C7CC), Color(0xFF48484A))         // systemGray3

val Stone200 get() = light(Color(0xFFD1D1D6), Color(0xFF3A3A3C))          // 描边 systemGray4
val Stone100 get() = light(Color(0xFFF2F2F7), Color(0xFF242426))          // 按钮浅底 / 次级卡片

// 卡片统一描边
val CardBorder get() = light(Color.White.copy(alpha = 0.9f), Color(0xFF2C2C2E))

// ===== 沉浸式页面（FM 电台页）主题色 =====
val FmBackground get() = light(Color(0xFFF2F2F7), Color(0xFF000000))
val FmScrimTop get() = light(Color(0x99FFFFFF), Color(0x99000000))
val FmScrimMid get() = light(Color(0xCCF2F2F7), Color(0xCC000000))
val FmScrimBottom get() = light(Color(0xFAF2F2F7), Color(0xFA000000))
val FmSheetContainer get() = light(Color(0xFFFFFFFF), Color(0xFF1C1C1E))

/** FM 页前景色：深色=白，浅色=黑（文字 / 图标 / 进度条） */
fun fmOn(alpha: Float = 1f): Color =
    if (Palette.isDark) Color.White.copy(alpha = alpha) else Color.Black.copy(alpha = alpha)

/** FM 页「玻璃」叠色：深色=白叠加，浅色=黑叠加（胶囊 / 按钮底 / 分隔） */
fun fmGlass(alpha: Float): Color =
    if (Palette.isDark) Color.White.copy(alpha = alpha) else Color.Black.copy(alpha = alpha)

// FM 卡片（玻璃感）
val FmCardBg get() = light(Color.White.copy(alpha = 0.88f), Color.White.copy(alpha = 0.10f))
val FmCardBorder get() = light(Color.Transparent, Color.White.copy(alpha = 0.16f))

// FM 抽屉里的卡片
val FmSheetCardBg get() = light(Color.White, Color.White.copy(alpha = 0.10f))
val FmSheetCardBorder get() = light(Stone200, Color.White.copy(alpha = 0.14f))

// FM 实心圆按钮：浅色纯黑、深色纯白（图标取反色）
val FmSolidFill get() = light(Color(0xFF000000), Color.White)
val FmOnSolid get() = light(Color.White, Color(0xFF000000))

// ===== 听歌统计页专用 =====
// 去掉蓝紫色调，改为中性灰渐变
val StatsGradientTop get() = light(Color(0xFFF8F8FA), Color(0xFF1C1C1E))
val StatsGradientMid get() = light(Color(0xFFF2F2F7), Color(0xFF0A0A0A))
val StatsGradientBottom get() = light(Color(0xFFF2F2F7), Color(0xFF000000))

// 毛玻璃卡片
val GlassSurface get() = light(Color(0xE6FFFFFF), Color(0xE61C1C1E))
val GlassSurfaceSoft get() = light(Color(0x99FFFFFF), Color(0x1AFFFFFF))
val GlassBorder get() = light(Color(0x66FFFFFF), Color(0x1FFFFFFF))

// 数据可视化（Apple indigo / teal）
val AccentViolet get() = light(Color(0xFF5856D6), Color(0xFF5E5CE6))
val AccentTeal get() = light(Color(0xFF30B0C7), Color(0xFF40C8E0))

// 排行榜前三名（金银铜）
val RankGold get() = light(Color(0xFFFFCC00), Color(0xFFFFD60A))
val RankSilver get() = light(Color(0xFF8E8E93), Color(0xFFAEAEB2))
val RankBronze get() = light(Color(0xFFA2845E), Color(0xFFAC8E68))

/**
 * 页面级静态配色。
 *
 * 除封面取色、播放页封面主色等由内容动态生成的颜色外，UI 中不再直接写 `Color(0x...)`：
 * 新颜色先在这里登记，再由页面引用。按用途分组，避免同一色值在多个页面各写一遍。
 */
object AppColors {
    // ===== 语义色 =====
    val onAccent = Color(0xFFF7F6F2)              // 主色底上的暖白文字
    val authCheck = Color(0xFF2C2523)             // 登录页协议勾选
    val errorSurface = Color(0xFFFFF1F0)
    val errorBorder = Color(0xFFFFA39E)
    val errorText = Color(0xFFCF1322)
    val successSurfaceLight = Color(0xFFEBFBF5)
    val successSurfaceDark = Color(0xFF142E25)
    val successSurfaceSoft = Color(0xFFE8F5E9)
    val successText = Color(0xFF2E7D32)

    // ===== 跨页面强调色（沿用现有视觉值）=====
    val sky = Color(0xFF38BDF8)
    val skyDeep = Color(0xFF0284C7)
    val skySoft = Color(0xFF7DD3FC)
    val indigo = Color(0xFF6366F1)
    val indigoLight = Color(0xFF818CF8)
    val purple = Color(0xFFA855F7)
    val pink = Color(0xFFEC4899)
    val emerald = Color(0xFF10B981)
    val emeraldDeep = Color(0xFF059669)
    val emeraldLight = Color(0xFF34D399)
    val amber = Color(0xFFF59E0B)
    val rose = Color(0xFFFF4D6D)
    val coral = Color(0xFFFB7185)
    val crimson = Color(0xFFE11D48)
    val blue = Color(0xFF3B82F6)
    val violet = Color(0xFF7C5CFF)
    val violetLight = Color(0xFFA78BFA)
    val lime = Color(0xFFEAF989)
    val favorite = Color(0xFFFF3366)

    // ===== Slate 灰阶（Tailwind 历史值）=====
    val slate50 = Color(0xFFF8FAFC)
    val slate100 = Color(0xFFF1F5F9)
    val slate200 = Color(0xFFE2E8F0)
    val slate300 = Color(0xFFCBD5E1)
    val slate400 = Color(0xFF94A3B8)
    val slate500 = Color(0xFF64748B)
    val slate600 = Color(0xFF475569)
    val slate700 = Color(0xFF334155)
    val slate800 = Color(0xFF1E293B)
    val slate900 = Color(0xFF0F172A)
    val slate950 = Color(0xFF020617)
    val gray = Color(0xFF8E8E93)
    val grayDeep = Color(0xFF6B7280)
    val graySoft = Color(0xFF9A9AA0)
    val ink = Color(0xFF1C1C1E)

    // ===== 平台品牌色 =====
    val brandNetease = Color(0xFFE53935)
    val brandNeteaseDeep = Color(0xFFC20C0C)
    val brandNeteaseRank = Color(0xFFE23B34)
    val brandQQ = Color(0xFF31C27C)
    val brandQQSearch = Color(0xFFFDD835)
    val brandKugou = Color(0xFF1E88E5)
    val brandKugouAlt = Color(0xFF2CA6E0)
    val brandKugouRank = Color(0xFF2C8BF0)
    val brandKuwo = Color(0xFFFFB300)
    val brandKuwoAlt = Color(0xFFE08A00)
    val brandKuwoRank = Color(0xFFFF7A18)
    val brandMigu = Color(0xFFEC5B8A)
    val brandUnknown = Color(0xFF9E9E9E)
    val brandUnknownAlt = Color(0xFF8893A7)

    // ===== 首页版块 =====
    val entryPink1 = Color(0xFFFF9DCE)
    val entryPink2 = Color(0xFFFFC5DC)
    val entryPink3 = Color(0xFFFFE8F3)
    val entryBlue1 = Color(0xFFBDDEFA)
    val entryBlue2 = Color(0xFFADE9F3)
    val entryGreen1 = Color(0xFF9DF3DD)
    val entryCyan = Color(0xFFB8EFF9)
    val entryMint = Color(0xFFBCEEDD)
    val entryLime = Color(0xFFE8FAA8)
    val entryCyan2 = Color(0xFF9DF3EE)
    val entryLime2 = Color(0xFFEAF989)
    val rankingRising = Color(0xFF59C7A4)
    val rankingNew = Color(0xFF5B9BD5)
    val rankingOriginal = Color(0xFF9A7BD4)
    val rankingHot = Color(0xFFF08A5D)
    val rankingPlaceholderDark = Color(0xFF303037)
    val rankingPlaceholderLight = Color(0xFFE8E8ED)
    val homeHole = Color(0xFF18181E)

    // ===== 首页环境光 =====
    val auroraLightBase = Color(0xFFFAFAFC)
    val auroraDarkBase = Color(0xFF0E0D12)
    val auroraDarkTop = Color(0xFF15131A)
    val auroraDarkBottom = Color(0xFF0A090D)
    val auroraPeach = Color(0xFFFFE8D8)
    val auroraLavender = Color(0xFFE0D9FF)
    val auroraMint = Color(0xFFD4F8F0)
    val auroraDarkPurple = Color(0xFF3A2A4A)
    val auroraDarkTeal = Color(0xFF1A2E36)
    val auroraOrange = Color(0xFFFF9A76)
    val auroraYellow = Color(0xFFFFD175)
    val auroraPink = Color(0xFFF8C0D0)

    // ===== 通用封面占位 =====
    val coverPlaceholderLight1 = Color(0xFFEDE9FE)
    val coverPlaceholderLight2 = Color(0xFFE0E7FF)
    val coverPlaceholderDark1 = Color(0xFF26262E)
    val coverPlaceholderDark2 = Color(0xFF1B1B22)
    val coverIconDark = Color(0xFF64748B)
    val coverIconUnknown = Color(0xFF8893A7)
    val coverPlateDark = Color(0xFF2C2E33)

    // ===== FM 页 =====
    val fmDeep = Color(0xFF101014)
    val fmPanel = Color(0xFF26262E)
    val fmBlack = Color(0xFF0F0F12)
    val fmGold = Color(0xFFD4AF37)

    // ===== 播放器 / 队列 / 音质抽屉 =====
    val disc1 = Color(0xFF232327)
    val disc2 = Color(0xFF141417)
    val disc3 = Color(0xFF1E1E22)
    val disc4 = Color(0xFF0F0F11)
    val disc5 = Color(0xFF26262B)
    val disc6 = Color(0xFF0B0B0C)
    val discAlt1 = Color(0xFF222226)
    val discAlt2 = Color(0xFF141416)
    val discAlt3 = Color(0xFF1F1F23)
    val discAlt4 = Color(0xFF101012)
    val discAlt5 = Color(0xFF26262B)
    val discAlt6 = Color(0xFF0C0C0D)
    val discBorder = Color(0xFF111113)
    val vinylLabel = Color(0xFF2A1C16)
    val goldDisc = Color(0xFFD4AF37)
    val goldLight = Color(0xFFE5C158)
    val goldMid = Color(0xFF8A6D1C)
    val goldDark = Color(0xFF261D09)
    val deepest = Color(0xFF09090A)
    val deepestAlt = Color(0xFF09090C)
    val playerPanel = Color(0xFF1E1E24)
    val neonPink = Color(0xFFF472B6)
    val neonYellow = Color(0xFFFACC15)
    val neonOrange = Color(0xFFFB923C)
    val neonPinkSoft = Color(0x33F472B6)
    val neonVioletSoft = Color(0x44A78BFA)
    val neonBlueSoft = Color(0x4438BDF8)
    val neonGreenSoft = Color(0x4434D399)
    val neonYellowSoft = Color(0x44FACC15)
    val neonOrangeSoft = Color(0x44FB923C)
    val coverBgTop = Color(0xFF5C2612)
    val coverBgBottom = Color(0xFF1F0D06)
    val neonBg1 = Color(0xFF311B92)
    val neonBg2 = Color(0xFF880E4F)
    val neonBg3 = Color(0xFF1A237E)
    val coverGlow = Color(0x953B187B)
    val neonGlow = Color(0x859D174D)
    val coverScrim1 = Color(0x555C2612)
    val coverScrim2 = Color(0xD51F0D06)
    val coverScrim3 = Color(0xFA100603)
    val darkScrim1 = Color(0x880F172A)
    val darkScrim2 = Color(0xEE020617)
    val grayScrim1 = Color(0x66475569)
    val grayScrim2 = Color(0xEE0F172A)
    val bottomScrim1 = Color(0xEA0E1015)
    val bottomScrim2 = Color(0xF508090C)
    val controlBg = Color(0xB512141C)
    val controlBgTop = Color(0xE0181A24)
    val controlBgBottom = Color(0xF010121A)
    val controlGradient1 = Color(0xFF181A20)
    val controlGradient2 = Color(0xFF0C0D10)
    val controlCoverTop = Color(0xFF5E2B16)
    val controlCoverBottom = Color(0xFF281109)
    val controlNeon1 = Color(0xFF3B1E82)
    val controlNeon2 = Color(0xFF881958)
    val controlNeon3 = Color(0xFF1E2675)
    val controlGray1 = Color(0xFF2D3748)
    val controlGray2 = Color(0xFF1A202C)
    val queueContainerDark = Color(0xFF16171D)
    val queueContainerLight = Color(0xFFF7F7FA)
    val queueTitleLight = Color(0xFF1C1C1E)
    val queueTitleDark = Color(0xFFF2F2F7)
    val queueSubLight = Color(0xFF9A9AA0)
    val queueSubDark = Color(0xFF8E8E93)
    val queueIconLight = Color(0xFF6E6E78)
    val queueChipLight = Color(0xFFE9E9EF)
    val queueActiveLight = Color(0x0D000000)
    val queueAccent = Color(0xFFFF3B30)
    val eqSkyGlow = Color(0x5538BDF8)
    val audioSheetBg = Color(0xB81A1614)
    val audioSheetTop = Color(0xF0201B17)
    val audioSheetBottom = Color(0xF514110E)
    val karaokeActive = Color(0xFF9E2A2B)
    val karaokeIdle = Color(0xFF7A2526)

    // ===== 个人中心 =====
    val profileOnLight = Color(0xFF1E2329)
    val profileSecondaryOnLight = Color(0xFF5E6B7A)
    val profileAvatar1 = Color(0xFFCCE4FF)
    val profileAvatar2 = Color(0xFFF7FAFD)
    val profileAvatar3 = Color(0xFFFFB3C6)

    // ===== 歌单 / 榜单 / 缓存 =====
    val activeRowBgDark = Color(0x2838BDF8)
    val songTitleDark = Color(0xFFF1F5F9)
    val bentoOverlayWhite20 = Color(0x33FFFFFF)
    val bentoOverlayWhite12 = Color(0x1FFFFFFF)
    val bentoOverlayBlack8 = Color(0x15000000)
    val bentoOverlayBlack8Alt = Color(0x14000000)
    val rankRisingStart = Color(0xFF7C5CFF)
    val rankRisingEnd = Color(0xFFA78BFA)
    val rankNewStart = Color(0xFF22B573)
    val rankNewEnd = Color(0xFF6EE7B7)
    val rankOriginalStart = Color(0xFF2BA6E8)
    val rankOriginalEnd = Color(0xFF7DD3FC)
    val rankHotStart = Color(0xFFE85D75)
    val rankHotEnd = Color(0xFFFDA4AF)
    val cacheImage = Color(0xFF3E7CB1)
    val cacheTemp = Color(0xFF8A8F98)
    val cacheDanger = Color(0xFFC0392B)

    // ===== Toast =====
    val toastError = Color(0xFFF87171)
    val toastSuccess = Color(0xFF4ADE80)
    val toastInfo = Color(0xFFFDE68A)
    val toastBgTop = Color(0xE6241E1C)
    val toastBgBottom = Color(0xE638302C)
    val toastBorder = Color(0x33FFFFFF)
    val toastShadow = Color(0x66000000)

    // ===== GZ 玻璃 token（GlassTokens.kt）=====
    val glassTextHighLight = Color(0xFF1C1C1E)
    val glassTextHighDark = Color(0xFFF2F2F7)
    val glassTextMediumLight = Color(0xFF8E8E93)
    val glassTextMediumDark = Color(0xFFA8A8AE)
    val glassTextLowLight = Color(0xFF9A9AA0)
    val glassTextLowDark = Color(0xFF8E8E93)
    val glassBgDark = Color(0xFF1C1C1E)
    val defaultCoverTone = Color(0xFFFA243C)
    val coverToneFallback = Color(0xFF8E8E93)

    // ===== 其他 =====
    val quietSurfaceDark = Color(0xFF2A2A30)
    val quietSurfaceLight = Color(0xFFF0F0F4)
    val equalizerNegativeLight = Color(0xFF6B7280)
}
