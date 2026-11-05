package com.aesthetic.music.ui.home

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.DefaultCoverTone
import com.aesthetic.music.ui.components.coverPlaceholder
import com.aesthetic.music.ui.components.glassCard
import com.aesthetic.music.ui.components.glassToneCard
import com.aesthetic.music.ui.components.rememberCoverTone
import com.aesthetic.music.ui.components.textHigh
import com.aesthetic.music.ui.components.textMedium
import kotlin.math.max

/*
 * ============================================================================
 * GZ 首页「时段场景大卡」逐值还原
 * ============================================================================
 * 源（反编译产物，行号均为 HomeScreenKt.java 的行号）：
 *   m42DailyStackHeroegy_3UM   : 3614-4118  大卡主体 = [公告条 + 12dp 间隔 + Hero 卡片] 的竖直堆叠
 *   m41DailyHeroCardjt2gSs     : 2963-3412  Hero 卡片（外层 16dp 内边距 + BoxWithConstraints 定高）
 *     └ DailyHeroCard__jt2gSs$lambda$5        : 3370-3412  卡片本体（fillMaxWidth/height/clip/background）
 *     └ DailyHeroCard__jt2gSs$lambda$5$0$0    : 3415-3445  HorizontalPager + HeroPageIndicator
 *     └ DailyHeroCard__jt2gSs$lambda$5$0$0$0$0: 3448-3473  取 list[page] 交给 DailyHeroPage
 *     └ DailyHeroPage                         : 3475-3587  单页 = 只有封面图（**无任何文字节点、无内嵌面板**）
 *     └ CdPlaceholder                         : 1974-2038  封面为空时的 Canvas 占位（见 TODO ②）
 *   m43HomeCoverPlayButtonS9WYxP8 : 4606-4935+  封面播放按钮
 *   m46HomePreviewSongRowiJQMabo   : 7899-8032  试听歌曲行
 *
 * ⚠ 版式来源声明（本轮重做的核心前提，先读这段）
 *   本文件的「左方形封面 + 右内嵌信息面板 + 歌单名（2 行）+ 曲目数」版式**不存在于 GZ 2.1.8 的
 *   反编译产物中**。逐函数核对结论：
 *     · DailyHeroPage（3475-3587）整函数只有两个分支，都是图：coverUrl 空 → CdPlaceholder(3550)；
 *       否则 rememberCoverModel(3554) + tf2.e(3575)。**没有任何 `c88.b(...)` 文本调用**，
 *       也没有 glassToneCard / liquidGlassCardSurface / discoveryGradientCard 内嵌面板调用。
 *     · jp6（= RecommendPlaylist）的 name(jp6.b)、songCount(jp6.d)、description(jp6.e) 在
 *       m41(2963-3412) / m42(3614-4118) / DailyHeroPage 里**从未被读取**；只用到 id(jp6.a)、
 *       source(jp6.h)（拼共享元素 key，3456）与 coverUrl(jp6.c)（3460/3554）。
 *     · 卡片底色也不是「封面主色渐变」：3388 的底色是 `GlassTokensKt.coverPlaceholder()`，
 *       即 GlassTokensKt.java:1199-1200 的 `textMedium.copy(alpha = 0.12f)` 纯色。
 *   该版式的唯一出处是用户提供的**更新版 GZ 真机截图**：
 *     `.shots/compare/ref_hero_zoom2.png`（4x 放大）、`ref_gz_home.png`（整屏）。
 *   因此：结构照参考图；**配方类**数值仍取 GZ 2.1.8 的同类元素并标行号；
 *   只有「2.1.8 里没有对应元素」的几何量（封面边长/圆角、面板内缩/圆角、极小字号）
 *   才用参考图像素测量值，逐条标 `// TODO(未确定) ⑫`。绝不谎称有源。
 *
 * 已解码符号（本项目既有能力，直接复用）：
 *   hp1.e(long)          = Color(0x........)
 *   g91.c(a, c)          = c.copy(alpha = a)；g91.d = Color.White；g91.b = Color.Black
 *   g91.i                = Color.Unspecified 的占位 int（反编译里只用于内联局部量）
 *   hp1.h0(a, t, b)      = lerp(a, b, t)（hp1.java:3275-3293 → hp1.h0 内部 ig2.z(start, stop, fraction)）
 *   hp1.s(m, color, shape)= Modifier.background(color, shape)
 *   hp1.q0(m, h, v, mask)= Modifier.padding(horizontal = h, vertical = v)（hp1.java:3958-3966 → p0 → ko5）
 *   x8.g(m, w, c, shape) = Modifier.border(w, c, shape)
 *   hq3.g(m, shape)      = Modifier.clip(shape)
 *   o13.a(m, brush, shape, _) = Modifier.background(brush, shape)
 *   mg2.I(m, e, shape, ambient, spot, _) = Modifier.shadow(elevation, shape, ambientColor, spotColor)
 *   ij7.g(m, f)          = Modifier.fillMaxWidth(f)   （ij7.java:70 → FillElement(Direction.Horizontal)）
 *   ij7.e(m, f)          = Modifier.fillMaxSize(f)    （ij7.java:62 → FillElement(Direction.Both)）
 *   ij7.i(m, dp)         = Modifier.height(dp)        （hj7 的 (minWidth,minHeight,maxWidth,maxHeight,enforceIncoming)）
 *   ij7.r(m, dp)         = Modifier.width(dp)
 *   ij7.j(m, min, max, mask) = Modifier.heightIn(minHeight = min, maxHeight = max)
 *   ij7.n(m, dp)         = Modifier.size(dp)
 *   ij7.o(m, w, h)       = Modifier.size(width = w, height = h)（ij7.java:102-104 → hj7(w,h,w,h,true)）
 *   hp1.s0(m, s, t, e, b, mask) = Modifier.padding(start, top, end, bottom)（hp1.java:3980-3993 → r0 → ko5(start,top,end,bottom)，ko5.java:11）
 *   oj0.a.a(m, align)    = BoxScope 内的 Modifier.align(align)（oj0.java:9-11；oj0 实现 nj0 = BoxScope）
 *   il0.Y                = Alignment(0.0f, 1.0f) = Alignment.BottomCenter（il0.java:58）
 *   ij1.a(a, b, t, c)    = (a - b) * t + c（ij1.java:44-46，即 lerp 的展开）
 *   yn.b(f, spec, label, …) = animateFloatAsState（yn.java:28，返回 ys7 = State<Float>）
 *   qk5.r0(dur, mask, easing) = tween(durationMillis, delayMillis, easing)（qk5.java:1654-1662 → tf8.java:10）
 *   bs4.a                = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)（bs4.java:6 → wm1.java:15-21）
 *   ku4.f(i, s)          = s + i（ku4.java:28-30，即 "heroDot" + index）
 *   er5.c(m, onDraw)     = Canvas(m, onDraw)（er5.java:596-617 → s(...)=drawBehind（er5.java:1832-1834）→ wl7.d = Spacer）
 *   g72                  = DrawScope；g72.a() 打包 Size（低 32 位 = 高，高 32 位 = 宽）、E0(dp) = toPx、C0 = drawRoundRect
 *   jv4.x(m, block)      = Modifier.graphicsLayer(block)（jv4.java:745 → nh0 → oh0 = BlockGraphicsLayerModifier）
 *   wi0.f(m, dp)         = Modifier.graphicsLayer { blurEffect = BlurEffect(dp.toPx(), dp.toPx(), Clamp); shape = RectangleShape; clip = true }
 *                          （wi0.java:1306-1311 + bi0.java:21-34）
 *   zf8.b0(m, x, y, mask)= Modifier.offset(x, y)（zf8.java:2314 → a0 → ki5；ki5 = OffsetModifierElement rtlAware=true）
 *   zf8.a0(m, x, y)      = Modifier.offset(x, y)
 *   by6.a                = CircleShape（by6.java:6-11，RoundedCornerShape(50%)）
 *   by6.a(dp)            = RoundedCornerShape(dp)（by6.java:13-16 + s42.java:29 "CornerSize(size = X.dp)"）
 *   f30.n(a, b)          = a + b（字符串拼接）
 *   co7.j(int)           = int.sp（co7.java:1101-1103，TextUnit 类型位 = 2）
 *   sq2.j                = FontWeight(500) = FontWeight.Medium（sq2.java:23,31）
 *   pr6.M(list, _, _, _) = Brush.verticalGradient(colors)（掩码 14 → startY/endY/tileMode 全默认）
 *   pr6.O(h, s, v)       = Color.hsv(h, s, v)（pr6.java:221-228）
 *   pr6.M(list,..)       = Brush.verticalGradient(colors)；pr6.S(stops,..) = Brush.linearGradient(colorStops)
 *   sq2.p                = FontWeight(700) = Bold（sq2.java:33；j = 500、X = 800）
 *   co7.j(int)           = int.sp（co7.java:1101-1103）
 *   GlassTokensKt.coverPlaceholder() = textMedium.copy(alpha = 0.12f)（GlassTokensKt.java:1199-1200）
 *   Modifier.glassToneCard(shape, tone, selected, gradient)（GlassTokensKt.java:1296-1349）
 *                        = 浅色下 border(1.dp, glassBorder) + background(linearGradient(
 *                          tone@20% over glassBg → tone@8% → tone@0%)) + clip
 *   Modifier.glassCard(shape, elevation)（GlassTokensKt.java:1247-1266）
 *                        = border(1.dp, glassBorder) + background(glassBg) + clip（深色才叠 elevation 阴影）
 *   CoverToneKt.rememberCoverTone(url)（CoverToneKt.java:68-106）= 逐封面提取主色（500ms 动画过渡）
 *   oy2/yd1 = Composer；末尾 int 为 Compose 变更位掩码，不是业务参数
 * ============================================================================
 */

