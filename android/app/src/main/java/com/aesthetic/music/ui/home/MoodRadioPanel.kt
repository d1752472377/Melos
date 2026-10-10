package com.aesthetic.music.ui.home

import android.animation.ValueAnimator
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.ui.components.discoveryGradientCard
import com.aesthetic.music.ui.components.glassBg
import com.aesthetic.music.ui.components.glassSubtleSurface
import com.aesthetic.music.ui.components.textHigh
import com.aesthetic.music.ui.components.textLow
import com.aesthetic.music.ui.components.textMedium
import com.aesthetic.music.ui.theme.appIsDark
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.isActive

/*
 * ============================================================================
 * GZ Music「心情/情绪电台」版块 —— 逐值还原自 JADX 反编译产物
 * ============================================================================
 *
 * 源文件：
 *   gz_decompiled/sources/com/gzmusic/app/feature/home/HomeScreenKt.java
 *     - m48MoodRadioChannelTabsOadGlvw   HomeScreenKt.java:10072-10331  频道标签栏
 *     - MoodRadioPanel                   HomeScreenKt.java:10334-10739  版块容器（卡片）
 *     - m47HourBarsRPmYEkk               HomeScreenKt.java:9627-9814    时段柱状装饰
 *   gz_decompiled/sources/com/gzmusic/app/feature/home/MoodRadioArtworkKt.java
 *     - m53MoodRadioArtworksW7UJKQ       MoodRadioArtworkKt.java:56-246  插画区
 *     - a(name): 每个频道的 3 段插画调色板  MoodRadioArtworkKt.java:250-309
 *   gz_decompiled/sources/defpackage/o05.java:36-93    插画真正的绘制 lambda（DrawScope）
 *   gz_decompiled/sources/defpackage/q05.java:45-139   插画进度动画的挂起循环
 *
 * 反编译门面解码（全部核对过定义，不是猜测）：
 *   - hp1.e(long) = Color(0x........)              hp1.java:3160
 *   - hp1.h0(c, t, target) = lerp(c, target, t)    hp1.java:3275-3294（逐通道 lerp）
 *   - hp1.i0(c) = c.luminance()                    hp1.java:3394
 *   - hp1.s(m, color, shape) = background(color, shape)   hp1.java:3976（hp1.n = RectangleShape，ue3.java:33）
 *   - hp1.p0(m, h, v) = padding(horizontal, vertical)     hp1.java:3950
 *   - hp1.r0(m, s, t, e, b) = padding(start, top, end, bottom)  hp1.java:3972
 *   - hp1.s0(m, s, t, e, b, mask) = padding(..., 带 $default)    hp1.java:3980
 *   - hp1.q0(m, h, v, mask) = padding(horizontal, vertical, $default) hp1.java:3958
 *   - g91.c(a, c) = c.copy(alpha = a)；g91.d = White；g91.b = Black
 *   - ij7.g(m, f) = fillMaxWidth(f)   ij7.java:70（uy1.d = Width）
 *   - ij7.c(m, f) = fillMaxHeight(f)  ij7.java:54（uy1.c = Height）
 *   - ij7.i(m, d) = height(d)         ij7.java:78（hj7 的 int-5 构造 → SizeElement(NaN, d, NaN, d, true)）
 *   - ij7.j(m, d, _, 2) = heightIn(min = d)  ij7.java:82（SizeElement(NaN, d, NaN, NaN, true)）
 *   - ij7.n(m, d) = size(d)；ij7.k(m, d) = requiredSize(d)
 *   - jj0.a(m, …) = Spacer(m)；jj0.d(align, false) = Box(contentAlignment = align)  jj0.java:12/80
 *   - gy6.a(hArrangement, vAlignment, …) = Row(...)  gy6.java:8
 *   - aa1.a(vArrangement, hAlignment, …) = Column(...)  aa1.java:8
 *   - zh.b = Arrangement.Start（g10.java:107-109）；zh.d = Arrangement.Top（h10.java:43-49）
 *   - zh.e = Arrangement.Center（tl6.java:220-221）
 *   - il0.p = Alignment.CenterEnd；il0.f = TopStart；il0.f0 = CenterVertically；il0.g0 = Bottom；
 *     il0.e0 = Top；il0.h0 = Start；il0.Y = BottomCenter；il0.o = Center   il0.java:51-65
 *   - sq2.o = SemiBold(600)；sq2.p = Bold(700)；sq2.j = Medium(500)     sq2.java:18-35
 *   - c88.b(text, modifier, color, fontStyle, fontSize, fontWeight, fontFamily, letterSpacing,
 *           textDecoration, lineHeight, overflow, softWrap, maxLines, minLines, onTextLayout, style, …)
 *     → material3 Text 的形参顺序（少了 textAlign，多余两个 JADX 未还原的形参）  c88.java:153
 *   - qk5.r0(ms, 2, easing) = tween(durationMillis = ms, easing = easing)  qk5.java:1654-1663
 *   - bs4.a = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)   bs4.java:6（Material3 强调缓动）
 *   - zi7.a(color, spec, label) = animateColorAsState   zi7.java:8
 *   - yn.b(target, spec, label) = animateFloatAsState（内部走 yn.c → Animatable）  yn.java:28-55
 *   - jv4.a(0f, 0.01f) = Animatable(0f, 0.01f)          jv4.java:236-238
 *   - er5.t(m, onDraw) = Modifier.drawBehind(onDraw)    er5.java:1836（j72 → mm0 绘制节点）
 *   - pr6.N(stops, _, _, _) = Brush.horizontalGradient(colorStops)  pr6.java:211
 *   - o13.a(m, brush, shape, _) = background(brush, shape)          o13.java:9
 *   - sh6.a(m, color, strokeWidth, track, cap, _) = CircularProgressIndicator  sh6.java:32
 *   - si3.a(vector, desc, modifier, tint, …) = Icon(...)
 *   - rt0.e(…) = GlassTokensKt.textHigh()；rt0.C(…) = GlassTokensKt.textMedium()  rt0.java:88/29
 *   - rt0.g(…, 1.0f, true) = Modifier.weight(1.0f, fill = true)（ez3 = WeightElement，ez3.java:9）
 *   - x77.a(m, z, onClick) = Modifier.then(r00(onClick, z))（r00.java:9 —— 第二个字段是 enabled）
 *   - MotionKt.c(m, enabled, pressedScale, z2, interactionSource, onClick, mask)
 *     = Modifier.bounceClick(enabled, pressedScale, …)  com/gzmusic/app/ui/MotionKt.java:74-99
 *     （内部：animateFloatAsState(label = "pressScale"，MotionKt.java:62) + scale + clickable）
 *   - ct.a = tween(150, easing = bs4.a)（按压动画规格，见 CategoryBlindBoxRow.kt:213 的既有解码）
 *   - xn = ChevronRight/Rounded 图标包（zh.java:2219 "Rounded.ChevronRight"）
 *   - rd2.C() = "Rounded.Pause"（rd2.java:137）；fd2.q() = "Rounded.PlayArrow"（fd2.java:1309）
 *
 * 本文件**不修改**任何其它文件；MoodRadioPanel 的参数名与顺序保持主控 HomeScreen 的调用方式，
 * 新增的当前播放/连接状态都是**带默认值的可选参数**（见下方 doc）。
 */

