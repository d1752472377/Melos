package com.aesthetic.music.ui.home

import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.DefaultCoverTone
import com.aesthetic.music.ui.components.glassCard
import com.aesthetic.music.ui.components.textHigh
import com.aesthetic.music.ui.components.textLow
import com.aesthetic.music.ui.components.textMedium
import com.aesthetic.music.ui.theme.appIsDark
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.AppColors

/**
 * 首页「排行榜」卡片（手机版）—— 逐值还原 `HomeRankingCardKt.java` 的 `HomeRankingCard`
 * （函数体 HomeRankingCardKt.java:147-1186，内部封面层叠 RankingCoverStack = :1188-1343）。
 *
 * 反编译解码约定（均已核对定义，非猜测）：
 *  - `oy2`/`yd1` = Composer，末尾 int 是 Compose 变更位掩码，不是业务参数
 *  - `c05.a` = Modifier；`f05` = Modifier；`ay6` = Shape；`ez3(w, true)` = `Modifier.weight(w)`
 *  - `aa1.a(v, h)` = `Column(verticalArrangement = v, horizontalAlignment = h)`
 *      （aa1.java:a(k10 k10Var, wd wdVar, …)，k10=Arrangement.Vertical、wd=Alignment.Horizontal）
 *  - `gy6.a(h, v)` = `Row(horizontalArrangement = h, verticalAlignment = v)`
 *      （gy6.java:a(i10 i10Var, wg0 wg0Var, …)，i10=Arrangement.Horizontal、wg0=Alignment.Vertical）
 *  - `jj0.a(m, …)` = `Box(m)`；`jj0.d(align, z)` = `Box(contentAlignment = align, propagateMinConstraints = z)`
 *  - `wl7.d(composer, m)` = `Spacer(m)`（wl7.java:d 只吃一个 Modifier）
 *  - `ij7.n(m, f)` = size(f)；`ij7.o(m, w, h)` = size(w, h)；`ij7.r(m, f)` = width(f)；
 *    `ij7.i(m, f)` = height(f)；`ij7.j(m, min, max)` = heightIn(min, max)；
 *    `ij7.g(m, f)` = fillMaxWidth(f)；`ij7.e(m, f)` = fillMaxSize(f)
 *    （ij7.java 里 hj7 = SizeElement(minWidth,minHeight,maxWidth,maxHeight,enforce)，逐个按掩码核对）
 *  - `hp1.p0(m, h, v)` = `Modifier.padding(horizontal = h, vertical = v)`（hp1.java:p0 → ko5(h,v,h,v)）
 *  - `hp1.q0(m, s, t, mask)` = `Modifier.padding(start = s, top = t)`
 *  - `hp1.e(N)` = `Color(0xFF......)`；`g91.c(a, c)` = `c.copy(alpha = a)`；`g91.b`=Black、`g91.d`=White、
 *    `g91.g`=Color.Unspecified；`hp1.n` = RectangleShape（ue3.java 的 case 2）
 *  - `by6.a(f)` = `RoundedCornerShape(f.dp)`；`by6.a`（无参）= `RoundedCornerShape(50%)`；
 *    `zh.d`=`Arrangement#Top`(h10)、`zh.e`=`Arrangement#Center`(tl6(13))、`zh.b`=`Arrangement#Start`(g10(3));
 *    `il0.h0`=Alignment.Start、`il0.i0`=`Alignment.CenterHorizontally`、`il0.f0`=`Alignment.CenterVertically`、
 *    `il0.e0`=`Alignment.Top`、`il0.f`=`Alignment.TopStart`、`il0.o`=`Alignment.Center`
 *  - `c88.b/c` = material3 `Text(...)`；`si3.a(v, desc, m, tint, …)` = `Icon(...)`；
 *    `sh6.a(m, color, stroke, …)` = `CircularProgressIndicator(...)`；
 *    `co7.j(i)` = `i.sp`，`co7.i(d)` = `d.sp`
 *  - `sq2` = FontWeight（`sq2.X`=800、`sq2.j`=500、`sq2.o`=600、`sq2.i`=400）
 *  - `CoverToneKt.b` = `hp1.e(4294583356L)` = `0xFFFA243C` = 本项目 `DefaultCoverTone`
 *  - `MotionKt.c(m, enabled, scale, …)` = 按压缩放（GZ `MotionKt.java:52-99`），
 *    动画规格 `ct.a = qk5.r0(150, 2, MotionKt.a)` = `tween(durationMillis = 150,
 *    easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))`（ct.java:9 + bs4.java:14）
 *
 * 注意：**HomeRankingCardKt.java 里不存在「展开/收起」折叠交互，也不存在任何渐变
 * （无 pr6.M / pr6.e0 / pr6.S / Brush 调用）**；唯一的动画是上面的按压缩放。
 * 4 个榜单色调（[RankingTab.tone]）来自 HomeScreenKt.java:309，只被平板/宽屏的
 * ChartCard（HomeScreenKt.java:2071 m38 / :10798 m49）使用，手机版本卡片不使用它们，
 * 选中 Tab 的指示条用的是 DefaultCoverTone（HomeRankingCardKt.java:689、:1072）。
 */

