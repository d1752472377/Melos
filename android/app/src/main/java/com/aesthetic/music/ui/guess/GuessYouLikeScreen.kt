package com.aesthetic.music.ui.guess

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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.BottomContentSpacer
import com.aesthetic.music.ui.components.DefaultCoverTone
import com.aesthetic.music.ui.components.PlayingBars
import com.aesthetic.music.ui.components.coverHairline
import com.aesthetic.music.ui.components.coverPlaceholder
import com.aesthetic.music.ui.components.glassToneCard
import com.aesthetic.music.ui.components.rememberCoverTone
import com.aesthetic.music.ui.components.textHigh
import com.aesthetic.music.ui.components.textLow
import com.aesthetic.music.ui.components.textMedium
import com.aesthetic.music.ui.home.HomeAmbientGlowBackground
import com.aesthetic.music.ui.theme.appIsDark

/**
 * 「猜你喜欢」页 —— 逐值还原 GZ Music 2.1.8
 * `com/gzmusic/app/feature/guess/GuessYouLikeScreenKt.java`（2696 行反编译源）。
 *
 * ## 页面结构（与截图一致，行号指 `GuessYouLikeScreenKt.java`）
 * ```
 * Box
 *  ├─ HomeAmbientGlowBackground()                     // 全 App 同款极光背景
 *  ├─ GuessTopBar                                     // :1501  返回 + 大标题 + 副标题 + 换一批
 *  └─ 内容（按状态四选一）
 *      ├─ Loading                                     // :1664 前的 loading 分支
 *      ├─ GuessEmpty                                  // :512
 *      ├─ GuessError                                  // :562   文案 + 重试胶囊
 *      └─ LazyColumn（spacedBy 10dp / 横 16dp）
 *           ├─ item "hero"          GuessHero          // :611
 *           ├─ item "taste_profile" GuessTasteProfile  // :1455
 *           ├─ item "personal_section" GuessPersonalSectionHeader // :968
 *           └─ items(songs)         GuessSongRow       // :1211
 * ```
 *
 * ## 三个必须照抄的「语义」细节
 * 1. **Hero 的配色不是主题色，是封面主色**（`j` 参数）：
 *    由 `rememberCoverTone(第一首封面)` 得到，再经 `DiscoveryStyleKt.discoveryAccent`
 *    （`DiscoveryStyleKt.java:12-19`：深色 `lerp(tone, White, 0.48f)`、浅色 `lerp(tone, Black, 0.28f)`）
 *    变成文字强调色。所以换一批歌，整张卡的主色会跟着换——这是「每日私人推荐」的视觉语言。
 * 2. **「18 首精选歌曲」的 18 是真实结果条数**，不是常量：GZ 传的是 `list.size`（`:763`）。
 * 3. **「来自收藏与最近播放的 N 条线索」的 N 是种子数**（`:783` 的 `i2`），
 *    为 0 时换成「越常听，越懂你」。同理 `personal_section` 的副标题在无种子时换成
 *    「继续听歌，推荐会越来越懂你」（`:1042`）。
 *
 * ## 与 GZ 的三处刻意差异（都不影响观感，但避免「点了没反应」）
 * 1. GZ 的 `GuessRefreshButton`（`:1068`）在刷新中把图标换成
 *    `CircularProgressIndicator(strokeWidth = 2.2.dp)`；这里保留同款，但额外把按钮禁用，
 *    避免连点导致两批结果互相覆盖。
 * 2. GZ 的「保存为歌单」成功后没有任何反馈；这里走全 App 统一的 toast 提示。
 * 3. GZ 的 `GuessLoadingArtwork`（`GuessLoadingArtworkKt.java`，一段 22KB 的加载动画）
 *    没有复刻：本项目所有页面统一用 `CircularProgressIndicator` 加载态，
 *    为这一个页面单独搬一套动画只会让「加载中」在不同页面长得不一样。
 *
 * @param state         页面状态（Loading / Empty / Error / Ready），见 [GuessController.State]
 * @param refreshing    是否正在换一批（刷新按钮转圈）
 * @param isPlaying     全局播放中
 * @param currentSongId 当前播放歌曲的 `platform:id`，用于行内高亮与音波
 * @param favoriteKeys  已收藏的 `platform:id` 集合
 */
