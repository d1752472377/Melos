package com.aesthetic.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.TextPrimary

/**
 * 二级页面统一顶栏（返回箭头 + 24sp 大标题 + 右侧可选操作）。
 *
 * ## 为什么要有这个组件
 * 此前每个二级页各写一套顶栏，同一个「返回」在不同页面长三种样子：
 *  - 排行榜/歌单广场：裸箭头 24dp + 24sp 粗标题，`top = 状态栏 + 14dp`；
 *  - 账号与云端同步：36dp 描边圆角方框 + 18dp 箭头 + 18sp 标题，且**没有**状态栏内边距，
 *    于是整条顶栏顶在状态栏下面，看起来又高又小、箭头也和别处不一样。
 *
 * 现在收敛成一个组件：所有二级页调用它，样式只有一处定义。
 * 数值沿用工程里出现最多的那一版（RankPage / SheetSquare / Favorites 都是这套）。
 *
 * @param title 页面标题
 * @param onBack 返回回调
 * @param trailing 右侧操作区（可为空）
 */
@Composable
fun SecondaryPageTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = AppSpacing.page,
                end = AppSpacing.xxl,
                top = statusBarTop + AppSpacing.xl,
                bottom = AppSpacing.md,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回",
            tint = TextPrimary,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .padding(AppSpacing.xxs)
                .size(24.dp),
        )
        Spacer(Modifier.width(AppSpacing.md))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (trailing != null) {
            Spacer(Modifier.width(AppSpacing.sm))
            trailing()
        }
    }
}