/**
 * GZ 首页「时段场景大卡」：堆叠式 Daily Hero + 试听行。
 *
 * GZ 的原始结构（`m42DailyStackHeroegy_3UM`，HomeScreenKt.java:3723-3771）是竖直 `Column`：
 * ```
 * Column(Modifier.fillMaxWidth()) {                       // 3726
 *     if (list2.isNotEmpty()) {                           // list2 = 公告列表
 *         HomeNoticeStrip(list2, ..., Modifier.padding(horizontal = 16.dp))   // 3753
 *         if (hero.isEmpty() || loading) Spacer(Modifier.height(12.dp))       // 3963 (ij1.w)
 *     }
 *     DailyHeroCard(hero.take(3), onClick, loading, 216.dp * uiScale)         // 3765
 * }
 * ```
 * 本文件按接口约定只还原其中的 **Hero 卡片** 与 **试听行**（`m46HomePreviewSongRowiJQMabo`，
 * GZ 里属于另一个区块，详见 [HomePreviewSongRow] 注释）；公告条不在本次范围内。
 *
 * GZ 取值来源（对照最终签名）：
 *  - [pages]         ← `List<jp6>` = `RecommendPlaylist` 列表（id/name/coverUrl/description/tag/songCount/
 *                       hasCustomCover/songs…，jp6.java:62-80 的 toString）；首页调用点
 *                      `m41DailyHeroCardjt2gSs(z81.L0(list4, 3), …)`（HomeScreenKt.java:3765）
 *                      已 `take(3)` → 最多 3 页（`z81.L0` = `List.take`）。
 *  - [tone]          ← 卡底/试听行的封面主色。GZ 由 `CoverToneKt.rememberCoverTone(url)` 逐封面提取
 *                      （CoverToneKt.java:68-106）；本项目已有该提取器的逐值还原
 *                      （CoverTone.kt:692）→ 本轮卡底就用它（fallback 才是本参数，默认 [DefaultCoverTone]）。
 *  - [isLoadingSongs]← `z6 = isRefreshing && heroList.isEmpty()`（HomeScreenKt.java:8763）→ m41 的 `z`
 *                      （决定卡片是「定高占位」还是「16:9 实际封面」，3378-3387；同时是「卡片点击禁用」的
 *                      局部量之一，3406）。
 *  - [hasCustomCover]← `jp6.i` = `hasCustomCover`（HomeScreenKt.java:3502 `z2 = jp6Var.i`）。
 *                      **注意**：GZ 是逐页读当前页的 `jp6Var.i`；Melos 的 [PlaylistItem] 没有该字段，
 *                      故仍沿用调用方给的整卡开关（见 TODO ⑨）。
 *
 * @param title   时段场景标题。仅用作封面/卡片的无障碍描述——GZ 的这张卡（DailyHeroPage，3475-3587）
 *                **没有任何 Text 节点**，卡上的标题在更新版 GZ 里是**歌单名**（[PlaylistItem.name]，
 *                对应 GZ 的 jp6.b），由 [pages] 逐页提供，故本组件不拿 [title] 当卡片标题。
 * @param subtitle 同上（GZ 2.1.8 无对应节点）；卡片面板里的「元信息」小字取的是
 *                [PlaylistItem.description]（绑定关系见 TODO ⑫）。
 * @param pages   轮播页（每页一个歌单，最多 3 个 —— 调用方按 GZ 的 `take(3)` 截断）。
 *                空列表时卡片进入「占位底色 + 无图」状态（对应 GZ 封面为空的分支），页数 0。
 * @param previewSongs 歌单内真实曲目（**本卡不再渲染**，试听行已按 HomeScreenKt.java:11261 归位到
 *                平板排行榜区块；参数保留是为了不动调用方签名）。
 * @param tone    封面主色调兜底值（[rememberCoverTone] 的 fallback）；实际卡底颜色由**当前页封面**提取
 *                （GZ CoverToneKt.java:68-106），提取未命中时才用该值。
 * @param isLoadingSongs 载入中（且歌单为空）→ 卡片用 `max(150.dp, heroHeight)` 定高，否则 16:9。
 * @param isPlaying GZ 的 m41/m42/m43/m46 中不存在「正在播放」状态：播放按钮恒为 PlayArrow，
 *                 无暂停图标、无音波指示。该参数在本组件内**无 GZ 依据**，未参与渲染（见 TODO ⑥）。
 * @param hasCustomCover GZ `jp6.i`：false（自动生成封面）时封面会放大 1.16× 并模糊 26.dp。
 * @param heroHeight 载入态卡片高度，GZ = `216.0f * uiScale`（HomeScreenKt.java:8764；
 *                 `uiScale` 在屏宽 ≥ 840dp 时为 1.2f，否则 1.0f —— HomeScreenKt.java:8405-8409）。
 * @param onPlaySong 试听行播放（GZ 行的 `ys2Var`；为 null 时 GZ 连按钮都不画，HomeScreenKt.java:8015）。
 * @param onOpenPlaylist 卡片点击（GZ `DailyHeroPage` 的 `ys2Var` 点击回调，HomeScreenKt.java:3505）；
 *                轮播下打开的是**当前页**的歌单（GZ 是逐页 `remember(jp6Var)` 绑定，3465-3470）。
 */
