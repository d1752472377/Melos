package com.aesthetic.music.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * GZ Music 的 light / dark ColorScheme，逐值还原自 JADX 反编译产物（无一处推测）。
 *
 * 来源（gz_decompiled/sources/）：
 *  - dark  来自 `com/gzmusic/app/ui/theme/ThemeKt.java:57` 的 `a = s91.b(...)`
 *  - light 来自 `com/gzmusic/app/ui/theme/ThemeKt.java:63` 的 `b = s91.e(...)`
 *
 * ── 关于 light/dark 归属（与最初的猜测相反，已用数值证据确认）──
 *  ThemeKt 静态字段 `a`（= s91.b）是 **dark**：s91.b 的默认值取自 `j91`，而 j91 的取值是
 *  Material3 ColorDarkTokens（background 默认 #141218、onPrimary 默认 #381E72、outline 默认 #938F99）。
 *  ThemeKt 静态字段 `b`（= s91.e）是 **light**：s91.e 的默认值取自 `m91` = ColorLightTokens
 *  （background 默认 #FEF7FF、onPrimary 默认 #FFFFFF、outline 默认 #79747E）。
 *  与 `ThemeKt.java:539` 的 `q91 q91Var = z ? a : b;`（z = isDark）完全吻合。
 *  旁证：s91.b 的形参里没有 primary 的默认分支（j91 也没有 Primary 字段），s91.e 里
 *  `(i & 1) != 0 ? m91.z : j` 保留了 m91.z = #6750A4（Primary40，即 light primary 默认值）。
 *
 * ── 掩码 → 参数 的对应关系 ──
 *  两处调用的最后一个实参都是 Compose 变更位掩码 `-80209904`（32 位无符号 = 0xFB381810）：
 *    置 1 的位 = 调用方没传 → 取 material3 默认值（省略即可）；置 0 的位 = 显式传入 → 必须写出来。
 *  掩码位 k 对应 material3 `lightColorScheme`/`darkColorScheme` 的第 (k+1) 个形参，
 *  也就是 `ColorScheme`（反编译里的 `q91`）构造函数的第 (k+1) 个位置参数；下面每行注释里的
 *  “q91 pos N”就是该位置（顺序取自 q91.toString()：primary, onPrimary, … , onTertiaryFixedVariant）。
 *  置 1（→ 取 material3 默认值、此处省略）：[4, 11, 12, 19, 20, 21, 24, 25, 27, 28, 29, 30, 31]
 *    = inversePrimary, tertiaryContainer, onTertiaryContainer, surfaceTint, inverseSurface,
 *      inverseOnSurface, errorContainer, onErrorContainer, outlineVariant, scrim,
 *      surfaceBright, surfaceDim, surfaceContainer
 *  置 0（→ 调用方显式传入，本文件写出的 19 个）：
 *    [0, 1, 2, 3, 5, 6, 7, 8, 9, 10, 13, 14, 15, 16, 17, 18, 22, 23, 26]
 *  掩码位 32..47（surfaceContainerHigh … onTertiaryFixedVariant）在 Kotlin 里本属于第二个掩码 int，
 *  R8 已把对应的形参整个删除（s91.b / s91.e 只剩下 19 个 long 形参 + 1 个 int 掩码），
 *  因此这些位置同样取 material3 默认值，本文件一律省略。
 *
 * ── 两个特殊位置 ──
 *  - surfaceTint（q91 pos 20）：掩码位 19 = 1 → 调用方没传；material3 的默认是
 *    `surfaceTint = primary`，ThemeKt 两处都没写它，所以这里也省略（省略后值仍等于各自的
 *    primary = 0xFFFA243C，与 GZ 运行时一致）。反编译里它被硬编码成“第 1 个实参”（见
 *    s91.java:66 `j` 与 s91.java:179 `j20`）。
 *  - inversePrimary / tertiaryContainer / onTertiaryContainer / inverseSurface / inverseOnSurface /
 *    errorContainer / onErrorContainer / outlineVariant / scrim / surfaceBright / surfaceDim /
 *    surfaceContainer 在 `s91.b` / `s91.e` 里是**硬编码**的 j91.* / m91.* 默认值（没有掩码判断、
 *    也没有对应形参），所以只能省略，不能写死。
 *
 * 颜色字面量统一写成 0xAARRGGBB：`hp1.e(4294583356L)` == `Color(0xFFFA243C)`
 * （hp1.e(long) = Compose `Color(Long)` 的打包实现，取值即低 32 位的 ARGB）。
 * 提取 + 掩码解析脚本：scratch/gz_extract/extract_colorscheme.py（输出报告 scratch/gz_extract/report.txt）。
 *
 * ── 两条已用脚本验证的补充结论 ──
 *  1) j91 / m91 的 93 个 token 取值与本项目实际依赖的 material3-android-1.4.0.aar 里的
 *     ColorDarkTokens / ColorLightTokens **逐值完全一致**（脚本 scratch/gz_extract/verify_m3_tokens.py，
 *     0 处差异）。所以“省略 = 取默认值”确实等价于 GZ 的运行结果。
 *  2) material3 1.4.0 里对 Kotlin 源码可见的 lightColorScheme / darkColorScheme 只有 48 参数那一个重载
 *     （36 / 29 参数的重载都带 @Deprecated(level = HIDDEN)，只是二进制兼容桥）。它的形参顺序/名字与
 *     掩码位严格一一对应：primary=0, onPrimary=1, …, surfaceVariant=17, onSurfaceVariant=18,
 *     surfaceTint=19, …, error=22, onError=23, …, outline=26（0 基下标）。
 *     这里全部用具名参数，所以即使 GZ 那版 Material3 把 surfaceDim 放在下标 30、1.4.0 放在 35，
 *     也不影响结果（这些位置都是省略的默认值）。
 */
