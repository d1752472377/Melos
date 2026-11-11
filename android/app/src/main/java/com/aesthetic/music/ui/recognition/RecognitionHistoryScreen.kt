package com.aesthetic.music.ui.recognition

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.recognition.RecognitionRecord
import com.aesthetic.music.data.recognition.RecognitionService
import com.aesthetic.music.data.recognition.RecognitionSong
import com.aesthetic.music.data.recognition.RecognitionStore
import com.aesthetic.music.ui.components.AppConfirmDialog
import com.aesthetic.music.ui.components.SecondaryPageTopBar
import com.aesthetic.music.ui.home.HomeAmbientGlowBackground
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.AppColors
import com.aesthetic.music.ui.theme.CardBorder
import com.aesthetic.music.ui.theme.Palette
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private enum class HistoryTab(val label: String) {
    SONGS("已识别歌曲"),
    RECORDINGS("全部录音"),
}

/**
 * 识别历史：左边是历次识别到的歌曲，右边是全部录音（可回放、可重新识别）。
 *
 * 录音只存在本机 files/recognition 下；「清空」会把索引和音频一起删掉。
 */
@Composable
fun RecognitionHistoryScreen(
    onBack: () -> Unit,
    onPickSong: (RecognitionSong) -> Unit,
    onOpenRecognition: () -> Unit,
    onToast: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val records by RecognitionStore.records.collectAsState()

    var tab by remember { mutableStateOf(HistoryTab.SONGS) }
    var confirmingClear by remember { mutableStateOf(false) }
    var playingId by remember { mutableLongStateOf(-1L) }
    var recognizingId by remember { mutableLongStateOf(-1L) }

    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                player?.stop()
                player?.release()
            }
            player = null
        }
    }

    fun stopPlayback() {
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        playingId = -1L
    }

    fun togglePlay(record: RecognitionRecord) {
        if (playingId == record.id) {
            stopPlayback()
            return
        }
        val file = RecognitionStore.audioFile(context, record)
        if (file == null) {
            onToast("这段录音已经不存在了")
            return
        }
        stopPlayback()
        runCatching {
            val mp = MediaPlayer()
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener { stopPlayback() }
            mp.setOnErrorListener { _, _, _ ->
                stopPlayback()
                onToast("录音播放失败")
                true
            }
            // 用异步准备，别在点播放这一下阻塞主线程
            mp.setOnPreparedListener {
                it.start()
                playingId = record.id
            }
            mp.prepareAsync()
            player = mp
        }.onFailure {
            stopPlayback()
            onToast("录音播放失败")
        }
    }

    fun reRecognize(record: RecognitionRecord) {
        if (recognizingId != -1L) return
        recognizingId = record.id
        scope.launch {
            val pcm = RecognitionStore.readPcm(context, record)
            if (pcm == null) {
                onToast("这段录音已经不存在了")
                recognizingId = -1L
                return@launch
            }
            runCatching { RecognitionService.match(pcm) }
                .onSuccess { songs ->
                    RecognitionStore.updateSongs(context, record.id, songs)
                    onToast(if (songs.isEmpty()) "仍未识别到" else "识别到 ${songs.size} 首")
                }
                .onFailure { e ->
                    onToast(e.message ?: "识别失败")
                }
            recognizingId = -1L
        }
    }

    // 已识别歌曲按时间倒序摊平，同一首只出现一次
    val recognizedSongs = remember(records) {
        records.asSequence()
            .flatMap { record -> record.songs.asSequence().map { record.createdAt to it } }
            .distinctBy { it.second.id }
            .toList()
    }

    Box(Modifier.fillMaxSize()) {
        HomeAmbientGlowBackground()

        Column(Modifier.fillMaxSize()) {
            SecondaryPageTopBar(
                title = "识别历史",
                onBack = {
                    stopPlayback()
                    onBack()
                },
                trailing = if (records.isNotEmpty()) {
                    {
                        Text(
                            text = "清空",
                            color = TextMuted,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { confirmingClear = true }
                                .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xxs),
                        )
                    }
                } else null,
            )

            HistoryTabs(selected = tab, onSelect = { tab = it })

            Spacer(Modifier.height(AppSpacing.md))

            when {
                records.isEmpty() -> EmptyHistory(onOpenRecognition)
                tab == HistoryTab.SONGS ->
                    if (recognizedSongs.isEmpty()) {
                        EmptyHistory(onOpenRecognition, text = "还没有识别到歌曲")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = AppSpacing.page,
                                end = AppSpacing.page,
                                bottom = AppSpacing.section,
                            ),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                        ) {
                            itemsIndexed(recognizedSongs, key = { _, item -> item.second.id }) { _, item ->
                                SongHistoryRow(song = item.second, timeMs = item.first) {
                                    onPickSong(item.second)
                                }
                            }
                        }
                    }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = AppSpacing.page,
                        end = AppSpacing.page,
                        bottom = AppSpacing.section,
                    ),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
                    itemsIndexed(records, key = { _, r -> r.id }) { index, record ->
                        RecordingRow(
                            record = record,
                            number = records.size - index,
                            playing = playingId == record.id,
                            recognizing = recognizingId == record.id,
                            onPlay = { togglePlay(record) },
                            onReRecognize = { reRecognize(record) },
                        )
                    }
                }
            }
        }
    }

    if (confirmingClear) {
        AppConfirmDialog(
            onDismissRequest = { confirmingClear = false },
            title = "清空识别历史",
            message = "会删除全部识别记录和本机保存的录音，无法恢复。",
            confirmText = "清空",
            onConfirm = {
                confirmingClear = false
                stopPlayback()
                scope.launch { RecognitionStore.clear(context) }
            },
        )
    }
}

