package com.aesthetic.music.ui.search

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.aesthetic.music.ui.theme.CardBorder
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft
import com.aesthetic.music.ui.components.glassCard
import com.aesthetic.music.R
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.AppColors
// --- 1. 更新数据模型：加入图标资源字段 ---
data class PlatformItem(
    val name: String,
    val id: String?,
    val fallbackColor: Color, // 备用颜色（没图标时显示彩色圆圈）
    @param:DrawableRes val iconRes: Int? = null // 真实的图片资源 ID
)

// --- 2. 配置平台数据源 ---
// TODO: 把 iconRes 换成你项目里真实的图片资源，例如 R.drawable.ic_qq_music
private val PlatformFilters = listOf(
    PlatformItem("全部平台", null, AppColors.brandUnknown, R.drawable.ic_music),

    PlatformItem("网易云", "netease", AppColors.brandNetease, R.drawable.ic_netease_music),
    PlatformItem("QQ", "qq", AppColors.brandQQSearch, R.drawable.ic_qq_music),
    PlatformItem("酷狗", "kugou", AppColors.brandKugou, R.drawable.ic_kugou_music),
    PlatformItem("酷我", "kuwo", AppColors.brandKuwo, R.drawable.ic_kuwo_music)
)
@Composable
fun SearchInputBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    platform: String?,
    onPlatformSelect: (String?) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .glassCard(shape = RoundedCornerShape(22.dp))
            .clickable {
                focusRequester.requestFocus()
                keyboard?.show()
            }
            .padding(start = AppSpacing.xs, end = AppSpacing.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlatformSelector(
            current = platform,
            onSelect = onPlatformSelect,
        )

        Spacer(Modifier.width(AppSpacing.sm))

        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    color = TextPrimary,
                ),
                cursorBrush = SolidColor(PrimaryBrown),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        onSubmit()
                        keyboard?.hide()
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
            if (query.isEmpty()) {
                Text(
                    "搜索歌曲、歌手、歌单...",
                    color = TextSoft,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                )
            }
        }

        if (query.isNotEmpty()) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "清空",
                tint = TextMuted,
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onQueryChange("") },
            )
        }
    }
}

@Composable
private fun PlatformSelector(
    current: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    // 获取当前选中的平台对象，如果没选则默认取第一个（全部平台）
    val currentPlatform = PlatformFilters.firstOrNull { it.id == current } ?: PlatformFilters.first()

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    val density = LocalDensity.current
    val yOffsetPx = with(density) { 8.dp.roundToPx() }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { expanded = true }
                )
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .padding(AppSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // --- 核心修改点：动态显示当前平台图标 ---
            PlatformIcon(item = currentPlatform, size = 22.dp)
            // --------------------------------------

            Spacer(Modifier.width(2.dp))

            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = "选择搜索平台",
                tint = TextMuted,
                modifier = Modifier.size(14.dp)
            )
        }

        if (expanded) {
            Popup(
                onDismissRequest = { expanded = false },
                offset = IntOffset(x = 0, y = yOffsetPx),
                properties = PopupProperties(focusable = true)
            ) {
                Column(
                    modifier = Modifier
                        .width(150.dp) // 稍微加宽一点给图标留空间
                        .shadow(
                            elevation = 12.dp,
                            shape = RoundedCornerShape(16.dp),
                            ambientColor = Color.Black.copy(alpha = 0.1f),
                            spotColor = Color.Black.copy(alpha = 0.1f)
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .background(Surface)
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                        .padding(vertical = AppSpacing.xs)
                ) {
                    PlatformFilters.forEach { item ->
                        PlatformMenuItem(
                            item = item,
                            isSelected = item.id == current,
                            onClick = {
                                expanded = false
                                onSelect(item.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

// --- 3. 提取出统一的图标渲染组件 ---
@Composable
private fun PlatformIcon(item: PlatformItem, size: androidx.compose.ui.unit.Dp) {
    if (item.iconRes != null) {
        // 统一由代码里的 size 控制显示尺寸（已去掉圆形裁剪），按 ContentScale.Fit 等比缩放，
        // 这样各图标即便固有尺寸/留白不同，显示的边长也一致。
        Image(
            painter = painterResource(id = item.iconRes),
            contentDescription = item.name,
            modifier = Modifier.size(size),
            contentScale = ContentScale.Fit,
        )
    } else {
        // 如果没有图片，回退显示彩色圆圈+首字母（为了让你现在能跑通）
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(item.fallbackColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = item.name.take(1),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PlatformMenuItem(
    item: PlatformItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = AppSpacing.xxl, vertical = AppSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 使用统一的图标组件
        PlatformIcon(item = item, size = 22.dp)

        Spacer(Modifier.width(AppSpacing.lg))

        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )

        if (isSelected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "已选中",
                tint = AppColors.brandNetease,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}