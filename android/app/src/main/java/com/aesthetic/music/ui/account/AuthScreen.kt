package com.aesthetic.music.ui.account

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.theme.AccentMint
import com.aesthetic.music.ui.theme.Background
import com.aesthetic.music.ui.theme.CardBorder
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.PrimaryGradientEnd
import com.aesthetic.music.ui.theme.PrimaryGradientStart
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.SurfaceSoft
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

private enum class LoginMethod {
    EMAIL,
    PASSWORD,
}

private enum class PasswordSubMode {
    LOGIN,
    REGISTER,
}

/**
 * 全屏强制鉴权界面（门禁屏）。
 *
 * 未登录用户彻底拦截在此页面，无法访问应用主界面；
 * 登录成功后自动切换至主界面。
 * 支持：
 *  1. 邮箱验证码免密直登 / 自动开户（主模式）
 *  2. 用户名密码登录 / 注册（兼容模式）
 *  3. 折叠式后端服务器地址配置与连通性测试
 */
@Composable
fun AuthScreen(viewModel: MainViewModel) {
    var method by remember { mutableStateOf(LoginMethod.EMAIL) }
    var passwordMode by remember { mutableStateOf(PasswordSubMode.LOGIN) }

    // 表单状态
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }

    // 服务器设置折叠状态
    var showServerConfig by remember { mutableStateOf(false) }

    // 观察 ViewModel 状态
    val isBusy by viewModel.isAuthBusy.collectAsState()
    val authError by viewModel.authError.collectAsState()
    val codeCountdown by viewModel.codeCountdown.collectAsState()
    val isSendingCode by viewModel.isSendingCode.collectAsState()
    val syncServerUrl by viewModel.syncServerUrl.collectAsState()
    val isTestingSync by viewModel.isTestingSync.collectAsState()

    var serverUrlInput by remember(syncServerUrl) { mutableStateOf(syncServerUrl) }
    var testResult by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(20.dp))

            // ===== 1. 品牌 Header =====
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(PrimaryGradientStart, PrimaryGradientEnd)
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = "Melos Logo",
                    tint = Color.White,
                    modifier = Modifier.size(42.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Melos 音乐",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                ),
                color = TextPrimary,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "轻量 · 纯粹 · 多端实时同步",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = TextMuted,
            )

            Spacer(Modifier.height(28.dp))

            // ===== 2. 登录方式切换卡片 =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceSoft)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TabChip(
                    text = "邮箱验证码登录",
                    selected = method == LoginMethod.EMAIL,
                    modifier = Modifier.weight(1f),
                ) {
                    method = LoginMethod.EMAIL
                    viewModel.clearAuthError()
                }
                TabChip(
                    text = "账号密码登录",
                    selected = method == LoginMethod.PASSWORD,
                    modifier = Modifier.weight(1f),
                ) {
                    method = LoginMethod.PASSWORD
                    viewModel.clearAuthError()
                }
            }

            Spacer(Modifier.height(20.dp))

            // ===== 3. 表单卡片 =====
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(Surface)
                    .padding(20.dp),
            ) {
                Column {
                    if (method == LoginMethod.EMAIL) {
                        // ---------- 邮箱验证码登录表单 ----------
                        Text(
                            text = "邮箱免密直登",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = TextPrimary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "输入邮箱获取验证码，新邮箱验证后将自动开户",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextSoft,
                        )

                        Spacer(Modifier.height(18.dp))

                        OutlinedTextField(
                            value = email,
                            onValueChange = {
                                email = it
                                viewModel.clearAuthError()
                            },
                            label = { Text("邮箱地址") },
                            placeholder = { Text("例如 user@example.com") },
                            leadingIcon = {
                                Icon(Icons.Filled.Email, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBrown,
                                unfocusedBorderColor = Stone200,
                            ),
                        )

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = code,
                                onValueChange = {
                                    if (it.length <= 6) {
                                        code = it
                                        viewModel.clearAuthError()
                                    }
                                },
                                label = { Text("验证码") },
                                placeholder = { Text("6位验证码") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Lock, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryBrown,
                                    unfocusedBorderColor = Stone200,
                                ),
                            )

                            // 获取验证码按钮
                            val isCountdownActive = codeCountdown > 0
                            val canSend = !isCountdownActive && !isSendingCode && email.isNotBlank()
                            Box(
                                modifier = Modifier
                                    .height(56.dp)
                                    .width(118.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (canSend) PrimaryBrown else Stone200
                                    )
                                    .clickable(enabled = canSend) {
                                        viewModel.sendCode(email)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSendingCode) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Text(
                                        text = if (isCountdownActive) "${codeCountdown}s 后重试" else "获取验证码",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        color = if (canSend) Color.White else TextMuted,
                                    )
                                }
                            }
                        }

                        // 错误提示
                        if (!authError.isNullOrBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = authError!!,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = Color(0xFFFF4757),
                            )
                        }

                        Spacer(Modifier.height(20.dp))

                        // 登录按钮
                        val canSubmit = !isBusy && email.isNotBlank() && code.length == 6
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (canSubmit) PrimaryBrown else Stone200)
                                .clickable(enabled = canSubmit) {
                                    viewModel.emailLogin(email, code)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(
                                    text = "登录 / 进入 Melos",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = if (canSubmit) Color.White else TextMuted,
                                )
                            }
                        }
                    } else {
                        // ---------- 账号密码登录 / 注册表单 ----------
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(bottom = 14.dp),
                        ) {
                            SubModeChip(
                                text = "账号登录",
                                selected = passwordMode == PasswordSubMode.LOGIN,
                            ) {
                                passwordMode = PasswordSubMode.LOGIN
                                viewModel.clearAuthError()
                            }
                            SubModeChip(
                                text = "新用户注册",
                                selected = passwordMode == PasswordSubMode.REGISTER,
                            ) {
                                passwordMode = PasswordSubMode.REGISTER
                                viewModel.clearAuthError()
                            }
                        }

                        OutlinedTextField(
                            value = username,
                            onValueChange = {
                                username = it
                                viewModel.clearAuthError()
                            },
                            label = { Text("用户名") },
                            placeholder = { Text("2-20 位字母/数字/下划线/中文") },
                            leadingIcon = {
                                Icon(Icons.Filled.Person, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBrown,
                                unfocusedBorderColor = Stone200,
                            ),
                        )

                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                viewModel.clearAuthError()
                            },
                            label = { Text(if (passwordMode == PasswordSubMode.REGISTER) "密码（5-64 位）" else "密码") },
                            leadingIcon = {
                                Icon(Icons.Filled.Lock, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
                            },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBrown,
                                unfocusedBorderColor = Stone200,
                            ),
                        )

                        if (passwordMode == PasswordSubMode.REGISTER) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = nickname,
                                onValueChange = { nickname = it },
                                label = { Text("个性昵称（选填）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryBrown,
                                    unfocusedBorderColor = Stone200,
                                ),
                            )
                        }

                        // 错误提示
                        if (!authError.isNullOrBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = authError!!,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = Color(0xFFFF4757),
                            )
                        }

                        Spacer(Modifier.height(20.dp))

                        val canSubmit = !isBusy && username.isNotBlank() && password.isNotBlank()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (canSubmit) PrimaryBrown else Stone200)
                                .clickable(enabled = canSubmit) {
                                    if (passwordMode == PasswordSubMode.REGISTER) {
                                        viewModel.register(username, password, nickname)
                                    } else {
                                        viewModel.login(username, password)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(
                                    text = if (passwordMode == PasswordSubMode.REGISTER) "注册并自动登录" else "登 录",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = if (canSubmit) Color.White else TextMuted,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ===== 4. 底部服务器设置折叠卡片 =====
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(Surface)
                    .clickable { showServerConfig = !showServerConfig }
                    .padding(14.dp),
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (syncServerUrl.isNotBlank()) Icons.Filled.CloudDone else Icons.Filled.CloudQueue,
                                contentDescription = null,
                                tint = if (syncServerUrl.isNotBlank()) AccentMint else TextMuted,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "后端服务器设置",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = TextPrimary,
                                )
                                Text(
                                    text = syncServerUrl.ifBlank { "未配置，点击填写" },
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = TextSoft,
                                    maxLines = 1,
                                )
                            }
                        }
                        Icon(
                            imageVector = if (showServerConfig) Icons.Filled.KeyboardArrowDown else Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    AnimatedVisibility(
                        visible = showServerConfig,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            OutlinedTextField(
                                value = serverUrlInput,
                                onValueChange = { serverUrlInput = it },
                                label = { Text("服务器地址") },
                                placeholder = { Text("例如 https://music.example.com") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (serverUrlInput.isNotBlank()) PrimaryBrown else Stone200)
                                        .clickable(enabled = serverUrlInput.isNotBlank()) {
                                            viewModel.saveSyncServerUrl(serverUrlInput)
                                            testResult = "已保存服务器地址"
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("保存地址", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, Stone200, RoundedCornerShape(8.dp))
                                        .background(Surface)
                                        .clickable(enabled = !isTestingSync) {
                                            viewModel.testSyncConnection { _, msg ->
                                                testResult = msg
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = if (isTestingSync) "测试中…" else "测试连接",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                    )
                                }
                            }

                            if (!testResult.isNullOrBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = testResult!!,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = TextSoft,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Melos · 自由轻量音乐体验",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = TextSoft,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun TabChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Surface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            ),
            color = if (selected) PrimaryBrown else TextMuted,
        )
    }
}

@Composable
private fun SubModeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) PrimaryBrown else Stone100)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = if (selected) Color.White else TextMuted,
        )
    }
}
