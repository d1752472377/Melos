package com.aesthetic.music.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * 冷淡风配色：整体偏白，中性色统一带一点冷蓝灰（色相约 215°），
 * 一个低饱和钢蓝作主色，强调色全部降饱和。
 */
object Palette {
    var isDark by mutableStateOf(false)
}

fun applyThemeMode(mode: com.aesthetic.music.data.settings.ThemeMode, systemDark: Boolean) {
    Palette.isDark = when (mode) {
        com.aesthetic.music.data.settings.ThemeMode.SYSTEM -> systemDark
        com.aesthetic.music.data.settings.ThemeMode.LIGHT -> false
        com.aesthetic.music.data.settings.ThemeMode.DARK -> true
    }
}

private fun light(c: Color, d: Color) = if (Palette.isDark) d else c

// ===== 底色 =====
val Background get() = Color.Transparent
val Surface get() = light(Color(0xFFFFFFFF), Color(0xFF16191E))
val SurfaceSoft get() = light(Color(0xFFEEF2F6), Color(0xFF1F242B))

// ===== 主色：低饱和钢蓝（白色图标放上去浅深色都清晰）=====
val PrimaryBrown get() = light(Color(0xFF4A6FA5), Color(0xFF6C94C8))
val PrimaryGradientStart get() = light(Color(0xFF4A6FA5), Color(0xFF6C94C8))
val PrimaryGradientEnd get() = light(Color(0xFF4A6FA5), Color(0xFF6C94C8))

// ===== 强调色（全部降饱和）=====
val AccentCoral get() = light(Color(0xFFD95C63), Color(0xFFE5737A))   // 收藏 / 榜一
val AccentMint get() = light(Color(0xFF4FA68A), Color(0xFF63BFA0))    // 播放中 / 活跃
val AccentCyan get() = light(Color(0xFF4A90B8), Color(0xFF66AACF))    // 发现 / 统计
val AccentAmber get() = light(Color(0xFFC99A4B), Color(0xFFD9AE62))   // VIP / 经典
val AccentRose get() = light(Color(0xFFD9778C), Color(0xFFE58EA0))    // 爱心

val PlayerBg get() = light(Color(0xFF1A1F26), Color(0xFF0F1318))
val PlayerBgDark get() = light(Color(0xFF12161B), Color(0xFF0A0D10))

val AmberLight = Color(0xFFF3E6C8)
val Rose get() = AccentCoral

// ===== 冷灰阶 =====
val TextPrimary get() = light(Color(0xFF1A2330), Color(0xFFF2F5F9))
val TextMuted get() = light(Color(0xFF5B6776), Color(0xFF9AA6B5))
val TextSoft get() = light(Color(0xFF8A95A5), Color(0xFF6F7B8B))
val TextFaint get() = light(Color(0xFFC9D1DB), Color(0xFF3E4753))

val Stone200 get() = light(Color(0xFFDDE3EA), Color(0xFF2A313A))
val Stone100 get() = light(Color(0xFFF1F4F8), Color(0xFF1D2229))

// 白底白卡需要一道极淡的冷灰描边才看得出轮廓
val CardBorder get() = light(Color(0xFFE8EDF2), Color(0xFF2A313A))

// ===== FM 电台页 =====
val FmBackground get() = light(Color(0xFFF7F9FB), Color(0xFF0B0E12))
val FmScrimTop get() = light(Color(0x99FFFFFF), Color(0x990A0D11))
val FmScrimMid get() = light(Color(0xCCF4F7FA), Color(0xCC0B0E12))
val FmScrimBottom get() = light(Color(0xFAF7F9FB), Color(0xFA080A0D))
val FmSheetContainer get() = light(Color(0xFFFFFFFF), Color(0xFF151A20))

fun fmOn(alpha: Float = 1f): Color =
    if (Palette.isDark) Color.White.copy(alpha = alpha) else Color(0xFF1A2330).copy(alpha = alpha)

fun fmGlass(alpha: Float): Color =
    if (Palette.isDark) Color.White.copy(alpha = alpha) else Color(0xFF1A2330).copy(alpha = alpha)

val FmCardBg get() = light(Color.White.copy(alpha = 0.88f), Color.White.copy(alpha = 0.10f))
val FmCardBorder get() = light(Color.Transparent, Color.White.copy(alpha = 0.16f))

val FmSheetCardBg get() = light(Color.White, Color.White.copy(alpha = 0.10f))
val FmSheetCardBorder get() = light(Stone200, Color.White.copy(alpha = 0.14f))