// ============================================================================
// 数据：12 个情绪频道（HomeScreenKt.java:314 静态字段 l，逐字面量照抄）
// ============================================================================

/**
 * 频道模型。对应反编译里的 `n05`（n05.java:5-14：cat / tagline / tone）。
 */
private data class GzMoodChannel(
    /** 频道名（n05.a） */
    val name: String,
    /** 频道副标题（n05.b） */
    val tagline: String,
    /** 频道主色调（n05.c = hp1.e(...)） */
    val tone: Color,
)

/** HomeScreenKt.java:314 —— `l = listOf(n05(...) × 12)`，顺序与字面量完全一致 */
private val GzMoodChannels: List<GzMoodChannel> = listOf(
    GzMoodChannel("怀旧", "让旧时光重新播放", Color(0xFFD88445)), // HomeScreenKt.java:314 hp1.e(4292379717L)
    GzMoodChannel("清新", "给耳朵一阵清风", Color(0xFF20B996)),   // HomeScreenKt.java:314 hp1.e(4280334742L)
    GzMoodChannel("浪漫", "把心动写进旋律", Color(0xFFDB5C98)),   // HomeScreenKt.java:314 hp1.e(4292566168L)
    GzMoodChannel("伤感", "把没说完的话听完", Color(0xFF7388DB)), // HomeScreenKt.java:314 hp1.e(4285761755L)
    GzMoodChannel("治愈", "让旋律轻轻接住你", Color(0xFFEAAF4B)), // HomeScreenKt.java:314 hp1.e(4293570379L)
    GzMoodChannel("放松", "把紧绷慢慢放下", Color(0xFF9476D8)),   // HomeScreenKt.java:314 hp1.e(4287919832L)
    GzMoodChannel("夜晚", "留一盏灯给深夜", Color(0xFF747AD1)),   // HomeScreenKt.java:314 hp1.e(4285823697L)
    GzMoodChannel("清晨", "用好心情叫醒今天", Color(0xFFF5AC4D)), // HomeScreenKt.java:314 hp1.e(4294290509L)
    GzMoodChannel("散步", "跟着晚风慢慢走", Color(0xFF5AAF7B)),   // HomeScreenKt.java:314 hp1.e(4284133243L)
    GzMoodChannel("运动", "让节拍推你向前", Color(0xFFED795F)),   // HomeScreenKt.java:314 hp1.e(4293753183L)
    GzMoodChannel("民谣", "听见故事里的远方", Color(0xFFD99353)), // HomeScreenKt.java:314 hp1.e(4292449107L)
    GzMoodChannel("电子", "让脉冲点亮此刻", Color(0xFF8874E7)),   // HomeScreenKt.java:314 hp1.e(4287132903L)
)

// ============================================================================
// 数据：每个频道的 3 段插画调色板（MoodRadioArtworkKt.java:250-309）
// ============================================================================

/** MoodRadioArtworkKt.java:287-288 —— switch 的 default 分支（12 个频道里 "电子" 走这条） */
private val GzMoodPaletteDefault: List<Color> = listOf(
    Color(0xFFEAE5FF), // MoodRadioArtworkKt.java:287 wi0.A(4293584383L, ...)
    Color(0xFFD7CEFF), // MoodRadioArtworkKt.java:287 wi0.A(..., 4292333311L, ...)
    Color(0xFFC3D8FF), // MoodRadioArtworkKt.java:287 wi0.A(..., 4291025151L)
)

