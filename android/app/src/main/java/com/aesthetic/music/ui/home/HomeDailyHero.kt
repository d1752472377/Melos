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
import com.aesthetic.music.ui.theme.AppSpacing

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
    val heroLoading = isLoadingSongs && pages.isEmpty()

    val pagerState = rememberPagerState(
        initialPage = 0,
        initialPageOffsetFraction = 0f,
    ) { pages.size }

    val currentPlaylist = pages.getOrNull(pagerState.currentPage)

    val cardTone = rememberCoverTone(currentPlaylist?.coverUrl, tone)

    Column(modifier = modifier.fillMaxWidth()) {

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.xxl)
        ) {
            val cardHeight = if (heroLoading) {
                maxOf(150.dp, heroHeight)
            } else {
                maxWidth * 9f / 16f
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight)
                    .glassToneCard(
                        shape = RoundedCornerShape(HeroCardCorner),
                        tone = cardTone,
                        selected = false,
                        gradient = true,
                    )
                    //   = 卡片点击 + 按下缩放 0.985f（Melos 无 bounceClick → 用 clickable，见 TODO ④）
                    .clickable(
                        enabled = currentPlaylist != null &&
                            !heroLoading &&
                            !pagerState.isScrollInProgress,
                    ) { currentPlaylist?.let(onOpenPlaylist) }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(HeroCardCorner)),
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1,
                        pageSpacing = 0.dp,
                        userScrollEnabled = pagerState.pageCount > 1,
                    ) { page ->
                        val item = pages.getOrNull(page)
                        //   版式数值出处见 [HomeHeroPage] 上方的测量表与 TODO ⑫。
                        HomeHeroPage(
                            item = item,
                            hasCustomCover = hasCustomCover,
                            cardHeight = cardHeight,
                            contentDescription = title.ifBlank { null },
                        )
                    }
                    HomeHeroIndicator(
                        currentPage = pagerState.currentPage,
                        pageCount = pagerState.pageCount,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = HeroIndicatorBottomPadding),
                    )
                }
            }
        }

    }
}

@Composable
private fun HomeHeroCover(
    url: String?,
    hasCustomCover: Boolean,
    contentDescription: String?,
) {
    if (url.isNullOrBlank()) {
        // 参数顺序无法 100% 确定 → 见 TODO ②；此处保留卡片底色 coverPlaceholder()（3388 的真值）。
        return
    }
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            // 不是硬性解码约束。Coil 2 的 `.size(n)` 会强制按 n×n 方形解码，
            .crossfade(200)
            .build(),
        contentDescription = contentDescription,
        // TODO(未确定) ①：GZ 的 tf2.e 未显式传 contentScale（tf2.java:503-504 掩码默认 → null，
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val scale = if (hasCustomCover) 1.0f else 1.16f
                scaleX = scale
                scaleY = scale
            }
            .graphicsLayer {
                if (!hasCustomCover) {
                    renderEffect = BlurEffect(26.dp.toPx(), 26.dp.toPx(), TileMode.Clamp)
                    shape = RectangleShape
                    clip = true
                }
            }
    )
}

private val HeroCardCorner = 20.dp
private val HeroCoverCorner = 6.dp
private val HeroPanelCorner = 6.dp
private val HeroRowStartPadding = 24.dp
private val HeroRowEndPadding = 21.dp
private val HeroCoverPanelGap = 15.dp
private val HeroPanelPadding = 12.dp
private const val HeroCoverHeightRatio = 0.59f
private const val HeroPanelHeightRatio = 0.63f
private const val HeroRowTopRatio = 0.21f
private val HeroTitleFontSize = 20.sp
private val HeroTinyFontSize = 9.sp
private val HeroTinyLineHeight = 12.sp

@Composable
private fun HomeHeroPage(
    item: PlaylistItem?,
    hasCustomCover: Boolean,
    cardHeight: Dp,
    contentDescription: String?,
) {
    val coverSize = cardHeight * HeroCoverHeightRatio
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = HeroRowStartPadding, end = HeroRowEndPadding)
            .padding(top = cardHeight * HeroRowTopRatio),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(coverSize)
                .clip(RoundedCornerShape(HeroCoverCorner))
                .background(coverPlaceholder()),
        ) {
            HomeHeroCover(
                url = item?.coverUrl,
                hasCustomCover = hasCustomCover,
                contentDescription = contentDescription,
            )
        }
        Spacer(modifier = Modifier.width(HeroCoverPanelGap))
        HomeHeroInfoPanel(
            item = item,
            modifier = Modifier
                .weight(1f)
                .height(cardHeight * HeroPanelHeightRatio),
        )
    }
}

@Composable
private fun HomeHeroInfoPanel(
    item: PlaylistItem?,
    modifier: Modifier = Modifier,
) {
    val titleColor = textHigh()
    val tinyColor = textMedium()

    // 元信息：TODO(未确定) ⑫ —— GZ 2.1.8 的这张卡没有这个文本节点，参考图上的字太小无法辨认，
    // 若更新版 GZ 实际显示的是别的字段（tag / playCount / 场景名…），改绑定即可，不要改字号。
    val metaText = item?.description?.takeIf { it.isNotBlank() }.orEmpty()
    val trackCountText = item?.trackCount?.let { "$it 首" }.orEmpty()

    Column(
        modifier = modifier
            .glassCard(shape = RoundedCornerShape(HeroPanelCorner), elevation = 0.dp)
            .padding(horizontal = HeroPanelPadding, vertical = HeroPanelPadding),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = metaText,
            color = tinyColor,
            fontSize = HeroTinyFontSize,
            lineHeight = HeroTinyLineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item?.name.orEmpty(),
            color = titleColor,
            fontSize = HeroTitleFontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            // 18sp 档传 0L（TabletHomeScreenKt.java:623）；参考图无法分辨 → 取 0（TODO ⑫）。
        )
        Text(
            text = trackCountText,
            color = tinyColor,
            fontSize = HeroTinyFontSize,
            lineHeight = HeroTinyLineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val HeroIndicatorBottomPadding = 12.dp
private val HeroDotHeight = 6.dp
private val HeroDotSelectedWidth = 14.dp
private val HeroDotSpacing = 4.dp

@Composable
private fun HomeHeroIndicator(
    currentPage: Int,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    if (pageCount <= 1) return

    val dots: List<State<Float>> = List(pageCount) { index ->
        animateFloatAsState(
            targetValue = if (index == currentPage) 1f else 0f,
            animationSpec = tween(
                durationMillis = 200,
                delayMillis = 0,
                easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
            ),
            label = "heroDot$index",
        )
    }

    val dotSpacing = HeroDotSpacing
    Canvas(
        modifier = modifier.size(
            width = HeroDotSpacing * (pageCount - 1) + HeroDotSelectedWidth * pageCount,
            height = HeroDotHeight,
        ),
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val spacingPx = dotSpacing.toPx()
        val minWidthPx = HeroDotHeight.toPx()
        val maxWidthPx = HeroDotSelectedWidth.toPx()
        val radius = canvasHeight / 2f
        var right = canvasWidth

        for (index in pageCount - 1 downTo 0) {
            val t = dots[index].value
            val dotWidth = minWidthPx + (maxWidthPx - minWidthPx) * t
            drawRoundRect(
                color = Color.White.copy(alpha = 0.42f + 0.53f * t),
                topLeft = Offset(right - dotWidth, 0f),
                size = Size(dotWidth, canvasHeight),
                cornerRadius = CornerRadius(radius, radius),
            )
            right -= dotWidth + spacingPx
        }
    }
}