@Composable
fun GuessYouLikeScreen(
    state: GuessController.State,
    refreshing: Boolean,
    isPlaying: Boolean,
    currentSongId: String?,
    favoriteKeys: Set<String>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onSavePlaylist: (List<Song>) -> Unit,
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier = Modifier.fillMaxSize()) {
        HomeAmbientGlowBackground()

        Column(modifier = Modifier.fillMaxSize()) {
            GuessTopBar(
                refreshing = refreshing,
                topPadding = statusBarTop,
                onBack = onBack,
                onRefresh = onRefresh,
            )

            when (state) {
                is GuessController.State.Loading -> GuessLoading()
                is GuessController.State.Empty -> GuessEmpty(
                    title = state.title,
                    subtitle = state.subtitle,
                )

                is GuessController.State.Error -> GuessError(
                    message = state.message,
                    onRetry = onRetry,
                )

                is GuessController.State.Ready -> GuessContent(
                    state = state,
                    refreshing = refreshing,
                    isPlaying = isPlaying,
                    currentSongId = currentSongId,
                    favoriteKeys = favoriteKeys,
                    onRefresh = onRefresh,
                    onPlayAll = onPlayAll,
                    onShuffle = onShuffle,
                    onPlaySong = onPlaySong,
                    onToggleFavorite = onToggleFavorite,
                    onSavePlaylist = onSavePlaylist,
                )
            }
        }
    }
}

// ============================================================
// 顶栏（GZ GuessTopBar，:1501-1663）
// ============================================================

/**
 * GZ 的大标题形态（`:1523-1571`）：
 *  - 容器 `fillMaxWidth().height(60.dp)` + `padding(start = 24, top = 4, end = 24, bottom = 12)`
 *    （`:1527` 的 `hp1.r0(ij7.j(ij7.g(...), 60.0f, 0.0f, 2), 24.0f, 4.0f, 24.0f, 12.0f)`）；
 *  - 标题「猜你喜欢」**32.sp / W800 / letterSpacing -0.8.sp / textHigh**（`:1566`）；
 *  - 副标题「你的每日私人推荐」**11.sp / W500 / letterSpacing 0.2.sp / textLow**（`:1567`）；
 *  - 右侧 `GuessRefreshButton`（`:1068`）：**40.dp 圆**、按压缩放 **0.9**、图标 **22.dp**、
 *    `contentDescription = "换一批"`（`:1138`），刷新中换成 **20.dp 圆环 + 2.2.dp 描边**（`:1132`）。
 *
 * 反编译的两种顶栏里，`z2 = false` 那一支走 `PhoneTopBar`（带返回箭头）。真机截图上是
 * 「← 猜你喜欢」，因此这里把返回箭头显式画在大标题左侧，其余数值沿用大标题形态。
 */
@Composable
private fun GuessTopBar(
    refreshing: Boolean,
    topPadding: androidx.compose.ui.unit.Dp,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = topPadding + 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回",
            tint = textHigh(),
            modifier = Modifier
                .clip(CircleShape)
                .pressClick(pressedScale = 0.9f, onClick = onBack)
                .padding(4.dp)
                .size(24.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "猜你喜欢", // :1566
                color = textHigh(),
                fontSize = 28.sp,
                fontWeight = FontWeight.W800,
                letterSpacing = (-0.8).sp,
                lineHeight = 34.sp,
            )
            Text(
                text = "你的每日私人推荐", // :1567
                color = textLow(),
                fontSize = 11.sp,
                fontWeight = FontWeight.W500,
                letterSpacing = 0.2.sp,
            )
        }
        GuessRefreshButton(refreshing = refreshing, onRefresh = onRefresh)
    }
}