/** MoodRadioArtworkKt.java:252-289 —— 11 个显式频道的原始色值（还未做 lerp 提亮） */
private val GzMoodPaletteBase: Map<String, List<Color>> = mapOf(
    "伤感" to listOf(Color(0xFFE8ECFF), Color(0xFFD6DEFF), Color(0xFFC6D3FA)), // :254 4293455103/4292271871/4291220474
    "夜晚" to listOf(Color(0xFFE5E4FF), Color(0xFFCDCFFA), Color(0xFFBABFF2)), // :257 4293256447/4291678202/4290428914
    "怀旧" to listOf(Color(0xFFFFEDD5), Color(0xFFFFDDBA), Color(0xFFFFCAA5)), // :260 4294962645/4294958522/4294953637
    "放松" to listOf(Color(0xFFEFE7FF), Color(0xFFE2D5FF), Color(0xFFCCDFFF)), // :263 4293912575/4293055999/4291616767
    "散步" to listOf(Color(0xFFE6F7DE), Color(0xFFD1F0C9), Color(0xFFBFEADB)), // :266 4293326814/4291948745/4290767579
    "治愈" to listOf(Color(0xFFFFF3D6), Color(0xFFFFE9B9), Color(0xFFFFD9B5)), // :269 4294964182/4294961593/4294957493
    "民谣" to listOf(Color(0xFFFFF0DA), Color(0xFFFFE2C0), Color(0xFFFAD2AF)), // :272 4294963418/4294959808/4294628015
    "浪漫" to listOf(Color(0xFFFFE7F2), Color(0xFFFFCFE8), Color(0xFFF4BBE6)), // :275 4294961138/4294954984/4294228966
    "清新" to listOf(Color(0xFFDDF9F1), Color(0xFFC5F3E3), Color(0xFFDAF6AC)), // :278 4292737521/4291163107/4292540076
    "清晨" to listOf(Color(0xFFFFF5D9), Color(0xFFFFE8AB), Color(0xFFFFD3B4)), // :281 4294964697/4294961323/4294955956
    "运动" to listOf(Color(0xFFFFE7DA), Color(0xFFFFCFBB), Color(0xFFFFBFAE)), // :284 4294961114/4294954939/4294950830
)

/**
 * MoodRadioArtworkKt.java:250-309 —— `a(name) : List<Color>`：
 * 取该频道的 3 个原始色，按下标做 `lerp(色, White, (i / max(1, size-1)) * 0.24f + 0.08f)`
 * （`hp1.h0(jLongValue, ((f / i3) * 0.24f) + 0.08f, g91.d)`，i3 = max(1, size-1)，见 :299-305）。
 */
private fun gzMoodPalette(name: String): List<Color> {
    val base = GzMoodPaletteBase[name] ?: GzMoodPaletteDefault
    val lastIndex = if (base.size - 1 >= 1) base.size - 1 else 1
    return base.mapIndexed { index, color ->
        lerp(color, Color.White, (index.toFloat() / lastIndex) * 0.24f + 0.08f)
    }
}

// ============================================================================
// 主入口（对应 HomeScreenKt.java:10334 MoodRadioPanel）
// ============================================================================

/**
 * 心情电台卡片。
 *
 * 必须保持的接口：[onPlayMood] / [modifier]（参数名与顺序不可改）。
 * 其余参数都是**带默认值的可选项**，用来承接 GZ 里来自 PlayerViewModel 的状态：
 *  - [playingMood]  当前播放队列的情绪电台名（GZ: queueSource.label 去掉 "心情电台 · " 前缀，:10369-10381）
 *  - [isPlaying]    是否正在播放（GZ: nowPlaying.isPlaying，:10438）
 *  - [isConnecting] 是否正在连接（GZ: str != null，:10440）
 *  - [hourBars]     可选：m47「时段柱状装饰」的数据（GZ 里它属于听力报告版块，见文件末尾说明）
 */
