package com.aesthetic.music.ui.recognition

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.recognition.AudioCapture

import com.aesthetic.music.data.recognition.RecognitionService
import com.aesthetic.music.data.recognition.RecognitionSong
import com.aesthetic.music.data.recognition.RecognitionStore
import com.aesthetic.music.ui.components.SecondaryPageTopBar
import com.aesthetic.music.ui.home.HomeAmbientGlowBackground
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.AppColors
import com.aesthetic.music.ui.theme.CardBorder
import com.aesthetic.music.ui.theme.Palette
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface RecognitionUiState {
    data object Idle : RecognitionUiState
    data class Recording(val elapsedMs: Long, val totalMs: Long) : RecognitionUiState
    data object Matching : RecognitionUiState
    data class Done(val songs: List<RecognitionSong>) : RecognitionUiState
    data class Empty(val message: String) : RecognitionUiState
    data class Failed(val message: String) : RecognitionUiState
}

/**
 * 听歌识曲页。
 *
 * 录 10 秒 → 本地降采样到 8 kHz → 上传短音频做指纹识别。录音会留在本机，
 * 可以在「识别历史」里回放或重新识别。
 */
@Composable
fun RecognitionScreen(
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onPickSong: (RecognitionSong) -> Unit,
    onOpenSearch: () -> Unit,
    onBeforeRecord: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val records by RecognitionStore.records.collectAsState()

    var state by remember { mutableStateOf<RecognitionUiState>(RecognitionUiState.Idle) }
    var recordJob by remember { mutableStateOf<Job?>(null) }
    var matchingSeconds by remember { mutableIntStateOf(0) }

    val busy = state is RecognitionUiState.Recording || state is RecognitionUiState.Matching

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) state = RecognitionUiState.Failed("没有麦克风权限，无法识别")
    }

    LaunchedEffect(state is RecognitionUiState.Matching) {
        if (state is RecognitionUiState.Matching) {
            matchingSeconds = 0
            while (true) {
                delay(1_000)
                matchingSeconds++
            }
        }
    }

    fun start() {
        if (!AudioCapture.hasPermission(context)) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        recordJob?.cancel()
        // 先停掉本机播放，否则麦克风录到的是自己
        onBeforeRecord()
        state = RecognitionUiState.Recording(0L, 10_000L)
        recordJob = scope.launch {
            val pcm = runCatching {
                AudioCapture.record(context, seconds = 10) { elapsed, total ->
                    state = RecognitionUiState.Recording(elapsed, total)
                }
            }.getOrElse { e ->
                state = RecognitionUiState.Failed(e.message ?: "录音失败")
                return@launch
            }
            state = RecognitionUiState.Matching
            val songs = runCatching { RecognitionService.match(pcm) }
                .getOrElse { e ->
                    state = RecognitionUiState.Failed(e.message ?: "识别失败")
                    return@launch
                }
            runCatching { RecognitionStore.add(context, 10_000L, pcm, songs) }
            state = if (songs.isEmpty()) {
                RecognitionUiState.Empty("没有识别到，已把这段录音留在识别历史里")
            } else {
                RecognitionUiState.Done(songs)
            }
        }
    }

    fun back() {
        recordJob?.cancel()
        onBack()
    }

    Box(Modifier.fillMaxSize()) {
        HomeAmbientGlowBackground()

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SecondaryPageTopBar(title = "听歌识曲", onBack = ::back)

            HistoryEntry(
                count = records.size,
                onClick = onOpenHistory,
            )

            Spacer(Modifier.weight(0.55f))

            MicOrb(
                active = state is RecognitionUiState.Recording,
                busy = state is RecognitionUiState.Matching,
                enabled = !busy,
                onClick = { start() },
            )

            Spacer(Modifier.height(AppSpacing.section))

            val title = when (val s = state) {
                is RecognitionUiState.Idle -> "点击识曲"
                is RecognitionUiState.Recording -> "声音采集中"
                is RecognitionUiState.Matching -> "识别中 ${matchingSeconds}秒"
                is RecognitionUiState.Done -> "识别到 ${s.songs.size} 首"
                is RecognitionUiState.Empty -> "暂无识别结果"
                is RecognitionUiState.Failed -> "识别失败"
            }
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(AppSpacing.sm))
            val subtitle = when (val s = state) {
                is RecognitionUiState.Idle -> "录 10 秒，自动识别周围在放的歌"
                is RecognitionUiState.Recording -> "靠近音源，识别更准确"
                is RecognitionUiState.Matching -> "安静环境下更易识别"
                is RecognitionUiState.Done -> "点一首去搜索播放"
                is RecognitionUiState.Empty -> s.message
                is RecognitionUiState.Failed -> s.message
            }
            Text(
                text = subtitle,
                color = TextMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.section),
            )

            if (state is RecognitionUiState.Recording || state is RecognitionUiState.Matching) {
                Spacer(Modifier.height(AppSpacing.sm))
                Text(
                    text = "录音只保存在本机，可在识别历史删除",
                    color = TextSoft,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            (state as? RecognitionUiState.Done)?.let { done ->
                Spacer(Modifier.height(AppSpacing.xl))
                // 结果最多 5 条，小屏上可能放不下：限高后内部滚动，避免把按钮挤出屏幕
                Column(
                    modifier = Modifier
                        .padding(horizontal = AppSpacing.page)
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
                    done.songs.forEach { song ->
                        RecognizedRow(song = song, onClick = { onPickSong(song) })
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            when (state) {
                is RecognitionUiState.Done,
                is RecognitionUiState.Empty,
                is RecognitionUiState.Failed -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppSpacing.page),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg),
                    ) {
                        OutlineButton(
                            text = "重新识别",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                state = RecognitionUiState.Idle
                                start()
                            },
                        )
                        FilledButton(
                            text = "去搜索",
                            modifier = Modifier.weight(1f),
                            onClick = onOpenSearch,
                        )
                    }
                    Spacer(Modifier.height(AppSpacing.page))
                }

                else -> Spacer(Modifier.height(AppSpacing.section))
            }
        }
    }
}