internal val GzDarkColorScheme: ColorScheme = darkColorScheme(
    // ThemeKt.java:57 = `a = s91.b(...)`，实参序号按调用点从左到右数（最后 1 个实参是掩码）
    primary = Color(0xFFFA243C), // q91 pos 1  | arg#1  hp1.e(4294583356L)
    onPrimary = Color.White, // q91 pos 2  | arg#2  g91.d = Color.White
    primaryContainer = Color(0xFF7A1220), // q91 pos 3  | arg#3  hp1.e(4286190112L)
    onPrimaryContainer = Color(0xFFFFDADE), // q91 pos 4  | arg#4  hp1.e(4294957790L)
    secondary = Color(0xFF8E8E93), // q91 pos 6  | arg#5  hp1.e(4287532691L) (= 局部变量 jE2)
    onSecondary = Color(0xFF1C1C1E), // q91 pos 7  | arg#6  hp1.e(4280032286L)
    secondaryContainer = Color(0xFF2C2C2E), // q91 pos 8  | arg#7  hp1.e(4281084974L)
    onSecondaryContainer = Color(0xFFE5E5EA), // q91 pos 9  | arg#8  hp1.e(4293256682L)
    tertiary = Color(0xFF636366), // q91 pos 10 | arg#9  hp1.e(4284703590L) (= 局部变量 jE3)
    onTertiary = Color.White, // q91 pos 11 | arg#10 g91.d = Color.White
    background = Color(0xFF0E0D12), // q91 pos 14 | arg#11 AuroraBackgroundKt.b = hp1.e(4279110930L)
    onBackground = Color(0xFFF2F2F7), // q91 pos 15 | arg#12 hp1.e(4294111991L)
    surface = Color(0xFF1C1C1E), // q91 pos 16 | arg#13 hp1.e(4280032286L)
    onSurface = Color(0xFFF2F2F7), // q91 pos 17 | arg#14 hp1.e(4294111991L)
    surfaceVariant = Color(0xFF2C2C2E), // q91 pos 18 | arg#15 hp1.e(4281084974L)
    onSurfaceVariant = Color(0xFFAEAEB2), // q91 pos 19 | arg#16 hp1.e(4289638066L)
    error = Color(0xFFFF453A), // q91 pos 23 | arg#17 hp1.e(4294919482L)
    onError = Color(0xFF1C1C1E), // q91 pos 24 | arg#18 hp1.e(4280032286L)
    outline = Color(0xFF3A3A3C), // q91 pos 27 | arg#19 hp1.e(4282006076L)
    // 省略（掩码位 = 1 → material3 默认值）：inversePrimary, tertiaryContainer, onTertiaryContainer,
    // surfaceTint(=primary), inverseSurface, inverseOnSurface, errorContainer, onErrorContainer,
    // outlineVariant, scrim, surfaceBright, surfaceDim, surfaceContainer,
    // surfaceContainerHigh … onTertiaryFixedVariant（掩码位 32..47）
)

