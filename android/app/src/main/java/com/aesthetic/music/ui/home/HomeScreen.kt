package com.aesthetic.music.ui.home

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.BottomContentSpacer
import com.aesthetic.music.ui.components.glassCard
import com.aesthetic.music.ui.components.liquidGlassCardSurface
import com.aesthetic.music.ui.components.rememberIOSOverScrollEffect
import com.aesthetic.music.ui.components.textHigh
import com.aesthetic.music.ui.components.textLow
import com.aesthetic.music.ui.components.textMedium
import com.aesthetic.music.ui.theme.appIsDark

/**
 * 首页。
 *
 * ## 版块顺序 = GZ Music 真值
 * 从 `gz_decompiled/.../feature/home/HomeScreenKt.java` 逐段解出（调用点行号见下），
 * 与页面内各组件自身的 `// xxx.java:行号` 注释共同构成完整溯源链：
 *
 * | # | 版块 | GZ 调用点 |
 * |---|---|---|
 * | 1 | 标题「发现好音乐」 | `HomeScreenKt.java:8698` → `:9571` `HomeTopBar` |
 * | 2 | 搜索栏（顶栏的平级兄弟节点） | `:8714` |
 * | 3 | 时段场景大卡 `DailyStackHero` | `:8790` |
 * | 4 | 三大拟态入口 `HomeEntryCards` | `:8815` |
 * | 5 | ~~分类盲盒~~ | 本版未使用，组件已删除 |
 * | 6 | 新歌首发（三个地区新歌榜） | 取代原「新碟首发」`NewAlbumSection`（`:8845`） |
 * | 7 | ~~情绪电台~~ | **已删除**，见下方「心情电台已移除」 |
 * | 8 | 折叠排行榜 `ChartCard` / `PhoneChartCard` | `:10798` / `:9208` |
 *
 * ## 心情电台已移除（本轮改动）
 * GZ 首页确实有「心情电台」（`HomeScreenKt.java:10049` 频道标签 + `:10634` 插画），
 * 但本项目这一版把它整块去掉了：
 *  - 它是**纯装饰**：点任意心情只会从「新歌/热歌池」里随机播一首（旧实现见下方注释），
 *    并没有真的按情绪取歌的能力，用户点完得到的是一次不可预期的随机播放；
 *  - 它占据了首屏一整屏的高度，把真正有内容密度的「新歌首发」「排行榜」推到了第二屏；
 *  - 情绪取歌这件事已经被「猜你喜欢」覆盖得更好（见 `ui/guess/GuessRecommend.kt`：
 *    用收藏 + 最近播放算权重，比「随便挑一个心情」更有依据）。
 *  组件文件 `MoodRadioPanel.kt` 随之删除（git 里可取回）。
 *
 * ## 刷新交互 = GZ 真值（**没有下拉刷新**）
 * 主控做过 5 项决定性检索：GZ 全仓 `PullToRefreshBox` / `rememberPullToRefreshState` /
 * `pullToRefresh` / `SwipeRefresh` / `PullRefreshIndicator` **均为 0 命中**；
 * `HomeScreenKt` 里也没有 `onRefresh` / `NestedScrollConnection`。
 * GZ 首页的「刷新」由两件事构成：
 *  1. **列表过卷 = iOS 回弹**：`rememberIOSOverScrollEffect(Orientation.Vertical)`
 *     （`IOSOverscrollEffectKt.java:16`，`fm5.c` = Vertical；实现在 `defpackage/li3.java`，
 *      构造器只收 CoroutineScope + Orientation，**不触发刷新**）
 *  2. **需要重试时 = 一行可点文字**「部分盲盒未刷新 · 点击重试」（`HomeScreenKt.java:512`）
 *
 * 因此本文件**删除了** Melos 原有的「触底继续上拉 → 玻璃胶囊 → 继续上拉刷新 / 松手立即刷新 /
 * 正在刷新推荐内容…」那套底部上拉刷新（那是 Antigravity 自造的交互，GZ 中不存在）。
 *
 * ## 横向留白
 * GZ 的每个版块都自己带 16.dp 横向内边距（`HomeScreenKt.java:3067`、`:7503`），
 * 列表层不加横向 padding，避免双重缩进。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    dailyPlaylists: List<PlaylistItem>,
    newSongs: List<Song>,
    hotSongs: List<Song>,
    hotPlaylists: List<PlaylistItem>,
    ranks: List<PlaylistItem>,
    recommendSongs: List<Song> = emptyList(),
    isLoadingRecommendSongs: Boolean = false,
    onLoadRecommendSongs: (PlaylistItem) -> Unit = {},
    /** 「新歌首发」三个地区新歌榜（华语/欧美/日语/韩语 每日轮换出三个） */
    newSongRegions: List<RegionNewSongs> = emptyList(),
    isLoading: Boolean = false,
    contentError: String? = null,
    onRetryContent: (() -> Unit)? = null,
    onPlaySong: (Song, List<Song>) -> Unit,
    onSearchBarClick: () -> Unit,
    /**
     * 搜索栏末尾的「听歌识曲」按钮回调。
     * GZ 的 `SearchBar` 只在回调非空时才渲染这颗圆钮（`HomeScreenKt.java:11352`）；
     * Melos 目前没有识曲能力，默认 null（即不渲染），由宿主决定是否打开。
     */
    onMicClick: (() -> Unit)? = null,
    onOpenPlaylistDetail: (PlaylistItem) -> Unit,
    /** 三大入口第一张卡「猜你喜欢」→ 独立页面（见 ui/guess） */
    onOpenGuess: () -> Unit,
    onOpenRanksPage: () -> Unit,
    onOpenFm: () -> Unit = {},
    isPlaying: Boolean = false,
) {
    // 候选歌曲池（优先新歌、热歌），去重防止下游 key 冲突
    val poolSongs = remember(newSongs, hotSongs) {
        (if (newSongs.isNotEmpty()) newSongs else hotSongs)
            .distinctBy { it.id }
    }

    // 推荐选取的精选歌单（优先取每日推荐歌单，次选热门歌单）
    val targetPlaylist = dailyPlaylists.firstOrNull() ?: hotPlaylists.firstOrNull()

    // 监听 targetPlaylist 变化，自动触发获取歌单内部真实歌曲
    LaunchedEffect(targetPlaylist?.id, targetPlaylist?.platform) {
        val pl = targetPlaylist
        if (pl != null && pl.id.isNotBlank()) {
            onLoadRecommendSongs(pl)
        }
    }

    // 全部数据区块为空的判定：用于首次加载态与失败横幅
    val hasNoContent = targetPlaylist == null &&
            poolSongs.isEmpty() &&
            hotPlaylists.isEmpty() &&
            ranks.isEmpty()

    // [GZ 真值] 时段场景（defpackage/me3.java:1031-1043）—— 旧实现里的「时段问候」是编造的
    val timeScene = remember { resolveTimeScene(currentHourOfDay()) }

    // 盲盒已从首页移除（见下方板块列表处的说明）。

    val songsMap = remember(newSongs, hotSongs, poolSongs) {
        mapOf(
            RankingTab.RISING to poolSongs.take(10),
            RankingTab.NEW to (if (newSongs.isNotEmpty()) newSongs else poolSongs).take(10),
            RankingTab.ORIGINAL to poolSongs.reversed().take(10),
            RankingTab.HOT to (if (hotSongs.isNotEmpty()) hotSongs else poolSongs).take(10)
        )
    }

    val listState = rememberLazyListState()

    // [GZ 真值] iOS 风格回弹过卷：GZ 首页的 LazyColumn 挂的就是
    // `rememberIOSOverScrollEffect(Orientation.Vertical)`（IOSOverscrollEffectKt.java:16，
    // 实现在 defpackage/li3.java）。GZ 没有下拉刷新，这套回弹就是它残留的过卷手感。
    val iosOverscroll = rememberIOSOverScrollEffect()

    Box(modifier = Modifier.fillMaxSize()) {
        // 极光流光背景 —— GZ 真值实现（AuroraBackgroundKt.java:29-40 + defpackage/p70.java:38-83）
        HomeAmbientGlowBackground()

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                // 1.7.4 里 LazyColumn 没有 overscrollEffect 形参，需用 Modifier.overscroll 挂载
                // （androidx.compose.foundation/Overscroll.kt:151）
                .overscroll(iosOverscroll),
            // 横向不加 padding：GZ 每个版块自带 16.dp（见本函数 KDoc）
            contentPadding = PaddingValues(
                top = 0.dp,
                start = 0.dp,
                end = 0.dp,
                bottom = BottomContentSpacer,
            ),
            // 18.dp 是 Melos 原有的版块间距（非 GZ token）——GZ 的版块间距由各版块自己给定
            // （例如场景大卡前的公告条间隔 12.dp，HomeScreenKt.java:3963）
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. 标题「发现好音乐」（HomeScreenKt.java:8698 → :9571 HomeTopBar；内部自带 statusBarsPadding）
            item(key = "home_top_bar") {
                HomeHeader()
            }

            // 2. 搜索栏（与顶栏平级的兄弟节点，HomeScreenKt.java:8714）
            item(key = "home_search_bar") {
                HomeSearchEntry(onClick = onSearchBarClick, onMicClick = onMicClick)
            }

            // 3. 时段场景大卡 / 轮播（DailyStackHero，HomeScreenKt.java:8790 → :3765 DailyHeroCard）
            //    GZ 传的是 `z81.L0(list4, 3)` = 最多 3 页的 HorizontalPager（配 HeroPageIndicator）
            if (targetPlaylist != null) {
                item(key = "daily_hero") {
                    HomeDailyHero(
                        title = timeScene.title,
                        subtitle = timeScene.category,
                        pages = (dailyPlaylists + hotPlaylists).distinctBy { it.id }.take(3),
                        previewSongs = (if (recommendSongs.isNotEmpty()) recommendSongs else poolSongs).take(3),
                        isLoadingSongs = isLoadingRecommendSongs && recommendSongs.isEmpty(),
                        isPlaying = isPlaying,
                        onOpenPlaylist = onOpenPlaylistDetail,
                    )
                }
            }

            // 4. 三大拟态入口（HomeEntryCards，HomeScreenKt.java:8815）
            item(key = "home_entry_cards") {
                HomeEntryCards(
                    // 「猜你喜欢」→ 独立页面：推荐结果由收藏 + 最近播放现场算（ui/guess）
                    onOpenRecommend = onOpenGuess,
                    onOpenRanks = onOpenRanksPage,
                    onOpenFm = onOpenFm,
                    isPlaying = isPlaying,
                )
            }

            // 首次加载：全部数据区块为空时显示加载态
            if (isLoading && hasNoContent) {
                item(key = "home_loading") {
                    HomeLoadingState()
                }
            }

            // 内容加载失败横幅
            if (contentError != null && targetPlaylist == null && poolSongs.isEmpty()) {
                item(key = "home_error_banner") {
                    HomeErrorBanner(message = contentError, onRetry = onRetryContent)
                }
            }

            // ── [已移除] 分类盲盒 ──
            // GZ 真机截图上，手机端首页在「三大入口」下方**直接就是心情电台**，
            // 并没有盲盒那一排。`CategoryBlindBoxRow` 的调用点在 HomeScreenKt.java:497-1945，
            // 那是**另一个内容函数**（不是 8072 起的这个 HomeScreen），此前按调用行号把它排进首页属于排序错误。
            // 本版已删除该组件；心情电台也已移除，所以「三大入口」下方直接是「新歌首发」。

            // ── [已删除] 心情电台 ──
            // 原第 7 块 `MoodRadioPanel`（GZ HomeScreenKt.java:10049 频道标签 + :10634 插画）已整块移除，
            // 组件文件 MoodRadioPanel.kt 一并删除。原因见本文件顶部 KDoc「心情电台已移除」：
            // 它点任意心情只是从新歌/热歌池随机播一首，没有真正按情绪取歌的能力，
            // 却占了首屏一整屏高度。情绪向的「猜点什么」由「猜你喜欢」页承担。

            // 6. 新歌首发（三个地区新歌榜；取代原「新碟首发」NewAlbumSection，HomeScreenKt.java:8845）
            if (newSongRegions.isNotEmpty()) {
                item(key = "new_song_region_section") {
                    NewSongRegionSection(
                        groups = newSongRegions,
                        // 点卡片 = 直接播这个地区的新歌（不再跳歌单广场）
                        onPlayRegion = { songs ->
                            val first = songs.firstOrNull()
                            if (first != null) onPlaySong(first, songs)
                        },
                    )
                }
            }

            // 8. 折叠排行榜（HomeRankingCard，HomeScreenKt.java:9208 / :10798）
            item(key = "home_ranking_card") {
                HomeRankingCard(
                    songsMap = songsMap,
                    onPlaySong = onPlaySong,
                    onOpenRanksPage = onOpenRanksPage
                )
            }

            item(key = "bottom_spacer") {
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/**
 * GZ `MotionKt.bounceClick` 的等价实现（按压回弹）。
 *
 * 数值全部来自反编译真值：
 *  - 动画 spec = `ct.java:8` 的 `qk5.r0(150, 2, MotionKt.a)` = `tween(150ms, easing = bs4.a)`
 *  - 缓动 = `bs4.java:6` 的 `bs4.a` = `CubicBezierEasing(0.2f, 0f, 0f, 1f)`
 *  - `label = "pressScale"` ← `MotionKt.java:62`
 *  - `indication = null`（GZ 的点击无涟漪）← `MotionKt.java:69`
 */
@Composable
private fun Modifier.gzPressScale(pressedScale: Float, onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(
            durationMillis = 150, // ct.java:8
            easing = CubicBezierEasing(0.2f, 0f, 0f, 1f), // bs4.java:6
        ),
        label = "pressScale", // MotionKt.java:62
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null, // MotionKt.java:69
            onClick = onClick,
        )
}

/**
 * 首页搜索栏 —— 逐值还原 GZ `HomeScreenKt.SearchBar`（定义在 `HomeScreenKt.java:11280`，调用点 `:8714`）。
 *
 * 真值（行号均指 `HomeScreenKt.java`）：
 *  - 圆角 `RoundedCornerShape(24.dp)`（:11322）
 *  - 外链：`fillMaxWidth → padding(horizontal = 16.dp) → height(48.dp)`（:11325）
 *  - 表面：`liquidGlassCardSurface(shape, tone = null, tones = null, selected = false,
 *    gradient = false, elevation = 0f, flat = true, settings = null)`（:11325）
 *    —— `settings = null` ⇒ `usesLiquidGlassButtons` 返回 false ⇒ 走 **flat 降级路径**；
 *    本项目用 `liquidGlassEnabled = false` 表达这个语义。
 *  - 按压缩放 **0.985**（:11325）
 *  - 内部 padding `start = 14.dp, end = 6.dp`（:11325 的 `hp1.s0(…,14,0,6,0,10)`）
 *  - 前导图标 **19.dp** + `textLow()`（:11349）
 *  - 提示文案 **"搜索歌曲、歌手、专辑"**、`textMedium()`、**13.sp**、**W500**、`weight(1f, fill = true)`（:11350）
 *  - 末尾「听歌识曲」圆钮（仅当回调非空，:11352）：**36.dp 圆**（:11354）、底色
 *    `textHigh()@10%(深)/6%(浅)`（:11356-11362）、按压缩放 **0.9**（:11362）、图标 **18.dp**
 *    `textHigh()`、`contentDescription = "听歌识曲"`（:11377）
 */
@Composable
private fun HomeSearchEntry(onClick: () -> Unit, onMicClick: (() -> Unit)?) {
    val dark = appIsDark()
    val shape = RoundedCornerShape(24.dp) // :11322
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(48.dp) // :11325
            .liquidGlassCardSurface(
                shape = shape,
                // flat = true 且 settings = null → GZ 走的是降级（非液态玻璃）那一支
                flat = true,
                elevation = 0.dp,
                liquidGlassEnabled = false,
            )
            .gzPressScale(pressedScale = 0.985f, onClick = onClick) // :11325
            .padding(start = 14.dp, top = 0.dp, end = 6.dp, bottom = 0.dp), // :11325
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = textLow(), // :11349 GlassTokensKt.textLow
            modifier = Modifier.size(19.dp), // :11349 ij7.n(c05Var, 19.0f)
        )
        Text(
            text = "搜索歌曲、歌手、专辑", // :11350
            color = textMedium(), // :11350 GlassTokensKt.textMedium
            fontSize = 13.sp, // :11350 co7.j(13)
            fontWeight = FontWeight.W500, // :11350 sq2.j = FontWeight(500)
            maxLines = 1,
            modifier = Modifier
                .padding(start = 10.dp)
                .weight(1f, fill = true), // :11350 jj1.h(…, 1.0f, true)
        )

        if (onMicClick != null) { // :11352 —— 回调为空则整颗按钮不渲染
            Box(
                modifier = Modifier
                    .size(36.dp) // :11354 ij7.n(c05Var, 36.0f)
                    .clip(CircleShape) // :11354 by6.a = RoundedCornerShape(50%)
                    .background(
                        color = textHigh().copy(alpha = if (dark) 0.1f else 0.06f), // :11356-11362
                        shape = RectangleShape, // :11362 hp1.n = RectangleShape
                    )
                    .gzPressScale(pressedScale = 0.9f, onClick = onMicClick), // :11362
                contentAlignment = Alignment.Center, // :11363 jj0.d(il0.o, false)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = "听歌识曲", // :11377
                    tint = textHigh(), // :11377
                    modifier = Modifier.size(18.dp), // :11377 ij7.n(c05Var, 18.0f)
                )
            }
        }
    }
}