@Composable
fun HomeDailyHero(
    title: String,
    subtitle: String,
    pages: List<PlaylistItem>,
    previewSongs: List<Song>,
    tone: Color = DefaultCoverTone,
    isLoadingSongs: Boolean = false,
    isPlaying: Boolean = false,
    hasCustomCover: Boolean = !pages.firstOrNull()?.coverUrl.isNullOrBlank(),
    heroHeight: Dp = 216.dp,
    onOpenPlaylist: (PlaylistItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    // HomeScreenKt.java:8763 —— `z6 = (刷新中) && (hero 列表为空)`；m41 用它决定卡片高度分支（3378）。
    val heroLoading = isLoadingSongs && pages.isEmpty()

    // HomeScreenKt.java:3032 —— `xp5.b(0, { size }, …)` = rememberPagerState(initialPage = 0, pageCount = { … })
    //   （xp5.java:33-53：默认 initialPage = 0、initialPageOffsetFraction = 0f，pageCount 回调由 xb3 包住）
    //   页数 = 调用方列表长度；GZ 调用点已 take(3)（HomeScreenKt.java:3765）。
    val pagerState = rememberPagerState(
        initialPage = 0, // HomeScreenKt.java:3032 —— 第 1 实参 0
        initialPageOffsetFraction = 0f, // HomeScreenKt.java:3032 —— xp5.java:40 的 `oy2Var.d(0.0f)`
    ) { pages.size } // HomeScreenKt.java:3024/3032 —— `size = list.size()`

    // 当前页（GZ：`vp5Var.d.b.h()`，HomeScreenKt.java:3439）。未滚动时 = 当前停留页。
    val currentPlaylist = pages.getOrNull(pagerState.currentPage)

    // 卡底 = 封面主色渐变：随**当前页封面**变化（参考图 ref_hero_zoom2.png 绿 / ref_hero_card.png 蓝）。
    // GZ 2.1.8 的这张卡没有这个元素（3388 是 coverPlaceholder 纯色），但同类配方在 2.1.8 里存在：
    //   CoverToneKt.m299rememberCoverToneiJQMabo(url, ...)（CoverToneKt.java:68-106）
    //     → 逐封面提取主色；TabletArtistDetailScreenKt.java:2733 就是这么给 Hero 卡取色的。
    //   GlassTokensKt.m327glassToneCardT042LqI(shape, tone, …)（GlassTokensKt.java:1296-1349）
    //     → 浅色下 linearGradient(tone@20% over glassBg → tone@8% @0.55 → tone@0% @1) + 1dp 玻璃描边。
    // 本项目已有逐值还原：rememberCoverTone（CoverTone.kt:692）/ glassToneCard（GlassTokens.kt:198）。
    val cardTone = rememberCoverTone(currentPlaylist?.coverUrl, tone)

    Column(modifier = modifier.fillMaxWidth()) { // HomeScreenKt.java:3726 —— ij7.g(c05.a, 1.0f)

        // ---------------- 大卡（Daily Hero） ----------------
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth() // HomeScreenKt.java:3067 —— ij7.g(c05.a, 1.0f)
                // HomeScreenKt.java:3067 —— hp1.q0(ij7.g(c05.a, 1.0f), 16.0f, 0.0f, 掩码 2)
                //   = Modifier.padding(horizontal = 16.dp)（vertical 走掩码默认 0.dp）
                .padding(horizontal = 16.dp)
        ) {
            // HomeScreenKt.java:3378-3387 —— fC 即卡片高度（qj0 = BoxWithConstraintsScope，c() = maxWidth）
            val cardHeight = if (heroLoading) {
                // HomeScreenKt.java:3379-3384 —— maxOf(Dp(150.0f), f2)
                maxOf(150.dp, heroHeight)
            } else {
                // HomeScreenKt.java:3386 —— (scope.maxWidth * 9.0f) / 16.0f
                maxWidth * 9f / 16f
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth() // HomeScreenKt.java:3388 —— ij7.g(c05.a, 1.0f)
                    .height(cardHeight) // HomeScreenKt.java:3388 —— ij7.i(mod, fC)
                    // HomeScreenKt.java:3066 —— by6.a(20.0f) = RoundedCornerShape(20.dp)
                    // HomeScreenKt.java:3388 —— hq3.g(mod, ay6Var) = Modifier.clip(shape)
                    //
                    // 卡底（本轮重做）：**封面主色渐变**而不是 2.1.8 的纯色占位底。
                    //   GZ 2.1.8 此处是 hp1.s(..., GlassTokensKt.coverPlaceholder(), shape)（3388）
                    //   = textMedium@12% 纯色（GlassTokensKt.java:1199-1200）——与参考图不符，
                    //   参考图里卡底是「随封面变的绿/蓝渐变」（见版式来源声明）。
                    //   配方取自 2.1.8 的同类元素 glassToneCard（GlassTokensKt.java:1296-1349）：
                    //   浅色 linearGradient(tone@20% over glassBg → tone@8% → tone@0%) + 1dp glassBorder
                    //   + clip；深色 tone@45% → glassBg + 10dp 阴影。shape 仍 = 20dp（3066）。
                    .glassToneCard(
                        shape = RoundedCornerShape(HeroCardCorner),
                        tone = cardTone,
                        selected = false,
                        gradient = true,
                    )
                    // HomeScreenKt.java:3505 —— MotionKt.c(f05VarF, false, 0.985f, !local, null, ys2Var2, 9)
                    //   = 卡片点击 + 按下缩放 0.985f（Melos 无 bounceClick → 用 clickable，见 TODO ④）
                    //   第 4 实参 = enabled = `!local`，local = CompositionLocal `HomeScreenKt.a`
                    //   （HomeScreenKt.java:3406 提供 `z2 || ys7Var`，ys7Var = derivedStateOf { pagerState 滚动中 }
                    //     —— 3406/3062 + l33.java:80-82 `((js1) obj).k.a()`，vp5.k = scrollableState，ts1.a() = isScrollInProgress）
                    .clickable(
                        enabled = currentPlaylist != null &&
                            !heroLoading &&
                            !pagerState.isScrollInProgress,
                    ) { currentPlaylist?.let(onOpenPlaylist) }
            ) {
                // HomeScreenKt.java:3420 —— `hq3.g(ij7.e(c05.a, 1.0f), ay6Var)`：内层 Box(fillMaxSize + clip(shape))，
                //   pager 与页码指示器都是它的子节点（3421 的 `jj0.d(il0.f, false)` = Box(contentAlignment=TopStart)）。
                Box(
                    modifier = Modifier
                        .fillMaxSize() // HomeScreenKt.java:3420 —— ij7.e(c05.a, 1.0f)
                        .clip(RoundedCornerShape(HeroCardCorner)), // HomeScreenKt.java:3420 —— hq3.g(mod, ay6Var)
                ) {
                    // HomeScreenKt.java:3438 —— `mf2.a(vp5Var, ij7.e(c05.a, 1.0f), null, null, 1, 0.0f,
                    //   null, null, i2 > 1, null, null, null, null, <pageContent>)`
                    //   = HorizontalPager(state, modifier, contentPadding=默认, pageSize=默认,
                    //     beyondViewportPageCount = 1, pageSpacing = 0.dp, verticalAlignment=默认,
                    //     flingBehavior=默认, userScrollEnabled = (页数 > 1), key=null, 其余默认)
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(), // HomeScreenKt.java:3438 —— 第 2 实参
                        beyondViewportPageCount = 1, // HomeScreenKt.java:3438 —— 第 5 实参（$default 掩码 16108 的 bit4 未置位）
                        pageSpacing = 0.dp, // HomeScreenKt.java:3438 —— 第 6 实参 0.0f
                        userScrollEnabled = pagerState.pageCount > 1, // HomeScreenKt.java:3438 —— 第 9 实参 `i2 > 1`
                    ) { page ->
                        // HomeScreenKt.java:3450 —— `z81.q0(i2, list)` = list.getOrNull(page)
                        val item = pages.getOrNull(page)
                        // HomeScreenKt.java:3471 —— DailyHeroPage(item, coverUrl, sharedKey, shape, onClick)
                        //   GZ 2.1.8 的 DailyHeroPage 只画封面；本轮按参考图改成「左方形封面 + 右内嵌信息面板」，
                        //   版式数值出处见 [HomeHeroPage] 上方的测量表与 TODO ⑫。
                        HomeHeroPage(
                            item = item,
                            hasCustomCover = hasCustomCover,
                            cardHeight = cardHeight, // 参考图版式按卡高取比例（见测量表）
                            contentDescription = title.ifBlank { null },
                        )
                    }
                    // HomeScreenKt.java:3439 —— `HeroPageIndicator(vp5Var.d.b.h(), i2, hp1.s0(…), …)`
                    //   第 1 实参 = pagerState.currentPage；第 2 = 页数；
                    //   第 3 = `hp1.s0(oj0.a.a(c05.a, il0.Y), 0f, 0f, 0f, b, 7)`
                    //        = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                    //        （oj0.a.a = BoxScope.align，oj0.java:9-11；il0.Y = xg0(0f, 1f) = BottomCenter，il0.java:58；
                    //          hp1.s0 掩码 7 → 只有第 4 个 padding 值 b = 12.0f 生效，hp1.java:3980-3993/3972-3974 + ko5.java:11）
                    HomeHeroIndicator(
                        currentPage = pagerState.currentPage, // HomeScreenKt.java:3439 —— 第 1 实参
                        pageCount = pagerState.pageCount, // HomeScreenKt.java:3439 —— 第 2 实参
                        modifier = Modifier
                            .align(Alignment.BottomCenter) // HomeScreenKt.java:3439 —— il0.Y
                            .padding(bottom = HeroIndicatorBottomPadding), // HomeScreenKt.java:3439 —— 12.0f（静态量 b）
                    )
                }
            }
        }

        // ------------------------------------------------------------------------------------
        // [已移除] 原先挂在这里的「试听歌曲行」是接错了位置。
        //
        // 依据（两条独立证据）：
        //  1. `m46HomePreviewSongRowiJQMabo` 的真实调用点是 HomeScreenKt.java:11261 —— 那是
        //     10798-11414 的**平板排行榜卡片区**（m49PhoneChartCard / m50 骨架屏一带），
        //     不属于 DailyStackHero（m42 在 3726-3771，DailyHeroCard 在 2963-3412）。
        //  2. GZ 真机截图（.shots/compare/ref_gz_home.png）里，轮播大卡**下方直接就是三大入口卡**
        //     （猜你喜欢 / 排行榜 / 随心听），没有任何歌曲行。
        //
        // 因此 Hero 卡不再渲染 previewSongs。参数保留是为了不动调用方签名；
        // 试听行组件 `HomePreviewSongRow` 仍留在本文件里，等移植平板排行榜卡片时复用。
        // ------------------------------------------------------------------------------------
    }
}

