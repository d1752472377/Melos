package com.aesthetic.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aesthetic.music.data.api.platform.CommentService
import com.aesthetic.music.data.model.MusicComment
import com.aesthetic.music.ui.components.AppBottomSheet
import com.aesthetic.music.ui.theme.AppColors
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * 歌曲评论抽屉（只读）。
 *
 * 只做「看」：热门 / 最新两栏，没有点赞、回复、发布。
 * 三个平台的评论接口匿名即可读，而互动需要账号，没有理由为了评论去引入登录态。
 * 接口本身是非公开接口，取不到时只显示失败与重试，不影响播放。
 */
@Composable
fun CommentSheet(
    songTitle: String,
    platform: String,
    songId: String,
    onDismiss: () -> Unit,
) {
    val dark = Palette.isDark
    val container = if (dark) AppColors.queueContainerDark else AppColors.queueContainerLight
    val titleColor = if (dark) AppColors.queueTitleDark else AppColors.ink
    val subColor = if (dark) AppColors.gray else AppColors.graySoft
    val chipBg = if (dark) Color.White.copy(alpha = 0.10f) else AppColors.queueChipLight
    val accent = AppColors.queueAccent

    var hot by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var total by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(1) }
    var comments by remember { mutableStateOf<List<MusicComment>>(emptyList()) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    fun load(targetHot: Boolean, targetPage: Int) {
        if (targetPage == 1) {
            loading = true
            error = null
        } else {
            loadingMore = true
        }
        scope.launch {
            runCatching {
                CommentService.fetch(
                    platform = platform,
                    songId = songId,
                    page = targetPage,
                    hot = targetHot,
                )
            }.onSuccess { result ->
                // 切过 tab 之后晚到的响应不能写进当前列表
                if (targetHot != hot) return@onSuccess
                comments = if (targetPage == 1) result.comments else comments + result.comments
                total = result.total
                page = targetPage
                error = null
            }.onFailure {
                if (targetPage == 1 && targetHot == hot) error = it.message ?: "评论加载失败"
            }
            if (targetHot != hot) return@launch
            loading = false
            loadingMore = false
        }
    }

    // 切换 tab 或换歌时重新拉第一页
    LaunchedEffect(platform, songId, hot) {
        comments = emptyList()
        total = 0
        page = 1
        load(hot, 1)
    }

    AppBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = container,
        showDragHandle = false,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AppSpacing.page, end = AppSpacing.lg, top = AppSpacing.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "评论",
                    color = titleColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = songTitle,
                    color = subColor,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "关闭",
                tint = subColor,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss)
                    .padding(2.dp),
            )
        }

        Spacer(Modifier.height(AppSpacing.lg))

        Row(
            modifier = Modifier.padding(horizontal = AppSpacing.page),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CommentTab("热门", selected = hot, accent = accent, chipBg = chipBg, subColor = subColor) {
                hot = true
            }
            CommentTab("最新", selected = !hot, accent = accent, chipBg = chipBg, subColor = subColor) {
                hot = false
            }
            Spacer(Modifier.weight(1f))
            if (total > 0) {
                Text(text = "共 $total 条", color = subColor, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(AppSpacing.sm))

        when {
            loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(26.dp),
                    )
                    Spacer(Modifier.height(AppSpacing.md))
                    Text("正在加载评论…", color = subColor, fontSize = 12.sp)
                }
            }

            error != null -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clickable { load(hot, 1) },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error!!, color = subColor, fontSize = 13.sp)
                    Spacer(Modifier.height(AppSpacing.xs))
                    Text("点击重试", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            comments.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (hot) "还没有热门评论" else "还没有评论",
                    color = subColor,
                    fontSize = 13.sp,
                )
            }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .padding(bottom = AppSpacing.page),
            ) {
                items(comments, key = { it.id }) { comment ->
                    CommentRow(
                        comment = comment,
                        titleColor = titleColor,
                        subColor = subColor,
                        chipBg = chipBg,
                    )
                }
                if (comments.size < total) {
                    item(key = "load_more") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !loadingMore) { load(hot, page + 1) }
                                .padding(vertical = AppSpacing.xl),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (loadingMore) "加载中…" else "加载更多",
                                color = if (loadingMore) subColor else accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentTab(
    text: String,
    selected: Boolean,
    accent: Color,
    chipBg: Color,
    subColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) accent.copy(alpha = 0.14f) else chipBg)
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) accent else subColor,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun CommentRow(
    comment: MusicComment,
    titleColor: Color,
    subColor: Color,
    chipBg: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.page, vertical = AppSpacing.md),
    ) {
        Row {
            CommentAvatar(comment.avatar)
            Spacer(Modifier.width(AppSpacing.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = comment.userName.ifBlank { "匿名用户" },
                        color = titleColor.copy(alpha = 0.86f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    comment.timeText?.let {
                        Spacer(Modifier.width(AppSpacing.sm))
                        Text(text = it, color = subColor.copy(alpha = 0.75f), fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = comment.text,
                    color = titleColor,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(AppSpacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    comment.location?.takeIf { it.isNotBlank() }?.let {
                        Text(text = it, color = subColor.copy(alpha = 0.8f), fontSize = 10.sp)
                        Spacer(Modifier.width(AppSpacing.md))
                    }
                    comment.likedCount?.takeIf { it > 0 }?.let {
                        Icon(
                            imageVector = Icons.Filled.ThumbUp,
                            contentDescription = null,
                            tint = subColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(11.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(text = "$it", color = subColor.copy(alpha = 0.8f), fontSize = 10.sp)
                    }
                }

                comment.replies.forEach { reply ->
                    Spacer(Modifier.height(AppSpacing.sm))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .background(chipBg)
                            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                    ) {
                        Text(
                            text = reply.userName.ifBlank { "匿名用户" },
                            color = titleColor.copy(alpha = 0.72f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = reply.text,
                            color = titleColor.copy(alpha = 0.86f),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentAvatar(url: String?) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (Palette.isDark) Color.White.copy(alpha = 0.12f) else AppColors.slate200),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = null,
            tint = AppColors.slate400,
            modifier = Modifier.size(16.dp),
        )
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
