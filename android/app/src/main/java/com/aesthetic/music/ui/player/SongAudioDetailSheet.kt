package com.aesthetic.music.ui.player

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.player.AudioRealtimeSpec
import com.aesthetic.music.player.LyricMetaSpec
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

/**
 * 歌曲来源、实时音频技术规格与歌词元数据详情面板（BottomSheet）。
 * 视觉对齐：半透明深色圆角毛玻璃卡片、6 宫格技术规格网格、歌词芯片 Badge。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongAudioDetailSheet(
    onDismissRequest: () -> Unit,
    song: Song?,
    audioSpec: AudioRealtimeSpec,
    lyricSpec: LyricMetaSpec,
    hazeState: HazeState? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = null,
    ) {
        val hazeModifier = if (hazeState != null) {
            Modifier.hazeEffect(
                state = hazeState,
                style = HazeDefaults.style(
                    backgroundColor = Color(0xB81A1614),
                    blurRadius = 36.dp,
                    noiseFactor = 0.02f,
                )
            )
        } else {
            Modifier.background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xF0201B17),
                        Color(0xF514110E),
                    )
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.White.copy(alpha = 0.05f),
                            Color.Transparent,
                        )
                    ),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                )
                .then(hazeModifier)
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(top = 16.dp, bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 顶部手柄指示线
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.25f))
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(2.dp))

            // 1. 顶部歌曲信息卡片
            DetailCardContainer {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!song?.artwork.isNullOrBlank()) {
                        AsyncImage(
                            model = song?.artwork,
                            contentDescription = null,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Melos", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                        }
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song?.title ?: "未知歌曲",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        val subtitle = buildString {
                            append(song?.artist?.ifBlank { "未知歌手" } ?: "未知歌手")
                            if (!song?.album.isNullOrBlank()) {
                                append(" · ")
                                append(song.album)
                            }
                        }
                        Text(
                            text = subtitle,
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 2. 来源卡片
            DetailCardContainer {
                Text(
                    text = "来源",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                val platformName = when (song?.platform) {
                    "netease" -> "网易云音乐"
                    "qq" -> "QQ音乐"
                    "kugou" -> "酷狗音乐"
                    "kuwo" -> "酷我音乐"
                    "migu" -> "咪咕音乐"
                    "bilibili" -> "哔哩哔哩"
                    "local" -> "本地文件"
                    else -> song?.platform ?: "未知平台"
                }

                InfoRow(label = "平台", value = platformName)
                Spacer(Modifier.height(10.dp))
                InfoRow(
                    label = "歌曲 ID",
                    value = song?.id ?: "—",
                    onClick = {
                        val id = song?.id
                        if (!id.isNullOrBlank()) {
                            clipboardManager.setText(AnnotatedString(id))
                            Toast.makeText(context, "歌曲 ID 已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                Spacer(Modifier.height(10.dp))
                InfoRow(label = "音质请求", value = audioSpec.qualityRequested)
                Spacer(Modifier.height(10.dp))
                InfoRow(label = "取链渠道", value = audioSpec.sourceChannel)
                Spacer(Modifier.height(10.dp))
                InfoRow(label = "来源插件", value = audioSpec.sourcePlugin)
            }

            // 3. 技术规格 · 实时
            DetailCardContainer {
                Text(
                    text = "技术规格 · 实时",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                // 2 列 3 行网格
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SpecGridItem(label = "格式", value = audioSpec.format, modifier = Modifier.weight(1f))
                    SpecGridItem(label = "采样率", value = audioSpec.sampleRateFormatted, modifier = Modifier.weight(1f))
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SpecGridItem(label = "位深", value = audioSpec.bitDepthFormatted, modifier = Modifier.weight(1f))
                    SpecGridItem(label = "比特率", value = audioSpec.bitrateFormatted, modifier = Modifier.weight(1f))
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SpecGridItem(label = "声道", value = audioSpec.channelCountFormatted, modifier = Modifier.weight(1f))
                    SpecGridItem(label = "时长", value = audioSpec.durationFormatted, modifier = Modifier.weight(1f))
                }

                Spacer(Modifier.height(14.dp))

                InfoRow(label = "解码方式", value = audioSpec.decodeMode)
                Spacer(Modifier.height(10.dp))
                InfoRow(label = "解码器", value = audioSpec.decoderName)
            }

            // 4. 歌词卡片
            DetailCardContainer {
                Text(
                    text = "歌词",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // 模式标签 Chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 14.dp)
                ) {
                    // 逐字 / 逐行 胶囊 Chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (lyricSpec.isKaraoke) Color(0xFF9E2A2B) else Color(0xFF7A2526))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (lyricSpec.isKaraoke) "逐字" else "逐行",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // LRC / YRC 格式 Chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = lyricSpec.formatType,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                InfoRow(label = "加载通道", value = lyricSpec.loadChannel)
                Spacer(Modifier.height(10.dp))
                InfoRow(label = "匹配来源", value = lyricSpec.matchSource)
                Spacer(Modifier.height(10.dp))
                InfoRow(label = "总行数", value = "${lyricSpec.totalLines} 行")
            }
        }
    }
}

/** 统一半透明圆角卡片外壳 */
@Composable
private fun DetailCardContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.075f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(16.dp)
    ) {
        content()
    }
}

/** 键值对行（左右对齐） */
@Composable
private fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ) else Modifier
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 14.sp
        )
        Text(
            text = value,
            color = Color.White.copy(alpha = 0.95f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 技术规格 6 宫格单项 */
@Composable
private fun SpecGridItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(0.5.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