/**
 * Hero 卡片内的封面（对应 `DailyHeroPage`，HomeScreenKt.java:3475-3587 的图片分支）。
 *
 * GZ 结构：
 * ```
 * if (coverUrl.isBlank()) CdPlaceholder(Modifier.fillMaxSize())            // 3547-3551
 * else AsyncImage(
 *     model = rememberCoverModel(coverUrl, if (hasCustomCover) 720 else 480), // 3554
 *     modifier = Modifier
 *         .fillMaxSize()                                                    // 3533
 *         .graphicsLayer { scaleX = scaleY = if (hasCustomCover) 1.0f else 1.16f } // 3555-3563 + p70(8)
 *         .graphicsLayer { blur(26dp); shape = RectangleShape; clip = true } // 3564-3565 + wi0.f/bi0
 * )
 * ```
 * 注意：GZ 2.1.8 的这张卡是**满屏封面**，所以封面四角 = 卡片的 20.dp（3388/3420 两次 clip）。
 * 本轮按参考图版式重做后，本函数只负责**画图**，被放进 [HomeHeroPage] 的方形封面容器里
 * （容器自带 `clip(RoundedCornerShape(HeroCoverCorner))`，参考图测得 ≈6.dp，见 TODO ⑫）；
 * 「1.16× 放大 + 26.dp 模糊」仍是 GZ 3555-3565 的真值，但它是为满屏封面设计的，
 * 更新版 GZ 的方形封面是否还这么做**无法验证** → 见 TODO ⑬。
 */
@Composable
private fun HomeHeroCover(
    url: String?,
    hasCustomCover: Boolean,
    contentDescription: String?,
) {
    if (url.isNullOrBlank()) {
        // HomeScreenKt.java:3547-3551 —— GZ 会再画一个 CdPlaceholder（1974-2038 的 Canvas 占位）。
        // 该占位的图形参数（渐变圆/3 根条的坐标与颜色）在反编译里是一串 DrawScope 调用，
        // 参数顺序无法 100% 确定 → 见 TODO ②；此处保留卡片底色 coverPlaceholder()（3388 的真值）。
        return
    }
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            // HomeScreenKt.java:3554 —— GZ 的 rememberCoverModel(str, z2 ? 720 : 480) 是**请求尺寸提示**，
            // 不是硬性解码约束。Coil 2 的 `.size(n)` 会强制按 n×n 方形解码，
            // 与本项目 SongCover（能正常加载封面）的写法不一致，故这里不传 size，改用与 SongCover 相同的
            // 「原始尺寸 + crossfade」策略；外观由 ContentScale.Crop + fillMaxSize 决定。
            // Melos 侧加载约定（Components.kt:81 SongCover 用 crossfade(200)），非 GZ 数值。
            .crossfade(200)
            .build(),
        contentDescription = contentDescription,
        // TODO(未确定) ①：GZ 的 tf2.e 未显式传 contentScale（tf2.java:503-504 掩码默认 → null，
        // 最终由 m50.m0 决定）；Melos 侧取 Cover 填充 16:9 卡片。
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize() // HomeScreenKt.java:3533 —— ij7.e(c05.a, 1.0f)
            .graphicsLayer {
                // HomeScreenKt.java:3555-3562 —— jv4.x(mod, p70(hasCustomCover, 8))
                // p70.java case 8（p70.java:135-142）：scaleX = scaleY = 有自定义封面 ? 1.0f : 1.16f
                val scale = if (hasCustomCover) 1.0f else 1.16f
                scaleX = scale
                scaleY = scale
            }
            .graphicsLayer {
                // HomeScreenKt.java:3564-3565 —— if (!hasCustomCover) wi0.f(mod, 26.0f)
                // bi0.java:21-34：radiusX = radiusY = density * 26.0f（= 26.dp 转 px）、
                //   shape = hp1.n（RectangleShape）、clip = true、edgeTreatment = ju4.I(0)
                if (!hasCustomCover) {
                    // GraphicsLayerScope 上的模糊属性名是 renderEffect（不是 blurEffect）
                    renderEffect = BlurEffect(26.dp.toPx(), 26.dp.toPx(), TileMode.Clamp)
                    shape = RectangleShape
                    clip = true
                }
            }
    )
}

/*
 * ===========================================================================
 * 参考图版式（更新版 GZ 真机；GZ 2.1.8 反编译产物里**没有**这个版式）
 * ===========================================================================
 * 出处：`.shots/compare/ref_hero_zoom2.png`（4x 放大）、`ref_gz_home.png`（整屏 355x800）。
 *
 * 换算前提：355x800 = 1080x2424 真机的等比缩略图；用 GZ 已确认的指示器几何反推比例尺
 * （底距 12.dp = HomeScreenKt.java:302/3439；圆点 6/14.dp、间隙 4.dp = :303-305）：
 * 底距测得 11.5px → 0.96 px/dp；选中圆点测得 12px（阈值会吃掉 1px 抗锯齿边）→ 0.86~1.0 px/dp。
 * 即 **1 参考图像素 ≈ 0.9~1.0 dp**；本文件统一按 **1px ≈ 1dp** 取整（误差 ≤10%，见 TODO ⑫）。
 *
 * 逐项测量（单位 = 参考图像素；卡左上角 (21,171)、卡 320x179 = 宽 x 9/16 ✓ 与 3386 的定高规则一致）：
 *   封面       x 45..150 / y 209..314  → 106x106 正方形（= 卡高 0.59）；上左圆角 ≈ 6
 *   信息面板   x 165.5..318.5 / y 208..320 → 153x113（= 卡高 0.63）；上左圆角 ≈ 6
 *   主行       start 24（封面左边距）/ end 21（面板右边距）/ 封面↔面板间距 15 / 顶距 37（0.21 卡高）
 *   面板内     padding ≈ 12；三行文字：元信息 y 220..223（很小很淡）、标题 y 248..285（两行、行距 21
 *              ≈ 20sp 默认行高 24 → 1px≈0.9dp 下自洽）、曲目数 y 309..312
 *   卡片另外还有 4 处极小字（本轮**不渲染**，文案来源未知，见 TODO ⑫）：
 *              顶部左（图标 + 极小字，y 183..189 / x 39..71）、顶部右（y 186..189 / x 283..322）、
 *              左下（y 324..325 / x 56..72）、右下（y 336..345 / x 305..325）
 * ===========================================================================
 */

private val HeroCardCorner = 20.dp // HomeScreenKt.java:3066 —— by6.a(20.0f)（参考图测得 ≈14，见 TODO ⑫）
private val HeroCoverCorner = 6.dp // TODO(未确定) ⑫：参考图像素测量（封面上左圆角 ≈6px）
private val HeroPanelCorner = 6.dp // TODO(未确定) ⑫：参考图像素测量（面板上左圆角 ≈6px）
private val HeroRowStartPadding = 24.dp // TODO(未确定) ⑫：参考图像素测量（封面左边距 24px）
private val HeroRowEndPadding = 21.dp // TODO(未确定) ⑫：参考图像素测量（面板右边距 21px）
private val HeroCoverPanelGap = 15.dp // TODO(未确定) ⑫：参考图像素测量（封面↔面板间距 15px）
private val HeroPanelPadding = 12.dp // TODO(未确定) ⑫：参考图像素测量（面板内边距 12px）
private const val HeroCoverHeightRatio = 0.59f // TODO(未确定) ⑫：106 / 179（封面边长 / 卡高）
private const val HeroPanelHeightRatio = 0.63f // TODO(未确定) ⑫：113 / 179（面板高 / 卡高）
private const val HeroRowTopRatio = 0.21f // TODO(未确定) ⑫：37 / 179（主行顶距 / 卡高）
private val HeroTitleFontSize = 20.sp // 参考图测得（≈20sp）；GZ 的 20sp 歌单名写法见 HomeScreenKt.java:2297/2520/10977
private val HeroTinyFontSize = 9.sp // HomeScreenKt.java:9796 —— co7.j(9)（GZ 的「N 首」小字档）
private val HeroTinyLineHeight = 12.sp // HomeScreenKt.java:9796 —— co7.j(12)

