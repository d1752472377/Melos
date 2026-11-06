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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aesthetic.music.ui.components.BottomContentSpacer
import com.aesthetic.music.ui.theme.AccentMint
import com.aesthetic.music.ui.theme.Background
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft
import kotlinx.coroutines.delay
import com.aesthetic.music.ui.theme.AppSpacing

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
    onPickAvatar: (Uri) -> String?,
    onSendEmailCode: (email: String, scene: String, onSent: () -> Unit) -> Unit = { _, _, _ -> },
    onBindEmail: (email: String, code: String, onSuccess: () -> Unit) -> Unit = { _, _, _ -> },
    onUnbindEmail: (code: String, onSuccess: () -> Unit) -> Unit = { _, _ -> },
    onChangePassword: (oldPwd: String, newPwd: String, onSuccess: () -> Unit) -> Unit = { _, _, _ -> },
    onShowToast: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var nickname by remember { mutableStateOf(initialNickname) }
    var bio by remember { mutableStateOf(initialBio) }
    var avatarPath by remember { mutableStateOf(initialAvatarPath) }
    val bound = ProfileFormat.isEmailBound(email)

    var showEditNicknameDialog by remember { mutableStateOf(false) }
    var showBindEmailDialog by remember { mutableStateOf(false) }
    var showUnbindEmailDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = onPickAvatar(uri)
            if (savedPath != null) {
                avatarPath = savedPath
                onSave(nickname.trim(), bio.trim(), savedPath)
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
                .padding(horizontal = AppSpacing.page)
                .padding(top = AppSpacing.section)
                .padding(bottom = BottomContentSpacer)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = TextPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(AppSpacing.sm))
                Text(
                    text = "个人资料",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = TextPrimary,
                )
            }

            Spacer(Modifier.height(AppSpacing.section))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface)
                    .padding(AppSpacing.page),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
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
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(AppSpacing.xxl))

                    Column {
                        Text(
                            text = ProfileFormat.displayName(nickname, username),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 25.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = TextPrimary,
                        )
                        Spacer(Modifier.height(AppSpacing.xs))
                        Text(
                            text = "@${ProfileFormat.usernameDisplay(username)}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.sp,
                            ),
                            color = TextMuted,
                        )
                    }
                }

                Spacer(Modifier.height(AppSpacing.xxl))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Stone200.copy(alpha = 0.5f))
                )

                Spacer(Modifier.height(AppSpacing.xxl))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ID",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                            color = TextMuted,
                        )
                        Spacer(Modifier.height(AppSpacing.xxs))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = userId,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = TextPrimary,
                            )
                            Spacer(Modifier.width(AppSpacing.xs))
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "复制 ID",
                                tint = TextMuted,
                                modifier = Modifier
                                    .size(15.dp)
                                    .clickable {
                                        clipboardManager.setText(AnnotatedString(userId))
                                        onShowToast("已复制 ID 到剪贴板")
                                    },
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "加入天数",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                            color = TextMuted,
                        )
                        Spacer(Modifier.height(AppSpacing.xxs))
                        Text(
                            text = ProfileFormat.joinedSummary(createdAt) ?: "未知",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = TextPrimary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(AppSpacing.xxl))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface)
                    .padding(vertical = AppSpacing.sm),
            ) {
                SettingItem(
                    icon = Icons.Filled.Person,
                    title = "修改昵称",
                    value = nickname,
                    onClick = { showEditNicknameDialog = true },
                )
                SettingItem(
                    icon = Icons.Filled.MailOutline,
                    title = "修改邮箱",
                    value = if (bound) ProfileFormat.maskEmail(email) else "未绑定",
                    valueColor = if (bound) AccentMint else TextMuted,
                    onClick = { showBindEmailDialog = true },
                )
                SettingItem(
                    icon = Icons.Filled.Lock,
                    title = "修改登录密码",
                    value = "点击修改",
                    onClick = { showPasswordDialog = true },
                )
            }
        }

        if (showEditNicknameDialog) {
            EditNicknameDialog(
                currentNickname = nickname,
                onDismiss = { showEditNicknameDialog = false },
                onConfirm = { newNickname ->
                    nickname = newNickname
                    onSave(newNickname, bio.trim(), avatarPath)
                    showEditNicknameDialog = false
                    onShowToast("昵称已更新")
                },
            )
        }

        if (showBindEmailDialog) {
            BindEmailDialog(
                isChange = bound,
                onDismiss = { showBindEmailDialog = false },
                onOpenUnbind = { showUnbindEmailDialog = true },
                onSendCode = onSendEmailCode,
                onConfirm = onBindEmail,
                onToast = onShowToast,
            )
        }

        if (showUnbindEmailDialog && !email.isNullOrBlank()) {
            UnbindEmailDialog(
                currentEmail = email,
                onDismiss = { showUnbindEmailDialog = false },
                onSendCode = onSendEmailCode,
                onConfirm = onUnbindEmail,
                onToast = onShowToast,
            )
        }

        if (showPasswordDialog) {
            ChangePasswordDialog(
                onDismiss = { showPasswordDialog = false },
                onConfirm = onChangePassword,
                onToast = onShowToast,
            )
        }
    }
}