/** GZ `GuessRefreshButton`（:1068-1207）：40.dp 圆 + 22.dp 图标；刷新中 20.dp 圆环（2.2.dp） */
@Composable
private fun GuessRefreshButton(refreshing: Boolean, onRefresh: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp) // :1110 ij7.n(f05Var3, 40.0f)
            .clip(CircleShape)
            .then(
                if (refreshing) {
                    Modifier // :1110 MotionKt.c(..., enabled = !z, ...) → 刷新中不可点
                } else {
                    Modifier.pressClick(pressedScale = 0.9f, onClick = onRefresh)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (refreshing) {
            // :1132 sh6.a(size 20.dp, color textMedium, strokeWidth 2.2f)
            CircularProgressIndicator(
                color = textMedium(),
                strokeWidth = 2.2.dp,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Refresh, // bd2.C()
                contentDescription = "换一批", // :1138
                tint = textMedium(),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// ============================================================
// 内容（Hero + 线索条 + 本次精选 + 歌曲行）
// ============================================================

@Composable
private fun GuessContent(
    state: GuessController.State.Ready,
    refreshing: Boolean,
    isPlaying: Boolean,
    currentSongId: String?,
    favoriteKeys: Set<String>,
    onRefresh: () -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onSavePlaylist: (List<Song>) -> Unit,
) {
    val songs = state.songs
    val listState = rememberLazyListState()

    // GZ 传给 Hero / 歌曲行的是同一个「封面主色」（:1664 的 j），这里同样只算一次
    val tone = rememberCoverTone(songs.firstOrNull()?.artwork, DefaultCoverTone)
    val accent = discoveryAccent(tone)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, // :2445 po5(16, 10, 16, bottomOverlay)
            end = 16.dp,
            top = 10.dp,
            bottom = BottomContentSpacer,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "hero") {
            GuessHero(
                songs = songs,
                seedCount = state.seedCount,
                tone = tone,
                accent = accent,
                refreshing = refreshing,
                onPlayAll = { onPlayAll(songs) },
                onShuffle = { onShuffle(songs) },
                onSavePlaylist = { onSavePlaylist(songs) },
            )
        }

        item(key = "taste_profile") {
            GuessTasteProfile(seedCount = state.seedCount, accent = accent)
        }

        item(key = "personal_section") {
            GuessPersonalSectionHeader(
                count = songs.size,
                seedCount = state.seedCount,
                accent = accent,
            )
        }

        itemsIndexed(songs, key = { _, s -> "guess:${s.platform}:${s.id}" }) { index, song ->
            val key = "${song.platform}:${song.id}"
            GuessSongRow(
                song = song,
                index = index,
                accent = accent,
                isCurrent = currentSongId != null && currentSongId == key,
                isPlaying = isPlaying,
                isFavorite = key in favoriteKeys,
                onPlay = { onPlaySong(song, songs) },
                onToggleFavorite = { onToggleFavorite(song) },
            )
        }
    }
}

/**
 * Hero 卡 —— GZ `GuessHero-Pd0R-II`（:611-851）。
 *
 * 表面（`:645`）：`glassToneCard(widthIn(max = 720.dp).fillMaxWidth(), RoundedCornerShape(20.dp),
 * tone = 封面主色, selected = true, gradient = false)` + `padding(20.dp)`；
 * 内部 `Column(verticalArrangement = spacedBy(20.dp), horizontalAlignment = Start)`。
 */
@Composable
private fun GuessHero(
    songs: List<Song>,
    seedCount: Int,
    tone: Color,
    accent: Color,
    refreshing: Boolean,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onSavePlaylist: () -> Unit,
) {
    Column(
        modifier = Modifier
            .widthIn(max = 720.dp) // :645 ij7.t(c05, 0.0f, 720.0f, 1)
            .fillMaxWidth()
            .glassToneCard(
                shape = RoundedCornerShape(20.dp), // by6.a(20.0f)
                tone = tone,
                selected = true, // :645 第 4 个实参 true
                gradient = false, // :645 第 5 个实参 false
            )
            .padding(20.dp), // :645 hp1.o0(..., 20.0f)
        verticalArrangement = Arrangement.spacedBy(20.dp), // :646 j10(20.0f, ...)
    ) {
        // ① 「为你精选」+「PRIVATE MIX」（:690 / :693）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "为你精选",
                color = accent, // :690 DiscoveryStyleKt.discoveryAccent
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold, // :689 sq2.p
            )
            Spacer(Modifier.weight(1f)) // :692 ky6.b(1.0f, ...)
            Text(
                text = "PRIVATE MIX",
                color = textMedium(),
                fontSize = 10.sp,
                letterSpacing = 2.sp, // :693 co7.j(2)
            )
        }

        // ② 大标题 + 副文案 + 封面叠层（:725-732）
        // 窄屏（< 320.dp 可用宽度）把封面叠层等比缩到 0.82：
        // 「也许就心动。」6 个字在 26.sp 下约 156.dp，叠层占满 132.dp 时窄屏会被挤成三行。
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val deckScale = if (maxWidth < 320.dp) 0.82f else 1f
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "下一首，\n也许就心动。", // :728
                        color = textHigh(),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.W800, // sq2.X
                        letterSpacing = (-0.8).sp, // :728 co7.i(-0.8d)
                        lineHeight = 33.sp, // :728 co7.j(33)
                    )
                    Spacer(Modifier.height(12.dp)) // :729
                    Text(
                        text = "沿着你的听歌足迹\n遇见新的喜欢", // :730
                        color = textMedium(),
                        fontSize = 12.sp,
                        lineHeight = 19.sp, // :730 co7.j(19)
                    )
                }
                Spacer(Modifier.width(8.dp)) // :731 f30.t(..., 8.0f)
                GuessCoverDeck(
                    covers = songs.take(3).map { it.artwork },
                    tone = tone,
                    scale = deckScale,
                )
            }
        }

        // ③ 分隔线（:734 zh.c(0.0f, textMedium().copy(alpha = 0.08f), ...)）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(textMedium().copy(alpha = 0.08f)),
        )

        // ④ 数量 + 线索（:735-784）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp), // :735 j10(20.0f, ...)
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(
                    // :763 dw7.Y(2, String.valueOf(i)) = 两位左补零
                    text = songs.size.toString().padStart(2, '0'),
                    color = textHigh(),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.W800,
                    letterSpacing = (-1).sp, // :763 co7.j(-1)
                )
                Text(
                    text = "首精选歌曲", // :764
                    color = textMedium(),
                    fontSize = 11.sp,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "由你的热爱出发", // :781
                    color = textHigh(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W800,
                )
                Spacer(Modifier.height(4.dp)) // :782
                Text(
                    // :783 —— 有线索时「来自收藏与最近播放的 N 条线索」，否则「越常听，越懂你」
                    text = if (seedCount > 0) {
                        "来自收藏与最近播放的 $seedCount 条线索"
                    } else {
                        "越常听，越懂你"
                    },
                    color = textMedium(),
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                )
            }
        }

        // ⑤ 播放全部 / 随机播放 / 保存为歌单（:786-835）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp), // :787 j10(10.0f, ...)
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GuessPlayAllButton(
                tone = tone,
                enabled = songs.isNotEmpty(),
                onClick = onPlayAll,
                modifier = Modifier.weight(1f),
            )
            GuessHeroRoundButton(
                icon = { tint ->
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "随机播放", // :827
                        tint = tint,
                        modifier = Modifier.size(22.dp),
                    )
                },
                label = "随机播放",
                enabled = songs.isNotEmpty(),
                tone = tone,
                onClick = onShuffle,
            )
            GuessHeroRoundButton(
                icon = { tint ->
                    if (refreshing) {
                        CircularProgressIndicator(
                            color = tint,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp), // :954 sh6.a(18.dp, ...)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd, // :958 xe2.v()
                            contentDescription = "保存为歌单",
                            tint = tint,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
                label = "保存为歌单",
                enabled = songs.isNotEmpty(),
                tone = tone,
                onClick = onSavePlaylist,
            )
        }
    }
}