/**
 * 单页内容 —— 参考图版式：**左方形封面 + 右内嵌信息面板**（对应 `DailyHeroPage`，HomeScreenKt.java:3475-3587）。
 *
 * 结构（自上而下/自左向右，括号内是数值出处）：
 * ```
 * Row(fillMaxSize, padding(start = 24.dp, end = 21.dp, top = 卡高 * 0.21), Top) {   // TODO ⑫
 *     Box(size = 卡高 * 0.59, clip(RoundedCornerShape(6.dp))) {                    // TODO ⑫
 *         HomeHeroCover(...)                                                       // 3532/3547-3577 的图片分支
 *     }
 *     Spacer(15.dp)                                                                // TODO ⑫
 *     HomeHeroInfoPanel(Modifier.weight(1f).height(卡高 * 0.63))                    // TODO ⑫
 * }
 * ```
 * 注意：GZ 2.1.8 的 DailyHeroPage 是「满屏封面」（`Modifier.fillMaxSize()` + 3532 的 clip），
 * 这里的方形封面容器是参考图版式新增的一层；封面图本身的加载/占位/模糊处理仍走 [HomeHeroCover]。
 *
 * @param cardHeight 卡片高度（`max(150.dp, heroHeight)` 或 `maxWidth * 9/16`，HomeScreenKt.java:3379-3386）；
 *                   参考图版式的所有纵向尺寸都按它取比例，以便在任意屏宽下保持同一版式比例。
 */
@Composable
private fun HomeHeroPage(
    item: PlaylistItem?,
    hasCustomCover: Boolean,
    cardHeight: Dp,
    contentDescription: String?,
) {
    val coverSize = cardHeight * HeroCoverHeightRatio // TODO ⑫：106/179
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = HeroRowStartPadding, end = HeroRowEndPadding)
            .padding(top = cardHeight * HeroRowTopRatio), // TODO ⑫：主行顶距 37/179
        // 参考图里封面上沿 y=209、面板上沿 y=208 → 两者顶部对齐（差 1px 是抗锯齿）
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(coverSize)
                .clip(RoundedCornerShape(HeroCoverCorner)) // TODO ⑫：圆角 ≈6px（参考图）
                .background(coverPlaceholder()), // HomeScreenKt.java:3388 —— 占位底色仍是 GZ 真值
        ) {
            HomeHeroCover(
                url = item?.coverUrl,
                hasCustomCover = hasCustomCover,
                contentDescription = contentDescription,
            )
        }
        Spacer(modifier = Modifier.width(HeroCoverPanelGap)) // TODO ⑫：15px
        HomeHeroInfoPanel(
            item = item,
            modifier = Modifier
                .weight(1f)
                .height(cardHeight * HeroPanelHeightRatio), // TODO ⑫：113/179
        )
    }
}

/**
 * 内嵌信息面板（参考图版式；GZ 2.1.8 无此节点）。
 *
 * 面板 = 比卡底更浅的一层，圆角 ≈6px（TODO ⑫）。用 GZ 2.1.8 的玻璃卡配方落地：
 *   `Modifier.glassCard(shape, elevation = 0.dp)`（GlassTokensKt.java:1247-1266）
 *   = border(1.dp, glassBorder()) + background(glassBg()) + clip(shape)；
 *   浅色下 glassBg = White@55%（GlassTokens.kt:83 ← GlassTokensKt.java:1239-1241），
 *   压在卡底渐变上得到的颜色与参考图实测的面板色（≈ #F0FCE5）一致。
 *
 * 三行文字（参考图 y 220/248-285/309，纵向用 `Arrangement.SpaceBetween` 摊开，
 * 实测两段留白 25px / 24px 基本相等，与 SpaceBetween 的等距特征相符）：
 *  ① 元信息（很小很淡）：**绑定关系是推断的** —— 见 TODO ⑫；无值时留空行。
 *  ② 标题 = 歌单名（GZ `jp6.b`，字段定义见 jp6.java:63/89；GZ 里渲染它的地方见
 *     TabletHomeScreenKt.java:623），20sp / 粗体 / 最多 2 行。
 *  ③ 曲目数 = `trackCount` + " 首"（GZ `jp6.d`；" 首" 后缀字面量见 rt0.p(count, " 首")，
 *     例：LocalAlbumDetailScreenKt.java:716 / LibrarySongsViewKt.java:2060）。
 */
