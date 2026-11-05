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
import com.aesthetic.music.data.settings.PlaylistCoverStore
import com.aesthetic.music.ui.components.AppBottomSheet
import com.aesthetic.music.ui.components.PlaylistCoverImage
import com.aesthetic.music.ui.library.PlaylistResolveState
import com.aesthetic.music.ui.theme.AccentCoral
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/** 歌单编辑表单的提交结果 */
data class PlaylistEditorResult(
    val name: String,
    /** 最终封面：本机图片绝对路径 / 网络封面 URL；空串表示不设置（回落首曲封面） */
    val cover: String,
    /** 平台导入带来的原始曲目（仅"新建"链路会带） */
    val importedSongs: List<Song> = emptyList(),
    /** 来源平台（空表示本地自建） */
    val sourcePlatform: String = "",
    val sourcePlaylistId: String = "",
    /** 来源平台的原始歌单名，用于溯源展示 */
    val sourceName: String = "",
)

/**
 * 自建歌单的创建 / 编辑表单（底部抽屉）。
 *
 * 相比旧的"只有一个名字输入框"的弹窗，这里补齐了两件事：
 * 1. 封面：可以从系统相册选一张本机图片（拷贝进私有目录，见 [PlaylistCoverStore]）；
 * 2. 可选的平台导入：粘贴链接或分享文案后回填歌单名/封面/曲目，作为歌单的原始记录。
 */
@Composable
fun PlaylistEditorSheet(
    title: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (PlaylistEditorResult) -> Unit,
    initialName: String = "",
    initialCover: String = "",
    /** 编辑已有歌单时隐藏"从其他平台导入"区块 */
    showPlatformImport: Boolean = false,
    /** 平台导入的解析状态（由 PlaylistImportController 提供） */
    importState: PlaylistResolveState = PlaylistResolveState.Idle,
    onResolveImport: (String) -> Unit = {},
    onClearImport: () -> Unit = {},
    onToast: (String) -> Unit = {},
) {
    var name by remember { mutableStateOf(initialName) }
    var nameEdited by remember { mutableStateOf(initialName.isNotBlank()) }
    /** 本次新选的本机封面（尚未落库；取消时删除该文件） */
    var localCover by remember { mutableStateOf<String?>(null) }
    /**
     * 本次新选封面的**可同步副本**（`data:image/jpeg;base64,...`）。
     *
     * 为什么要两份：本机文件用于即时预览（解码快），data URL 才是写进歌单、
     * 能跟着云端同步走的那个值。取消时本地文件会被删掉，data URL 只是内存里的字符串。
     */
    var pickedDataUrl by remember { mutableStateOf<String?>(null) }
    var importText by remember { mutableStateOf("") }

    val ready = importState as? PlaylistResolveState.Ready

    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val path = PlaylistCoverStore.savePickedImage(uri)
        if (path == null) {
            onToast("封面设置失败，请换一张图片试试")
            return@rememberLauncherForActivityResult
        }
        // 换图时清掉上一张未被引用的本机封面，避免文件越攒越多
        val previous = localCover
        if (previous != null && previous != initialCover) PlaylistCoverStore.delete(previous)
        localCover = path
        // 同一张图再压一份内联副本：跨设备同步靠它（见 PlaylistCoverStore.encodeCoverDataUrl）
        pickedDataUrl = PlaylistCoverStore.encodeCoverDataUrl(uri)
    }

    // 解析成功后自动回填名字（用户手动改过就不再覆盖）
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
            // 取消时删掉本次新选、尚未被任何歌单引用的本机封面
            val picked = localCover
            if (picked != null && picked != initialCover) PlaylistCoverStore.delete(picked)
            onDismiss()
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp)
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
            Spacer(Modifier.height(20.dp))

            // ===== 封面 + 名称 =====
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

                Spacer(Modifier.width(16.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = "歌单名称",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = TextSoft,
                    )
                    Spacer(Modifier.height(6.dp))
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
                    Spacer(Modifier.height(8.dp))
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
                        // 已设置过封面时给一个撤销入口，避免"选错图只能删歌单"
                        if (localCover == null && initialCover.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
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
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            // ===== 可选：从其他平台导入 =====
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
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "可选",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = TextMuted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Stone100)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "粘贴网易云 / QQ音乐 / 酷狗 / 酷我 的歌单链接或分享文案，自动带出歌单名、封面与歌曲",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                    color = TextMuted,
                )
                Spacer(Modifier.height(10.dp))

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
                    Spacer(Modifier.width(10.dp))
                    PillButton(
                        text = "解析",
                        enabled = importText.isNotBlank() && !importLoading,
                        onClick = { onResolveImport(importText) },
                    )
                }

                Spacer(Modifier.height(10.dp))
                ImportStatusBlock(
                    state = importState,
                    onClear = {
                        onClearImport()
                        importText = ""
                    },
                )
            }

            Spacer(Modifier.height(24.dp))

            // ===== 主按钮 =====
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (canConfirm) PrimaryBrown else Stone200)
                    .clickable(enabled = canConfirm) {
                        val inline = pickedDataUrl
                        // 用内联副本当封面时，本地预览文件就没有引用者了，顺手删掉
                        if (inline != null) {
                            val preview = localCover
                            if (preview != null && preview != initialCover) PlaylistCoverStore.delete(preview)
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
                    .padding(vertical = 14.dp),
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

/** 解析状态区：进度 / 失败原因 / 已解析出的歌单预览 */
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
            Spacer(Modifier.width(8.dp))
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
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaylistCoverImage(
                model = state.coverUrl,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp)),
                corner = 8.dp,
            )
            Spacer(Modifier.width(10.dp))
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
                    .padding(6.dp),
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

/** 表单输入框（与旧弹窗保持一致的浅底圆角样式） */
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
        textStyle = TextStyle(fontSize = 14.sp, color = TextPrimary),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Stone100)
            .padding(horizontal = 14.dp, vertical = 12.dp),
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

/** 小尺寸主色按钮 */
@Composable
private fun PillButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) PrimaryBrown else Stone200)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = if (enabled) Color.White else TextMuted,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = if (enabled) Color.White else TextMuted,
        )
    }
}