@Composable
fun MoodRadioPanel(
    onPlayMood: (String) -> Unit,
    modifier: Modifier = Modifier,
    playingMood: String? = null,
    isPlaying: Boolean = false,
    isConnecting: Boolean = false,
    hourBars: List<Long>? = null,
) {
    val dark = appIsDark()

    // GZ 的当前频道只看「正在播放的情绪」（HomeScreenKt.java:10415-10431），
    // 匹配不到（含索引 < 0）时回退到频道列表第一个。Melos 的播放层没有「情绪」元数据，
    // playingMood 永远传不进来，于是频道恒为第一个 —— 面板就成了静态的。
    //
    // 这里沿用 GZ 同一处的回退精神，补一个**本地选中态**作为 playingMood 的回退：
    // 点击频道后立即生效（频道名 / tagline / 插画调色板 / 副标题色都会走已有的
    // animateColorAsState 过渡，见下方 label = "moodNoteTone" / "moodBackground$i" / "moodSubtitleColor"）；
    // 一旦外部提供了权威的 playingMood，仍以外部为准。
    var localMood by rememberSaveable { mutableStateOf<String?>(null) }
    val effectiveMood = playingMood ?: localMood

    val currentChannel = remember(effectiveMood) {
        GzMoodChannels.firstOrNull { it.name == effectiveMood } ?: GzMoodChannels.first()
    }
    // :10437 —— `zE = iq3.e(str2, str5)`：当前队列情绪 == 选中的频道
    //（用 effectiveMood 以让本地选中态也能驱动动作文案的变化）
    val isThisChannel = effectiveMood != null && effectiveMood == currentChannel.name
    // :10438 —— `z3 = zE && nowPlaying.isPlaying`
    val isThisPlaying = isThisChannel && isPlaying

    // :10440-10447 —— 动作文案（"正在连接" / "暂停收听" / "继续收听" / "开始收听"）
    val actionLabel = when {
        isConnecting -> "正在连接" // HomeScreenKt.java:10441
        isThisPlaying -> "暂停收听" // HomeScreenKt.java:10443
        isThisChannel -> "继续收听" // HomeScreenKt.java:10445
        else -> "开始收听" // HomeScreenKt.java:10445
    }

    // :10436 —— `zi7.a(频道色调, qk5.r0(400, 2, bs4.a), "moodNoteTone")`
    val tone by animateColorAsState(
        targetValue = currentChannel.tone,
        animationSpec = tween(durationMillis = 400, easing = GzEmphasizedEasing),
        label = "moodNoteTone",
    )

    // :10449-10472 —— 3 个插画色各自 animateColorAsState，label = "moodBackground" + index
    val palette = gzMoodPalette(currentChannel.name)
    val animatedPalette = palette.mapIndexed { index, color ->
        animateColorAsState(
            targetValue = color,
            animationSpec = tween(durationMillis = 400, easing = GzEmphasizedEasing),
            label = "moodBackground$index",
        ).value
    }

    // :10474-10482 —— 对比度计算用的底色集合：深色下是 listOf(glassBg())，浅色下是插画调色板
    val contrastBackgrounds = if (dark) listOf(glassBg()) else palette

    // :10506-10537 —— 副标题色（对比度扫描），再 animateColorAsState(label = "moodSubtitleColor")
    val subtitleColor by animateColorAsState(
        targetValue = gzMoodSubtitleColor(currentChannel.tone, contrastBackgrounds),
        animationSpec = tween(durationMillis = 400, easing = GzEmphasizedEasing),
        label = "moodSubtitleColor",
    )

    // :10539-10542 —— Column 本体：
    //   `GlassTokensKt.discoveryGradientCard(hp1.q0(ij7.g(c05Var, 1.0f), 16.0f, 0.0f, 2),
    //                                        by6.a(20.0f), arrayList3, true)`
    //   = fillMaxWidth().padding(horizontal = 16.dp).discoveryGradientCard(RoundedCornerShape(20.dp),
    //                                                                      动效调色板, useSharedGradient = true)
    //   竖直 Arrangement.Top（zh.d）、水平 Alignment.Start（il0.h0）
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .discoveryGradientCard(
                shape = RoundedCornerShape(20.dp),
                colors = animatedPalette,
                useSharedGradient = true,
            ),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        // :10565-10616 —— 卡片内容区：
        //   fillMaxWidth() + MotionKt.c(!isConnecting, 0.98f, …, onClick = { if (!zE) onChannelClick else togglePlayPause })
        //   点击盒本身是 Box(contentAlignment = TopStart)（jj0.d(il0.f, false)，:10617）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pressClick(
                    enabled = !isConnecting,
                    pressedScale = 0.98f,
                    onClick = { localMood = currentChannel.name; onPlayMood(currentChannel.name) },
                ),
            contentAlignment = Alignment.TopStart,
        ) {
            // :10634 —— 插画区（m53）：Modifier.align(CenterEnd).fillMaxWidth(0.44f).height(92.dp)
            MoodRadioArtwork(
                channel = currentChannel.name,
                tone = tone,
                playing = isThisPlaying,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxWidth(0.44f)
                    .height(92.dp),
            )

            // :10635-10636 —— 文案列：fillMaxWidth(0.62f).heightIn(min = 112.dp)
            //                   .padding(start = 18.dp, top = 12.dp, end = 8.dp, bottom = 12.dp)
            //                 竖直 Arrangement.Center（zh.e）、水平 Alignment.Start
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .heightIn(min = 112.dp)
                    .padding(start = 18.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start,
            ) {
                // :10650-10673 —— 「心情电台 ›」小标题行：heightIn(min = 18.dp) + 可按压 + onClick = 播放/暂停
                Row(
                    modifier = Modifier
                        .heightIn(min = 18.dp)
                        .pressClick(
                            enabled = true, // MotionKt.c 的 mask 13 ⇒ enabled 取默认值 true（HomeScreenKt.java:10657）
                            pressedScale = 0.98f,
                            onClick = { localMood = currentChannel.name; onPlayMood(currentChannel.name) },
                        ),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "心情电台", // HomeScreenKt.java:10672
                        color = subtitleColor,
                        fontSize = 10.sp, // :10672 co7.j(10)
                        fontWeight = FontWeight.SemiBold, // :10672 sq2.o = 600
                        letterSpacing = 0.1.sp, // :10672 co7.i(0.1d)
                        lineHeight = 14.sp, // :10672 co7.j(14)
                    )
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight, // :10673 zh.v() = "Rounded.ChevronRight"
                        contentDescription = null, // :10673 反编译里 desc 传的就是 null
                        modifier = Modifier.size(12.dp), // :10673 ij7.n(c05Var, 12.0f)
                        tint = subtitleColor, // :10673
                    )
                }

                Spacer(modifier = Modifier.height(6.dp)) // :10674 ij1.x(…, 6.0f) = Spacer(Modifier.height(6.dp))

                // :10675 —— 频道名
                Text(
                    text = currentChannel.name, // :10675 str7
                    color = textHigh(), // :10675 GlassTokensKt.textHigh
                    fontSize = 22.sp, // :10675 co7.j(22)
                    fontWeight = FontWeight.Bold, // :10675 sq2.p = 700
                    letterSpacing = (-0.4).sp, // :10675 co7.i(-0.4d)
                    lineHeight = 28.sp, // :10675 co7.j(28)
                )

                Spacer(modifier = Modifier.height(6.dp)) // :10676 wl7.d(…, ij7.i(c05Var, 6.0f))

                // :10677 —— 频道副标题（tagline）
                Text(
                    text = currentChannel.tagline, // :10677 n05Var3.b
                    color = subtitleColor, // :10677
                    fontSize = 11.sp, // :10677 co7.j(11)
                    lineHeight = 16.sp, // :10677 co7.j(16)
                    overflow = TextOverflow.Ellipsis, // :10677 第 11 参 = 2
                    softWrap = false, // :10677 第 12 参 = false
                    maxLines = 2, // :10677 第 13 参 = 2
                    // TODO(未确定): 同一调用的第 14 参 = 0（minLines 位），Compose 不接受 0，故省略。
                )
            }
        }

        // :10730 —— 频道标签栏（m48）
        MoodChannelTabs(
            selectedChannel = currentChannel.name,
            tone = tone,
            isConnecting = isConnecting,
            isPlayingChannel = isThisPlaying,
            actionLabel = actionLabel,
            // 点击频道：先落本地选中态（驱动颜色与文案），再交给宿主播放该情绪
            onChannelClick = { name ->
                localMood = name
                onPlayMood(name)
            },
            onAction = { onPlayMood(currentChannel.name) },
        )

        // m47 在 GZ 里由听力报告版块调用（HomeScreenKt.java:10049），情绪电台本身不渲染；
        // 这里只在调用方显式传入 hourBars 时渲染（默认 null ⇒ 与原版一致）。
        if (hourBars != null) {
            MoodHourBars(values = hourBars, tone = tone)
        }
    }
}