/**
 * GZ `na3` 枚举（defpackage/na3.java:5-19）的 4 个榜单，
 * 展示顺序取 HomeRankingCardKt.java:107 的 `wi0.A(na3.RISING, na3.NEW, na3.ORIGINAL, na3.HOT)`。
 *
 * `tone` / `subtitle` 来自 `ChartTabPresentation`（defpackage/r31.java）在 HomeScreenKt.java:309
 * 的四元组 `new r31(na3.X, 副标题, hp1.e(色调))`；`key` 为 na3.c（接口用的平台 key）。
 */
enum class RankingTab(
    /** na3.c —— 平台 key（"rising"/"new"/"original"/"hot"） */
    val key: String,
    /** na3.d —— 榜单名 */
    val label: String,
    /** HomeScreenKt.java:309 —— 宽屏 ChartCard 副标题 */
    val subtitle: String,
    /** HomeScreenKt.java:309 —— 4 色角标色调（手机版 HomeRankingCard 不消费，见文件头说明） */
    val tone: Color,
) {
    /** na3.RISING("rising","飙升榜",19723756) + HomeScreenKt.java:309 `hp1.e(4284073892L)` */
    RISING("rising", "飙升榜", "正在快速上升的歌曲", AppColors.rankingRising),

    /** na3.NEW("new","新歌榜",3779629) + HomeScreenKt.java:309 `hp1.e(4284193749L)` */
    NEW("new", "新歌榜", "发现正在被听见的新声音", AppColors.rankingNew),

    /** na3.ORIGINAL("original","原创榜",2884035) + HomeScreenKt.java:309 `hp1.e(4288314324L)` */
    ORIGINAL("original", "原创榜", "华语原创好声音", AppColors.rankingOriginal),

    /** na3.HOT("hot","热歌榜",3778678) + HomeScreenKt.java:309 `hp1.e(4293954141L)` */
    HOT("hot", "热歌榜", "听听大家反复循环的歌", AppColors.rankingHot),
}

/**
 * GZ `MotionKt.bounceClick`（MotionKt.java:52-99）的精简等价实现：
 * 只在按下时把内容缩到 [pressedScale]，用 150ms 的 `CubicBezierEasing(0.2f, 0f, 0f, 1f)` 过渡
 * （`ct.a` = `qk5.r0(150, 2, bs4.a)`，ct.java:9；`bs4.a` = `new wm1(0.2f, 0.0f, 0.0f, 1.0f)`，bs4.java:14）。
 * GZ 用 `Modifier.graphicsLayer{ scaleX/scaleY }` + `clickable(interactionSource, indication, enabled, onClick)`
 * （`jv4.x` / `y65.U`），这里按同样方式落地，`indication` 取 GZ 的 `dx6(0)` 默认值（不额外加波纹）。
 */
