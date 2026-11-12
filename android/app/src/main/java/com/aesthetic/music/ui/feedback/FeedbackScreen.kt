package com.aesthetic.music.ui.feedback

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aesthetic.music.data.api.FeedbackItemDto
import com.aesthetic.music.ui.components.BottomContentSpacer
import com.aesthetic.music.ui.components.SecondaryPageTopBar
import com.aesthetic.music.ui.components.glassCard
import com.aesthetic.music.ui.components.textHigh
import com.aesthetic.music.ui.components.textLow
import com.aesthetic.music.ui.components.textMedium
import com.aesthetic.music.ui.home.HomeAmbientGlowBackground
import com.aesthetic.music.ui.theme.AppColors
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.CardBorder
import com.aesthetic.music.ui.theme.Palette
import com.aesthetic.music.ui.theme.PrimaryBrown
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * 反馈中心：提交反馈（类型/描述/图片/诊断日志/图形验证码）+ 我的反馈。
 */
@Composable
fun FeedbackScreen(
    controller: FeedbackController,
    onBack: () -> Unit,
    onToast: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tab by remember { mutableIntStateOf(0) }
    var type by remember { mutableStateOf(FeedbackType.PLAYBACK) }
    var content by remember { mutableStateOf("") }
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var withLogs by remember { mutableStateOf(true) }
    var captchaInput by remember { mutableStateOf("") }

    val captcha by controller.captcha.collectAsState()
    val captchaLoading by controller.captchaLoading.collectAsState()
    val submitState by controller.submitState.collectAsState()
    val mine by controller.mine.collectAsState()
    val mineLoading by controller.mineLoading.collectAsState()
    val mineError by controller.mineError.collectAsState()

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            images = (images + uris).distinct().take(3)
        }
    }

    LaunchedEffect(Unit) {
        controller.refreshCaptcha()
        controller.loadMine()
    }

    LaunchedEffect(tab) {
        if (tab == 1) controller.loadMine()
    }

    LaunchedEffect(submitState) {
        when (val s = submitState) {
            is FeedbackController.SubmitState.Done -> {
                content = ""
                images = emptyList()
                captchaInput = ""
                controller.resetSubmitState()
                tab = 1
                controller.loadMine()
                onToast("反馈已提交，我们会尽快处理")
            }

            is FeedbackController.SubmitState.Error -> {
                onToast(s.message)
                controller.resetSubmitState()
            }

            else -> Unit
        }
    }

    val submitting = submitState is FeedbackController.SubmitState.Submitting
    val canSubmit = content.trim().length in 10..2000 &&
        captchaInput.trim().length >= 4 &&
        captcha != null &&
        !submitting

    Box(Modifier.fillMaxSize()) {
        HomeAmbientGlowBackground()

        Column(Modifier.fillMaxSize()) {
            SecondaryPageTopBar(title = "反馈中心", onBack = onBack)

            Tabs(selected = tab, onSelect = { tab = it })

            Spacer(Modifier.height(AppSpacing.lg))

            if (tab == 0) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = AppSpacing.page)
                        .padding(bottom = BottomContentSpacer),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
                ) {
                    Text(
                        text = "描述你遇到的问题，我们一起让体验更好。",
                        color = textMedium(),
                        fontSize = 13.sp,
                    )

                    FeedbackCard(
                        icon = Icons.AutoMirrored.Filled.List,
                        title = "问题类型",
                        hint = "选择最接近的一项",
                    ) {
                        TypeChips(selected = type, onSelect = { type = it })
                    }

                    FeedbackCard(
                        icon = Icons.Filled.Edit,
                        title = "问题描述",
                        hint = "必填",
                    ) {
                        DescriptionField(
                            value = content,
                            onValueChange = { content = it.take(2000) },
                        )
                        Spacer(Modifier.height(AppSpacing.sm))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "至少 10 字",
                                color = textLow(),
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${content.length} / 2000",
                                color = if (content.length > 2000) AppColors.crimson else textLow(),
                                fontSize = 11.sp,
                            )
                        }
                    }

                    FeedbackCard(
                        icon = Icons.Filled.Photo,
                        title = "相关图片",
                        hint = "选填 · ${images.size} / 3",
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                            images.forEachIndexed { index, uri ->
                                Box {
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(78.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.55f))
                                            .clickable {
                                                images = images.filterIndexed { i, _ -> i != index }
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "移除",
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp),
                                        )
                                    }
                                }
                            }
                            if (images.size < 3) {
                                Column(
                                    modifier = Modifier
                                        .size(78.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                                        .clickable { picker.launch("image/*") },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = "添加图片",
                                        tint = textLow(),
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text("添加图片", color = textLow(), fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    FeedbackCard(
                        icon = Icons.Filled.BugReport,
                        title = "附上诊断日志",
                        hint = null,
                        trailing = {
                            Switch(
                                checked = withLogs,
                                onCheckedChange = { withLogs = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = PrimaryBrown,
                                ),
                            )
                        },
                    ) {
                        Text(
                            text = "提交时会带上本机版本与设备信息，便于定位问题；" +
                                "日志仅用于排查，不含账号密码。",
                            color = textLow(),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                    }

                    FeedbackCard(
                        icon = Icons.Filled.Lock,
                        title = "图形验证码",
                        hint = "有效期 5 分钟，点击图片可更换",
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(textHigh().copy(alpha = 0.04f))
                                    .padding(horizontal = AppSpacing.lg),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (captchaInput.isEmpty()) {
                                    Text("5 位验证码", color = textLow(), fontSize = 13.sp)
                                }
                                BasicTextField(
                                    value = captchaInput,
                                    onValueChange = { captchaInput = it.filter(Char::isLetterOrDigit).take(5) },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = textHigh(),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                    cursorBrush = SolidColor(PrimaryBrown),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .width(120.dp)
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(textHigh().copy(alpha = 0.04f))
                                    .clickable { controller.refreshCaptcha() },
                                contentAlignment = Alignment.Center,
                            ) {
                                val bitmap = captcha?.image
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = "验证码",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(46.dp),
                                    )
                                } else if (captchaLoading) {
                                    CircularProgressIndicator(
                                        color = textLow(),
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp),
                                    )
                                } else {
                                    Text("点击加载", color = textLow(), fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9999.dp))
                            .background(if (canSubmit) PrimaryBrown else textHigh().copy(alpha = 0.08f))
                            .clickable(enabled = canSubmit) {
                                scope.launch {
                                    controller.submit(
                                        context = context,
                                        type = type,
                                        content = content,
                                        images = images,
                                        withLogs = withLogs,
                                        captchaCode = captchaInput,
                                    )
                                }
                            }
                            .padding(vertical = AppSpacing.lg),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Text(
                                text = "提交反馈",
                                color = if (canSubmit) Color.White else textLow(),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    Spacer(Modifier.height(AppSpacing.section))
                }
            } else {
                MyFeedbackTab(
                    items = mine,
                    loading = mineLoading,
                    error = mineError,
                    onRefresh = { controller.loadMine() },
                )
            }
        }
    }
}