// ============================================================================
// 1) m53MoodRadioArtworksW7UJKQ → MoodRadioArtwork
//    MoodRadioArtworkKt.java:56-246 + o05.java:36-93（绘制）+ q05.java:45-139（动画循环）
// ============================================================================

/** o05.java:54 —— 13 根柱子的振幅权重，逐字面量照抄 */
private val GzMoodBarWeights = floatArrayOf(
    0.86f, 0.66f, 0.48f, 0.56f, 0.76f, 0.92f, 1.0f,
    0.88f, 0.7f, 0.5f, 0.46f, 0.64f, 0.86f,
)

@Composable
private fun MoodRadioArtwork(
    channel: String,
    tone: Color,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    val dark = appIsDark()

    // MoodRadioArtworkKt.java:109-121 —— remember { ValueAnimator.areAnimatorsEnabled() }
    //   `z3 = z && animatorsEnabled`
    val animatorsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    val animateNow = playing && animatorsEnabled

    // MoodRadioArtworkKt.java:122-127 —— jv4.a(0.0f, 0.01f) = Animatable(0f, 0.01f)
    val progress = remember { Animatable(0f, 0.01f) }

    // MoodRadioArtworkKt.java:128-135 —— yn.b(if (z) 1.0f else 0.35f, qk5.r0(400, 2, bs4.a), "moodEqualizerActivity")
    //   = animateFloatAsState(targetValue, tween(400, CubicBezierEasing(0.2,0,0,1)), label)
    val pulse by animateFloatAsState(
        targetValue = if (playing) 1.0f else 0.35f, // :128-132
        animationSpec = tween(durationMillis = 400, easing = GzEmphasizedEasing),
        label = "moodEqualizerActivity", // :135
    )

    // q05.java:45-139 —— LaunchedEffect(z3)：
    //   if (!playing) return
    //   while (isActive) {
    //     t = ((2π - value) / 2π).coerceIn(0, 1)
    //     animateTo(2π, tween(max(1, (2400f * t).toInt()), easing = LinearEasing))   // q05.java:97-104
    //     snapTo(0f)                                                                // q05.java:121-127
    //   }
    LaunchedEffect(animateNow) {
        if (!animateNow) return@LaunchedEffect
        val twoPi = 6.2831855f // q05.java:84
        while (isActive) {
            val remaining = ((twoPi - progress.value) / twoPi).coerceIn(0f, 1f) // q05.java:88-94
            val durationMillis = max(1, (2400f * remaining).toInt()) // q05.java:97-101
            progress.animateTo(
                targetValue = twoPi, // q05.java:96
                animationSpec = tween(durationMillis = durationMillis, easing = LinearEasing), // q05.java:103-104
            )
            progress.snapTo(0f) // q05.java:127
        }
    }

    // o05.java:43-57 —— 绘制参数
    val base = lerp(tone, if (dark) Color.White else Color.Black, if (dark) 0.42f else 0.16f) // :44-51
    val barAlpha = if (dark) 0.66f else 0.56f // :52
    // :57 —— `((str.hashCode() & Log.LOG_LEVEL_OFF) % 7) * 0.12f`；media3 的 Log.LOG_LEVEL_OFF = Int.MAX_VALUE
    val phase = ((channel.hashCode() and Int.MAX_VALUE) % 7) * 0.12f

    // MoodRadioArtworkKt.java:157 —— jj0.a(er5.t(modifier, onDraw)) = Spacer(modifier.drawBehind { … })
    Spacer(
        modifier = modifier.drawBehind {
            // o05.java:53 —— 四段 stop 的横向渐变
            val brush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0f to base.copy(alpha = 0f), // o05.java:53
                    0.3f to base.copy(alpha = barAlpha), // o05.java:53
                    0.7f to base.copy(alpha = barAlpha), // o05.java:53
                    1f to base.copy(alpha = 0f), // o05.java:53
                ),
            )
            val centerY = size.height * 0.52f // o05.java:43
            val pitch = size.width * 0.64f / 12f // o05.java:55
            val cornerRadius = (0.3f * pitch).coerceIn(2.dp.toPx(), 3.dp.toPx()) // o05.java:56
            val progressRadians = progress.value // o05.java:63
            val pulseValue = pulse

            for (index in 0 until 13) { // o05.java:65 —— 13 根柱子
                val i = index.toFloat()
                // o05.java:67
                var barHeight = size.height * 0.2f * GzMoodBarWeights[index] * pulseValue *
                    (
                        (sin(2f * progressRadians - 0.62f * i) * 0.06f) +
                            (sin(0.85f * i + progressRadians + phase) * 0.11f) +
                            1f
                        ) +
                    1.5.dp.toPx()
                val maxBarHeight = size.height * 0.3f // o05.java:68
                if (barHeight > maxBarHeight) barHeight = maxBarHeight // o05.java:69-71
                val x = (pitch * i) + (size.width * 0.18f) // o05.java:72

                // o05.java:74 —— g72.f1(...) = DrawScope.drawRoundRect(brush, topLeft, size, cornerRadius,
                //   <第 5 个 int 形参>, alpha)。
                //   反编译里给定的两个 packed long 分别是 `Offset(x, centerY - h)` 与
                //   `Offset(x, centerY + h)`（x 在高 32 位），这里按字面拆成 topLeft / Size 使用：
                //     topLeft = Offset(x, centerY - h)   size = Size(x, centerY + h)
                //   alpha = 1.0f（调用点掩码 480 ⇒ 第 6 形参取默认 1.0f）。
                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(x, centerY - barHeight),
                    size = Size(x, centerY + barHeight),
                    cornerRadius = CornerRadius(cornerRadius),
                    alpha = 1f,
                )
            }
        },
    )
    // TODO(未确定): 上面 drawRoundRect 的第 5 个 int 形参在反编译里由 f1 的掩码算出（本处取 1），
    //   g72.java:159 的签名是 g0(brush, long, long, float, int, float)，无法确定它对应 Compose 的
    //   style（对象）还是 blendMode（value class），故按 Compose 默认的 Fill / SrcOver 绘制。
}

