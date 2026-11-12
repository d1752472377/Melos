package com.aesthetic.music.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.aesthetic.music.R

/**
 * 内置的 MiSans 可变字体（MiSansVF.ttf）。
 *
 * 同一个 VF 文件按不同 weight 注册即可：Compose 会把 FontWeight 映射到字体
 * 的 wght 轴，不必为每个字重单独打包一份 TTF。
 *
 * 授权：MiSans 允许免费商用与嵌入软件，要求在软件中注明使用了 MiSans，
 * 对应说明见「设置 → 关于软件 → 字体」。
 */
val MiSans = FontFamily(
    Font(R.font.misans_variable, weight = FontWeight.Thin),
    Font(R.font.misans_variable, weight = FontWeight.ExtraLight),
    Font(R.font.misans_variable, weight = FontWeight.Light),
    Font(R.font.misans_variable, weight = FontWeight.Normal),
    Font(R.font.misans_variable, weight = FontWeight.Medium),
    Font(R.font.misans_variable, weight = FontWeight.SemiBold),
    Font(R.font.misans_variable, weight = FontWeight.Bold),
    Font(R.font.misans_variable, weight = FontWeight.ExtraBold),
    Font(R.font.misans_variable, weight = FontWeight.Black),
)

private val Base = Typography()

/** 把 Material3 默认排版整体换成 MiSans，保证所有 Text 都走同一套字体 */
val MiSansTypography = Typography(
    displayLarge = Base.displayLarge.copy(fontFamily = MiSans),
    displayMedium = Base.displayMedium.copy(fontFamily = MiSans),
    displaySmall = Base.displaySmall.copy(fontFamily = MiSans),
    headlineLarge = Base.headlineLarge.copy(fontFamily = MiSans),
    headlineMedium = Base.headlineMedium.copy(fontFamily = MiSans),
    headlineSmall = Base.headlineSmall.copy(fontFamily = MiSans),
    titleLarge = Base.titleLarge.copy(fontFamily = MiSans),
    titleMedium = Base.titleMedium.copy(fontFamily = MiSans),
    titleSmall = Base.titleSmall.copy(fontFamily = MiSans),
    bodyLarge = Base.bodyLarge.copy(fontFamily = MiSans),
    bodyMedium = Base.bodyMedium.copy(fontFamily = MiSans),
    bodySmall = Base.bodySmall.copy(fontFamily = MiSans),
    labelLarge = Base.labelLarge.copy(fontFamily = MiSans),
    labelMedium = Base.labelMedium.copy(fontFamily = MiSans),
    labelSmall = Base.labelSmall.copy(fontFamily = MiSans),
)
