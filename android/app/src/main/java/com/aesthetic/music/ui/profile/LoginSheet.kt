package com.aesthetic.music.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/**
 * 登录 / 注册底部抽屉（现代化设计重构版）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginSheet(
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
) {
    var isRegister by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isInputValid = username.isNotBlank() && password.length >= 6

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface,
        tonalElevation = 0.dp,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 20.dp, bottom = 32.dp)
        ) {
            // Header: 标题 + 关闭图标
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isRegister) "注册 Melos 账号" else "欢迎回来",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (isRegister) "创建账号以开始同步歌单与音乐偏好" else "登录以获取完整的云端体验",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = TextSoft
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Stone100)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // 表单字段
            AuthField(
                label = "用户名",
                placeholder = "请输入用户名",
                value = username,
                onValueChange = { if (it.length <= 20) username = it }
            )

            Spacer(Modifier.height(12.dp))

            AuthField(
                label = "密码",
                placeholder = "至少 6 位字符",
                value = password,
                onValueChange = { if (it.length <= 64) password = it },
                secret = true
            )

            if (isRegister) {
                Spacer(Modifier.height(12.dp))
                AuthField(
                    label = "昵称（可选）",
                    placeholder = "个性化的显示名称",
                    value = nickname,
                    onValueChange = { if (it.length <= 24) nickname = it }
                )
            }

            // 错误信息展示
            if (!error.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(22.dp))

            // 核心 Submit 主按钮 (Level 1 CTA)
            Button(
                onClick = {
                    if (isRegister) onRegister(username, password, nickname)
                    else onLogin(username, password)
                },
                enabled = !busy && isInputValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBrown,
                    contentColor = Color.White,
                    disabledContainerColor = PrimaryBrown.copy(alpha = 0.4f),
                    disabledContentColor = Color.White.copy(alpha = 0.6f)
                )
            ) {
                Text(
                    text = if (busy) "处理中..." else if (isRegister) "创建账号" else "立即登录",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                )
            }

            Spacer(Modifier.height(16.dp))

            // 模式切换辅助入口
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isRegister) "已有 Melos 账号？" else "还没有账号？",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = TextSoft
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (isRegister) "去登录" else "免费注册",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = PrimaryBrown,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { isRegister = !isRegister }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun AuthField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    secret: Boolean = false,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(5.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontSize = 14.sp),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = if (secret) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Stone100)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = TextMuted,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}