// ============================================================================
// 2) m48MoodRadioChannelTabsOadGlvw → MoodChannelTabs（+ MoodChannelChip）
//    HomeScreenKt.java:10072-10331
// ============================================================================

@Composable
private fun MoodChannelTabs(
    selectedChannel: String,
    tone: Color,
    isConnecting: Boolean,
    isPlayingChannel: Boolean,
    actionLabel: String,
    onChannelClick: (String) -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = appIsDark()

    // :10101 —— s24.a(0, 0, …) = rememberLazyListState()
    val listState = rememberLazyListState()
    val selectedIndex = remember(selectedChannel) {
        GzMoodChannels.indexOfFirst { it.name == selectedChannel }
    }

    // hd3.java:48-87 —— LaunchedEffect(index)：index < 0 直接返回；已在可见区则不动，否则 animateScrollToItem
    LaunchedEffect(selectedIndex) {
        if (selectedIndex < 0) return@LaunchedEffect
        if (listState.layoutInfo.visibleItemsInfo.any { it.index == selectedIndex }) return@LaunchedEffect
        listState.animateScrollToItem(selectedIndex)
    }

    // :10125-10126 —— Row(fillMaxWidth().padding(start=12, top=0, end=12, bottom=12),
    //                      horizontalArrangement = spacedBy(10.dp), verticalAlignment = CenterVertically)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 0.dp, end = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // :10148-10278 —— LazyRow：Modifier.weight(1f, fill = true)，state = listState，
        //                  horizontalArrangement = spacedBy(8.dp)，userScrollEnabled = false
        LazyRow(
            modifier = Modifier.weight(1f, fill = true), // rt0.g(…, 1.0f, true)
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(8.dp), // :10162 j10(8.0f, …)
            userScrollEnabled = false, // :10278
            // TODO(未确定): GZ 还挂了 IOSOverscrollEffectKt.rememberIOSOverScrollEffect(fm5.d, null, …)（iOS 回弹）
            //   与 Modifier.drawWithContent(fw2(19)) / drawBehind(nc3(listState)) 两个装饰绘制 lambda
            //   （HomeScreenKt.java:10154/10161），两个 lambda 的合成体无法逐值还原，这里省略。
        ) {
            items(items = GzMoodChannels, key = { it.name }) { channel -> // :10199 list.get(i6)
                MoodChannelChip(
                    channel = channel,
                    selected = channel.name == selectedChannel, // :10203
                    dark = dark,
                    enabled = !isConnecting, // :10234 z6 = !z4
                    onClick = { onChannelClick(channel.name) }, // :10240 c7(at2Var3, n05Var, 6)
                )
            }
        }

        // :10280-10314 —— 右侧动作按钮：40dp 圆形 + [频道色调 → 色调提亮 22%] 线性渐变
        Box(
            modifier = Modifier
                .size(40.dp) // :10280 ij7.n(c05Var, 40.0f)
                .clip(CircleShape) // :10280 by6.a = RoundedCornerShape(50%) = CircleShape
                .background(
                    Brush.linearGradient(
                        listOf(tone, lerp(tone, Color.White, 0.22f)), // :10283 hp1.h0(j2, 0.22f, White)
                    ),
                )
                .pressClick(
                    enabled = !isConnecting, // :10290 MotionKt.c(…, !z, 0.9f, …)
                    pressedScale = 0.9f,
                    onClick = onAction,
                ),
            contentAlignment = Alignment.Center, // :10291 jj0.d(il0.o, false)
        ) {
            if (isConnecting) {
                // :10307 sh6.a(ij7.n(c05Var, 18.0f), White, 2.0f, …) = CircularProgressIndicator
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    // :10312 —— z2 ? rd2.C()("Rounded.Pause") : fd2.q()("Rounded.PlayArrow")
                    imageVector = if (isPlayingChannel) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    // GZ 的 si3.a 这里 desc 传 null（:10312）；m48 的动作文案 str2 只被点击 lambda 捕获、
                    // 界面上没有可见渲染，这里把同一字面量用作无障碍描述。
                    contentDescription = actionLabel,
                    modifier = Modifier.size(24.dp), // :10312 ij7.n(c05Var, 24.0f)
                    tint = Color.White, // :10312 j3 = g91.d
                )
            }
        }
    }
}