@Composable
private fun HomeHeroInfoPanel(
    item: PlaylistItem?,
    modifier: Modifier = Modifier,
) {
    // 颜色 token 在 @Composable 上下文里读一次（GZ 的 c88.b 是逐调用读 GlassTokensKt.textXxx）
    val titleColor = textHigh() // GlassTokensKt.textHigh（文本色 1 级）
    val tinyColor = textMedium() // GlassTokensKt.textMedium（文本色 2 级，GZ 的 "N 首" 用的就是它）

    // 元信息：TODO(未确定) ⑫ —— GZ 2.1.8 的这张卡没有这个文本节点，参考图上的字太小无法辨认，
    // 这里用歌单自身的 description 顶上（依据：GZ 平板 Hero 的副行就是 description，
    // TabletHomeScreenKt.java:625-629 / TabletArtistDetailScreenKt.java:2889）；
    // 若更新版 GZ 实际显示的是别的字段（tag / playCount / 场景名…），改绑定即可，不要改字号。
    val metaText = item?.description?.takeIf { it.isNotBlank() }.orEmpty()
    // 曲目数：GZ jp6.d（songCount）+ " 首"（rt0.p(count, " 首") 的字面量后缀）
    val trackCountText = item?.trackCount?.let { "$it 首" }.orEmpty()

    Column(
        modifier = modifier
            .glassCard(shape = RoundedCornerShape(HeroPanelCorner), elevation = 0.dp) // GlassTokensKt.java:1247-1266
            .padding(horizontal = HeroPanelPadding, vertical = HeroPanelPadding), // TODO ⑫：12px
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = metaText,
            color = tinyColor,
            fontSize = HeroTinyFontSize, // HomeScreenKt.java:9796 —— co7.j(9)
            lineHeight = HeroTinyLineHeight, // HomeScreenKt.java:9796 —— co7.j(12)
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item?.name.orEmpty(),
            color = titleColor,
            fontSize = HeroTitleFontSize, // 参考图测得 ≈20sp；字面量 20sp 见 HomeScreenKt.java:2297
            // GZ 里同一个字段 jp6.b（歌单名）用的是 sq2.p = FontWeight(700)（TabletHomeScreenKt.java:623）
            fontWeight = FontWeight.Bold,
            // 行高：GZ 的 18sp 歌单名档传 0L（未指定）= 用字体默认（TabletHomeScreenKt.java:623）；
            // 参考图实测两行行距 21px ≈ 1px/0.9dp 下的 23.3dp ≈ 20sp 的默认行高（24dp）→ 同样不指定。
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            // 字距：GZ 的 20sp 档有的传 -0.4/-0.5（HomeScreenKt.java:2297/10977），
            // 18sp 档传 0L（TabletHomeScreenKt.java:623）；参考图无法分辨 → 取 0（TODO ⑫）。
        )
        Text(
            text = trackCountText,
            color = tinyColor,
            fontSize = HeroTinyFontSize, // HomeScreenKt.java:9796 —— co7.j(9)
            lineHeight = HeroTinyLineHeight, // HomeScreenKt.java:9796 —— co7.j(12)
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/*
 * ---------------------------------------------------------------------------
 * Hero 页码指示器静态量
 * 全部来自 HomeScreenKt 的静态字段（HomeScreenKt.java:288-305 的 static 块）：
 *   b = 12.0f（:302）  c = 6.0f（:303）  d = 14.0f（:304）  e = 4.0f（:305）
 * 用途与取值见 [HomeHeroIndicator]；这三个量在反编译里只被 HeroPageIndicator 与它的
 * DrawScope lambda（uw1.java:49-72）读到，故可确定为指示器专用。
 * ---------------------------------------------------------------------------
 */
private val HeroIndicatorBottomPadding = 12.dp // HomeScreenKt.java:302 —— b = 12.0f（3439 的 padding 第 4 值）
private val HeroDotHeight = 6.dp // HomeScreenKt.java:303 —— c = 6.0f（4412 的高度 / Canvas 高）
private val HeroDotSelectedWidth = 14.dp // HomeScreenKt.java:304 —— d = 14.0f（4412 的每点宽 / 选中宽）
private val HeroDotSpacing = 4.dp // HomeScreenKt.java:305 —— e = 4.0f（4412 的间隙）

/**
 * 页码指示器（`HeroPageIndicator`，HomeScreenKt.java:4320-4555）。
 *
 * 结构逐值还原（`pageCount > 1` 分支，4396-4518）：
 * ```
 * if (pageCount <= 1) return                                     // 4367-4371：单页/0 页直接不渲染
 * val dots = List(pageCount) { index ->                          // 4396-4408：ArrayList + while（inline List{}）
 *     animateFloatAsState(                                       // 4405 `yn.b(target, spec, label, …)` → ys7 = State<Float>
 *         targetValue   = if (index == currentPage) 1f else 0f,  // 4399-4403
 *         animationSpec = tween(200, 0, CubicBezierEasing(0.2f, 0f, 0f, 1f)), // 4405 `qk5.r0(200, 2, bs4.a)`
 *         label         = "heroDot$index",                        // 4405 `ku4.f(i10, "heroDot")` = "heroDot" + i
 *     )
 * }
 * Canvas(Modifier.size(width = 4.dp * (pageCount - 1) + 14.dp * pageCount, height = 6.dp)) {  // 4412 `ij7.o`
 *     // uw1.java:49-72（i3 = pageCount，arrayList = dots）
 *     for (index in pageCount - 1 downTo 0) {
 *         val t = dots[index].value                               // uw1.java:61 —— 0f（未选中）→ 1f（选中）
 *         val w = 6.dp.toPx() + (14.dp.toPx() - 6.dp.toPx()) * t  // uw1.java:62 `ij1.a(d, c, t, c)` = (a-b)*t+b
 *         drawRoundRect(
 *             color        = Color.White.copy(alpha = 0.42f + 0.53f * t), // uw1.java:65 `g91.c(t*0.53f+0.42f, g91.d)`
 *             topLeft      = Offset(right - w, 0f),               // uw1.java:65 第 3 实参（x 高 32 位 = 宽 - w）
 *             size         = Size(w, canvasHeight),               // uw1.java:65 第 4 实参（高 = Canvas 高 = 6.dp）
 *             cornerRadius = CornerRadius(h / 2f, h / 2f),        // uw1.java:63/65 —— 圆角 = 高的一半 = 3.dp（胶囊）
 *         )
 *         right -= w + 4.dp.toPx()                                // uw1.java:66 —— 从右往左依次排（视觉上仍是从左到右）
 *     }
 * }
 * ```
 * 位置：`Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)`（3439）—— **叠在卡片上**，
 * 即封面之上、卡片底边上方 12.dp 处居中（内层是 3420 的 Box，不是 Column，所以是真·图层叠加）。
 * 颜色：只有一个色 —— 白色按透明度插值（未选中 0.42f → 选中 0.95f），没有「选中色/未选中色」两个常量。
 * 动画：tween(200ms, delay 0, CubicBezierEasing(0.2, 0, 0, 1))，逐点独立（每个点一个 animateFloatAsState）。
 *
 * @param currentPage GZ `pagerState.currentPage`（3439 第 1 实参）
 * @param pageCount   GZ `i2` = 页数（3439 第 2 实参）
 */
@Composable
private fun HomeHeroIndicator(
    currentPage: Int,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    // HomeScreenKt.java:4367-4371 —— `if (i3 <= 1) { …return }`：0/1 页时整个指示器不存在
    if (pageCount <= 1) return

    // HomeScreenKt.java:4396-4408 —— `List(pageCount) { index -> animateFloatAsState(…) }`
    val dots: List<State<Float>> = List(pageCount) { index ->
        animateFloatAsState(
            // HomeScreenKt.java:4399-4403 —— `i10 == i8 ? 1.0f : 0.0f`
            targetValue = if (index == currentPage) 1f else 0f,
            // HomeScreenKt.java:4405 —— `qk5.r0(200, 2, bs4.a)`
            //   qk5.java:1654-1662 → `new tf8(200, 0, bs4.a)`（tf8.java:10-14 = TweenSpec(duration, delay, easing)）
            //   bs4.java:6 → `new wm1(0.2f, 0.0f, 0.0f, 1.0f)` = CubicBezierEasing(0.2f, 0f, 0f, 1f)（wm1.java:15-21）
            animationSpec = tween(
                durationMillis = 200,
                delayMillis = 0,
                easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
            ),
            // HomeScreenKt.java:4405 —— ku4.f(i, "heroDot") = "heroDot" + i（ku4.java:28-30）
            label = "heroDot$index",
        )
    }

    // HomeScreenKt.java:4412 —— `ij7.o(f05Var2, (e * (i9 - 1)) + (d * i9), c)`
    //   ij7.java:102-104 → `new hj7(w, h, w, h, true)` = Modifier.size(width = w, height = h)
    //   宽 = 4.dp * (页数 - 1) + 14.dp * 页数（间隙 * (n-1) + 每点宽 * n）
    // 这个 modifier 就是 3439 的 `hp1.s0(…, 0f,0f,0f, b, 7)`（已在调用点给出 align + padding bottom 12.dp）
    val dotSpacing = HeroDotSpacing // uw1.java:54 —— `E0(HomeScreenKt.e)` = 4.dp
    Canvas(
        modifier = modifier.size(
            width = HeroDotSpacing * (pageCount - 1) + HeroDotSelectedWidth * pageCount,
            height = HeroDotHeight,
        ),
    ) {
        // uw1.java:51-59 —— DrawScope：`g72Var.a()` 低 32 位 = 高、高 32 位 = 宽（Size 打包顺序）
        val canvasWidth = size.width
        val canvasHeight = size.height
        val spacingPx = dotSpacing.toPx() // uw1.java:54 —— `E0(HomeScreenKt.e)` = 4.dp→px
        val minWidthPx = HeroDotHeight.toPx() // uw1.java:55 —— `E0(HomeScreenKt.c)` = 6.dp→px（未选中宽 = 高）
        val maxWidthPx = HeroDotSelectedWidth.toPx() // uw1.java:56 —— `E0(HomeScreenKt.d)` = 14.dp→px
        // uw1.java:58/63 —— `fIntBitsToFloat / 2.0f`，fIntBitsToFloat = 高 → 圆角 = 高/2 = 3.dp
        val radius = canvasHeight / 2f
        var right = canvasWidth // uw1.java:59 —— 从画布右边缘开始往左排

        // uw1.java:60-72 —— `while (i2 < i4) { … i4-- }`，实际按 index 从 pageCount-1 递减到 0
        for (index in pageCount - 1 downTo 0) {
            // uw1.java:61 —— `((ys7) arrayList.get(i4)).getValue()`（在 DrawScope 里读，滚动/动画即重绘）
            val t = dots[index].value
            // uw1.java:62 —— `ij1.a(fE2, fE1, t, fE1)`；ij1.java:44-46 `(f - f2) * f3 + f4`
            //   = (14px - 6px) * t + 6px = lerp(6.dp, 14.dp, t)
            val dotWidth = minWidthPx + (maxWidthPx - minWidthPx) * t
            drawRoundRect(
                // uw1.java:65 第 2 实参 —— `g91.c(t * 0.53f + 0.42f, g91.d)` = Color.White.copy(alpha = 0.42f + 0.53f·t)
                color = Color.White.copy(alpha = 0.42f + 0.53f * t),
                // uw1.java:65 第 3 实参 —— Offset(x = 画布宽 - 当前点宽, y = 0f)
                topLeft = Offset(right - dotWidth, 0f),
                // uw1.java:65 第 4 实参 —— Size(width = 当前点宽, height = 画布高)
                size = Size(dotWidth, canvasHeight),
                // uw1.java:65 第 5 实参 —— CornerRadius(radius, radius)
                cornerRadius = CornerRadius(radius, radius),
            )
            // uw1.java:66 —— `fIntBitsToFloat2 -= fA + f3`（点宽 + 间隙）
            right -= dotWidth + spacingPx
        }
    }
}

/**
 * 「封面播放按钮」（`m43HomeCoverPlayButtonS9WYxP8`，HomeScreenKt.java:4606 起）。
 *
 * 外层：`Modifier.size(size)` + 点击（HomeScreenKt.java:4763）：
 * ```
 * MotionKt.c(ij7.n(mod, size), z3, 0.9f, false, null, onClick, 12)   // 4763
 * ```
 * 内容（Box 的 contentAlignment = Alignment.Center，4765 `jj0.d(il0.o, false)`）：
 *  - `glassCircle == true`（GZ `z2` 默认 true，4759）：
 *    ```
 *    Modifier.size(36.dp)                                             // 4798
 *        .shadow(4.dp, CircleShape, Black 15%, Black 25%)             // 4800
 *        .background(Brush.verticalGradient(listOf(color, lerp(color, White, 0.22f))), CircleShape) // 4795+4797
 *        .border(1.dp, White.copy(alpha = 0.65f), CircleShape)         // 4797
 *    Icon(Icons.Rounded.PlayArrow, desc, Modifier.size(22.dp).offset(1.dp, 0.dp), tint = White)      // 4815
 *    ```
 *  - `glassCircle == false`（试听行用的分支：GZ 传 z2 = false，HomeScreenKt.java:8020）：
 *    ```
 *    Icon(Icons.Rounded.PlayArrow, desc, Modifier.size(iconSize).offset(1.dp, 0.dp), tint = color)   // 4824
 *    ```
 *
 * @param size 外层尺寸：GZ 默认 44.0f（4744/4868），试听行传 28.0f（8020）。
 * @param circleSize 玻璃圆尺寸：GZ 默认 36.0f（4749），试听行传 0.0f（该分支未走到）。
 * @param iconSize 图标尺寸：GZ 默认 22.0f（4754），试听行传 16.0f（8020）。
 * @param color 着色：试听行传 `tone.copy(alpha = if (dark) 0.68f else 0.54f)`（7919）。
 */
@Composable
private fun HomeCoverPlayButton(
    color: Color,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glassCircle: Boolean = true,
    size: Dp = 44.dp,
    circleSize: Dp = 36.dp,
    iconSize: Dp = 22.dp,
) {
    Box(
        modifier = modifier
            .size(size) // HomeScreenKt.java:4763 —— ij7.n(f05Var2, f10)
            // HomeScreenKt.java:4763 —— MotionKt.c(..., 0.9f, ...)：按下缩放 0.9f（见 TODO ④）
            .clickable(onClick = onClick),
        // HomeScreenKt.java:4765 —— jj0.d(il0.o, false) = Box(contentAlignment = Alignment.Center)
        contentAlignment = Alignment.Center,
    ) {
        if (glassCircle) {
            Box(
                modifier = Modifier
                    .size(circleSize) // HomeScreenKt.java:4798 —— ij7.n(c05.a, f9)
                    // HomeScreenKt.java:4800 —— mg2.I(..., 4.0f, CircleShape, Black 15%, Black 25%, 4)
                    .shadow(
                        elevation = 4.dp,
                        shape = CircleShape,
                        ambientColor = Color.Black.copy(alpha = 0.15f),
                        spotColor = Color.Black.copy(alpha = 0.25f),
                    )
                    // HomeScreenKt.java:4795 —— pr6.M(listOf(color, hp1.h0(color, 0.22f, White)), 0f, 0f, 14)
                    //   = Brush.verticalGradient(colors = [color, lerp(color, Color.White, 0.22f)])
                    // HomeScreenKt.java:4797 —— o13.a(mod, brush, CircleShape, 4)
                    .background(
                        Brush.verticalGradient(
                            listOf(color, lerp(color, Color.White, 0.22f))
                        ),
                        CircleShape,
                    )
                    // HomeScreenKt.java:4797 —— x8.g(mod, 1.0f, g91.c(0.65f, White), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.65f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow, // HomeScreenKt.java:4815 —— fd2.q()
                    contentDescription = contentDescription,
                    // HomeScreenKt.java:4815 —— zf8.b0(ij7.n(c05.a, f11), 1.0f, 0.0f, 2) = size(22.dp).offset(1.dp, 0.dp)
                    modifier = Modifier.size(iconSize).offset(x = 1.dp, y = 0.dp),
                    tint = Color.White, // HomeScreenKt.java:4815 —— g91.d
                )
            }
        } else {
            Icon(
                imageVector = Icons.Rounded.PlayArrow, // HomeScreenKt.java:4824 —— fd2.q()
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize).offset(x = 1.dp, y = 0.dp), // HomeScreenKt.java:4824
                tint = color, // HomeScreenKt.java:4824 —— 第 4 实参 j2
            )
        }
    }
}