/**
 * 「播放全部」胶囊 —— GZ `:808-826`。
 *
 * - `height(48.dp)` + `RoundedCornerShape(14.dp)`（`:808` 的 `ky6.b(1.0f, ...)` + `by6.a(14.0f)`）；
 * - 底：`Brush.linearGradient([tone, lerp(tone, White, 0.24f)])`（`:805` 的 `ob4` 两色渐变，
 *   `hp1.h0(j, 0.24f, g91.d)` = `lerp(tone, White, 0.24f)`）；
 * - 按压缩放 **0.98**（`:808` MotionKt.c(..., 0.98f, ...)）；
 * - 内部 `padding(12.dp)`、图标 **22.dp** 白色、文案「播放全部」**14.sp / W800 / 白**（`:823-825`）。
 */
@Composable
private fun GuessPlayAllButton(
    tone: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brush = remember(tone) {
        Brush.linearGradient(listOf(tone, lerp(tone, Color.White, 0.24f)))
    }
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(brush, RoundedCornerShape(14.dp))
            .then(
                if (enabled) Modifier.pressClick(pressedScale = 0.98f, onClick = onClick)
                else Modifier,
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.PlayArrow, // :823 fd2.q()
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = "播放全部", // :825
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.W800,
        )
    }
}

/**
 * Hero 里的圆形次按钮 —— GZ `GuessHeroRoundButton-ww6aTOc`（:854-934）。
 *
 * `48.dp` 圆（`:877`）、底色 `tone.copy(alpha = 0.1f)`（`:806` 的 `g91.c(0.1f, j)`）、
 * 图标色 `lerp(tone, White, 0.8f)`（`:807` 的 `g91.c(0.8f, discoveryAccent)`）、
 * 按压缩放 **0.9**（`:887` MotionKt.c(..., 0.9f, ...)）；禁用时底色 alpha 0.5、内容 alpha 0.45（`:885/911`）。
 */
