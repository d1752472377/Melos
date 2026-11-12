package com.aesthetic.music.ui.playlist

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.PlaylistCoverPick
import com.aesthetic.music.ui.components.AppBottomSheet
import com.aesthetic.music.ui.components.AppSolidButton
import com.aesthetic.music.ui.components.PlaylistCoverImage
import com.aesthetic.music.ui.library.PlaylistResolveState
import com.aesthetic.music.ui.theme.MiSans
import com.aesthetic.music.ui.theme.AccentCoral
import com.aesthetic.music.ui.theme.CardBorder
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft
import com.aesthetic.music.ui.theme.AppSpacing

data class PlaylistEditorResult(
    val name: String,
    val cover: String,
    val importedSongs: List<Song> = emptyList(),
    val sourcePlatform: String = "",
    val sourcePlaylistId: String = "",
    val sourceName: String = "",
)

@Composable
fun PlaylistEditorSheet(
    title: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (PlaylistEditorResult) -> Unit,
    initialName: String = "",
    initialCover: String = "",
    showPlatformImport: Boolean = false,
    importState: PlaylistResolveState = PlaylistResolveState.Idle,
    onResolveImport: (String) -> Unit = {},
    onClearImport: () -> Unit = {},
    onPickCover: (Uri) -> PlaylistCoverPick? = { null },
    onDeleteCover: (String?) -> Unit = {},
    onToast: (String) -> Unit = {},
) {
    var name by remember { mutableStateOf(initialName) }
    var nameEdited by remember { mutableStateOf(initialName.isNotBlank()) }
    var localCover by remember { mutableStateOf<String?>(null) }
    var pickedDataUrl by remember { mutableStateOf<String?>(null) }
    var importText by remember { mutableStateOf("") }

    val ready = importState as? PlaylistResolveState.Ready

    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val picked = onPickCover(uri)
        if (picked == null) {
            onToast("封面设置失败，请换一张图片试试")
            return@rememberLauncherForActivityResult
        }
        val previous = localCover
        if (previous != null && previous != initialCover) onDeleteCover(previous)
        localCover = picked.path
        pickedDataUrl = picked.dataUrl
    }

    LaunchedEffect(ready?.sourcePlaylistId) {
        val target = ready ?: return@LaunchedEffect
        if (!nameEdited || name.isBlank()) {
            name = target.name
            nameEdited = false
        }
    }

    val displayCover = localCover ?: ready?.coverUrl ?: initialCover.takeIf { it.isNotBlank() }
    val canConfirm = name.isNotBlank()
    val importLoading = importState is PlaylistResolveState.Loading

    AppBottomSheet(
        onDismissRequest = {
            val picked = localCover
            if (picked != null && picked != initialCover) onDeleteCover(picked)
            onDismiss()
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = AppSpacing.section)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = TextPrimary,
            )
            Spacer(Modifier.height(AppSpacing.page))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Stone100)
                        .clickable { coverPicker.launch("image/*") },
                    contentAlignment = Alignment.Center,
                ) {
                    PlaylistCoverImage(
                        model = displayCover,
                        modifier = Modifier.fillMaxSize(),
                        corner = 16.dp,
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(PrimaryBrown)
                            .border(1.5.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = "选择本机封面",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }

                Spacer(Modifier.width(AppSpacing.xxl))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = "歌单名称",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = TextSoft,
                    )
                    Spacer(Modifier.height(AppSpacing.xs))
                    InputBox(
                        value = name,
                        onValueChange = {
                            if (it.length <= 30) {
                                name = it
                                nameEdited = true
                            }
                        },
                        placeholder = "给歌单起个名字",
                        singleLine = true,
                    )
                    Spacer(Modifier.height(AppSpacing.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when {
                                localCover == null -> "点击左侧封面可选择本机图片"
                                localCover.isNullOrBlank() -> "已清除封面，将使用首曲封面"
                                else -> "已选择本机图片封面"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = if (localCover.isNullOrBlank()) TextMuted else PrimaryBrown,
                        )
                        if (localCover == null && initialCover.isNotBlank()) {
                            Spacer(Modifier.width(AppSpacing.sm))
                            Text(
                                text = "清除封面",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = PrimaryBrown,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { localCover = "" }
                                    .padding(horizontal = AppSpacing.xxs, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            if (showPlatformImport) {
                Spacer(Modifier.height(22.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "从其他平台导入",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = TextPrimary,
                    )
                    Spacer(Modifier.width(AppSpacing.xs))
                    Text(
                        text = "可选",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = TextMuted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Stone100)
                            .padding(horizontal = AppSpacing.xs, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.height(AppSpacing.xxs))
                Text(
                    text = "粘贴网易云 / QQ音乐 / 酷狗 / 酷我 的歌单链接或分享文案，自动带出歌单名、封面与歌曲",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                    color = TextMuted,
                )
                Spacer(Modifier.height(AppSpacing.md))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        InputBox(
                            value = importText,
                            onValueChange = { importText = it },
                            placeholder = "歌单链接 / 分享文案",
                            singleLine = false,
                            maxLines = 3,
                        )
                    }
                    Spacer(Modifier.width(AppSpacing.md))
                    AppSolidButton(
                        text = "解析",
                        enabled = importText.isNotBlank() && !importLoading,
                        icon = Icons.Filled.Search,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
                        onClick = { onResolveImport(importText) },
                    )
                }

                Spacer(Modifier.height(AppSpacing.md))
                ImportStatusBlock(
                    state = importState,
                    onClear = {
                        onClearImport()
                        importText = ""
                    },
                )
            }

            Spacer(Modifier.height(AppSpacing.section))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (canConfirm) PrimaryBrown else Stone200)
                    .clickable(enabled = canConfirm) {
                        val inline = pickedDataUrl
                        if (inline != null) {
                            val preview = localCover
                            if (preview != null && preview != initialCover) onDeleteCover(preview)
                        }
                        onConfirm(
                            PlaylistEditorResult(
                                name = name.trim(),
                                cover = inline ?: localCover ?: ready?.coverUrl ?: initialCover,
                                importedSongs = ready?.songs ?: emptyList(),
                                sourcePlatform = ready?.platform ?: "",
                                sourcePlaylistId = ready?.sourcePlaylistId ?: "",
                                sourceName = ready?.name ?: "",
                            )
                        )
                    }
                    .padding(vertical = AppSpacing.xl),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = confirmText,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = if (canConfirm) Color.White else TextMuted,
                )
            }
        }
    }
}

@Composable
private fun ImportStatusBlock(
    state: PlaylistResolveState,
    onClear: () -> Unit,
) {
    when (state) {
        PlaylistResolveState.Idle -> Unit

        PlaylistResolveState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = PrimaryBrown,
            )
            Spacer(Modifier.width(AppSpacing.sm))
            Text(
                text = "正在解析歌单…",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = TextSoft,
            )
        }

        is PlaylistResolveState.Failed -> Text(
            text = state.message,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
            color = AccentCoral,
        )

        is PlaylistResolveState.Ready -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Stone100)
                .padding(AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaylistCoverImage(
                model = state.coverUrl,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp)),
                corner = 8.dp,
            )
            Spacer(Modifier.width(AppSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${state.songs.size} 首 · 来自${state.platformName}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = TextSoft,
                )
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onClear)
                    .padding(AppSpacing.xs),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "移除导入的歌单",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun InputBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        maxLines = maxLines,
        textStyle = TextStyle(fontFamily = MiSans, fontSize = 14.sp, color = TextPrimary),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = AppSpacing.xl, vertical = AppSpacing.lg),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = TextSoft,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    )
                }
                inner()
            }
        },
    )
}