@Composable
private fun Tabs(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.page)
            .clip(RoundedCornerShape(9999.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(9999.dp))
            .padding(3.dp),
    ) {
        listOf("提交反馈", "我的反馈").forEachIndexed { index, label ->
            val active = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9999.dp))
                    .background(
                        if (active) {
                            if (Palette.isDark) Color.White.copy(alpha = 0.12f) else Color.White
                        } else {
                            Color.Transparent
                        }
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = AppSpacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (active) textHigh() else textMedium(),
                    fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun FeedbackCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    hint: String?,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(20.dp))
            .padding(AppSpacing.xl),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textHigh(),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(AppSpacing.sm))
            Text(
                text = title,
                color = textHigh(),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                trailing()
            } else if (hint != null) {
                Text(text = hint, color = textLow(), fontSize = 11.sp)
            }
        }
        if (trailing != null && hint != null) {
            Spacer(Modifier.height(AppSpacing.xxs))
        }
        Spacer(Modifier.height(AppSpacing.lg))
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeChips(selected: FeedbackType, onSelect: (FeedbackType) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        FeedbackType.entries.forEach { item ->
            val active = item == selected
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(9999.dp))
                    .border(
                        width = 1.dp,
                        color = if (active) AppColors.sky else CardBorder,
                        shape = RoundedCornerShape(9999.dp),
                    )
                    .background(if (active) AppColors.sky.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable { onSelect(item) }
                    .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (active) Icons.Filled.Check else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (active) AppColors.skyDeep else textLow(),
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = item.label,
                    color = if (active) textHigh() else textMedium(),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun DescriptionField(value: String, onValueChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 132.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(textHigh().copy(alpha = 0.04f))
            .padding(AppSpacing.lg),
    ) {
        if (value.isEmpty()) {
            Text(
                text = "例如：点击播放后一直加载，重试后仍然出现。",
                color = textLow(),
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                color = textHigh(),
                fontSize = 13.sp,
                lineHeight = 19.sp,
            ),
            cursorBrush = SolidColor(PrimaryBrown),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MyFeedbackTab(
    items: List<FeedbackItemDto>,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.page),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "反馈进度与回复",
                color = textHigh(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            if (loading) {
                CircularProgressIndicator(
                    color = textLow(),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp),
                )
            } else {
                Text(
                    text = "刷新",
                    color = textMedium(),
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onRefresh)
                        .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xxs),
                )
            }
        }

        Spacer(Modifier.height(AppSpacing.lg))

        when {
            items.isEmpty() && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = textLow(), strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            }

            items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = textLow(),
                        modifier = Modifier.size(34.dp),
                    )
                    Spacer(Modifier.height(AppSpacing.md))
                    Text(
                        text = error ?: "还没有反馈",
                        color = textHigh(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(AppSpacing.xs))
                    Text("提交后，可在这里查看处理进度和回复。", color = textMedium(), fontSize = 12.sp)
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = AppSpacing.page,
                    end = AppSpacing.page,
                    bottom = BottomContentSpacer,
                ),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
            ) {
                items(items, key = { it.id }) { item ->
                    FeedbackItemCard(item)
                }
            }
        }
    }
}