@Composable
private fun GuessHeroRoundButton(
    icon: @Composable (Color) -> Unit,
    label: String,
    enabled: Boolean,
    tone: Color,
    onClick: () -> Unit,
) {
    val accent = discoveryAccent(tone)
    val iconTint = remember(accent) { lerp(accent, Color.White, 0.8f) }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(tone.copy(alpha = if (enabled) 0.1f else 0.05f), CircleShape)
            .then(
                if (enabled) Modifier.pressClick(pressedScale = 0.9f, onClick = onClick)
                else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.graphicsLayer { alpha = if (enabled) 1f else 0.45f }) {
            icon(iconTint)
        }
    }
}

/**
 * 封面叠层 —— GZ `GuessCoverDeck-RPmYEkk`（:234-330）。
 *
 * 容器 `width(132.dp) x height(126.dp)`、`contentAlignment = Center`（`:255-256`）；
 * 三张封面：后排两张 **92.dp**（`:294/301`），前排 **104.dp**（`:302`），圆角 **16.dp**（`:386`）、
 * 阴影 **9.dp**（黑 10% / 18%，`:389`）、描边 1.dp `coverHairline`（`:389`）、
 * 底叠 `[tone, lerp(tone, White, 0.24f)]` 渐变（`:392`，所以没有封面时叠层仍是卡片主色）。
 * 右下角一枚 **28.dp** 圆徽（`:303`）：阴影 5.dp 黑 22%、底色 tone、图标 14.dp 白色（`:305/320`）。
 *
 * TODO(未确定)：后排两张的旋转角在反编译里是 `fw2(9)` / `fw2(10)` 两个 `graphicsLayer` lambda，
 *   常量被内联进 lambda 体（JADX 未展开），此处取视觉等价的手感值 ±8°（截图量角约 8°~10°）。
 */
@Composable
private fun GuessCoverDeck(covers: List<String?>, tone: Color, scale: Float = 1f) {
    val front = covers.getOrNull(0)
    val back1 = covers.getOrNull(1) ?: front
    val back2 = covers.getOrNull(2) ?: back1

    Box(
        modifier = Modifier
            .width(132.dp * scale)
            .height(126.dp * scale),
        contentAlignment = Alignment.Center,
    ) {
        DeckCover(
            url = back1,
            size = 92.dp * scale,
            tone = tone,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .graphicsLayer { rotationZ = -8f },
        )
        DeckCover(
            url = back2,
            size = 92.dp * scale,
            tone = tone,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .graphicsLayer { rotationZ = 8f },
        )
        DeckCover(
            url = front,
            size = 104.dp * scale,
            tone = tone,
            modifier = Modifier.align(Alignment.Center),
        )

        // 右下角徽标（:303-320）
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 4.dp * scale, y = (-2).dp * scale)
                .size(28.dp * scale)
                .shadow(5.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.22f))
                .clip(CircleShape)
                .background(tone, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome, // uy8.y()
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp * scale),
            )
        }
    }
}

@Composable
private fun DeckCover(
    url: String?,
    size: androidx.compose.ui.unit.Dp,
    tone: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(16.dp) // :386 by6.a(16.0f)
    val brush = remember(tone) {
        Brush.linearGradient(listOf(tone, lerp(tone, Color.White, 0.24f)))
    }
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 9.dp, // :389 mg2.I(..., 9.0f, ...)
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.1f),
                spotColor = Color.Black.copy(alpha = 0.18f),
            )
            .border(1.dp, coverHairline(), shape)
            .background(brush, shape)
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(url).crossfade(200).build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote, // mf2.o()
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(size * 0.34f),
            )
        }
    }
}