internal val GzLightColorScheme: ColorScheme = lightColorScheme(
    // ThemeKt.java:63 = `b = s91.e(...)`，实参序号按调用点从左到右数（最后 1 个实参是掩码）
    primary = Color(0xFFFA243C), // q91 pos 1  | arg#1  hp1.e(4294583356L)
    onPrimary = Color.White, // q91 pos 2  | arg#2  g91.d = Color.White
    primaryContainer = Color(0xFFFDE0E4), // q91 pos 3  | arg#3  hp1.e(4294828260L) (= 局部变量 jE4)
    onPrimaryContainer = Color(0xFF7A0E1D), // q91 pos 4  | arg#4  hp1.e(4286189085L) (= 局部变量 jE5)
    secondary = Color(0xFF8E8E93), // q91 pos 6  | arg#5  hp1.e(4287532691L) (= 局部变量 jE2)
    onSecondary = Color.White, // q91 pos 7  | arg#6  g91.d = Color.White
    secondaryContainer = Color(0xFFE5E5EA), // q91 pos 8  | arg#7  hp1.e(4293256682L) (= 局部变量 jE6)
    onSecondaryContainer = Color(0xFF1C1C1E), // q91 pos 9  | arg#8  hp1.e(4280032286L) (= 局部变量 jE7)
    tertiary = Color(0xFF636366), // q91 pos 10 | arg#9  hp1.e(4284703590L) (= 局部变量 jE3)
    onTertiary = Color.White, // q91 pos 11 | arg#10 g91.d = Color.White
    background = Color(0xFFFAFAFC), // q91 pos 14 | arg#11 AuroraBackgroundKt.a = hp1.e(4294638332L)
    onBackground = Color(0xFF1C1C1E), // q91 pos 15 | arg#12 hp1.e(4280032286L)
    surface = Color(0xFFFAFAFC), // q91 pos 16 | arg#13 AuroraBackgroundKt.a = hp1.e(4294638332L)
    onSurface = Color(0xFF1C1C1E), // q91 pos 17 | arg#14 hp1.e(4280032286L)
    surfaceVariant = Color(0xFFF2F2F7), // q91 pos 18 | arg#15 hp1.e(4294111991L)
    onSurfaceVariant = Color(0xFF8E8E93), // q91 pos 19 | arg#16 hp1.e(4287532691L)
    error = Color(0xFFFF3B30), // q91 pos 23 | arg#17 hp1.e(4294916912L)
    onError = Color.White, // q91 pos 24 | arg#18 g91.d = Color.White
    outline = Color(0xFFD1D1D6), // q91 pos 27 | arg#19 hp1.e(4291940822L)
    // 省略（掩码位 = 1 → material3 默认值）：inversePrimary, tertiaryContainer, onTertiaryContainer,
    // surfaceTint(=primary), inverseSurface, inverseOnSurface, errorContainer, onErrorContainer,
    // outlineVariant, scrim, surfaceBright, surfaceDim, surfaceContainer,
    // surfaceContainerHigh … onTertiaryFixedVariant（掩码位 32..47）
)