@Composable
private fun FeedbackItemCard(item: FeedbackItemDto) {
    val typeLabel = FeedbackType.entries.firstOrNull { it.key == item.type }?.label ?: "反馈"
    val (statusLabel, statusColor) = when (item.status) {
        "resolved" -> "已解决" to AppColors.emerald
        "processing" -> "处理中" to AppColors.skyDeep
        else -> "待处理" to AppColors.amber
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(16.dp))
            .padding(AppSpacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(typeLabel, color = textHigh(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(AppSpacing.sm))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9999.dp))
                    .background(statusColor.copy(alpha = 0.14f))
                    .padding(horizontal = AppSpacing.sm, vertical = 2.dp),
            ) {
                Text(statusLabel, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.weight(1f))
            Text(formatTime(item.createdAt), color = textLow(), fontSize = 11.sp)
        }
        Spacer(Modifier.height(AppSpacing.sm))
        Text(
            text = item.content,
            color = textMedium(),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.reply.isNotBlank()) {
            Spacer(Modifier.height(AppSpacing.sm))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.sky.copy(alpha = 0.10f))
                    .padding(AppSpacing.md),
            ) {
                Column {
                    Text("官方回复", color = AppColors.skyDeep, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(3.dp))
                    Text(item.reply, color = textHigh(), fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return ""
    val pattern = if (ms > 1_000_000_000_000L) "MM-dd HH:mm" else "yyyy-MM-dd HH:mm"
    return SimpleDateFormat(pattern, Locale.CHINA).format(Date(ms))
}