/**
 * 线索提示条 —— GZ `GuessTasteProfile-Iv8Zu3U`（:1455-1499）。
 *
 * `Row(fillMaxWidth().padding(4.dp), CenterVertically)`；图标 **16.dp** 取强调色（`:1487`），
 * 间距 **8.dp**（`:1488`），文案 **12.sp / lineHeight 18.sp / textMedium**（`:1489`）。
 */
@Composable
private fun GuessTasteProfile(seedCount: Int, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp), // :1469 hp1.p0(..., 4.0f, 4.0f)
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome, // :1487 uy8.y()
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp)) // :1488
        Text(
            // :1489 —— 有线索「熟悉的偏爱，也有意外的新发现」，否则「多听几首，让推荐慢慢懂你」
            text = if (seedCount > 0) "熟悉的偏爱，也有意外的新发现" else "多听几首，让推荐慢慢懂你",
            color = textMedium(),
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
    }
}

/**
 * 「本次精选」标题行 —— GZ `GuessPersonalSectionHeader-3J-VO9M`（:968-1054）。
 *
 * - 左列：标题「本次精选」**20.sp / W800 / -0.4.sp**（`:1040`）；副标题 **11.sp / W500 / textLow**（`:1042`）；
 * - 右侧胶囊：`"${count} 首"`、底 `accent.copy(alpha = 0.14f)`、圆角 **8.dp**、
 *   padding `(9.dp, 5.dp)`、文案 **10.sp / W700 / accent**（`:1044`）。
 */