/*
 * ============================================================================
 * TODO(未确定) 清单
 * ============================================================================
 * ① 图片 contentScale
 *    Hero 卡片：`tf2.e(model, null, modifier, null, null, ...)`（HomeScreenKt.java:3575）未传 contentScale
 *      （tf2.java:503-504 掩码默认 → null），最终值由 GZ 自带图片库的默认量 `m50.m0` 决定（tf2.java:520），
 *      该默认量的具体枚举值未在本轮确认 → 这里用 ContentScale.Crop。
 *    试听行封面：`tf2.f(..., qh1.a, ...)`（HomeScreenKt.java:7984），`qh1.a = new pr6(23)`（qh1.java:6）
 *      的真实语义（FilterQuality / ContentScale / placeholder）未确认 → 这里用 ContentScale.Crop。
 *
 * ② CdPlaceholder（封面为空时的 Canvas 占位，HomeScreenKt.java:1974-2038）
 *    已确认的量：底色 alpha = 深色 0.1f / 浅色 0.08f（1994）、小元素 alpha = 深色 0.06f / 浅色 0.04f（1995）、
 *    第二档 alpha = 0.1f * 1.6f（1997）、描线 alpha = 深色 0.28f / 浅色 0.2f（1999）、
 *    圆半径 = 0.3f * 宽度（2013）、3 根条比例 0.88f / 0.72f / 0.56f（2016）、线宽 = f4 * 0.15f（2022）、
 *    描边 dp→px 用 0.8f（2015）、圆角 = lw7(fE0, 0f, 0, 0, null, 30)（2019）。
 *    但 `g72.F0(...)` / `g72.r0(...)` 的形参顺序无法从反编译确定 → 不写死，保留卡片底色。
 *
 * ③ 轮播（HorizontalPager + 页码指示器）—— **本轮已实现**（见 [HomeDailyHero] 与 [HomeHeroIndicator]）
 *    调用链：m42 在 `z81.L0(list4, 3)`（HomeScreenKt.java:3765 = `take(3)`）后把列表交给 m41；
 *    m41 取 `size = list.size()`（3024）建 `rememberPagerState(0, { size })`（3032 `xp5.b`），
 *    lambda$5（3370-3412）里 `mf2.a(...)`（3438）画 HorizontalPager、3439 画 `HeroPageIndicator`。
 *    逐值（均已写进代码注释）：modifier = fillMaxSize（3438），beyondViewportPageCount = 1（3438 第 5 实参，
 *    $default 掩码 16108 的 bit4 未置位 = 显式传入），pageSpacing = 0.dp（第 6 实参 0.0f），
 *    userScrollEnabled = (页数 > 1)（第 9 实参 `i2 > 1`），contentPadding/pageSize/verticalAlignment/
 *    flingBehavior/key 全走默认（掩码置位 → 不过滤成自定义值），**没有** reverseLayout / 无限循环相关实参。
 *    指示器：圆点未选中宽 6.dp → 选中宽 14.dp（c/d，303/304）、间隙 4.dp（e，305）、Canvas 高 6.dp（c）、
 *    整体宽 = 4.dp*(n-1) + 14.dp*n（4412）、右下角圆角 = 高/2 = 3.dp 的胶囊（uw1.java:63/65）、
 *    颜色 = Color.White.copy(alpha = 0.42f + 0.53f·t)（uw1.java:65，未选中 0.42 / 选中 0.95）、
 *    动画 = tween(200ms, delay 0, CubicBezierEasing(0.2f, 0f, 0f, 1f))（4405 + qk5.java:1654 + bs4.java:6）、
 *    位置 = 叠在卡片内、`align(Alignment.BottomCenter).padding(bottom = 12.dp)`（3439 + il0.java:58 + b=12.0f）、
 *    `pageCount <= 1` 时整个指示器不渲染（4367-4371）。
 *    **层叠偏移**：m41/m42 里**没有**任何 `Modifier.offset`/负边距式错位；所谓“堆叠”=
 *    竖直 Column 堆叠（公告条 → 12dp → 卡片，HomeScreenKt.java:3726-3771）
 *    + 卡片内部「1.16× 放大 + 26.dp 模糊」的封面层（HomeScreenKt.java:3555-3565）。
 *    唯一非零偏移是播放按钮图标的 `offset(x = 1.dp, y = 0.dp)`（HomeScreenKt.java:4815 / 4824）。
 *
 * ④ 按下缩放动画
 *    卡片：pressedScale = 0.985f（HomeScreenKt.java:3505）；播放按钮：pressedScale = 0.9f（4763 / 4887），
 *    且第 4 实参（enabled）来自 CompositionLocal（`HomeScreenKt.a`，3406 提供
 *    `z2 || derivedStateOf { pagerState 滚动中 }` —— 3062 + l33.java:80-82 + vp5.k = scrollableState）。
 *    该语义本轮已确认 → 卡片 clickable 的 enabled 已按 3406/3505 写为
 *    `当前页非空 && !heroLoading && !pagerState.isScrollInProgress`。
 *    Melos 无 bounceClick/pressScale 修饰符 → 仍未复刻 0.985f 的按下缩放。
 *
 * ⑤ title / subtitle —— **本轮已改**（原「GZ 无文本节点 → 不渲染」的结论保留，但版式换成参考图版式）
 *    GZ 2.1.8 的这张卡确实**没有任何标题/副标题文本节点**：DailyHeroPage（3475-3587）整函数只有
 *    cover（clip + background + AsyncImage / CdPlaceholder），无 `c88.b` 文本调用；jp6 的
 *    name/songCount/description 在 m41/m42/DailyHeroPage 里从未被读取（详见文件头「版式来源声明」）。
 *    本轮按用户提供的**更新版 GZ 真机截图**重做单页版式：卡上标题 = 歌单名（[PlaylistItem.name] ← GZ jp6.b），
 *    面板内元信息 = 歌单描述（[PlaylistItem.description] ← GZ jp6.e，绑定为推断，见 TODO ⑫），
 *    曲目数 = `trackCount` + " 首"（GZ jp6.d + `rt0.p(count, " 首")` 的后缀字面量）。
 *    字号字重：标题 20sp / Bold(700, GZ sq2.p) / 最多 2 行 / 行高不指定；小字 9sp / 行高 12sp
 *    （HomeScreenKt.java:9796）。**没有**沿用公告条（HomeNoticeStripKt.java:300-305）的 13sp，那是另一个组件。
 *
 * ⑥ isPlaying
 *    m41/m42/m43/m46 中不存在“正在播放”状态：播放按钮恒为 `fd2.q()` = Icons.Rounded.PlayArrow
 *    （HomeScreenKt.java:4815 / 4824），没有暂停图标、没有 PlayingBars、没有波形。
 *    → 该参数在本组件内无 GZ 依据，未参与渲染。
 *
 * ⑦ 试听行的左右内边距
 *    GZ 的行函数（7932）只有 `fillMaxWidth().heightIn(min = 38.dp)`，横向内边距由调用方提供（本文件用的是
 *    同一张卡片自身的 16.dp，HomeScreenKt.java:3067）。若上游首页已有自己的 16.dp 容器，请去掉这里的 padding。
 *
 * ⑧ 其它未纳入的元素
 *    - 公告条 `HomeNoticeStrip(list2, ..., Modifier.padding(horizontal = 16.dp))`（HomeScreenKt.java:3753）
 *      与「公告条 → 卡片」之间的 `Spacer(Modifier.height(12.dp))`（3963 `ij1.w(..., 12.0f, ...)`，仅在
 *      hero 为空或载入中时插入）不属于本次接口范围，未实现。
 *    - 载入骨架：HomeScreenKt.java:11187-11250 有一段骨架屏（112.dp / 19.dp / 24.dp / 36.dp 等），
 *      属于另一个区块（不是 m41/m46），未合并进来。
 *
 * ⑨ hasCustomCover 的粒度（**未确定**）
 *    GZ 是逐页读 `jp6Var.i`（HomeScreenKt.java:3502 `z2 = jp6Var != null && jp6Var.i`），即每页各自决定
 *    「1.16× 放大 + 26.dp 模糊」；Melos 的 [PlaylistItem]（MusicApi.kt:47-62）没有 hasCustomCover 字段，
 *    本组件的 `hasCustomCover` 是整卡一个开关，故 3 页共用同一个值（`pages.firstOrNull()` 推导默认值）。
 *    如需逐页还原，需要给 PlaylistItem 补字段（超出本次改动范围）。
 *
 * ⑩ PagerSettleGuard（未纳入）
 *    GZ 在 3033 调 `PagerSettleKt.PagerSettleGuard(pagerState, …)`（另有 3308），是 GZ 自带的
 *    「翻页未结束时不触发点击」守卫；本轮用 `!pagerState.isScrollInProgress`（3406/3505 的局部量）
 *    覆盖了点击禁用，未单独复刻该守卫（其内部实现在反编译产物里未进一步展开）。
 *
 * ⑪ 卡底色：GZ 2.1.8 ↔ 更新版 GZ 的差异（**已按更新版实现，配方取 2.1.8 同类元素**）
 *    2.1.8：`hp1.s(..., GlassTokensKt.coverPlaceholder(), shape)`（HomeScreenKt.java:3388）
 *           = `textMedium.copy(alpha = 0.12f)` 纯色（GlassTokensKt.java:1199-1200）——与封面无关。
 *    参考图：卡底是**随封面变的主色渐变**（第 1 页绿、第 2 页蓝），且实测其左上角比右下角更饱和
 *           （斜向渐变）。本轮改用 2.1.8 的同类元素 `glassToneCard`（GlassTokensKt.java:1296-1349 /
 *           GlassTokens.kt:198）+ `rememberCoverTone`（CoverToneKt.java:68-106 / CoverTone.kt:692），
 *           并让 tone 跟随**当前页封面**（`rememberCoverTone(currentPlaylist?.coverUrl, tone)`）。
 *    **仍有偏差**：参考图左上角实测 ≈ rgb(205,244,166)（比 glassToneCard 浅色档的 tone@20% 更饱和），
 *           若要做到像素级接近，需要把 0.20/0.08/0.0 这三档调大 —— 但那是**猜数值**，故未改，
 *           等拿到更新版 GZ 的真值再定。
 *
 * ⑫ 参考图版式里的「测量值」清单（**全部来自像素测量，GZ 2.1.8 无对应元素**）
 *    换算前提与逐项测量见 [HomeHeroPage] 上方的测量表（1 参考图像素 ≈ 1dp，误差 ≤10%）。
 *    落到代码里的量：HeroCoverCorner(6) / HeroPanelCorner(6) / HeroRowStartPadding(24) /
 *    HeroRowEndPadding(21) / HeroCoverPanelGap(15) / HeroPanelPadding(12) /
 *    HeroCoverHeightRatio(0.59) / HeroPanelHeightRatio(0.63) / HeroRowTopRatio(0.21)。
 *    另外三处**有已知分歧**，需要更新版 GZ 的真值才能定：
 *      · HeroCardCorner 保持 20dp（GZ 真值 HomeScreenKt.java:3066）；参考图实测 ≈14px（≈14~16dp），
 *        说明更新版可能把卡片圆角改小了。用户要求按 20dp，故未动。
 *      · 标题字距：参考图分辨不出；GZ 的 20sp 档用 -0.4/-0.5（HomeScreenKt.java:2297/10977），
 *        18sp 档用 0L（TabletHomeScreenKt.java:623）→ 本文件取 0。
 *      · 面板内三行文字的字号：标题 20sp 是按「墨高 ≈16px、行距 ≈21px」反推的（20sp 默认行高 ≈24dp，
 *        在 1px≈0.9dp 下 ≈21.6px ✓ 自洽）；元信息/曲目数 9sp 是按 HomeScreenKt.java:9796 的
 *        `co7.j(9)/co7.j(12)` 档取的，实测墨高仅 4~5px，无法独立确定字号。
 *
 * ⑬ 参考图里有、本轮**没有实现**的 4 处极小字（不编文案）
 *    位置（参考图像素）与形状：卡片顶部左 x39-71 / y183-189（一个图标 + 一行极小字）、
 *    顶部右 x283-322 / y186-189（一行极小字）、左下 x56-72 / y324-325（封面正下方）、
 *    右下 x305-325 / y336-345。四处字都只有 4~6px 高，放大 7 倍后仍无法辨认内容。
 *    找过的地方：HomeScreenKt 的 m41/m42/DailyHeroPage 无任何文本节点；`首`/`50 首`/`今日精选 · `
 *    这类固定串在 2.1.8 里只出现在别的组件（如 TabletHomeScreenKt.java:627 的「今日精选 · N 首」）；
 *    Melos 的 [PlaylistItem] 可用的候选字段只有 playCount / platform / kind / group（无法判断哪个对应哪一处）。
 *    → 按「绝不编文案」的要求，这 4 处**未渲染**；若后续确认来源，补 4 个 Text 即可（版式槽位在
 *    [HomeHeroPage] 里预留了位置：顶部行用 `padding(top = 卡高 * 0.21)`、底部行靠卡底 12.dp 指示器上方）。
 * ============================================================================
 */
