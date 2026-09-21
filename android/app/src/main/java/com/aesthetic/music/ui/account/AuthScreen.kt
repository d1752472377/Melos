package com.aesthetic.music.ui.account

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.R
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.home.HomeAmbientGlowBackground
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

private enum class AuthTab {
    LOGIN,
    REGISTER,
}

private enum class LoginMode {
    PASSWORD,
    EMAIL_CODE,
}

/**
 * 现代极简一体化鉴权门禁界面（Melos Modern Auth Screen）。
 *
 * 遵循 Stitch 暖调纸质素雅风格：
 * 1. 顶部 Brand Header：Melos 品牌标志与精致副标题；
 * 2. 顶级 Segmented Control 胶囊：【登录】与【注册】平滑切换；
 * 3. 登录面板：
 *    - 默认账号密码（支持输入用户名或绑定的邮箱 + 密码）
 *    - 可无缝切换为「邮箱验证码免密登录」
 * 4. 注册面板：
 *    - 输入邮箱 -> 获取 6 位邮件验证码（60秒倒计时） -> 输入用户名与密码 -> 开启音乐之旅
 * 5. 全流程开箱即用，无任何服务器配置冗杂输入。
 */
@Composable
fun AuthScreen(viewModel: MainViewModel) {
    var activeTab by remember { mutableStateOf(AuthTab.LOGIN) }
    var loginMode by remember { mutableStateOf(LoginMode.PASSWORD) }

    // 登录表单
    var loginAccount by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var loginEmail by remember { mutableStateOf("") }
    var loginCode by remember { mutableStateOf("") }
    var showLoginPassword by remember { mutableStateOf(false) }

    // 注册表单
    var regEmail by remember { mutableStateOf("") }
    var regCode by remember { mutableStateOf("") }
    var regUsername by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var regNickname by remember { mutableStateOf("") }
    var showRegPassword by remember { mutableStateOf(false) }
    var agreedToTerms by remember { mutableStateOf(true) }

    // 观察 ViewModel 状态
    val isBusy by viewModel.isAuthBusy.collectAsState()
    val authError by viewModel.authError.collectAsState()
    val codeCountdown by viewModel.codeCountdown.collectAsState()
    val isSendingCode by viewModel.isSendingCode.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 底层动态光晕背景（解决全黑/底色穿透问题，与全局 iOS 拟态质感保持一致）
        HomeAmbientGlowBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))

            // ===== 1. 品牌 Header =====
            Image(
                painter = painterResource(id = R.drawable.ic_melos_logo),
                contentDescription = "Melos Logo",
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape),
            )

            Spacer(Modifier.height(18.dp))

            Text(
                text = "Melos",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                ),
                color = TextPrimary,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "探索声音与灵感的纯粹栖所",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = TextMuted,
            )

            Spacer(Modifier.height(32.dp))

            // ===== 2. 顶级 Tab 切换 (Segmented Pill Control) =====
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(9999.dp))
                    .background(SurfaceSoft)
                    .border(1.dp, CardBorder, RoundedCornerShape(9999.dp))
                    .padding(4.dp),
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // 登录 Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9999.dp))
                            .background(if (activeTab == AuthTab.LOGIN) Surface else Color.Transparent)
                            .clickable {
                                if (activeTab != AuthTab.LOGIN) {
                                    activeTab = AuthTab.LOGIN
                                    viewModel.clearAuthError()
                                }
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "登录",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 14.sp,
                                fontWeight = if (activeTab == AuthTab.LOGIN) FontWeight.Bold else FontWeight.Medium,
                            ),
                            color = if (activeTab == AuthTab.LOGIN) PrimaryBrown else TextMuted,
                        )
                    }

                    // 注册 Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9999.dp))
                            .background(if (activeTab == AuthTab.REGISTER) Surface else Color.Transparent)
                            .clickable {
                                if (activeTab != AuthTab.REGISTER) {
                                    activeTab = AuthTab.REGISTER
                                    viewModel.clearAuthError()
                                }
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "注册",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 14.sp,
                                fontWeight = if (activeTab == AuthTab.REGISTER) FontWeight.Bold else FontWeight.Medium,
                            ),
                            color = if (activeTab == AuthTab.REGISTER) PrimaryBrown else TextMuted,
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // ===== 3. 表单卡片容器 =====
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Surface)
                    .border(1.dp, CardBorder, RoundedCornerShape(24.dp))
                    .padding(22.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (activeTab == AuthTab.LOGIN) {
                        // ----------------- 【登录面板】 -----------------
                        if (loginMode == LoginMode.PASSWORD) {
                            // 账号密码登录
                            Text(
                                text = "账号或邮箱",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                                color = TextSoft,
                            )
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = loginAccount,
                                onValueChange = {
                                    loginAccount = it
                                    if (authError != null) viewModel.clearAuthError()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("输入用户名或绑定的邮箱", color = TextMuted, fontSize = 14.sp) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Person, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryBrown,
                                    unfocusedBorderColor = Stone200,
                                ),
                            )

                            Spacer(Modifier.height(16.dp))

                            Text(
                                text = "密码",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                                color = TextSoft,
                            )
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = loginPassword,
                                onValueChange = {
                                    loginPassword = it
                                    if (authError != null) viewModel.clearAuthError()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("输入登录密码", color = TextMuted, fontSize = 14.sp) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Lock, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                                },
                                trailingIcon = {
                                    IconButton(onClick = { showLoginPassword = !showLoginPassword }) {
                                        Icon(
                                            imageVector = if (showLoginPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = if (showLoginPassword) "隐藏密码" else "显示密码",
                                            tint = TextMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                visualTransformation = if (showLoginPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryBrown,
                                    unfocusedBorderColor = Stone200,
                                ),
                            )

                            Spacer(Modifier.height(12.dp))

                            // 切换到邮箱验证码登录
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                Text(
                                    text = "使用邮箱验证码免密登录",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                                    color = PrimaryBrown,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            loginMode = LoginMode.EMAIL_CODE
                                            viewModel.clearAuthError()
                                        }
                                        .padding(4.dp),
                                )
                            }
                        } else {
                            // 邮箱验证码登录
                            Text(
                                text = "邮箱地址",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                                color = TextSoft,
                            )
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = loginEmail,
                                onValueChange = {
                                    loginEmail = it
                                    if (authError != null) viewModel.clearAuthError()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("输入注册/绑定的邮箱", color = TextMuted, fontSize = 14.sp) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Email, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryBrown,
                                    unfocusedBorderColor = Stone200,
                                ),
                            )

                            Spacer(Modifier.height(16.dp))

                            Text(
                                text = "验证码",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                                color = TextSoft,
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                OutlinedTextField(
                                    value = loginCode,
                                    onValueChange = {
                                        if (it.length <= 6) loginCode = it
                                        if (authError != null) viewModel.clearAuthError()
                                    },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("6 位数字", color = TextMuted, fontSize = 14.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PrimaryBrown,
                                        unfocusedBorderColor = Stone200,
                                    ),
                                )

                                // 发送验证码按钮
                                Box(
                                    modifier = Modifier
                                        .height(52.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (codeCountdown > 0 || isSendingCode) Stone100 else SurfaceSoft)
                                        .border(1.dp, if (codeCountdown > 0 || isSendingCode) Stone200 else PrimaryBrown, RoundedCornerShape(14.dp))
                                        .clickable(enabled = codeCountdown <= 0 && !isSendingCode) {
                                            viewModel.sendCode(loginEmail, scene = "login")
                                        }
                                        .padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isSendingCode) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = PrimaryBrown)
                                    } else {
                                        Text(
                                            text = if (codeCountdown > 0) "${codeCountdown}s 后重发" else "获取验证码",
                                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                                            color = if (codeCountdown > 0) TextMuted else PrimaryBrown,
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            // 切换回密码登录
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                Text(
                                    text = "使用账号密码登录",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                                    color = PrimaryBrown,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            loginMode = LoginMode.PASSWORD
                                            viewModel.clearAuthError()
                                        }
                                        .padding(4.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        // 错误提示区
                        AnimatedVisibility(visible = authError != null) {
                            authError?.let { err ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFFFF1F0))
                                        .border(1.dp, Color(0xFFFFA39E), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = Color(0xFFCF1322), modifier = Modifier.size(16.dp))
                                    Text(text = err, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = Color(0xFFCF1322))
                                }
                                Spacer(Modifier.height(14.dp))
                            }
                        }

                        // 登录主按钮 (52dp Pill)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(9999.dp))
                                .background(
                                    if (isBusy) SolidColor(PrimaryBrown.copy(alpha = 0.45f))
                                    else Brush.horizontalGradient(listOf(PrimaryGradientStart, PrimaryGradientEnd))
                                )
                                .clickable(enabled = !isBusy) {
                                    if (loginMode == LoginMode.PASSWORD) {
                                        viewModel.login(loginAccount, loginPassword)
                                    } else {
                                        viewModel.emailLogin(loginEmail, loginCode)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFFF7F6F2))
                            } else {
                                Text(
                                    text = "登录并进入 Melos",
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                                    color = Color(0xFFF7F6F2),
                                )
                            }
                        }
                    } else {
                        // ----------------- 【注册面板】 -----------------
                        // 1. 邮箱
                        Text(
                            text = "注册邮箱",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                            color = TextSoft,
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = regEmail,
                            onValueChange = {
                                regEmail = it
                                if (authError != null) viewModel.clearAuthError()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("输入您的真实邮箱地址", color = TextMuted, fontSize = 14.sp) },
                            leadingIcon = {
                                Icon(Icons.Filled.Email, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBrown,
                                unfocusedBorderColor = Stone200,
                            ),
                        )

                        Spacer(Modifier.height(14.dp))

                        // 2. 验证码
                        Text(
                            text = "邮箱验证码",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                            color = TextSoft,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedTextField(
                                value = regCode,
                                onValueChange = {
                                    if (it.length <= 6) regCode = it
                                    if (authError != null) viewModel.clearAuthError()
                                },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("6 位数字验证码", color = TextMuted, fontSize = 14.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryBrown,
                                    unfocusedBorderColor = Stone200,
                                ),
                            )

                            Box(
                                modifier = Modifier
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (codeCountdown > 0 || isSendingCode) Stone100 else SurfaceSoft)
                                    .border(1.dp, if (codeCountdown > 0 || isSendingCode) Stone200 else PrimaryBrown, RoundedCornerShape(14.dp))
                                    .clickable(enabled = codeCountdown <= 0 && !isSendingCode) {
                                        viewModel.sendCode(regEmail, scene = "register")
                                    }
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSendingCode) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = PrimaryBrown)
                                } else {
                                    Text(
                                        text = if (codeCountdown > 0) "${codeCountdown}s 后重发" else "获取验证码",
                                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                                        color = if (codeCountdown > 0) TextMuted else PrimaryBrown,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // 3. 用户名
                        Text(
                            text = "用户名",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                            color = TextSoft,
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = regUsername,
                            onValueChange = {
                                regUsername = it
                                if (authError != null) viewModel.clearAuthError()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("2-20 位（字母/数字/中文/下划线）", color = TextMuted, fontSize = 14.sp) },
                            leadingIcon = {
                                Icon(Icons.Filled.Person, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBrown,
                                unfocusedBorderColor = Stone200,
                            ),
                        )

                        Spacer(Modifier.height(14.dp))

                        // 4. 密码
                        Text(
                            text = "设置密码",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                            color = TextSoft,
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = regPassword,
                            onValueChange = {
                                regPassword = it
                                if (authError != null) viewModel.clearAuthError()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("5-64 位密码", color = TextMuted, fontSize = 14.sp) },
                            leadingIcon = {
                                Icon(Icons.Filled.Lock, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                IconButton(onClick = { showRegPassword = !showRegPassword }) {
                                    Icon(
                                        imageVector = if (showRegPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (showRegPassword) "隐藏密码" else "显示密码",
                                        tint = TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            visualTransformation = if (showRegPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBrown,
                                unfocusedBorderColor = Stone200,
                            ),
                        )

                        Spacer(Modifier.height(14.dp))

                        // 5. 昵称（选填）
                        Text(
                            text = "音乐昵称 (选填)",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                            color = TextSoft,
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = regNickname,
                            onValueChange = {
                                regNickname = it
                                if (authError != null) viewModel.clearAuthError()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("展示在个人主页的昵称", color = TextMuted, fontSize = 14.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBrown,
                                unfocusedBorderColor = Stone200,
                            ),
                        )

                        Spacer(Modifier.height(16.dp))

                        // 协议勾选
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { agreedToTerms = !agreedToTerms },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(if (agreedToTerms) Color(0xFF2C2523) else Color.Transparent)
                                    .border(1.5.dp, if (agreedToTerms) Color(0xFF2C2523) else Stone200, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (agreedToTerms) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                }
                            }
                            Text(
                                text = "我已阅读并同意《用户服务协议》与《隐私政策》",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = TextMuted,
                            )
                        }

                        Spacer(Modifier.height(18.dp))

                        // 错误提示区
                        AnimatedVisibility(visible = authError != null) {
                            authError?.let { err ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFFFF1F0))
                                        .border(1.dp, Color(0xFFFFA39E), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = Color(0xFFCF1322), modifier = Modifier.size(16.dp))
                                    Text(text = err, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = Color(0xFFCF1322))
                                }
                                Spacer(Modifier.height(14.dp))
                            }
                        }

                        // 注册主按钮 (52dp Pill)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(9999.dp))
                                .background(
                                    if (isBusy || !agreedToTerms) SolidColor(PrimaryBrown.copy(alpha = 0.45f))
                                    else Brush.horizontalGradient(listOf(PrimaryGradientStart, PrimaryGradientEnd))
                                )
                                .clickable(enabled = !isBusy && agreedToTerms) {
                                    viewModel.register(
                                        username = regUsername,
                                        password = regPassword,
                                        email = regEmail,
                                        code = regCode,
                                        nickname = regNickname
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFFF7F6F2))
                            } else {
                                Text(
                                    text = "注册并开启音乐之旅",
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                                    color = Color(0xFFF7F6F2),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = "Melos Music · 云端账号与漫游曲库就绪",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = TextSoft,
                textAlign = TextAlign.Center,
            )
        }
    }
}
