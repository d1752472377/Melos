package com.aesthetic.music.ui.profile

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aesthetic.music.ui.theme.AccentMint
import com.aesthetic.music.ui.theme.Background
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft
import com.aesthetic.music.ui.components.BottomContentSpacer
import kotlinx.coroutines.delay

/**
 * 个人资料页（重写）。
 *
 * 与旧版的差异（都来自用户反馈）：
 * 1. **显示注册日期**：账号是什么时候注册的，是资料页最该有的信息之一；
 * 2. **去掉性别与出生日期**：这两项此前只存在本地、服务端根本没有对应字段，
 *    填了也不会跟着账号走，属于"看起来很正式但其实是假的"的字段，直接移除；
 * 3. **增加邮箱绑定**：绑定/解绑走服务端 `PUT /api/me`，并以服务端返回为准显示；
 * 4. **增加登录用户名**：用户名（账号）与昵称是两个东西——昵称可改、用户名不可改，
 *    旧版只显示昵称，用户换设备后根本认不出自己登的是哪个账号。
 *
 * 布局：顶部身份卡（头像 + 昵称 + 用户名）→ 账号信息（只读：用户名/Melos ID/注册日期）
 * → 邮箱绑定（可编辑）→ 可编辑资料（昵称/签名）。
 */