/** HomeScreenKt.java:10199-10265 —— 单个频道胶囊 */
@Composable
private fun MoodChannelChip(
    channel: GzMoodChannel,
    selected: Boolean,
    dark: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    // :10204-10208 —— 深色下把色调 `lerp(tone, White, 0.35f)`
    val chipTone = if (dark) lerp(channel.tone, Color.White, 0.35f) else channel.tone

    // :10209-10217 —— 选中：chipTone.copy(alpha = 深色 0.22f / 浅色 0.18f)；未选中：glassSubtleSurface()
    val containerColor = if (selected) {
        chipTone.copy(alpha = if (dark) 0.22f else 0.18f)
    } else {
        glassSubtleSurface()
    }

    // :10219 —— zi7.a(containerColor, qk5.r0(150, 2, bs4.a), "moodChannel" + name)
    val animatedContainerColor by animateColorAsState(
        targetValue = containerColor,
        animationSpec = tween(durationMillis = 150, easing = GzEmphasizedEasing),
        label = "moodChannel${channel.name}",
    )

    Box(
        modifier = Modifier
            // :10221-10225 —— ij7.j(c05Var2, 36.0f, 0.0f, 2) = heightIn(min = 36.dp)
            //                  → hq3.g(…, CircleShape) → hp1.s(…, 动画色, hp1.n = RectangleShape)
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .background(animatedContainerColor, RectangleShape)
            // :10243 —— MotionKt.c(…, !isConnecting, 0.96f, …)：按压缩放 + 真正的点击
            .pressClick(enabled = enabled, pressedScale = 0.96f, onClick = onClick)
            // :10243 —— hp1.p0(…, 12.0f, 8.0f) = padding(horizontal = 12.dp, vertical = 8.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // :10244 —— Row(spacedBy(6.dp), CenterVertically)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // :10261 —— 5dp 圆点，颜色 = chipTone（jj0.a(hp1.s(ij7.n(c05Var2, 5.0f), j3, CircleShape)))
            Spacer(
                modifier = Modifier
                    .size(5.dp)
                    .background(chipTone, CircleShape),
            )

            // :10262 —— 频道名：选中 textHigh()/sq2.p(Bold)，未选中 textMedium()/sq2.j(Medium)
            Text(
                text = channel.name,
                color = if (selected) textHigh() else textMedium(),
                fontSize = 12.sp, // :10262 co7.j(12)
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, // :10262 sq2.p / sq2.j
                lineHeight = 16.sp, // :10262 co7.j(16)
                softWrap = false, // :10262 第 12 参 = false
                maxLines = 1, // :10262 第 13 参 = 1
                // TODO(未确定): 同一调用的第 11 参 = 0（overflow 位）与第 14 参 = 0（minLines 位）
                //   在 Compose 里没有对应常量/取值，故省略（HomeScreenKt.java:10262）。
            )
        }
    }
    // TODO(未确定): :10233 还有一层 `x77.a(胶囊修饰符, false, onClick)`（r00.java:9 的第二个字段看起来是
    //   enabled），即 GZ 额外挂了一个 enabled = false 的 clickable；因为真正的点击由 :10243 的 MotionKt.c
    //   承担，这里没有复刻这一层无效果的语义节点。
}

// ============================================================================
// 3) m47HourBarsRPmYEkk → MoodHourBars（时段柱状装饰）
//    HomeScreenKt.java:9627-9814
// ============================================================================

