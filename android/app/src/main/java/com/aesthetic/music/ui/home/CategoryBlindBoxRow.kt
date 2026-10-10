package com.aesthetic.music.ui.home

import android.graphics.Color as AndroidColor
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aesthetic.music.data.api.HomeCategoryDto
import com.aesthetic.music.ui.components.DefaultCoverTone
import com.aesthetic.music.ui.components.coverHairline
import com.aesthetic.music.ui.components.coverPlaceholder
import com.aesthetic.music.ui.components.discoveryGradientCard
import com.aesthetic.music.ui.components.rememberCoverTone
import com.aesthetic.music.ui.components.textHigh
import com.aesthetic.music.ui.components.textMedium
import com.aesthetic.music.ui.theme.appIsDark
import kotlin.math.max
import kotlin.math.min

/**
 * GZ Music 首页「音乐盲盒」分类卡行 —— 逐值还原自反编译真值
 * `com/gzmusic/app/feature/home/CategoryBlindBoxRowKt.java`（**408** 行，非 403 行）。
 * 下文所有 `xxx.java:NNN` 注释都是该文件的行号（除非写明别的文件名）。
 *
 * ## 源文件结构
 * - `m36CategoryBlindBoxRowau3_HiA`（84-193）：主 Composable；133 行外面还包了一层
 *   `CompositionLocalProvider`（`CoverToneKt.c` 提供一个 boolean，语义见 TODO ③）。
 * - `CategoryBlindBoxRow_au3_HiA$lambda$0`（196-227）：行内容 = `Row` + 3 张等宽卡。
 * - `CategoryRoundCard`（229-403）：单张卡。
 *
 * ## ★ 与「横滑 6 宫格渐变卡」印象不同的真值
 * 1. 行是 **`Row`**（200 `gy6.a`），**不是** LazyRow / horizontalScroll：
 *    199 `Modifier.fillMaxWidth().padding(horizontal = spacing).height(IntrinsicSize.Min)`；
 *    3 张卡各 `weight(1f, fill = true)` + `fillMaxHeight()`（219）等分宽度；
 *    200 卡间距 `Arrangement.spacedBy(10.dp)`。
 * 2. 固定 **3 个槽位**：218 `for (i2 = 0; i2 < 3; i2++)`，219 `z81.q0(i2, list)` 即 `getOrNull(i2)`
 *    —— 取不到的位置渲染「占位卡」（标题 `音乐盲盒` + 三态副标题，381-392）。
 *    调用方另外还做了 `take(3)`（HomeScreenKt.java:497 `z81.L0(list, 3)`，z81.java:168）。
 * 3. 「6 大盲盒」在**数据层**：me3.java:39
 *    `F0 = listOf("流行", "摇滚", "民谣", "电子", "轻音乐", "古典")`；
 *    me3.java:1096 `z81.L0(wi0.K(F0), 3)`，其中 `wi0.K` = `shuffled()`（wi0.java:1143-1148）
 *    —— 每次刷新从 6 个里**随机洗牌取 3 个**，「盲盒」即此随机语义；
 *    me3.java:398 列表为空时兜底 `F0.take(3)`。本 Composable 只渲染传进来的列表，
 *    随机逻辑不在本文件（也不该在这里复现）。
 * 4. 卡上**没有图标**：画面主体是歌单封面位图（313 `qk5.N(context, coverUrl, 200)` 载入，
 *    374 以 `ContentScale.Crop` 铺满）。
 * 5. 卡上**没有小节标题、也没有「更多」入口**（旧版 Melos 实现里的
 *    「音乐盲盒 / 拆开未知的听觉宇宙 / 更多」全部是编造的）。首页那句
 *    「部分盲盒未刷新 · 点击重试」在 HomeScreenKt.java:512，不在本文件。
 *
 * ## 卡片三段视觉（数值全部来自字面量）
 * - 卡片底：`discoveryGradientCard(RoundedCornerShape(16.dp), colors = [白↔色调 40%, 白↔色调 8%])`（321-323）
 * - 封面：`widthIn(max = 70.dp).fillMaxWidth().aspectRatio(1f)`（357）→ 正方形，边长 ≤ 70dp
 *   + `background(coverPlaceholder(), 12.dp)` + `clip(12.dp)` + `border(0.7.dp, coverHairline(), 12.dp)`（357）
 * - 文字：标题 13sp / Bold / lineHeight 18sp / Center / 1 行（384）；
 *   副标题 10sp / 默认字重 / lineHeight 14sp / Center / 2 行 / minLines 2（393）
 *
 * ## TODO（未确定）
 * - ① 封面环境色：GZ 用 `CoverToneKt.m299rememberCoverToneiJQMabo(url)`（CoverToneKt.java:68-106）
 *   从封面图异步提取环境色（`CoverAmbientKt.h`，前有 80ms 延迟 —— wl1 反编译常量 r2=80，
 *   结果 500ms `tween` 过渡，CoverToneKt.java:105），初始值 `CoverToneKt.a = 0xFF8E8E93`（:28）。
 *   Melos 没有等价提取器 → 本文件用 `DefaultCoverTone` 兜底，3 张卡颜色完全相同。
 * - ② 200 `spacedBy` 的 alignment 实参是 JADX lambda `c(9)`，无法逐值确定；
 *   因 3 张卡都 `weight(1f, fill = true)` 撑满整行，该 alignment 不产生任何位移，故按单参形式书写。
 * - ③ 133 `CompositionLocalProvider(CoverToneKt.c provides z3)`：`z3` 是「是否禁用封面环境色提取」
 *   的 boolean（wl1.java:97/145 `if (bool) 跳过提取`）。Melos 无提取器亦无该 Local → 未暴露参数。
 * - ④ 图片载入尺寸 200（313，`qk5.N` 的第 3 参）在 coil 侧未指定；GZ 是 bitmap 直绘、无 crossfade。
 * - ⑤ Melos 的 `HomeCategoryDto` 另有 category 级 `coverUrl` / `quote`，GZ 的 `s11` 只有
 *   `playlist`（`s11.b.c` 封面、`s11.b.b` 名称）。此处以 GZ 为准、Melos 字段做兜底，见下方注释。
 */