@Composable
private fun SettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    valueColor: Color = TextMuted,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.page, vertical = AppSpacing.xxl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PrimaryBrown,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(AppSpacing.xxl))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = valueColor,
            maxLines = 1,
        )
        Spacer(Modifier.width(AppSpacing.sm))
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = TextSoft,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun UnderlineInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    maxLength: Int = Int.MAX_VALUE,
    isPassword: Boolean = false,
    placeholder: String = "",
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextMuted,
        )
        Spacer(Modifier.height(AppSpacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = { if (it.length <= maxLength) onValueChange(it) },
                singleLine = true,
                visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text
                ),
                textStyle = TextStyle(
                    fontSize = 16.sp,
                    color = TextPrimary,
                ),
                cursorBrush = SolidColor(PrimaryBrown),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (value.isEmpty() && placeholder.isNotBlank()) {
                        Text(
                            text = placeholder,
                            style = TextStyle(fontSize = 15.sp, color = TextMuted.copy(alpha = 0.6f)),
                        )
                    }
                    innerTextField()
                },
            )
            if (isPassword) {
                Spacer(Modifier.width(AppSpacing.sm))
                Icon(
                    imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                    tint = TextMuted,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { passwordVisible = !passwordVisible }
                )
            }
        }
        Spacer(Modifier.height(AppSpacing.sm))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Stone200)
        )
    }
}

@Composable
private fun EditNicknameDialog(
    currentNickname: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var input by remember { mutableStateOf(currentNickname) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White,
        title = {
            Text(
                text = "修改昵称",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = TextPrimary,
            )
        },
        text = {
            UnderlineInput(
                value = input,
                onValueChange = { input = it },
                label = "新昵称",
                maxLength = 20,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (input.isBlank()) return@TextButton
                    onConfirm(input.trim())
                }
            ) {
                Text("确认修改", color = PrimaryBrown, fontWeight = FontWeight.Bold)
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
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (oldPwd: String, newPwd: String, onSuccess: () -> Unit) -> Unit,
    onToast: (String) -> Unit,
) {
    var oldPwd by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    var confirmPwd by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White,
        title = {
            Text(
                text = "修改登录密码",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = TextPrimary,
            )
        },
        text = {
            Column {
                UnderlineInput(
                    value = oldPwd,
                    onValueChange = { oldPwd = it },
                    label = "当前密码",
                    isPassword = true,
                    placeholder = "请输入原密码",
                )
                Spacer(Modifier.height(AppSpacing.xxl))
                UnderlineInput(
                    value = newPwd,
                    onValueChange = { newPwd = it },
                    label = "新密码",
                    isPassword = true,
                    placeholder = "请输入 5-64 位新密码",
                )
                Spacer(Modifier.height(AppSpacing.xxl))
                UnderlineInput(
                    value = confirmPwd,
                    onValueChange = { confirmPwd = it },
                    label = "确认新密码",
                    isPassword = true,
                    placeholder = "请再次输入新密码",
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSubmitting,
                onClick = {
                    if (oldPwd.isBlank() || newPwd.isBlank()) {
                        onToast("密码不能为空")
                        return@TextButton
                    }
                    if (newPwd != confirmPwd) {
                        onToast("两次输入的新密码不一致")
                        return@TextButton
                    }
                    isSubmitting = true
                    onConfirm(oldPwd, newPwd) {
                        isSubmitting = false
                        onDismiss()
                        onToast("密码修改成功")
                    }
                },
            ) {
                Text(
                    text = if (isSubmitting) "提交中…" else "确认修改",
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
private fun BindEmailDialog(
    isChange: Boolean,
    onDismiss: () -> Unit,
    onOpenUnbind: () -> Unit = {},
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
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White,
        title = {
            Text(
                text = if (isChange) "更换安全邮箱" else "绑定安全邮箱",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = TextPrimary,
            )
        },
        text = {
            Column {
                Text(
                    text = "请输入要绑定的新邮箱地址，并输入查收到的 6 位验证码以完成核验。",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = TextMuted,
                )
                Spacer(Modifier.height(AppSpacing.xxl))

                UnderlineInput(
                    value = emailInput,
                    onValueChange = { emailInput = it.trim() },
                    label = "新邮箱地址",
                )

                Spacer(Modifier.height(AppSpacing.xxl))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                ) {
                    UnderlineInput(
                        value = codeInput,
                        onValueChange = { codeInput = it.trim() },
                        label = "6 位验证码",
                        maxLength = 6,
                        modifier = Modifier.weight(1f),
                    )

                    val canSend = countdown == 0 && emailInput.isNotBlank()
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (canSend) PrimaryBrown else Stone100)
                            .clickable(enabled = canSend) {
                                val target = emailInput.trim()
                                if (!ProfileFormat.isValidEmail(target)) {
                                    onToast("请输入正确的邮箱地址")
                                    return@clickable
                                }
                                onSendCode(target, "bind") { countdown = 60 }
                            }
                            .padding(horizontal = AppSpacing.xl),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (countdown > 0) "${countdown}s" else "获取验证码",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 13.sp,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isChange) {
                    TextButton(onClick = {
                        onDismiss()
                        onOpenUnbind()
                    }) {
                        Text("解绑邮箱", color = TextSoft)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("取消", color = TextMuted)
                }
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
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White,
        title = {
            Text(
                text = "解绑安全邮箱",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = TextPrimary,
            )
        },
        text = {
            Column {
                Text(
                    text = "为保障账号安全，解绑前需向当前绑定邮箱 ${ProfileFormat.maskEmail(currentEmail)} 发送验证码进行身份核验。",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = TextMuted,
                )
                Spacer(Modifier.height(AppSpacing.xl))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                ) {
                    UnderlineInput(
                        value = codeInput,
                        onValueChange = { codeInput = it.trim() },
                        label = "6 位验证码",
                        maxLength = 6,
                        modifier = Modifier.weight(1f),
                    )

                    val canSend = countdown == 0
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (canSend) PrimaryBrown else Stone100)
                            .clickable(enabled = canSend) {
                                onSendCode(currentEmail, "unbind") { countdown = 60 }
                            }
                            .padding(horizontal = AppSpacing.xl),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (countdown > 0) "${countdown}s" else "获取验证码",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 13.sp,
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