package com.aesthetic.music.ui.shell.dialogs

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.api.PlaylistLinkParser
import com.aesthetic.music.ui.components.AppBottomSheet
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.library.PlaylistResolveState
import com.aesthetic.music.ui.theme.MiSans
import com.aesthetic.music.ui.theme.AccentCoral
import com.aesthetic.music.ui.theme.CardBorder
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft
import com.aesthetic.music.ui.theme.AppSpacing

@Suppress("DEPRECATION") // 同上：粘贴链接用的旧剪贴板 API
@Composable
fun ImportPlaylistDialog(
    state: PlaylistResolveState,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
    onLoginImport: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current

    var mode by remember { mutableIntStateOf(0) }
    var linkInput by remember { mutableStateOf("") }
    var textInput by remember { mutableStateOf("") }

    val input = if (mode == 0) linkInput else textInput
    val loading = state is PlaylistResolveState.Loading
    val canImport = mode != 2 && input.isNotBlank() && !loading

    fun pasteFromClipboard() {
        val text = clipboard.getText()?.text?.trim().orEmpty()
        if (text.isBlank()) return
        if (mode == 0) linkInput = text else textInput = text
    }

    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = AppSpacing.section)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = "导入歌单",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = TextPrimary,
            )
            Spacer(Modifier.height(AppSpacing.xxs))
            Text(
                text = "导入后会自动收藏到「收藏歌单」，平台原歌单的名字、封面与歌曲一并抓取",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                color = TextMuted,
            )
            Spacer(Modifier.height(AppSpacing.xxxl))

            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                ModeChip("粘贴链接", Icons.Filled.Link, mode == 0) { mode = 0 }
                ModeChip("识别文案", Icons.AutoMirrored.Filled.TextSnippet, mode == 1) { mode = 1 }
                ModeChip("登录账号", Icons.Filled.AccountCircle, mode == 2) { mode = 2 }
            }

            Spacer(Modifier.height(AppSpacing.xxl))

            when (mode) {
                0 -> {
                    Text(
                        text = "支持 网易云音乐 / QQ音乐 / 酷狗音乐 / 酷我音乐 的歌单链接",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = TextSoft,
                    )
                    Spacer(Modifier.height(AppSpacing.sm))
                    InputArea(
                        value = linkInput,
                        onValueChange = { linkInput = it },
                        placeholder = "https://music.163.com/#/playlist?id=5202687076",
                        singleLine = true,
                    )
                    Spacer(Modifier.height(AppSpacing.sm))
                    PasteRow(onPaste = { pasteFromClipboard() })
                }

                1 -> {
                    Text(
                        text = "把音乐 App 里的分享文案整段粘进来，会自动识别歌单名与链接",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = TextSoft,
                    )
                    Spacer(Modifier.height(AppSpacing.sm))
                    InputArea(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = "分享@Melos的歌单《深夜emo》https://music.163.com/#/playlist?id=5202687076 来自网易云音乐",
                        singleLine = false,
                    )
                    Spacer(Modifier.height(AppSpacing.sm))
                    PasteRow(onPaste = { pasteFromClipboard() })
                }

                else -> LoginImportSection(
                    onLoginImport = onLoginImport,
                )
            }

            if (mode != 2) {
                Spacer(Modifier.height(AppSpacing.lg))
                StatusBlock(state)

                Spacer(Modifier.height(AppSpacing.page))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (canImport) PrimaryBrown else Stone200)
                        .clickable(enabled = canImport) { onImport(input) }
                        .padding(vertical = AppSpacing.xl),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (loading) "正在导入…" else "导入到收藏歌单",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = if (canImport) Color.White else TextMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeChip(
    text: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) PrimaryBrown else Stone100)
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.lg, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) Color.White else TextMuted,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (selected) Color.White else TextPrimary,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun InputArea(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 5,
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
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                        maxLines = if (singleLine) 1 else 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun PasteRow(onPaste: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onPaste)
            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
    ) {
        Icon(
            imageVector = Icons.Filled.ContentPaste,
            contentDescription = null,
            tint = PrimaryBrown,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = "读取剪贴板",
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = PrimaryBrown,
        )
    }
}

@Composable
private fun LoginImportSection(onLoginImport: (String) -> Unit) {
    Column {
        Text(
            text = "登录音乐平台账号后，可直接导入你在该平台创建或收藏的歌单",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
            color = TextSoft,
        )
        Spacer(Modifier.height(AppSpacing.lg))

        PlaylistLinkParser.supportedPlatforms.forEach { platform ->
            val name = PlaylistLinkParser.platformName(platform)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Stone100)
                    .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(AppSpacing.md))
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Stone200)
                        .clickable { onLoginImport(name) }
                        .padding(horizontal = AppSpacing.lg, vertical = 7.dp),
                ) {
                    Text(
                        text = "登录导入",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = TextMuted,
                    )
                }
            }
            Spacer(Modifier.height(AppSpacing.sm))
        }

        Text(
            text = "账号导入功能正在开发中，当前请使用「粘贴链接」或「识别文案」导入。",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
            color = TextMuted,
        )
    }
}

@Composable
private fun StatusBlock(state: PlaylistResolveState) {
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
                text = "正在解析歌单信息…",
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
            SongCover(
                url = state.coverUrl,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp)),
                corner = 8.dp,
            )
            Spacer(Modifier.width(AppSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "已导入《${state.name}》",
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
        }
    }
}