@Composable
fun CategoryBlindBoxRow(
    categories: List<HomeCategoryDto>,
    onCategoryClick: (HomeCategoryDto) -> Unit,
    isLoading: Boolean = false,
    isError: Boolean = false,
    onRetry: () -> Unit = {},
    spacing: Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    // CategoryBlindBoxRowKt.java:200 —— Row(horizontalArrangement = ..., verticalAlignment = Alignment.Top)
    //   `gy6.a(new j10(10.0f, true, new c(9)), il0.e0, ...)`（gy6.java:6/8）；il0.e0 = Alignment.Top
    // CategoryBlindBoxRowKt.java:199 —— `hp1.q0(ij7.g(c05.a, 1.0f), f, 0.0f, 2)`
    //   = Modifier.fillMaxWidth().padding(horizontal = f, vertical = 0.dp)
    // CategoryBlindBoxRowKt.java:199 —— `zh.A(...)` = Modifier.height(IntrinsicSize.Min)
    //   （xp3.java:8-11 建节点 + yp3.java:17-26 固定 height 区间）
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing)
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // CategoryBlindBoxRowKt.java:218-220 —— 固定 3 个槽位，越界用 getOrNull（z81.q0 = getOrNull）
        for (index in 0 until 3) {
            CategoryRoundCard(
                item = categories.getOrNull(index),
                isLoading = isLoading,
                isError = isError,
                onCategoryClick = onCategoryClick,
                onRetry = onRetry,
                // CategoryBlindBoxRowKt.java:219 —— `ij7.c(new ez3(1.0f, true), 1.0f)`
                //   = Modifier.weight(1f, fill = true).fillMaxHeight()（ij7.c = fillMaxHeight，ij7.java:54-56）
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxHeight(),
            )
        }
    }
}

/**
 * 单张盲盒卡 —— 还原 `CategoryBlindBoxRowKt.CategoryRoundCard`（229-403）。
 *
 * `item == null` 即 GZ 的「占位槽」：标题回退 `音乐盲盒`，副标题回退三态文案。
 */
