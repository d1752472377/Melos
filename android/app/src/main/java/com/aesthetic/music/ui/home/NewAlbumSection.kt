package com.aesthetic.music.ui.home

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aesthetic.music.ui.components.coverHairline
import com.aesthetic.music.ui.components.coverPlaceholder
import com.aesthetic.music.ui.components.glassCard
import com.aesthetic.music.ui.components.rememberCoverTone
import com.aesthetic.music.ui.components.textHigh
import com.aesthetic.music.ui.components.textLow
import com.aesthetic.music.ui.components.textMedium

/**
 * 首页「新碟首发」卡片数据。
 *
 * 对应反编译 `HomeNewAlbum`（ua3.java:5-25 的 toString 给出字段语义）：
 *  - ua3.a = id / ua3.b = name / ua3.c = coverUrl / ua3.d = artist
 *  - ua3.e = publishTime / ua3.f = areaName / ua3.g = source
 *
 * 前 5 个字段的**名称与顺序**保持调用方（HomeScreen.kt:131-137）现有用法不变；
 * GZ 用到但调用方暂未提供的 publishTime / areaName / source 追加在末尾并给出默认值。
 */
data class AlbumDisplayItem(
    val id: String,
    val title: String,
    val artist: String,
    val coverUrl: String?,
    val onClick: () -> Unit = {},
    /** ua3.e —— 发行时间，形如 "2024-05-12"；卡片副行取 substring(5,10) 后把 '-' 换成 '.'。 */
    val publishTime: String = "",
    /** ua3.f —— 地区/流派文案（反编译字段名 areaName），与发行日期用 " · " 连接。 */
    val areaName: String = "",
    /** ua3.g —— 数据来源（用于埋点/跳转，卡片渲染未使用）。 */
    val source: String = "",
)

/**
 * `rememberCoverTone` 的默认色调：`CoverToneKt.java:28`
 * `a = hp1.e(4287532691L)` = `Color(0xFF8E8E93)`（`hp1.e(j) = j << 32`，hp1.java:3160）。
 *
 * TODO(未确定)：GZ 用 `CoverToneKt.m299rememberCoverToneiJQMabo(url, 0L, ...)`（NewAlbumSectionKt.java:98）
 *   从封面异步提取主色（走 CoverAmbient 缓存 + 500ms animateColorAsState，CoverToneKt.java:68-106），
 *   本项目没有等价实现 → 唱片一律退回该默认色。见文件末尾 TODO 清单。
 */
private val CoverToneFallback = Color(0xFF8E8E93)

/**
 * 首页「新碟首发」版块 —— 逐值还原 `NewAlbumSectionKt.m55NewAlbumSectionhGBTI10`。
 *
 * 接口（参数名与顺序）按主控约定固定，未改动。
 */