@Composable
private fun MoodHourBars(
    values: List<Long>,
    tone: Color,
    modifier: Modifier = Modifier,
) {
    // :9644-9665 —— 最大值（至少 1）与峰值下标
    var maxValue = values.lastOrNull() ?: 1L // z81.z0(list)
    if (maxValue < 1L) maxValue = 1L
    val peakIndex = values.indexOfFirst { it > 0L && it == maxValue }

    // :9666-9691 —— Column(fillMaxWidth, Arrangement.Top, Alignment.Start) { Row(fillMaxWidth().height(56.dp), …) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp), // :9691 ij7.i(ij7.g(c05Var, 1.0f), 56.0f)
            horizontalArrangement = Arrangement.Start, // :9692 zh.b
            verticalAlignment = Alignment.Bottom, // :9692 il0.g0
        ) {
            values.forEachIndexed { index, value -> // :9708-9748
                // :9717 —— ka7.v(value / maxValue, 0.0f, 1.0f)
                val ratio = (value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)

                Box(
                    modifier = Modifier
                        // :9718 —— Modifier.weight(1f, fill = true).fillMaxHeight()
                        //            .padding(horizontal = 0.75.dp)
                        .weight(1f, fill = true)
                        .fillMaxHeight()
                        .padding(horizontal = 0.75.dp),
                    contentAlignment = Alignment.BottomCenter, // :9719 jj0.d(il0.Y, false)
                ) {
                    // :9737 —— fillMaxHeight(if (v <= 0) 0.07f else 0.86f * ratio + 0.14f)
                    val heightFraction = if (value <= 0L) 0.07f else (0.86f * ratio) + 0.14f
                    // :9738-9742 —— if (i == peak) 0.92f else if (v <= 0) 0.12f else 0.3f
                    val barAlpha = when {
                        index == peakIndex -> 0.92f
                        value <= 0L -> 0.12f
                        else -> 0.3f
                    }
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth(1f) // :9737 ij7.g(c05Var, 1.0f)
                            .fillMaxHeight(heightFraction)
                            .clip(RoundedCornerShape(3.dp)) // :9737 by6.a(3.0f)
                            // :9743 —— hp1.s(…, g91.c(barAlpha, tone), hp1.n = RectangleShape)
                            .background(tone.copy(alpha = barAlpha), RectangleShape),
                    )
                }
            }

            // :9749 —— wl7.d(composer, ij1.k(composer, false, true, c05, 6.0f)) = Spacer(Modifier.height(6.dp))
            Spacer(modifier = Modifier.height(6.dp))
        }

        // :9750-9751 —— Row(fillMaxWidth, Arrangement.Start, Alignment.Top) { 0 / 6 / 12 / 18 / 23 }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top,
        ) {
            listOf("0", "6", "12", "18").forEach { label -> // :9769
                // :9777-9779 —— 每个刻度：Modifier.weight(1f, fill = true) + Box(contentAlignment = TopStart)
                Box(
                    modifier = Modifier.weight(1f, fill = true),
                    contentAlignment = Alignment.TopStart,
                ) {
                    MoodHourLabel(label) // :9796
                }
            }
            // :9802 —— 最后一个刻度 "23" 不在 weight 里
            MoodHourLabel("23")
        }
    }
}

/** HomeScreenKt.java:9796/9802 —— 时段刻度文字（9.sp / 行高 12.sp / textLow()） */
@Composable
private fun MoodHourLabel(text: String) {
    Text(
        text = text,
        color = textLow(), // GlassTokensKt.textLow
        fontSize = 9.sp, // :9796 co7.j(9)
        lineHeight = 12.sp, // :9796 co7.j(12)
        softWrap = false, // :9796 第 12 参 = false
        // TODO(未确定): GZ 该调用的字体族是 HomeScreenKt.f（HomeScreenKt.java:306-308 =
        //   FontFamily(listOf(Font(weight = FontWeight.Normal, …)))），Melos 没有对应 token，故用默认字体。
        // TODO(未确定): 该调用的第 11 参 = 0（overflow 位）、第 13 参 = 0（maxLines 位）、
        //   第 14 参 = 0（minLines 位）在 Compose 里没有对应取值，故省略（HomeScreenKt.java:9796）。
    )
}

// ============================================================================
// 共用辅助
// ============================================================================

/** bs4.java:6 —— `bs4.a` = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)（Material3 强调缓动） */
private val GzEmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * MotionKt.c 的等价物：按压缩放 + 点击（com/gzmusic/app/ui/MotionKt.java:74-99）。
 *  - 缩放目标：`if (pressed && enabled) pressedScale else 1.0f`
 *  - 动画：`animateFloatAsState(label = "pressScale")`，规格 = `ct.a` = tween(150, easing = bs4.a)
 *    （MotionKt.java:62；ct/bs4 的解码见 CategoryBlindBoxRow.kt:213 的既有结论）
 */
@Composable
private fun Modifier.pressClick(
    enabled: Boolean,
    pressedScale: Float,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = tween(durationMillis = 150, easing = GzEmphasizedEasing),
        label = "pressScale",
    )
    return this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

/**
 * HomeScreenKt.java:10506-10535 —— 副标题色：
 * 先用 18 轮扫描把 `tone` 往「黑/白」方向 lerp，取第一个与所有底色对比度都 ≥ 4.5 的候选色。
 *
 * 反编译原样（:10506-10535）：
 *   base = if (底色的平均亮度 >= 0.5) Black else White
 *   i in 0 until 18:  t = min(i * 0.04f + 0.32f, 1f)
 *                     candidate = lerp(tone, base, t)
 *                     若存在底色亮度使 (max(l, bg) + 0.05) / (min(l, bg) + 0.05) < 4.5 → 下一轮，否则采用
 *
 * TODO(未确定): JADX 把这段的循环结构还原成了无法直接照搬的 `while(true)` 嵌套（:10508-10533），
 *   这里按代码里出现的字面量（18 / 0.04f / 0.32f / 0.05f / 4.5f / 0.5）重建了「首个满足对比度即采用」的语义。
 */
private fun gzMoodSubtitleColor(tone: Color, backgrounds: List<Color>): Color {
    val luminances = backgrounds.map { it.luminance() }
    val average = if (luminances.isEmpty()) Float.NaN else luminances.sum() / luminances.size
    var target = if (average >= 0.5f) Color.Black else Color.White // :10506
    for (i in 0 until 18) { // :10508
        val fraction = min((i * 0.04f) + 0.32f, 1f) // :10509-10512
        val candidate = lerp(tone, target, fraction) // :10513
        val candidateLuminance = candidate.luminance() // :10514
        val contrastOk = luminances.all {
            (max(candidateLuminance, it) + 0.05f) / (min(candidateLuminance, it) + 0.05f) >= 4.5f // :10524
        }
        if (contrastOk) {
            target = candidate // :10532
            break
        }
    }
    return target
}