@Composable
fun ProfileEditScreen(
    initialNickname: String,
    initialBio: String,
    initialAvatarPath: String?,
    username: String,
    userId: String,
    email: String?,
    createdAt: Long,
    onBack: () -> Unit,
    onSave: (nickname: String, bio: String, avatarPath: String?) -> Unit,
    onSendEmailCode: (email: String, scene: String, onSent: () -> Unit) -> Unit = { _, _, _ -> },
    onBindEmail: (email: String, code: String, onSuccess: () -> Unit) -> Unit = { _, _, _ -> },
    onUnbindEmail: (code: String, onSuccess: () -> Unit) -> Unit = { _, _ -> },
    onShowToast: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var nickname by remember { mutableStateOf(initialNickname) }
    var bio by remember { mutableStateOf(initialBio) }
    var avatarPath by remember { mutableStateOf(initialAvatarPath) }
    val bound = ProfileFormat.isEmailBound(email)
    var showBindDialog by remember { mutableStateOf(false) }
    var showUnbindDialog by remember { mutableStateOf(false) }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = ProfileUtils.saveImageToInternal(context, uri, "avatar")
            if (savedPath != null) {
                avatarPath = savedPath
                onShowToast("头像已更新")
            } else {
                onShowToast("头像更新失败")
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp)
                .padding(bottom = BottomContentSpacer)
                .verticalScroll(rememberScrollState()),
        ) {
            // ===== 顶部导航栏 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .border(1.dp, Stone200, RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(10.dp))
                            .background(Surface)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "个人资料",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = TextPrimary,
                    )
                }

                // 保存按钮（只提交昵称/签名/头像；邮箱有自己的绑定按钮）
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(PrimaryBrown)
                        .clickable {
                            if (nickname.isBlank()) {
                                onShowToast("昵称不能为空")
                                return@clickable
                            }
                            onSave(nickname.trim(), bio.trim(), avatarPath)
                            onBack()
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "保存",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = Color.White,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ===== 头像 + 身份 =====
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .clip(CircleShape)
                        .border(2.dp, PrimaryBrown.copy(alpha = 0.5f), CircleShape)
                        .clickable { avatarPickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center,
                ) {
                    if (!avatarPath.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(avatarPath)
                                .crossfade(true)
                                .build(),
                            contentDescription = "用户头像",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Stone100),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(48.dp),
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(PrimaryBrown)
                            .border(1.5.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = "更换头像",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = ProfileFormat.displayName(nickname, username),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = TextPrimary,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "@${ProfileFormat.usernameDisplay(username)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = TextMuted,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "点击头像更换",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = PrimaryBrown,
                    modifier = Modifier.clickable { avatarPickerLauncher.launch("image/*") },
                )
            }

            Spacer(Modifier.height(22.dp))

            // ===== 账号信息（只读） =====
            SectionCard(title = "账号信息") {
                ReadOnlyRow(
                    label = "登录用户名",
                    value = ProfileFormat.usernameDisplay(username),
                    hint = "登录账号，不可修改",
                )
                ReadOnlyRow(
                    label = "Melos ID",
                    value = userId,
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(userId))
                        onShowToast("已复制 ID 到剪贴板")
                    },
                )
                ReadOnlyRow(
                    label = "注册日期",
                    value = ProfileFormat.registerDate(createdAt) ?: "未知",
                    // 注册时间来自服务端 createdAt：拿不到就显示"未知"，绝不假造一个日期
                    hint = ProfileFormat.joinedSummary(createdAt),
                )
            }

            Spacer(Modifier.height(16.dp))

            // ===== 安全邮箱 =====
            SectionCard(title = "安全邮箱") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (bound) AccentMint.copy(alpha = 0.15f) else Stone100),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (bound) Icons.Filled.VerifiedUser else Icons.Filled.MailOutline,
                            contentDescription = null,
                            tint = if (bound) AccentMint else TextMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (bound) ProfileFormat.maskEmail(email) else "未绑定安全邮箱",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = if (bound) TextPrimary else TextMuted,
                            )
                            if (bound) {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(AccentMint.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = "已验证",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        color = AccentMint,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (bound) "已启用邮箱免密登录与安全保护" else "绑定后可使用邮箱免密验证码登录",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextSoft,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(
                        text = if (bound) "更换邮箱" else "绑定邮箱",
                        primary = true,
                        onClick = { showBindDialog = true },
                    )
                    if (bound) {
                        PillButton(
                            text = "解绑",
                            primary = false,
                            onClick = { showUnbindDialog = true },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "出于安全保障，更换或解绑邮箱均需输入查收到的 6 位邮件验证码。",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = TextSoft,
                )
            }

            Spacer(Modifier.height(16.dp))

            // ===== 可编辑资料 =====
            SectionCard(title = "个人资料") {
                FieldLabel(text = "用户昵称")
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { if (it.length <= 20) nickname = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBrown,
                        unfocusedBorderColor = Stone200,
                    ),
                    placeholder = { Text("输入您的昵称", color = TextMuted) },
                )

                Spacer(Modifier.height(16.dp))

                FieldLabel(text = "个性签名")
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = bio,
                    onValueChange = { if (it.length <= 80) bio = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBrown,
                        unfocusedBorderColor = Stone200,
                    ),
                    placeholder = { Text("介绍一下你喜欢的音乐风格…", color = TextMuted) },
                )
            }
        }

        if (showBindDialog) {
            BindEmailDialog(
                isChange = bound,
                onDismiss = { showBindDialog = false },
                onSendCode = onSendEmailCode,
                onConfirm = onBindEmail,
                onToast = onShowToast,
            )
        }

        if (showUnbindDialog && !email.isNullOrBlank()) {
            UnbindEmailDialog(
                currentEmail = email,
                onDismiss = { showUnbindDialog = false },
                onSendCode = onSendEmailCode,
                onConfirm = onUnbindEmail,
                onToast = onShowToast,
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Stone200, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .padding(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = PrimaryBrown,
        )
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        ),
        color = TextPrimary,
    )
}

/** 只读一行的统一样式：左侧标签 + 右侧值（可复制），用于用户名 / ID / 注册日期 */
@Composable
private fun ReadOnlyRow(
    label: String,
    value: String,
    hint: String? = null,
    onCopy: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Stone100)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = TextSoft,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = TextPrimary,
                maxLines = 1,
            )
            if (!hint.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = TextSoft,
                )
            }
        }
        if (onCopy != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onCopy)
                    .padding(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "复制$label",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun PillButton(text: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (primary) PrimaryBrown else Surface)
            .border(1.dp, if (primary) PrimaryBrown else Stone200, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = if (primary) Color.White else TextPrimary,
        )
    }
}