@Composable
private fun CategoryRoundCard(
    item: HomeCategoryDto?,
    isLoading: Boolean,
    isError: Boolean,
    onCategoryClick: (HomeCategoryDto) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = appIsDark() // CategoryBlindBoxRowKt.java:261 —— `ThemeKt.appIsDark(...)`

    // CategoryBlindBoxRowKt.java:259 —— 封面 URL 取 `s11Var.b.c` = playlist.coverUrl
    //   （jp6/lo0 字段序：a=id, b=name, c=coverUrl …，lo0.java:77 toString）
    // ⑤ Melos 兜底：HomeCategoryDto 自带 category 级 coverUrl
    val coverUrl = item?.playlist?.coverUrl?.takeIf { it.isNotBlank() } ?: item?.coverUrl

    // ① 封面环境色（GZ: CoverToneKt.java:68-106）
    val coverTone = rememberBlindBoxCoverTone(coverUrl)

    // CategoryBlindBoxRowKt.java:266-275 —— accent = Color.hsv(h, min(s, 0.4f), isDark ? 0.78f : 0.52f)
    //   267 `Color.colorToHSV(hp1.x0(coverTone), fArr)`；271-273 `if (s > 0.4f) s = 0.4f`；
    //   274 `pr6.O(h, s, zAppIsDark ? 0.78f : 0.52f)`（pr6.O = Color.hsv）
    val accent = remember(coverTone, isDark) {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(coverTone.toArgb(), hsv)
        Color.hsv(hsv[0], min(hsv[1], 0.4f), if (isDark) 0.78f else 0.52f)
    }

    // CategoryBlindBoxRowKt.java:278-284 —— 副标题色：
    //   有数据：280 `hp1.h0(GlassTokensKt.textMedium(), 0.55f, accent)` = lerp(textMedium, accent, 0.55f)
    //   占位态：283 `rt0.C(...)` → rt0.java:29-34 直接返回 `GlassTokensKt.textMedium()`
    val subtitleColor = if (item != null) gzMix(textMedium(), 0.55f, accent) else textMedium()

    // CategoryBlindBoxRowKt.java:285 —— `by6.a(12.0f)` = RoundedCornerShape(12.dp)（by6.java:13-15）
    val artworkShape = RoundedCornerShape(12.dp)

    // CategoryBlindBoxRowKt.java:286-304 —— 封面底色（渐变基色）：
    //   289-301 `Color.colorToHSV(coverTone, fArr2)` → sat `s * 1.12f`，上限 0.7f（293-296）
    //   → value `v`，下限 0.92f（297-300）→ `Color.hsv(h, s, v)`（301）
    val artworkTone = remember(coverTone) {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(coverTone.toArgb(), hsv)
        Color.hsv(hsv[0], min(hsv[1] * 1.12f, 0.7f), max(hsv[2], 0.92f))
    }

    // CategoryBlindBoxRowKt.java:321-323 ——
    //   shape = `by6.a(16.0f)` = RoundedCornerShape(16.dp)
    //   colors = listOf(hp1.h0(Color.White, 0.4f, artworkTone), hp1.h0(Color.White, 0.08f, artworkTone))
    //   （`g91.d` = Color.White，g91.java:12/23 = hp1.e(0xFFFFFFFF)）
    //   useSharedGradient = false（323 第 4 实参）→ 浅色下走 Brush.linearGradient
    val cardShape = RoundedCornerShape(16.dp)
    val gradientColors = listOf(
        gzMix(Color.White, 0.4f, artworkTone),
        gzMix(Color.White, 0.08f, artworkTone),
    )

    // CategoryBlindBoxRowKt.java:324 —— `z3 = s11Var != null || (z2 && !z)`
    //   即「有数据，或（加载失败且不在加载中）」时卡片才可点
    val enabled = item != null || (isError && !isLoading)

    // CategoryBlindBoxRowKt.java:331 —— `MotionKt.c(m, z3, 0.98f, false, null, onClick, 12)`
    //   mask 12 → 第 4 实参（interaction enabled）与 interactionSource 取默认值（true / 新建）
    //   按压缩放 0.98f；动画 = `ct.a` = tween(150ms, easing = bs4.a)（ct.java:8；bs4.java:6 =
    //   CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)），label "pressScale"（MotionKt.java:62）
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.98f else 1f,
        animationSpec = tween(durationMillis = 150, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)),
        label = "pressScale",
    )

    // CategoryBlindBoxRowKt.java:326-330 —— `o6(s11Var, 5, at2Var, ys2Var)`
    //   o6.java:163-172：有数据 → `at2Var.invoke(s11Var)`；无数据 → `ys2Var.invoke()`
    //   （即占位卡点击 = 重试 / 加载更多，对应 HomeScreen 传进来的 `loadMoreCategories`）
    val onClick: () -> Unit = {
        if (item != null) onCategoryClick(item) else onRetry()
    }

    // CategoryBlindBoxRowKt.java:332-354 —— **卡片本体就是 Column**（没有额外的 Box）：
    //   332 `aa1.a(zh.d, il0.i0, ...)` → Column(verticalArrangement = Arrangement.Top,
    //   horizontalAlignment = Alignment.CenterHorizontally)（aa1.java:6 的默认值即 zh.d；
    //   il0.i0 = Alignment.CenterHorizontally，il0.java:64）；335 `o40.E(composer, f05VarP0)`
    //   就是把 f05VarP0 交给这个 Column。这里**没有** fillMaxWidth：宽度由 Row 侧
    //   `weight(1f, fill = true)` 强制定死，CenterHorizontally 因此正好按卡片宽度居中。
    Column(
        modifier = modifier
            // CategoryBlindBoxRowKt.java:323 —— `GlassTokensKt.discoveryGradientCard(f05, shape, colors, false, ...)`
            .discoveryGradientCard(
                shape = cardShape,
                colors = gradientColors,
                useSharedGradient = false,
            )
            // CategoryBlindBoxRowKt.java:331 —— 先 `MotionKt.c(...)`（scale + clickable），再 `hp1.p0(...)`
            .scale(pressScale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            // CategoryBlindBoxRowKt.java:331 —— `hp1.p0(m, 9.0f, 14.0f)`
            //   = padding(horizontal = 9.dp, vertical = 14.dp)（hp1.java:3950-3952）
            .padding(horizontal = 9.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // CategoryBlindBoxRowKt.java:357-358 —— 封面 Box。修饰符顺序照抄，不要「理顺」：
        //   widthIn → fillMaxWidth → aspectRatio → background → clip → border
        //   `ij7.t(c05, 0.0f, 70.0f, 1)` = widthIn(max = 70.dp)（ij7.java:121-133 → ij7.s/hj7 语义）
        //   `ij7.g(..., 1.0f)` = fillMaxWidth（ij7.java:70-72）
        //   `o40.k(..., 1.0f)` = aspectRatio(1f)（o40.java:739-741）
        //   `hp1.s(..., coverPlaceholder(), ay6)` = background(color, shape)（hp1.java:3976-3978）
        //   `hq3.g(..., ay6)` = clip(shape)
        //   `x8.g(..., 0.7f, coverHairline(), ay6)` = border(0.7.dp, color, shape)（x8.java:402）
        Box(
            modifier = Modifier
                .widthIn(max = 70.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(coverPlaceholder(), artworkShape)
                .clip(artworkShape)
                .border(0.7.dp, coverHairline(), artworkShape),
            // CategoryBlindBoxRowKt.java:358 —— `jj0.d(il0.o, false)`；il0.o = Alignment.Center（il0.java:55）
            contentAlignment = Alignment.Center,
        ) {
            // CategoryBlindBoxRowKt.java:372-379 —— `if (bitmap != null) Image(bitmap, null, fillMaxSize(),
            //   ContentScale.Crop, ...)`；`qh1.a` = ContentScale.Crop（pr6.java:879-893，case 23 = 取 max 比例）
            // ④ Melos 改用 coil 直载（GZ 的 200px 载入尺寸、bitmap 直绘无 crossfade，见 TODO）
            if (!coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        // CategoryBlindBoxRowKt.java:380 —— `ij1.x(...)` = Spacer(Modifier.height(10.dp))（ij1.java:162-165）
        Spacer(Modifier.height(10.dp))

        // CategoryBlindBoxRowKt.java:381-383 —— 标题：`s11Var.a ?: "音乐盲盒"`
        //   Melos 的 HomeCategoryDto.title 默认空串而非 null，故用 isNullOrBlank 才可能回退
        val title = item?.title?.takeIf { it.isNotBlank() } ?: "音乐盲盒"

        // CategoryBlindBoxRowKt.java:384 —— Text(str, null, textHigh(), null, 13.sp, FontWeight.Bold,
        //   null, 0L/*letterSpacing 默认*/, TextAlign.Center, 18.sp, TextOverflow.Ellipsis,
        //   /*softWrap 默认 true*/, maxLines = 1, /*minLines 默认 1*/)
        //   fontWeight 真值 `sq2.p` = W700 = FontWeight.Bold（sq2.java:27/33）
        //   textAlign 真值 `new b58(3)` = TextAlign.Center（b58.java:16-36，3 = "Center"）
        //   overflow 实参 2 = TextOverflow.Ellipsis（c88.java 该参默认 1 = Clip）
        Text(
            text = title,
            color = textHigh(),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
        )

        // CategoryBlindBoxRowKt.java:385 —— `wl7.d(...)` = Spacer(Modifier.height(5.dp))（wl7.java:12）
        Spacer(Modifier.height(5.dp))

        // CategoryBlindBoxRowKt.java:386-392 —— 副标题：
        //   有数据 → `s11Var.b.b` = playlist.name（lo0.java:77 字段序位 b = name）
        //   ⑤ Melos 兜底：HomeCategoryDto.quote
        //   回退文案取自 388/390/390 的字面量（含 \n）
        val boundName = item?.playlist?.name?.takeIf { it.isNotBlank() }
            ?: item?.quote?.takeIf { it.isNotBlank() }
        val subtitle = boundName ?: when {
            isLoading -> "正在发现\n新的好音乐"
            isError -> "加载失败\n点击重试"
            else -> "等待发现\n新的好音乐"
        }

        // CategoryBlindBoxRowKt.java:393 —— Text(str2, null, 副标题色, null, 10.sp, /*fontWeight 默认 null*/,
        //   null, 0L, TextAlign.Center, 14.sp, TextOverflow.Ellipsis, /*softWrap 默认*/, maxLines = 2, minLines = 2)
        Text(
            text = subtitle,
            color = subtitleColor,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
            overflow = TextOverflow.Ellipsis,
            maxLines = 2,
            minLines = 2,
        )
    }
}

/**
 * ① 封面环境色。
 *
 * GZ：`CoverToneKt.m299rememberCoverToneiJQMabo(coverUrl, initial = CoverToneKt.a)`（CoverToneKt.java:68-106）
 * 通过 `CoverAmbientKt` 异步提取封面主色（先 `delay(80)`，结果用 `tween(500)` 过渡），
 * 初始值 `CoverToneKt.a = hp1.e(4287532691L) = 0xFF8E8E93`（CoverToneKt.java:28）。
 *
 * 已接入真值提取器 [rememberCoverTone]（`ui/components/CoverTone.kt`，逐值还原自
 * `CoverAmbientKt.o(Bitmap)` + `CoverAmbientKt.d(long)`），3 张卡现在各按自己的封面取色。
 */
@Composable
private fun rememberBlindBoxCoverTone(url: String?): Color = rememberCoverTone(url)

/**
 * `hp1.h0(base, t, target)` 的等价实现。
 *
 * 真值 hp1.java:3275-3294 把两个颜色各自转到 sRGB 后取 4 个分量，
 * 逐分量调用 `ig2.z(comp(base), comp(target), t)`，其中
 * `ig2.z(f2, f3, f4) = f4 * f3 + (1 - f4) * f2`（ig2.java:1448-1450）
 * —— 即 `lerp(base, target, t)`（t 先 clamp 到 [0,1]，hp1.java:3287-3292）。
 *
 * Melos 没有该 helper，这里直接用 Compose 的 `lerp(Color, Color, Float)`
 * （两个颜色都是 sRGB，故与真值的逐分量 sRGB 插值等价），不引入新数值。
 */
private fun gzMix(base: Color, t: Float, target: Color): Color =
    lerp(base, target, t.coerceIn(0f, 1f))