val FmSolidFill get() = light(Color(0xFF1A2330), Color.White)
val FmOnSolid get() = light(Color.White, Color(0xFF1A2330))

// ===== 听歌统计页 =====
val StatsGradientTop get() = light(Color(0xFFF4F7FB), Color(0xFF161B22))
val StatsGradientMid get() = light(Color(0xFFF8FAFC), Color(0xFF0F1318))
val StatsGradientBottom get() = light(Color(0xFFFFFFFF), Color(0xFF0A0D10))

val GlassSurface get() = light(Color(0xE6FFFFFF), Color(0xE6171B21))
val GlassSurfaceSoft get() = light(Color(0x99FFFFFF), Color(0x1AFFFFFF))
val GlassBorder get() = light(Color(0x141A2330), Color(0x1FFFFFFF))

val AccentViolet get() = light(Color(0xFF6E7FA3), Color(0xFF8A9BC0))
val AccentTeal get() = light(Color(0xFF5FA3A8), Color(0xFF7BBCC0))

val RankGold get() = light(Color(0xFFC9A24A), Color(0xFFD8B35E))
val RankSilver get() = light(Color(0xFF9AA6B5), Color(0xFFB3BDCA))
val RankBronze get() = light(Color(0xFFB08560), Color(0xFFC09870))

object AppColors {
    val onAccent = Color(0xFFF8FAFC)
    val authCheck = Color(0xFF1A2330)
    val errorSurface = Color(0xFFFFF1F0)
    val errorBorder = Color(0xFFFFA39E)
    val errorText = Color(0xFFCF1322)
    val successSurfaceLight = Color(0xFFEBFBF5)
    val successSurfaceDark = Color(0xFF142E25)
    val successSurfaceSoft = Color(0xFFE8F5E9)
    val successText = Color(0xFF2E7D32)

    // 下面这组原来是高饱和彩色，统一压成冷色低饱和
    val sky = Color(0xFF7FB2D6)
    val skyDeep = Color(0xFF4A90B8)
    val skySoft = Color(0xFFA9CCE4)
    val indigo = Color(0xFF6E7FA3)
    val indigoLight = Color(0xFF8A9BC0)
    val purple = Color(0xFF8A94B5)
    val pink = Color(0xFFD9778C)
    val emerald = Color(0xFF4FA68A)
    val emeraldDeep = Color(0xFF3D8C73)
    val emeraldLight = Color(0xFF7CC3AB)
    val amber = Color(0xFFC99A4B)
    val rose = Color(0xFFD9778C)
    val coral = Color(0xFFE08A92)
    val crimson = Color(0xFFC4505C)
    val blue = Color(0xFF4A6FA5)
    val violet = Color(0xFF6E7FA3)
    val violetLight = Color(0xFF9AA8C8)
    val lime = Color(0xFFDDE8E3)
    val favorite = Color(0xFFE0525E)

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
    val gray = Color(0xFF8A95A5)
    val grayDeep = Color(0xFF6B7280)
    val graySoft = Color(0xFF9AA5B3)
    val ink = Color(0xFF1A2330)

    // 各平台品牌色保持原样（品牌识别）
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
    val brandUnknownAlt = Color(0xFF8A95A5)

    // 首页入口卡片：粉绿柠檬 → 冷调淡彩
    val entryPink1 = Color(0xFFC9D8EA)
    val entryPink2 = Color(0xFFDCE6F2)
    val entryPink3 = Color(0xFFEEF3F9)
    val entryBlue1 = Color(0xFFBFD6EC)
    val entryBlue2 = Color(0xFFC4E3EA)
    val entryGreen1 = Color(0xFFC3E6DD)
    val entryCyan = Color(0xFFCBE8EE)
    val entryMint = Color(0xFFCDE7DF)
    val entryLime = Color(0xFFDDE8E3)
    val entryCyan2 = Color(0xFFBFE3E3)
    val entryLime2 = Color(0xFFE6ECE8)
    val rankingRising = Color(0xFF6DB5A0)
    val rankingNew = Color(0xFF6B9BC3)
    val rankingOriginal = Color(0xFF8A97BD)
    val rankingHot = Color(0xFFD99A82)
    val rankingPlaceholderDark = Color(0xFF262C34)
    val rankingPlaceholderLight = Color(0xFFE8EDF2)
    val homeHole = Color(0xFF12161B)