@Composable
private fun HistoryEntry(count: Int, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(9999.dp))
            .background(if (Palette.isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.75f))
            .border(1.dp, CardBorder, RoundedCornerShape(9999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.xl, vertical = AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.History,
            contentDescription = null,
            tint = AppColors.sky,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(AppSpacing.sm))
        Text(
            text = if (count > 0) "识别历史 · $count" else "识别历史",
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
    }
}

@Composable
private fun MicOrb(
    active: Boolean,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "mic")
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave",
    )

    Box(
        modifier = Modifier
            .size(260.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            // 静态底纹：两圈很淡的圆
            drawCircle(AppColors.sky.copy(alpha = 0.06f), radius = radius * 0.92f)
            drawCircle(AppColors.sky.copy(alpha = 0.08f), radius = radius * 0.72f)

            if (active) {
                // 两圈错开的扩散环，模拟"正在收音"
                repeat(2) { index ->
                    val progress = (wave + index * 0.5f) % 1f
                    drawCircle(
                        color = AppColors.sky.copy(alpha = 0.22f * (1f - progress)),
                        radius = radius * (0.52f + 0.42f * progress),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .size(132.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(AppColors.skySoft, AppColors.sky, AppColors.skyDeep),
                        start = Offset.Zero,
                        end = Offset(260f, 260f),
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(34.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = "开始识别",
                    tint = Color.White,
                    modifier = Modifier.size(52.dp),
                )
            }
        }
    }
}

@Composable
private fun RecognizedRow(song: RecognitionSong, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (Palette.isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.8f))
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AppColors.sky.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = AppColors.skyDeep,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(AppSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(
                song.artist.takeIf { it.isNotBlank() },
                song.album.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (sub.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = sub,
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun FilledButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9999.dp))
            .background(PrimaryBrown)
            .clickable(onClick = onClick)
            .padding(vertical = AppSpacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun OutlineButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9999.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(9999.dp))
            .clickable(onClick = onClick)
            .padding(vertical = AppSpacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
