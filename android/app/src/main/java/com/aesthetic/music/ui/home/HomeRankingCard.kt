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

enum class RankingTab(
    val key: String,
    val label: String,
    val subtitle: String,
    val tone: Color,
) {
    RISING("rising", "飙升榜", "正在快速上升的歌曲", AppColors.rankingRising),

    NEW("new", "新歌榜", "发现正在被听见的新声音", AppColors.rankingNew),

    ORIGINAL("original", "原创榜", "华语原创好声音", AppColors.rankingOriginal),

    HOT("hot", "热歌榜", "听听大家反复循环的歌", AppColors.rankingHot),
}

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

@Composable
fun HomeRankingCard(
    songsMap: Map<RankingTab, List<Song>>,
    onPlaySong: (Song, List<Song>) -> Unit,
    onOpenRanksPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf(RankingTab.NEW) }
    val tab = selected

    val tabSongs = songsMap[tab].orEmpty()
    // TODO(未确定): GZ 的加载/已加载集合（set/set2）与首帧标志 z 不在本 API 里，无法逐值还原。
    val isLoading = !songsMap.containsKey(tab)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.xxl)
            .glassCard(RoundedCornerShape(20.dp))
            .padding(AppSpacing.xl),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = tab.label,
                modifier = Modifier
                    .weight(1f)
                    .gzBounceClick(pressedScale = 0.98f, onClick = onOpenRanksPage),
                color = textHigh(),
                fontSize = 20.sp,
                fontWeight = FontWeight(800),
                letterSpacing = (-0.4).sp,
                lineHeight = 26.sp,
            )
            Row(
                modifier = Modifier
                    .heightIn(min = 30.dp)
                    .gzBounceClick(pressedScale = 0.96f, onClick = onOpenRanksPage),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "完整榜单",
                    color = textLow(),
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = textLow(),
                )
            }
        }
        Spacer(Modifier.height(AppSpacing.lg))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RankingCoverStack(
                songs = tabSongs,
                enabled = tabSongs.isNotEmpty(),
                onPlayFirst = {
                    // TODO(未确定): 列表为空时的行为 GZ 由外层 enabled 兜住，这里同样不外抛。
                    tabSongs.firstOrNull()?.let { onPlaySong(it, tabSongs) }
                },
            )
            Spacer(Modifier.width(AppSpacing.lg))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 90.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start,
            ) {
                if (isLoading) {
                    Row(
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = DefaultCoverTone,
                            strokeWidth = 1.7.dp,
                        )
                        Spacer(Modifier.width(AppSpacing.sm))
                        Text(
                            text = "正在加载榜单",
                            color = textMedium(),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                    }
                } else if (tabSongs.isEmpty()) {
                    Text(
                        text = "暂未获取到榜单",
                        color = textMedium(),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                    Text(
                        text = "点击重试",
                        modifier = Modifier
                            .heightIn(min = 32.dp)
                            .gzBounceClick(
                                pressedScale = 0.96f,
                                // TODO(未确定): :527 点击回调是 at2Var2(tab)（重试拉取榜单），
                                onClick = {},
                            ),
                        color = DefaultCoverTone,
                        fontSize = 11.sp,
                        fontWeight = FontWeight(500),
                        lineHeight = 16.sp,
                    )
                } else {
                    tabSongs.take(3).forEachIndexed { index, song ->
                        val rankColor = textHigh()
                        val titleColor = textHigh()
                        val artistColor = textLow()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 30.dp)
                                .gzBounceClick(
                                    pressedScale = 0.98f,
                                    onClick = { onPlaySong(song, tabSongs) },
                                ),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = (index + 1).toString(),
                                modifier = Modifier.width(20.dp),
                                color = rankColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight(800),
                                lineHeight = 18.sp,
                            )
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(
                                        SpanStyle(
                                            color = titleColor,
                                            fontWeight = FontWeight(600),
                                        ),
                                    ) {
                                        append(song.title)
                                    }
                                    withStyle(
                                        SpanStyle(color = artistColor),
                                    ) {
                                        append(" · " + song.artist.ifBlank { "未知歌手" })
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                color = Color.Unspecified,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(AppSpacing.lg))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            RankingTab.entries.forEach { entry ->
                val isSelected = entry == tab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .gzBounceClick(
                            pressedScale = 0.96f,
                            onClick = { selected = entry },
                        ),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = entry.label,
                        modifier = Modifier.padding(vertical = AppSpacing.xs),
                        color = if (isSelected) {
                            textHigh()
                        } else {
                            textLow()
                        },
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) {
                            FontWeight(800)
                        } else {
                            FontWeight(500)
                        },
                        lineHeight = 16.sp,
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 18.dp, height = 3.dp)
                            .background(
                                color = if (isSelected) {
                                    DefaultCoverTone
                                } else {
                                    Color.Unspecified
                                },
                                shape = RoundedCornerShape(2.dp),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun RankingCoverStack(
    songs: List<Song>,
    enabled: Boolean,
    onPlayFirst: () -> Unit,
) {
    val placeholder = if (appIsDark()) AppColors.rankingPlaceholderDark else AppColors.rankingPlaceholderLight
    val iconTint = textLow()

    Box(
        modifier = Modifier
            .width(104.dp)
            .height(90.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        for (i in 2 downTo 0) {
            val coverSize = (82 - i * 15).dp
            val song = songs.getOrNull(i)
            val coverUrl = song?.artwork

            Box(
                modifier = Modifier
                    .offset(x = (i * 25).dp)
                    .size(coverSize)
                    .clip(RoundedCornerShape(12.dp))
                    .background(placeholder),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Album,
                    contentDescription = null,
                    modifier = Modifier.size(coverSize * 0.32f),
                    tint = iconTint,
                )
                if (!coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                if (i == 0) {
                    Box(
                        modifier = Modifier.gzBounceClick(
                            enabled = enabled,
                            pressedScale = 0.96f,
                            onClick = onPlayFirst,
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(
                                    color = Color.Black.copy(alpha = 0.28f),
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "播放当前榜单",
                                modifier = Modifier.size(22.dp),
                                tint = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}