    // 环境光：暖色（桃/薰衣草/薄荷）→ 冷白蓝灰
    val auroraLightBase = Color(0xFFF7F9FB)
    val auroraDarkBase = Color(0xFF0B0E12)
    val auroraDarkTop = Color(0xFF11151A)
    val auroraDarkBottom = Color(0xFF07090B)
    val auroraPeach = Color(0xFFE8EEF5)
    val auroraLavender = Color(0xFFE3EAF4)
    val auroraMint = Color(0xFFE1F0F0)
    val auroraDarkPurple = Color(0xFF1E2A3A)
    val auroraDarkTeal = Color(0xFF15262B)
    val auroraOrange = Color(0xFFCFDDEB)
    val auroraYellow = Color(0xFFE9EDF2)
    val auroraPink = Color(0xFFDDE5EF)

    val coverPlaceholderLight1 = Color(0xFFE6ECF4)
    val coverPlaceholderLight2 = Color(0xFFDCE4EF)
    val coverPlaceholderDark1 = Color(0xFF20262E)
    val coverPlaceholderDark2 = Color(0xFF161B21)
    val coverIconDark = Color(0xFF64748B)
    val coverIconUnknown = Color(0xFF8A95A5)

    val fmDeep = Color(0xFF101014)
    val fmPanel = Color(0xFF26262E)
    val fmBlack = Color(0xFF0F0F12)
    val fmGold = Color(0xFFD4AF37)

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
    val queueContainerLight = Color(0xFFF7F9FB)
    val queueTitleLight = Color(0xFF1A2330)
    val queueTitleDark = Color(0xFFF2F5F9)
    val queueSubLight = Color(0xFF8A95A5)
    val queueSubDark = Color(0xFF8A95A5)
    val queueIconLight = Color(0xFF66717F)
    val queueChipLight = Color(0xFFE8EDF2)
    val queueActiveLight = Color(0x0D000000)
    val queueAccent = Color(0xFF4A6FA5)
    val eqSkyGlow = Color(0x5538BDF8)
    val audioSheetBg = Color(0xB81A1614)
    val audioSheetTop = Color(0xF0201B17)
    val audioSheetBottom = Color(0xF514110E)
    val karaokeActive = Color(0xFF9E2A2B)
    val karaokeIdle = Color(0xFF7A2526)

    val profileOnLight = Color(0xFF1A2330)
    val profileSecondaryOnLight = Color(0xFF5E6B7A)
    val profileAvatar1 = Color(0xFFD6E4F5)
    val profileAvatar2 = Color(0xFFF7FAFD)
    val profileAvatar3 = Color(0xFFCBD8EA)

    val activeRowBgDark = Color(0x286C94C8)
    val songTitleDark = Color(0xFFF1F5F9)
    val bentoOverlayWhite20 = Color(0x33FFFFFF)
    val bentoOverlayWhite12 = Color(0x1FFFFFFF)
    val bentoOverlayBlack8 = Color(0x15000000)
    val bentoOverlayBlack8Alt = Color(0x14000000)
    val rankRisingStart = Color(0xFF6C7FA8)
    val rankRisingEnd = Color(0xFF9CAAC8)
    val rankNewStart = Color(0xFF4FA68A)
    val rankNewEnd = Color(0xFF8CCBB5)
    val rankOriginalStart = Color(0xFF4A90B8)
    val rankOriginalEnd = Color(0xFF8EC0DA)
    val rankHotStart = Color(0xFFD9707E)
    val rankHotEnd = Color(0xFFEDAAB3)
    val cacheImage = Color(0xFF3E7CB1)
    val cacheTemp = Color(0xFF8A8F98)
    val cacheDanger = Color(0xFFC0392B)

    val toastError = Color(0xFFF87171)
    val toastSuccess = Color(0xFF4ADE80)
    val toastShadow = Color(0x66000000)

    val glassTextHighLight = Color(0xFF1A2330)
    val glassTextHighDark = Color(0xFFF2F5F9)
    val glassTextMediumLight = Color(0xFF8A95A5)
    val glassTextMediumDark = Color(0xFFA8B1BD)
    val glassTextLowLight = Color(0xFF9AA5B3)
    val glassTextLowDark = Color(0xFF8A95A5)
    val glassBgDark = Color(0xFF16191E)
    val defaultCoverTone = Color(0xFF4A6FA5)
    val coverToneFallback = Color(0xFF8A95A5)

    val quietSurfaceDark = Color(0xFF232930)
    val quietSurfaceLight = Color(0xFFEEF2F6)
    val equalizerNegativeLight = Color(0xFF6B7280)
}