@Composable
private fun GuessPersonalSectionHeader(count: Int, seedCount: Int, accent: Color) {
    Row(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "本次精选",
                color = textHigh(),
                fontSize = 20.sp,
                fontWeight = FontWeight.W800,
                letterSpacing = (-0.4).sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                // :1042 —— 有种子时「综合收藏偏好与近期播放排序」
                text = if (seedCount > 0) "综合收藏偏好与近期播放排序" else "继续听歌，推荐会越来越懂你",
                color = textLow(),
                fontSize = 11.sp,
                fontWeight = FontWeight.W500,
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                .padding(horizontal = 9.dp, vertical = 5.dp),
        ) {
            Text(
                text = "$count 首", // :1044
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * 单曲行 —— GZ `GuessSongRow-TgFrcIs`（:1211-1452）。
 *
 * 结构：`Row(fillMaxWidth().height(72.dp).background(播放中 ? discoveryInset : 透明, RoundedCornerShape(14.dp))
 *   .bounceClick(0.98f).padding(horizontal = 8.dp), CenterVertically)`
 * ```
 * ├─ 序号  宽 28.dp / 10.sp / 前 3 名 W800 其余 W700 / letterSpacing 0.4.sp   (:1312-1324)
 * ├─ Spacer 4.dp                                                            (:1325)
 * ├─ 封面  46.dp / 圆角 12.dp / 底 coverPlaceholder + 1.dp coverHairline 描边  (:1326-1329)
 * │   播放中额外压 40% 黑幕 + PlayingBars（:1362-1363）
 * ├─ Spacer 12.dp                                                           (:1372)
 * ├─ Column(weight 1f)
 * │   ├─ 歌名 14.sp / W500 / textHigh（播放中取强调色）/ 单行 Ellipsis    (:1388-1398)
 * │   └─ 「歌手 · 专辑」 11.sp / textLow / 单行，歌手空回退「未知歌手」     (:1400-1409)
 * └─ 收藏钮 44.dp / 图标 20.dp / 按压缩放 0.85（:1411-1434）
 * ```
 * 序号与歌名在「当前正在播放」时取 `discoveryAccent`（`:1318` / `:1393`）。
 */
@Composable
private fun GuessSongRow(
    song: Song,
    index: Int,
    accent: Color,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val context = LocalContext.current
    val dark = appIsDark()
    val quietSurface = if (dark) Color(0xFF2A2A30) else Color(0xFFF0F0F4) // discoveryInset

    Row(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .height(72.dp) // :1287 ij7.i(..., 72.0f)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isCurrent) quietSurface else Color.Transparent)
            .pressClick(pressedScale = 0.98f, onClick = onPlay)
            .padding(horizontal = 8.dp), // :1287 hp1.q0(..., 8.0f, 0.0f, 2)
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            // :1312 dw7.Y(2, String.valueOf(i + 1))
            text = (index + 1).toString().padStart(2, '0'),
            color = if (isCurrent) accent else textLow(),
            fontSize = 10.sp,
            fontWeight = if (index < 3) FontWeight.W800 else FontWeight.W700, // :1314
            letterSpacing = 0.4.sp, // :1315 co7.i(0.4d)
            modifier = Modifier.width(28.dp), // :1324 ij7.r(c05Var, 28.0f)
        )
        Spacer(Modifier.width(4.dp))

        Box(
            modifier = Modifier
                .size(46.dp) // :1326
                .clip(RoundedCornerShape(12.dp))
                .background(coverPlaceholder(), RoundedCornerShape(12.dp))
                .border(1.dp, coverHairline(), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (!song.artwork.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(song.artwork).crossfade(200).build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            if (isCurrent) {
                // :1362 —— 播放中压一层 40% 黑幕，再叠音波
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                )
                PlayingBars(
                    isPlaying = isPlaying,
                    color = Color.White,
                    modifier = Modifier.height(13.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp)) // :1372

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title.ifBlank { "未知歌曲" }, // :1388
                color = if (isCurrent) accent else textHigh(),
                fontSize = 14.sp,
                fontWeight = FontWeight.W500,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp)) // :1399
            Text(
                // :1400-1409 —— 歌手空回退「未知歌手」，有专辑时用「 · 」连接
                text = buildString {
                    append(song.artist.ifBlank { "未知歌手" })
                    if (song.album.isNotBlank()) append(" · ").append(song.album)
                },
                color = textLow(),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box(
            modifier = Modifier
                .size(44.dp) // :1411
                .clip(CircleShape)
                .pressClick(pressedScale = 0.85f, onClick = onToggleFavorite),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (isFavorite) "取消收藏" else "收藏", // :1427
                tint = if (isFavorite) accent else textLow(),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ============================================================
// 加载 / 空 / 错误（GZ :512 GuessEmpty、:562 GuessError）
// ============================================================

@Composable
private fun GuessLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = textMedium(),
                strokeWidth = 2.dp,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "正在根据你的收藏挑选…",
                color = textMedium(),
                fontSize = 13.sp,
            )
        }
    }
}

/** GZ `GuessEmpty`（:512-560）：图标 40.dp textLow + 标题 14.sp W700 textHigh + 副文案 13.sp textLow */
@Composable
private fun GuessEmpty(title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.MusicNote, // mf2.o()
                contentDescription = null,
                tint = textLow(),
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = title,
                color = textHigh(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                color = textLow(),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** GZ `GuessError`（:562-608）：文案 + 「重试」胶囊（`GuessChipButton`，:177） */
@Composable
private fun GuessError(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message.ifBlank { "推荐获取失败" }, // :595
                color = textMedium(),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            // GuessChipButton：圆角 20.dp、padding(14, 8)、按压缩放 0.96（:198-201）
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(textMedium().copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                    .pressClick(pressedScale = 0.96f, onClick = onRetry)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "重试",
                    color = textHigh(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W800,
                )
            }
        }
    }
}

// ============================================================
// 交互与配色小工具
// ============================================================

/**
 * GZ `MotionKt.c`（按压缩放、无涟漪）的等价实现：
 * 150ms + `CubicBezierEasing(0.2f, 0f, 0f, 1f)`（`ct.java:8` / `bs4.java:6`）。
 */
@Composable
private fun Modifier.pressClick(pressedScale: Float, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
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
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

/**
 * GZ `DiscoveryStyleKt.discoveryAccent`（`DiscoveryStyleKt.java:12-19`）：
 * 深色下把主色往白色提 48%，浅色下往黑色压 28%——保证同一主色在两种主题下都能当文字色用。
 */
@Composable
private fun discoveryAccent(tone: Color): Color =
    if (appIsDark()) lerp(tone, Color.White, 0.48f) else lerp(tone, Color.Black, 0.28f)