@Composable
fun NewAlbumSection(
    albums: List<AlbumDisplayItem>,
    onOpenMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // NewAlbumSectionKt.java:458 —— if (list.isEmpty()) return
    if (albums.isEmpty()) return

    // NewAlbumSectionKt.java:469 —— z81.L0(list, 3) = list.take(3)
    val visible = albums.take(3)

    // NewAlbumSectionKt.java:470 ——
    //   Modifier.fillMaxWidth()                        (ij7.g(f05, 1f))
    //     .padding(horizontal = 16.dp)                 (hp1.q0(mod, 16.0f, 0.0f, mask=2))
    //     .glassCard(shape = RoundedCornerShape(20.dp)) (GlassTokensKt.glassCard + by6.a(20.0f))
    //     .padding(14.dp)                              (hp1.o0(mod, 14.0f))
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .glassCard(shape = RoundedCornerShape(20.dp))
            .padding(14.dp),
    ) {
        // NewAlbumSectionKt.java:494/599 —— Row(horizontalArrangement = Arrangement.Start[默认],
        //   verticalAlignment = il0.f0 = Alignment.CenterVertically)（gy6.a(zh.b, il0.f0)）
        Row(verticalAlignment = Alignment.CenterVertically) {
            // NewAlbumSectionKt.java:509 —— "新碟首发"
            //   color = textHigh(), fontSize = 14.sp, fontWeight = FontWeight.Bold(= sq2.p = 700),
            //   lineHeight = 18.sp；默认掩码 260010 未含 bit4/bit6/bit11 → 这三项确实是显式传入。
            Text(
                text = "新碟首发",
                color = textHigh(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 18.sp,
            )
            // NewAlbumSectionKt.java:510 —— Spacer(Modifier.width(8.dp))（ij7.r = width）
            Spacer(Modifier.width(8.dp))
            // NewAlbumSectionKt.java:511 —— "NEW RELEASES"
            //   color = textLow(), fontSize = 8.sp, letterSpacing = 1.sp, lineHeight = 12.sp,
            //   fontWeight = 默认(null)；默认掩码 259818 未含 bit4/bit8/bit11 → 三项均显式传入。
            Text(
                text = "NEW RELEASES",
                color = textLow(),
                fontSize = 8.sp,
                letterSpacing = 1.sp,
                lineHeight = 12.sp,
            )
            // 「更多」入口：**不属于 NewAlbumSectionKt**（GZ 该版块没有更多入口）。
            // 为满足调用方接口 onOpenMore（HomeScreen.kt:319 传入 onOpenSheet）而接入同 APK 的
            // MoreText（HomeScreenKt.java:10741-10782），数值全部取自该处，见下方 MoreText 注释。
            Spacer(Modifier.weight(1f))
            MoreText(onClick = onOpenMore)
        }

        // NewAlbumSectionKt.java:513 —— Spacer(Modifier.height(12.dp))（ij1.x 内联：wl7.d + ij7.i）
        Spacer(Modifier.height(12.dp))

        // TODO(未确定)：NewAlbumSectionKt.java:514-520 用
        //   CompositionLocalProvider(CoverToneKt.c provides (!local || forceShow)) 包裹卡片行；
        //   CoverToneKt.c 是只被 rememberCoverTone 消费的 Boolean CompositionLocal，
        //   本项目没有该 CompositionLocal（也没有 rememberCoverTone）→ 未提供。

        // NewAlbumSectionKt.java:1322 —— Row(horizontalArrangement = Arrangement.spacedBy(10.dp, x),
        //   verticalAlignment = il0.e0 = Alignment.Top[默认])，修饰符 = Modifier.fillMaxWidth()（ij7.g）
        // TODO(未确定)：spacedBy 的第 2 个实参是反编译里丢失的 Alignment 单例 `new c(9)`
        //   （j10.java:83 的 case 9 表示「不额外偏移」= start 语义）；类 `c` 在反编译产物中与
        //   media3 的同名类冲突而丢失，无法写成具名 Compose Alignment。此处省略该实参，
        //   视觉等价（子项从行首排列，且卡片 weight(1f) 已占满整行）。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // NewAlbumSectionKt.java:1338-1348 —— for (album in listL0) NewAlbumCard(album, Modifier.weight(1f))
            visible.forEach { album ->
                NewAlbumCard(
                    album = album,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * 单张新碟卡 —— `NewAlbumSectionKt.NewAlbumCard`（NewAlbumSectionKt.java:238-336）。
 */
@Composable
private fun NewAlbumCard(
    album: AlbumDisplayItem,
    modifier: Modifier = Modifier,
) {
    // NewAlbumSectionKt.java:251-253 —— Column(horizontalAlignment = il0.i0 = Alignment.CenterHorizontally,
    //   verticalArrangement = zh.d = Arrangement.Top[默认])，修饰符 = 调用方传入的 Modifier（weight(1f)）
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // NewAlbumSectionKt.java:278-279 ——
        //   Modifier.widthIn(max = 160.dp)（ij7.t(mod, 0.0f, 160.0f, mask=1)：只给 maxWidth）
        //     .fillMaxWidth()（ij7.g）
        //     .bounceClick(pressedScale = 0.98f, onClick)（MotionKt.c(..., false, 0.98f, false, null, onClick, 13)）
        //   Column(horizontalAlignment = il0.h0 = Alignment.Start[默认])
        Column(
            modifier = Modifier
                .widthIn(max = 160.dp)
                .fillMaxWidth()
                .bounceClick(pressedScale = 0.98f, onClick = album.onClick),
            horizontalAlignment = Alignment.Start,
        ) {
            // NewAlbumSectionKt.java:293 —— AlbumRecord(album, Modifier.fillMaxWidth())
            AlbumRecord(
                album = album,
                modifier = Modifier.fillMaxWidth(),
            )

            // NewAlbumSectionKt.java:294 —— Spacer(Modifier.height(8.dp))
            Spacer(Modifier.height(8.dp))

            // NewAlbumSectionKt.java:296 —— 专辑名：color = textHigh(), fontSize = 13.sp,
            //   fontWeight = FontWeight.Bold(sq2.p), lineHeight = 18.sp, maxLines = 1
            //   （默认掩码 239530 未含 bit11/bit14 → lineHeight、maxLines 显式传入；
            //    overflow 走默认 Clip，**不是** Ellipsis）
            Text(
                text = album.title,
                color = textHigh(),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 18.sp,
                maxLines = 1,
            )

            // NewAlbumSectionKt.java:297 —— Spacer(Modifier.height(2.dp))
            Spacer(Modifier.height(2.dp))

            // NewAlbumSectionKt.java:298-302 —— 歌手：空串回退 "未知歌手"，color = textLow(),
            //   fontSize = 10.sp, lineHeight = 14.sp, maxLines = 1, fontWeight 默认(null)
            Text(
                text = album.artist.ifBlank { "未知歌手" },
                color = textLow(),
                fontSize = 10.sp,
                lineHeight = 14.sp,
                maxLines = 1,
            )

            // NewAlbumSectionKt.java:304-309 ——
            //   publishTime 长度 ≥ 10 时取 substring(5, 10) 并把 '-' 换成 '.'
            //   （"2024-05-12" → "05.12"），与 areaName 组成 [areaName, 日期]
            val publishDate = album.publishTime.let { raw ->
                if (raw.length >= 10) raw.substring(5, 10).replace('-', '.') else raw
            }
            // NewAlbumSectionKt.java:309-316 —— 过滤空串后用 " · " 连接（z81.v0(list, " · ", ...)）
            val meta = listOf(album.areaName, publishDate)
                .filter { it.isNotEmpty() }
                .joinToString(" · ")

            // NewAlbumSectionKt.java:317-319 —— if (strV0.length() > 0) { Spacer(3.dp); Text(...) }
            if (meta.isNotEmpty()) {
                // NewAlbumSectionKt.java:318 —— Spacer(Modifier.height(3.dp))（ij1.w 内联）
                Spacer(Modifier.height(3.dp))
                // NewAlbumSectionKt.java:319 —— color = textLow(), fontSize = 9.sp,
                //   letterSpacing = 0.3.sp（co7.i(0.3d) = 0.3.sp；co7.java:1097/1101 pack 类型 1<<32 = Sp）,
                //   lineHeight = 12.sp, maxLines = 1（默认掩码 239338 未含 bit8/bit11/bit14）
                Text(
                    text = meta,
                    color = textLow(),
                    fontSize = 9.sp,
                    letterSpacing = 0.3.sp,
                    lineHeight = 12.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 唱片 + 封套组合容器 —— `NewAlbumSectionKt.AlbumRecord`（NewAlbumSectionKt.java:83-148）。
 *
 * 结构：`BoxWithConstraints(Modifier.fillMaxWidth())`（jv4.d，内容对齐参数为 null → Alignment.TopStart 默认）
 *   - 取 scope.maxWidth（rj0.java:27-33 的 `c()` = maxWidth，注意 rj0.b() 是 maxHeight）
 *   - 内部 Box：`fillMaxWidth().height(maxWidth / 1.216f)`，contentAlignment = il0.f = Alignment.TopStart
 *       · AlbumVinyl：`size(0.9f * fC).align(Alignment.CenterEnd).shadow(3.dp, CircleShape)`
 *       · AlbumSleeve：`size(fC)`（后绘制 → 盖住唱片左侧，只露出一小截黑胶）
 */
@Composable
private fun AlbumRecord(
    album: AlbumDisplayItem,
    modifier: Modifier = Modifier,
) {
    // GZ：CoverToneKt.m299rememberCoverToneiJQMabo(album.coverUrl, 0L, ...)（NewAlbumSectionKt.java:98），
    // 逐封面提取主色。已接入真值提取器 rememberCoverTone（ui/components/CoverTone.kt）。
    val tone = rememberCoverTone(album.coverUrl)

    BoxWithConstraints(modifier = modifier) {
        // NewAlbumSectionKt.java:120 —— fC = maxWidth / 1.216f
        val sleeveSize = maxWidth / 1.216f

        // NewAlbumSectionKt.java:124 —— Box(contentAlignment = il0.f = Alignment.TopStart)
        Box(
            // NewAlbumSectionKt.java:123 —— Modifier.fillMaxWidth().height(fC)
            modifier = Modifier
                .fillMaxWidth()
                .height(sleeveSize),
            contentAlignment = Alignment.TopStart,
        ) {
            // NewAlbumSectionKt.java:141 —— AlbumVinyl(tone, Modifier.size(0.9f * fC)
            //   .align(il0.p = Alignment.CenterEnd).shadow(3.dp, CircleShape))。
            //   注：shadow 的 clip 参数取默认值（= elevation > 0.dp → true），因此唱片内容被裁成圆形。
            AlbumVinyl(
                tone = tone,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(sleeveSize * 0.9f)
                    .shadow(elevation = 3.dp, shape = CircleShape),
            )

            // NewAlbumSectionKt.java:142 —— AlbumSleeve(album, Modifier.size(fC))
            AlbumSleeve(
                album = album,
                modifier = Modifier.size(sleeveSize),
            )
        }
    }
}

/**
 * 专辑封套 —— `NewAlbumSectionKt.AlbumSleeve`（NewAlbumSectionKt.java:150-206）。
 *
 * `Modifier.size(fC)` 上依次：`shadow(4.dp, RoundedCornerShape(3.dp))`
 *   → `background(coverPlaceholder(), shape)` → `clip(shape)`；
 * 内部：封面为空时只留占位底色，否则 `AsyncImage`（请求尺寸 200×200，ContentScale.Crop），
 * 最上层再叠一个 `matchParentSize().border(1.dp, coverHairline(), shape)` 描边。
 */
@Composable
private fun AlbumSleeve(
    album: AlbumDisplayItem,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // NewAlbumSectionKt.java:164 —— ay6 = by6.a(3.0f) = RoundedCornerShape(3.dp)
    val shape = RoundedCornerShape(3.dp)

    // NewAlbumSectionKt.java:166 ——
    //   hq3.g(hp1.s(mg2.I(mod, 4.0f, shape, ...), coverPlaceholder(), shape), shape)
    //   = Modifier.shadow(4.dp, shape).background(coverPlaceholder(), shape).clip(shape)
    Box(
        modifier = modifier
            .shadow(elevation = 4.dp, shape = shape)
            .background(coverPlaceholder(), shape)
            .clip(shape),
    ) {
        // NewAlbumSectionKt.java:184-194 —— 封面为空（dw7.S = isBlank）时什么都不画（只有占位底色）
        if (!album.coverUrl.isNullOrBlank()) {
            // NewAlbumSectionKt.java:193 —— tf2.f(qk5.N(context, url, 200), album.b, Modifier.matchParentSize(), qh1.a)
            //   · qk5.java:1104-1112：ImageRequest.size(200, 200)（入参 < 64 时抬到 64）
            //   · qh1.a = new pr6(23)；pr6.java:882-884 的 case 23 = max(dstW/srcW, dstH/srcH) = ContentScale.Crop
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(album.coverUrl)
                    .size(200, 200)
                    .build(),
                contentDescription = album.title,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        }
        // NewAlbumSectionKt.java:196 —— Box(Modifier.matchParentSize().border(1.dp, coverHairline(), shape))
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(width = 1.dp, color = coverHairline(), shape = shape),
        )
    }
}

/**
 * 黑胶唱片绘制 —— `NewAlbumSectionKt.AlbumVinyl`（NewAlbumSectionKt.java:209-236，Canvas = er5.c）
 * 及绘制内容 `li.java:302-339`（`li(long tone, 13)` 的 case 13，接收 g72 = DrawScope）。
 *
 * 绘制顺序（全部字面量见下方逐行注释）：
 *  1. 整张唱片底：`Brush.sweepGradient`（角向渐变），7 个色标
 *  2. 24 道同心音轨（白色描边圆，每 3 道加深一档）
 *  3. 外圈边线（半径 0.985R）
 *  4. 内圈标签底色（半径 0.29R，填充）
 *  5. 内圈标签描边（半径 0.23R）
 *  6. 中心主轴孔（半径 0.055R，填充 0xFF18181E）
 *  7. 主轴孔白色细环（半径 0.055R）
 *
 * R = size.minDimension / 2（li.java:305：cj7.c(scope.size) 取宽高较小值再除以 2）。
 * 颜色混合：`hp1.h0(c, f, other)`（hp1.java:3275-3293）= `lerp(c, other, f)`。
 */
@Composable
private fun AlbumVinyl(
    tone: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        // li.java:305 —— float fC = minDimension / 2.0f
        val radius = size.minDimension / 2f

        // li.java:306-310 ——
        //   j2 = g91.b = Black；j3 = tone；g91.d = White
        //   jH0 = lerp(tone, Black, 0.22f)；jH1 = lerp(tone, Black, 0.40f)；jH2 = lerp(tone, White, 0.38f)
        val toneBlack22 = lerp(tone, Color.Black, 0.22f)
        val toneBlack40 = lerp(tone, Color.Black, 0.40f)
        val toneWhite38 = lerp(tone, Color.White, 0.38f)

        // li.java:311-320 —— 7 个色标 (0.0/0.14/0.25/0.52/0.65/0.77/1.0) 的 sweepGradient，
        //   中心取 j18 的 Offset.Unspecified（li.java:320 的 9205357640488583168L）；
        //   随即 g72.F0(brush, ...) = DrawScope.drawRect(brush) 铺满整张唱片。
        drawRect(
            brush = Brush.sweepGradient(
                0.00f to toneBlack22,
                0.14f to toneWhite38,
                0.25f to toneBlack22,
                0.52f to toneBlack40,
                0.65f to toneWhite38,
                0.77f to toneBlack22,
                1.00f to toneBlack22,
            ),
        )

        // li.java:322-328 —— for (i5 in 0 until 24)：
        //   颜色 = White.copy(alpha = if (i % 3 == 0) 0.15f else 0.07f)
        //   半径 = ij1.A(i, 0.027f, 0.34f, R) = (i * 0.027f + 0.34f) * R
        //   描边宽 = R * 0.007f（lw7 = Stroke）
        for (i in 0 until 24) {
            drawCircle(
                color = Color.White.copy(alpha = if (i % 3 == 0) 0.15f else 0.07f),
                radius = (i * 0.027f + 0.34f) * radius,
                style = Stroke(width = radius * 0.007f),
            )
        }

        // li.java:331-332 —— 外圈：色 jH2，半径 R * 0.985f，描边宽 R * 0.01f
        drawCircle(
            color = toneWhite38,
            radius = radius * 0.985f,
            style = Stroke(width = radius * 0.01f),
        )

        // li.java:334 —— 标签底色：lerp(tone, White, 0.28f)，半径 R * 0.29f（填充）
        drawCircle(
            color = lerp(tone, Color.White, 0.28f),
            radius = radius * 0.29f,
        )

        // li.java:335 —— 标签描边：纯 tone，半径 R * 0.23f，描边宽 R * 0.014f
        drawCircle(
            color = tone,
            radius = radius * 0.23f,
            style = Stroke(width = radius * 0.014f),
        )

        // li.java:336-337 —— 主轴孔半径 f3 = R * 0.055f；填充色 hp1.e(4279769118L) = Color(0xFF18181E)
        val holeRadius = radius * 0.055f
        drawCircle(
            color = Color(0xFF18181E),
            radius = holeRadius,
        )

        // li.java:338 —— White.copy(alpha = 0.5f)，半径同 f3，描边宽 R * 0.01f
        drawCircle(
            color = Color.White.copy(alpha = 0.5f),
            radius = holeRadius,
            style = Stroke(width = radius * 0.01f),
        )
    }
}

/**
 * 「更多」入口 —— **不是 NewAlbumSectionKt 的内容**。
 *
 * GZ 的新碟首发版块没有更多入口；此处照搬同 APK 的 `HomeScreenKt.MoreText`
 * （HomeScreenKt.java:10741-10782）以接入调用方的 `onOpenMore`：
 *  - Row(verticalAlignment = il0.f0 = Alignment.CenterVertically，horizontalArrangement 默认 Start)
 *  - Text("更多", color = textMedium(), fontSize = 12.sp, fontWeight = sq2.j = FontWeight(500))
 *    —— HomeScreenKt.java:10771
 *  - Icon(zh.v() = "Rounded.ChevronRight"（zh.java:2219），contentDescription = null,
 *    modifier = Modifier.size(18.dp), tint = textMedium()) —— HomeScreenKt.java:10773
 *  - 整体包 bounceClick(pressedScale = 0.96f) —— HomeScreenKt.java:10753
 */
@Composable
private fun MoreText(onClick: () -> Unit) {
    Row(
        modifier = Modifier.bounceClick(pressedScale = 0.96f, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "更多",
            color = textMedium(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = textMedium(),
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * GZ `MotionKt.bounceClick`（MotionKt.java:74-99）的等价实现：
 *  - 按下时缩放到 pressedScale，松开回到 1.0f（MotionKt.java:62 的 targetValue 判定）
 *  - 动画 `ct.a = tween(durationMillis = 150, easing = MotionKt.a)`
 *    （ct.java:8 `qk5.r0(150, 2, MotionKt.a)`；MotionKt.a = bs4.a = CubicBezierEasing(0.2f, 0f, 0f, 1f)，bs4.java:6）
 *  - 用 `graphicsLayer { scaleX/scaleY }` 承载缩放（MotionKt.java:69 的 jv4.x）
 *  - clickable 的 indication 传 null（MotionKt.java:69 第 3 个实参），即无涟漪
 */
@Composable
private fun Modifier.bounceClick(
    pressedScale: Float,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1.0f,
        animationSpec = tween(
            durationMillis = 150,
            easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f),
        ),
        label = "pressScale", // MotionKt.java:62 的 label 字面量
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
}

/*
 * TODO(未确定) 清单
 * 1. 唱片主色：GZ `rememberCoverTone(url, 默认 = CoverToneKt.a)`（CoverToneKt.java:68-106，
 *    结合 CoverAmbient 缓存 + 500ms animateColorAsState），本项目无等价实现 →
 *    统一使用其默认色 Color(0xFF8E8E93)（CoverToneKt.java:28）。
 * 2. 卡片行外层的 `CompositionLocalProvider(CoverToneKt.c provides ...)`（NewAlbumSectionKt.java:514-520）
 *    未实现：项目没有该 Boolean CompositionLocal，且它只被 rememberCoverTone 消费。
 * 3. `Arrangement.spacedBy(10.dp, <Alignment>)` 的第 2 个实参是反编译丢失的 `new c(9)`
 *    （NewAlbumSectionKt.java:1322）；类 `c` 与 media3 的同名类冲突，无法确定具名 Compose Alignment
 *    （j10.java:83 的 case 9 = 不额外偏移 = start 语义）→ 省略该实参，视觉等价。
 * 4. 「更多」入口不属于本版块：为满足 onOpenMore 接口，照搬 HomeScreenKt.MoreText 的实现与数值
 *    （HomeScreenKt.java:10741-10782）；头部用它需要的 `Spacer(Modifier.weight(1f))` 顶到行尾，
 *    该 weight 为结构性布局参数，GZ 无对应写法。
 * 5. 版块入参 `horizontalPadding: Dp = 16.dp` / `forceShow: Boolean = false`
 *    （NewAlbumSectionKt.java:446-457 的默认实参）未暴露：接口已由主控固定为 3 个参数。
 */