@Composable
private fun BindEmailDialog(
    isChange: Boolean,
    onDismiss: () -> Unit,
    onSendCode: (email: String, scene: String, onSent: () -> Unit) -> Unit,
    onConfirm: (email: String, code: String, onSuccess: () -> Unit) -> Unit,
    onToast: (String) -> Unit,
) {
    var emailInput by remember { mutableStateOf("") }
    var codeInput by remember { mutableStateOf("") }
    var countdown by remember { mutableStateOf(0) }
    var isSubmitting by remember { mutableStateOf(false) }

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000L)
            countdown--
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = if (isChange) "更换安全邮箱" else "绑定安全邮箱",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column {
                Text(
                    text = "请输入要绑定的新邮箱地址，并输入查收到的 6 位验证码以完成核验。",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = TextSoft,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it.trim() },
                    label = { Text("新邮箱地址") },
                    placeholder = { Text("name@example.com") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBrown,
                        unfocusedBorderColor = Stone200,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { if (it.length <= 6) codeInput = it.trim() },
                        label = { Text("6 位验证码") },
                        placeholder = { Text("验证码") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBrown,
                            unfocusedBorderColor = Stone200,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    val canSend = countdown == 0 && emailInput.isNotBlank()
                    Box(
                        modifier = Modifier
                            .height(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (canSend) PrimaryBrown else Stone100)
                            .border(1.dp, Stone200, RoundedCornerShape(10.dp))
                            .clickable(enabled = canSend) {
                                val target = emailInput.trim()
                                if (!ProfileFormat.isValidEmail(target)) {
                                    onToast("请输入正确的邮箱地址")
                                    return@clickable
                                }
                                onSendCode(target, "bind") {
                                    countdown = 60
                                }
                            }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (countdown > 0) "${countdown}s" else "获取验证码",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = if (canSend) Color.White else TextMuted,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSubmitting,
                onClick = {
                    val target = emailInput.trim()
                    if (!ProfileFormat.isValidEmail(target)) {
                        onToast("请输入正确的邮箱地址")
                        return@TextButton
                    }
                    if (codeInput.length != 6) {
                        onToast("请输入 6 位数字验证码")
                        return@TextButton
                    }
                    isSubmitting = true
                    onConfirm(target, codeInput) {
                        isSubmitting = false
                        onDismiss()
                    }
                },
            ) {
                Text(
                    text = if (isSubmitting) "正在绑定…" else "确认绑定",
                    color = PrimaryBrown,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextMuted)
            }
        },
    )
}

@Composable
private fun UnbindEmailDialog(
    currentEmail: String,
    onDismiss: () -> Unit,
    onSendCode: (email: String, scene: String, onSent: () -> Unit) -> Unit,
    onConfirm: (code: String, onSuccess: () -> Unit) -> Unit,
    onToast: (String) -> Unit,
) {
    var codeInput by remember { mutableStateOf("") }
    var countdown by remember { mutableStateOf(0) }
    var isSubmitting by remember { mutableStateOf(false) }

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000L)
            countdown--
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "解绑安全邮箱",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column {
                Text(
                    text = "为保障账号安全，解绑前需向当前绑定邮箱 ${ProfileFormat.maskEmail(currentEmail)} 发送验证码进行身份核验。",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = TextSoft,
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { if (it.length <= 6) codeInput = it.trim() },
                        label = { Text("6 位验证码") },
                        placeholder = { Text("验证码") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBrown,
                            unfocusedBorderColor = Stone200,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    val canSend = countdown == 0
                    Box(
                        modifier = Modifier
                            .height(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (canSend) PrimaryBrown else Stone100)
                            .border(1.dp, Stone200, RoundedCornerShape(10.dp))
                            .clickable(enabled = canSend) {
                                onSendCode(currentEmail, "unbind") {
                                    countdown = 60
                                }
                            }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (countdown > 0) "${countdown}s" else "获取验证码",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = if (canSend) Color.White else TextMuted,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSubmitting,
                onClick = {
                    if (codeInput.length != 6) {
                        onToast("请输入 6 位数字验证码")
                        return@TextButton
                    }
                    isSubmitting = true
                    onConfirm(codeInput) {
                        isSubmitting = false
                        onDismiss()
                    }
                },
            ) {
                Text(
                    text = if (isSubmitting) "正在解绑…" else "确认解绑",
                    color = PrimaryBrown,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextMuted)
            }
        },
    )
}

