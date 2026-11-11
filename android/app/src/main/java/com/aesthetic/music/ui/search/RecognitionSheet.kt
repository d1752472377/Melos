package com.aesthetic.music.ui.search

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.recognition.AudioCapture
import com.aesthetic.music.data.recognition.RecognitionService
import com.aesthetic.music.data.recognition.RecognitionSong
import com.aesthetic.music.ui.components.AppBottomSheet
import com.aesthetic.music.ui.theme.AccentCoral
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.CardBorder
import com.aesthetic.music.ui.theme.Palette
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft
import kotlinx.coroutines.launch

private sealed interface RecognitionState {
    data object Idle : RecognitionState
    data class Recording(val elapsedMs: Long, val totalMs: Long) : RecognitionState
    data object Matching : RecognitionState
    data class Done(val songs: List<RecognitionSong>) : RecognitionState
    data class Failed(val message: String) : RecognitionState
}

/**
 * 听歌识曲抽屉。
 *
 * 点一下开始，录 10 秒后自动上传识别，不用按住。音频只在内存里处理、不落盘，
 * 识别完即丢弃；开始前明确告知会上传一段短音频给识别服务。
 */
@Composable
fun RecognitionSheet(
    onDismiss: () -> Unit,
    onPickSong: (RecognitionSong) -> Unit,
    onBeforeRecord: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RecognitionState>(RecognitionState.Idle) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            state = RecognitionState.Failed("没有麦克风权限，无法识别")
        }
        // 授权成功后由用户再点一次开始，避免"授予权限即偷偷录音"
    }

    fun start() {
        if (!AudioCapture.hasPermission(context)) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        // 先停掉自己的播放，否则录到的是本机在放的声音
        onBeforeRecord()
        state = RecognitionState.Recording(0L, 10_000L)
        scope.launch {
            val pcm = runCatching {
                AudioCapture.record(context, seconds = 10) { elapsed, total ->
                    // 快照状态可以跨线程写；AudioCapture 内部已把回调节流到约 10 次/秒
                    state = RecognitionState.Recording(elapsed, total)
                }
            }.getOrElse { e ->
                state = RecognitionState.Failed(e.message ?: "录音失败")
                return@launch
            }
            state = RecognitionState.Matching
            runCatching { RecognitionService.match(pcm) }
                .onSuccess { songs ->
                    state = if (songs.isEmpty()) {
                        RecognitionState.Failed("没有识别到，换个环境再试一次")
                    } else {
                        RecognitionState.Done(songs)
                    }
                }
                .onFailure { e ->
                    state = RecognitionState.Failed(e.message ?: "识别失败")
                }
        }
    }

    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.section)
                .padding(bottom = AppSpacing.page),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "听歌识曲",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = TextMuted,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss),
                )
            }

            Spacer(Modifier.height(AppSpacing.md))

            MicCircle(state = state, onClick = { if (state !is RecognitionState.Recording) start() })

            Spacer(Modifier.height(AppSpacing.lg))

            val hint = when (val s = state) {
                is RecognitionState.Idle -> "点一下开始，自动听 10 秒"
                is RecognitionState.Recording -> "正在听… ${(s.elapsedMs / 1000).coerceAtMost(10)}s / 10s"
                is RecognitionState.Matching -> "正在识别…"
                is RecognitionState.Done -> "识别到 ${s.songs.size} 首，点一首去搜索"
                is RecognitionState.Failed -> s.message
            }
            Text(
                text = hint,
                color = if (state is RecognitionState.Failed) AccentCoral else TextMuted,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth(),
            )

            if (state is RecognitionState.Recording || state is RecognitionState.Matching) {
                Spacer(Modifier.height(AppSpacing.xs))
                Text(
                    text = "音频只在本机处理，识别完即丢弃",
                    color = TextSoft,
                    fontSize = 11.sp,
                )
            }

            (state as? RecognitionState.Done)?.let { done ->
                Spacer(Modifier.height(AppSpacing.lg))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                ) {
                    items(done.songs, key = { it.id }) { song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (Palette.isDark) Color.White.copy(alpha = 0.06f) else Stone100)
                                .border(
                                    1.dp,
                                    CardBorder,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable { onPickSong(song) }
                                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = PrimaryBrown,
                                modifier = Modifier.size(18.dp),
                            )
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
                }
            }

            if (state is RecognitionState.Failed || state is RecognitionState.Done) {
                Spacer(Modifier.height(AppSpacing.lg))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(PrimaryBrown)
                        .clickable {
                            state = RecognitionState.Idle
                            start()
                        }
                        .padding(vertical = AppSpacing.lg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "再试一次",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun MicCircle(state: RecognitionState, onClick: () -> Unit) {
    val active = state is RecognitionState.Recording || state is RecognitionState.Matching
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (active) 1.12f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        if (active) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = 0.18f
                    }
                    .clip(CircleShape)
                    .background(AccentCoral),
            )
        }
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(if (active) AccentCoral else PrimaryBrown)
                .clickable(enabled = !active, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (state is RecognitionState.Matching) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = "开始识别",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}