@Composable
private fun Modifier.gzBounceClick(
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = tween(
            durationMillis = 150,
            easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
        ),
        label = "pressScale",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

/**
 * 首页排行榜卡片。
 *
 * 结构（HomeRankingCardKt.java:272-786）：
 * ```
 * Column(卡片)                       // :274 Column(Arrangement.Top, Alignment.Start)
 *   ├─ Row 榜单名 + 「完整榜单 >」    // :301 Row(Start, CenterVertically)
 *   ├─ Spacer(12dp)                  // :402
 *   ├─ Row 封面层叠 + Top3           // :404 Row(Start, CenterVertically)
 *   ├─ Spacer(12dp)                  // :700
 *   └─ Row 四联 Tab（label + 指示条） // :702 Row(spacedBy(8dp), Top)
 * ```
 */
@Composable
fun HomeRankingCard(
    songsMap: Map<RankingTab, List<Song>>,
    onPlaySong: (Song, List<Song>) -> Unit,
    onOpenRanksPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // HomeRankingCardKt.java:245-246 —— 默认选中 na3.NEW（新歌榜）
    var selected by remember { mutableStateOf(RankingTab.NEW) }
    val tab = selected

    // HomeRankingCardKt.java:255-265 —— 当前 Tab 对应的歌曲列表（:257 list / :264 list2 / :259 map[str]）
    val tabSongs = songsMap[tab].orEmpty()
    // HomeRankingCardKt.java:266 —— 原判定 `z5 = list3.isEmpty() && (!set2.contains(str) || set.contains(str)
    // || (tab == na3.NEW && z))`，依赖 set/set2/z 三个本 API 未暴露的集合；
    // 这里退化为「map 里还没有该 Tab 的键 ⇒ 仍在加载」，键存在但为空则走空态。
    // TODO(未确定): GZ 的加载/已加载集合（set/set2）与首帧标志 z 不在本 API 里，无法逐值还原。
    val isLoading = !songsMap.containsKey(tab)

    Column(
        modifier = modifier
            .fillMaxWidth() // :272 ij7.g(c05Var, 1.0f)
            .padding(horizontal = AppSpacing.xxl) // :272 hp1.q0(…, 16.0f, 0.0f)
            .glassCard(RoundedCornerShape(20.dp)) // :272 by6.a(20.0f)；elevation 掩码位 2 ⇒ 走默认 8dp
            .padding(AppSpacing.xl), // :272 hp1.p0(…, 14.0f, 14.0f)
        verticalArrangement = Arrangement.Top, // :274 zh.d = Arrangement#Top
        horizontalAlignment = Alignment.Start, // :274 il0.h0 = Alignment.Start
    ) {
        // ── 顶部：榜单名 + 「完整榜单 >」 ─────────────────────── HomeRankingCardKt.java:301-397
        Row(
            modifier = Modifier.fillMaxWidth(), // :299 f05VarG = fillMaxWidth
            horizontalArrangement = Arrangement.Start, // :301 zh.b = Arrangement#Start
            verticalAlignment = Alignment.CenterVertically, // :301 il0.f0 = CenterVertically
        ) {
            Text(
                text = tab.label, // :315 na3Var7.d
                modifier = Modifier
                    .weight(1f) // :319 ez3(1.0f, true)
                    .gzBounceClick(pressedScale = 0.98f, onClick = onOpenRanksPage), // :351 MotionKt.c(…, 0.98f)
                color = textHigh(), // :351 GlassTokens.textHigh
                fontSize = 20.sp, // :351 co7.j(20)
                fontWeight = FontWeight(800), // :351 sq2.X = FontWeight(800)
                letterSpacing = (-0.4).sp, // :351 co7.i(-0.4d)
                lineHeight = 26.sp, // :351 co7.j(26)
                // overflow / softWrap / maxLines 在 :351 是默认位占位（0/false/0），保持 Compose 默认
            )
            Row(
                modifier = Modifier
                    .heightIn(min = 30.dp) // :352 ij7.j(c05Var, 30.0f, 0.0f, 2)
                    .gzBounceClick(pressedScale = 0.96f, onClick = onOpenRanksPage), // :383
                verticalAlignment = Alignment.CenterVertically, // :384 il0.f0
            ) {
                Text(
                    text = "完整榜单", // :399
                    color = textLow(), // :399 GlassTokens.textLow
                    fontSize = 10.sp, // :399 co7.j(10)
                    lineHeight = 14.sp, // :399 co7.j(14)
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight, // :400 zh.v() = "Rounded.ChevronRight"(24dp 图标)
                    contentDescription = null, // :400
                    modifier = Modifier.size(14.dp), // :400 ij7.n(c05Var, 14.0f)
                    tint = textLow(), // :400
                )
            }
        }
        Spacer(Modifier.height(AppSpacing.lg)) // :402 ij1.k(…, 12.0f) → height(12.dp)

        // ── 中部：封面层叠 + Top3 ─────────────────────────────── HomeRankingCardKt.java:404-697
        Row(
            modifier = Modifier.fillMaxWidth(), // :403 f05VarG2 = fillMaxWidth
            horizontalArrangement = Arrangement.Start, // :404 zh.b
            verticalAlignment = Alignment.CenterVertically, // :404 il0.f0
        ) {
            RankingCoverStack(
                songs = tabSongs,
                enabled = tabSongs.isNotEmpty(), // :418 z7 = !list5.isEmpty()
                onPlayFirst = {
                    // :790 objT8 = new o6(pt2Var, 18, na3, list6) ⇒ 播放该榜单第 0 首
                    // TODO(未确定): 列表为空时的行为 GZ 由外层 enabled 兜住，这里同样不外抛。
                    tabSongs.firstOrNull()?.let { onPlaySong(it, tabSongs) }
                },
            )
            Spacer(Modifier.width(AppSpacing.lg)) // :432 ij7.r(c05Var, 12.0f)

            Column(
                modifier = Modifier
                    .weight(1f) // :441 ez3(f, true)
                    .heightIn(min = 90.dp), // :441 ij7.j(…, 90.0f, 0.0f, 2)
                verticalArrangement = Arrangement.Center, // :442 zh.e = Arrangement#Center
                horizontalAlignment = Alignment.Start, // :442 il0.h0 = Alignment.Start
            ) {
                if (isLoading) {
                    // :459-479 Row(CenterVertically)：进度圈 + 间距 8dp + 「正在加载榜单」
                    Row(
                        horizontalArrangement = Arrangement.Start, // :459 zh.b
                        verticalAlignment = Alignment.CenterVertically, // :459 il0.f0
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp), // :475 ij7.n(c05Var, 16.0f)
                            color = DefaultCoverTone, // :475 CoverToneKt.b = 0xFFFA243C
                            strokeWidth = 1.7.dp, // :475 1.7f
                        )
                        Spacer(Modifier.width(AppSpacing.sm)) // :476 ij1.B(c05Var, 8.0f, …) 内联的 Spacer(width 8dp)
                        Text(
                            text = "正在加载榜单", // :476
                            color = textMedium(), // :476 GlassTokens.textMedium
                            fontSize = 11.sp, // :476 ij1.B 的返回值 co7.j(11)
                            lineHeight = 16.sp, // :476 co7.j(16)
                        )
                    }
                } else if (tabSongs.isEmpty()) {
                    // :487 + :527 空态
                    Text(
                        text = "暂未获取到榜单", // :487
                        color = textMedium(), // :487
                        fontSize = 12.sp, // :487 co7.j(12)
                        lineHeight = 18.sp, // :487 co7.j(18)
                    )
                    Text(
                        text = "点击重试", // :527
                        modifier = Modifier
                            .heightIn(min = 32.dp) // :488 ij7.j(c05Var, 32.0f, 0.0f, 2)
                            .gzBounceClick(
                                pressedScale = 0.96f, // :527 MotionKt.c(…, 0.96f)
                                // TODO(未确定): :527 点击回调是 at2Var2(tab)（重试拉取榜单），
                                // 本 API 没有对应回调；GZ 视觉与按压反馈保留，动作留空。
                                onClick = {},
                            ),
                        color = DefaultCoverTone, // :527 CoverToneKt.b
                        fontSize = 11.sp, // :527 co7.j(11)
                        fontWeight = FontWeight(500), // :527 sq2.j = FontWeight(500)
                        lineHeight = 16.sp, // :527 co7.j(16)
                    )
                } else {
                    // :534 z81.L0(list6, 3) —— 只显示前 3 首
                    tabSongs.take(3).forEachIndexed { index, song ->
                        // :577 / :579 / :584 —— 三处取色都提到组合函数前面（buildAnnotatedString 不是 inline，
                        // 不允许在它的 lambda 里调用 @Composable 取色函数）
                        val rankColor = textHigh()
                        val titleColor = textHigh()
                        val artistColor = textLow()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth() // :543 ij7.g(c05Var, 1.0f)
                                .heightIn(min = 30.dp) // :543 ij7.j(…, 30.0f, 0.0f, 2)
                                .gzBounceClick(
                                    pressedScale = 0.98f, // :556 MotionKt.c(…, 0.98f)
                                    onClick = { onPlaySong(song, tabSongs) }, // :553 mb3(pt2Var, tab, list6, i4)
                                ),
                            horizontalArrangement = Arrangement.Start, // :557 gy6.a(zh.b, …)
                            verticalAlignment = Alignment.CenterVertically, // :557 il0.f0
                        ) {
                            Text(
                                text = (index + 1).toString(), // :577 String.valueOf(i6)，i6 = i4 + 1
                                modifier = Modifier.width(20.dp), // :577 ij7.r(c05Var, 20.0f)
                                color = rankColor, // :577
                                fontSize = 13.sp, // :577 co7.j(13)
                                fontWeight = FontWeight(800), // :577 sq2.X
                                lineHeight = 18.sp, // :577 co7.j(18)
                            )
                            Text(
                                // :578-592 op/AnnotatedString：歌名（textHigh + 600）+ " · " + 歌手（textLow）
                                text = buildAnnotatedString {
                                    withStyle(
                                        SpanStyle(
                                            color = titleColor, // :579 vq7(jTextHigh, …)
                                            fontWeight = FontWeight(600), // :579 sq2.o = 600
                                        ),
                                    ) {
                                        append(song.title) // :581 chartSong.getName()
                                    }
                                    withStyle(
                                        SpanStyle(color = artistColor), // :584 vq7(jTextLow, …)
                                    ) {
                                        // :587-590 dw7.S(artist) ⇒ "未知歌手"
                                        append(" · " + song.artist.ifBlank { "未知歌手" })
                                    }
                                },
                                modifier = Modifier.weight(1f), // :592 ez3(1.0f, true)
                                color = Color.Unspecified, // :592 0L
                                fontSize = 12.sp, // :592 co7.j(i17)，i17 = 12
                                lineHeight = 18.sp, // :592 co7.j(18)
                                overflow = TextOverflow.Ellipsis, // :592 overflow = 2
                                softWrap = false, // :592 softWrap = false
                                maxLines = 1, // :592 maxLines = 1
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(AppSpacing.lg)) // :700 ij1.k(…, 12.0f)

        // ── 底部：四联 Tab ────────────────────────────────────── HomeRankingCardKt.java:702-785
        Row(
            modifier = Modifier.fillMaxWidth(), // :701 f05VarG4 = fillMaxWidth
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm), // :702 new j10(8.0f, true, …) = spacedBy(8dp)
            verticalAlignment = Alignment.Top, // :702 il0.e0 = Alignment.Top
        ) {
            RankingTab.entries.forEach { entry ->
                val isSelected = entry == tab
                Column(
                    modifier = Modifier
                        .weight(1f) // :640 ez3(1.0f, true)
                        .gzBounceClick(
                            pressedScale = 0.96f, // :648 MotionKt.c(…, 0.96f)
                            // :645 objT3 = new iw2(na3Var3, d65Var, 6) ⇒ 写入选中态
                            onClick = { selected = entry },
                        ),
                    verticalArrangement = Arrangement.Top, // :650 zh.d = Arrangement#Top
                    horizontalAlignment = Alignment.CenterHorizontally, // :650 il0.i0
                ) {
                    Text(
                        text = entry.label, // :668 na3Var3.d
                        modifier = Modifier.padding(vertical = AppSpacing.xs), // :669 hp1.q0(c05Var, 0.0f, 6.0f, 1)
                        color = if (isSelected) {
                            textHigh() // :679 rt0.e = composer 内联的 GlassTokens.textHigh
                        } else {
                            textLow() // :681 yb2.c = GlassTokens.textLow
                        },
                        fontSize = 11.sp, // :670 co7.j(i5)，i5 = 11
                        fontWeight = if (isSelected) {
                            FontWeight(800) // :673/:675 sq2.X
                        } else {
                            FontWeight(500) // :675 sq2.j
                        },
                        lineHeight = 16.sp, // :671 co7.j(16)
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 18.dp, height = 3.dp) // :687 ij7.o(c05Var, 18.0f, 3.0f)
                            .background(
                                color = if (isSelected) {
                                    DefaultCoverTone // :689 CoverToneKt.b = 0xFFFA243C
                                } else {
                                    Color.Unspecified // :692 g91.g
                                },
                                shape = RoundedCornerShape(2.dp), // :694 by6.a(2.0f)
                            ),
                    )
                }
            }
        }
    }
}

/**
 * 封面三重层叠 —— `RankingCoverStack`（HomeRankingCardKt.java:1188-1343）。
 *
 * 固定 104dp × 90dp 的 Box（:1212），从底层 index=2 画到顶层 index=0（:1231-1232）：
 *  - 尺寸 `82 - i*15` dp（:1233）⇒ i=2:52dp、i=1:67dp、i=0:82dp
 *  - 水平偏移 `i*25` dp（:1239）⇒ 0 / 25 / 50
 *  - 圆角 RoundedCornerShape(12.dp)（:1208 by6.a(12.0f)），底色按深浅色取字面量（:1209）
 *  - 兜底图标 `Rounded.Album`，尺寸为封面的 32%（:1267），tint = textLow
 *  - 只有最上层（i == 0）叠播放按钮：外层按压缩放 0.96（:1284），
 *    30dp 黑色 28% 圆（:1299），内部 22dp 白色 PlayArrow（:1317），
 *    contentDescription = "播放当前榜单"（:1317）
 *
 * @param enabled 对应 GZ 的 `z`（:1284 传入 :418 的 `!list.isEmpty()`）
 * @param onPlayFirst 点击播放按钮 ⇒ `pt2Var(tab, list, 0)`
 */
@Composable
private fun RankingCoverStack(
    songs: List<Song>,
    enabled: Boolean,
    onPlayFirst: () -> Unit,
) {
    // :1209 hp1.e(appIsDark ? 4281348151L : 4293454061L) = 0xFF303037 / 0xFFE8E8ED
    val placeholder = if (appIsDark()) AppColors.rankingPlaceholderDark else AppColors.rankingPlaceholderLight
    val iconTint = textLow()

    Box(
        modifier = Modifier
            .width(104.dp) // :1212 ij7.r(c05Var, 104.0f)
            .height(90.dp), // :1212 ij7.i(…, 90.0f)
        contentAlignment = Alignment.TopStart, // :1213 jj0.d(il0.f, false)，il0.f = TopStart
    ) {
        for (i in 2 downTo 0) { // :1231-1232
            val coverSize = (82 - i * 15).dp // :1233 float f = 82 - (i3 * 15)
            val song = songs.getOrNull(i) // :1234 z81.q0(i3, list2)
            val coverUrl = song?.artwork // :1235 chartSong.getCoverUrl()

            Box(
                modifier = Modifier
                    .offset(x = (i * 25).dp) // :1239 zf8.b0(…, i3 * 25, 0.0f, 2)
                    .size(coverSize) // :1239 ij7.n(…, f)
                    .clip(RoundedCornerShape(12.dp)) // :1208/:1239 hq3.g(…, ay6VarA)
                    .background(placeholder), // :1239 hp1.s(…, jE, hp1.n=RectangleShape)
                contentAlignment = Alignment.Center, // :1241 jj0.d(il0.o, false)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Album, // :1267 wi0.o() = "Rounded.Album"
                    contentDescription = null,
                    modifier = Modifier.size(coverSize * 0.32f), // :1267 f * 0.32f
                    tint = iconTint, // :1267 jTextLow
                )
                if (!coverUrl.isNullOrBlank()) { // :1269 dw7.S(coverUrl) 取反
                    AsyncImage(
                        // :1276 qk5.N(context, coverUrl, 200) —— 请求尺寸 200
                        model = coverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(), // :1276 ij7.e(c05Var2, 1.0f)
                        contentScale = ContentScale.Crop, // :1276 qh1.a（pr6.java:882 的 max 缩放 = Crop）
                    )
                }
                if (i == 0) { // :1281
                    Box(
                        modifier = Modifier.gzBounceClick(
                            enabled = enabled, // :1284 MotionKt.c(oj0Var.b(), z, 0.96f, …)
                            pressedScale = 0.96f, // :1284
                            onClick = onPlayFirst, // :1284 ys2Var
                        ),
                        contentAlignment = Alignment.Center, // :1285 jj0.d(il0.o, z4)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp) // :1299 ij7.n(c05Var2, 30.0f)
                                .background(
                                    color = Color.Black.copy(alpha = 0.28f), // :1299 g91.c(0.28f, g91.b)
                                    shape = CircleShape, // :1299 by6.a = RoundedCornerShape(50%)
                                ),
                            contentAlignment = Alignment.Center, // :1300
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow, // :1317 fd2.q() = "Rounded.PlayArrow"
                                contentDescription = "播放当前榜单", // :1317
                                modifier = Modifier.size(22.dp), // :1317 ij7.n(c05Var2, 22.0f)
                                tint = Color.White, // :1317 g91.d
                            )
                        }
                    }
                }
            }
        }
    }
}