@Composable
private fun HistoryTabs(selected: HistoryTab, onSelect: (HistoryTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.page),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.section),
    ) {
        HistoryTab.entries.forEach { item ->
            val active = item == selected
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = item.label,
                    color = if (active) TextPrimary else TextMuted,
                    fontSize = 15.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppSpacing.xxs))
                        .clickable { onSelect(item) }
                        .padding(horizontal = AppSpacing.xxs, vertical = AppSpacing.xxs),
                )
                Spacer(Modifier.height(AppSpacing.xxs))
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(9999.dp))
                        .background(if (active) AppColors.sky else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun SongHistoryRow(song: RecognitionSong, timeMs: Long, onClick: () -> Unit) {
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
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AppColors.sky.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = AppColors.skyDeep,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(AppSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = listOfNotNull(
                    song.artist.takeIf { it.isNotBlank() },
                    formatTime(timeMs),
                ).joinToString(" · "),
                color = TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RecordingRow(
    record: RecognitionRecord,
    number: Int,
    playing: Boolean,
    recognizing: Boolean,
    onPlay: () -> Unit,
    onReRecognize: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = !recognizing, onClick = onReRecognize)
            .padding(horizontal = AppSpacing.xs, vertical = AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .border(1.dp, CardBorder, CircleShape)
                .clickable(enabled = record.audioName != null, onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "暂停" else "播放录音",
                tint = if (record.audioName != null) AppColors.skyDeep else TextSoft,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(AppSpacing.lg))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (record.recognized) "已识别录音 $number" else "未能识别录音 $number",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${formatTime(record.createdAt)}（听歌识曲）",
                color = TextMuted,
                fontSize = 11.sp,
            )
        }
        if (recognizing) {
            CircularProgressIndicator(
                color = AppColors.skyDeep,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
        } else {
            Text(
                text = "重新识别",
                color = AppColors.skyDeep,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onReRecognize)
                    .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xxs),
            )
        }
    }
}

@Composable
private fun EmptyHistory(onOpenRecognition: () -> Unit, text: String = "还没有识别记录") {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppSpacing.page),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            text = text,
            color = TextMuted,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(AppSpacing.xl))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(9999.dp))
                .background(AppColors.sky.copy(alpha = 0.14f))
                .clickable(onClick = onOpenRecognition)
                .padding(horizontal = AppSpacing.section, vertical = AppSpacing.md),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "去识别一首",
                color = AppColors.skyDeep,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.weight(1.2f))
    }
}

private fun formatTime(timeMs: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { this.timeInMillis = timeMs }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    val pattern = if (sameDay) "'今天' HH:mm:ss" else "MM-dd HH:mm"
    return SimpleDateFormat(pattern, Locale.CHINA).format(Date(timeMs))
}
