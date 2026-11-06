package com.aesthetic.music.ui.account

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.R
import com.aesthetic.music.ui.account.AuthController
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
import com.aesthetic.music.ui.theme.AppRadius
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.AppColors

private enum class AuthTab {
    LOGIN,
    REGISTER,
}

private enum class LoginMode {
    PASSWORD,
    EMAIL_CODE,
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector? = null,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(AppRadius.lg)
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, if (focused) PrimaryBrown else Color.Transparent, shape),
        placeholder = { Text(placeholder, color = TextMuted, fontSize = 14.sp) },
        leadingIcon = leadingIcon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = if (focused) PrimaryBrown else TextMuted,
                    modifier = Modifier.size(19.dp),
                )
            }
        },
        trailingIcon = trailingIcon,
        singleLine = true,
        shape = shape,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        interactionSource = interaction,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = SurfaceSoft,
            unfocusedContainerColor = SurfaceSoft,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = PrimaryBrown,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
        ),
    )
}

@Composable
fun AuthScreen(auth: AuthController) {
    var activeTab by remember { mutableStateOf(AuthTab.LOGIN) }
    var loginMode by remember { mutableStateOf(LoginMode.PASSWORD) }

    var loginAccount by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var loginEmail by remember { mutableStateOf("") }
    var loginCode by remember { mutableStateOf("") }
    var showLoginPassword by remember { mutableStateOf(false) }

    var regEmail by remember { mutableStateOf("") }
    var regCode by remember { mutableStateOf("") }
    var regUsername by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var regNickname by remember { mutableStateOf("") }
    var showRegPassword by remember { mutableStateOf(false) }
    var agreedToTerms by remember { mutableStateOf(true) }

    val isBusy by auth.isBusy.collectAsState()
    val authError by auth.error.collectAsState()
    val codeCountdown by auth.codeCountdown.collectAsState()
    val isSendingCode by auth.isSendingCode.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        HomeAmbientGlowBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.section, vertical = AppSpacing.section),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(AppSpacing.xxl))

            Image(
                painter = painterResource(id = R.drawable.ic_melos_logo),
                contentDescription = "Melos Logo",
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape),
            )

            Spacer(Modifier.height(AppSpacing.xxxl))

            Text(
                text = "Melos",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                ),
                color = TextPrimary,
            )

            Spacer(Modifier.height(AppSpacing.xs))

            Text(
                text = "探索声音与灵感的纯粹栖所",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = TextMuted,
            )

            Spacer(Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(AppRadius.pill))
                    .background(Stone100)
                    .padding(3.dp),
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    val loginSelected = activeTab == AuthTab.LOGIN
                    val loginBg by animateColorAsState(
                        if (loginSelected) PrimaryBrown else Color.Transparent,
                        tween(180),
                        label = "loginTabBg",
                    )
                    val loginText by animateColorAsState(
                        if (loginSelected) Color.White else TextMuted,
                        tween(180),
                        label = "loginTabText",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(AppRadius.pill))
                            .background(loginBg)
                            .clickable {
                                if (activeTab != AuthTab.LOGIN) {
                                    activeTab = AuthTab.LOGIN
                                    auth.clearError()
                                }
                            }
                            .padding(vertical = AppSpacing.sm),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "登录",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 14.sp,
                                fontWeight = if (loginSelected) FontWeight.Bold else FontWeight.Medium,
                            ),
                            color = loginText,
                        )
                    }

                    val registerSelected = activeTab == AuthTab.REGISTER
                    val registerBg by animateColorAsState(
                        if (registerSelected) PrimaryBrown else Color.Transparent,
                        tween(180),
                        label = "registerTabBg",
                    )
                    val registerText by animateColorAsState(
                        if (registerSelected) Color.White else TextMuted,
                        tween(180),
                        label = "registerTabText",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(AppRadius.pill))
                            .background(registerBg)
                            .clickable {
                                if (activeTab != AuthTab.REGISTER) {
                                    activeTab = AuthTab.REGISTER
                                    auth.clearError()
                                }
                            }
                            .padding(vertical = AppSpacing.sm),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "注册",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 14.sp,
                                fontWeight = if (registerSelected) FontWeight.Bold else FontWeight.Medium,
                            ),
                            color = registerText,
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

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
                        if (loginMode == LoginMode.PASSWORD) {
                            Text(
                                text = "账号或邮箱",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                                color = TextSoft,
                            )
                            Spacer(Modifier.height(AppSpacing.xs))
                            AuthField(
                                value = loginAccount,
                                onValueChange = {
                                    loginAccount = it
                                    if (authError != null) auth.clearError()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = "输入用户名或绑定的邮箱",
                                leadingIcon = Icons.Filled.Person,
                            )

                            Spacer(Modifier.height(AppSpacing.xxl))

                            Text(
                                text = "密码",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                                color = TextSoft,
                            )
                            Spacer(Modifier.height(AppSpacing.xs))
                            AuthField(
                                value = loginPassword,
                                onValueChange = {
                                    loginPassword = it
                                    if (authError != null) auth.clearError()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = "输入登录密码",
                                leadingIcon = Icons.Filled.Lock,
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
                            )

                            Spacer(Modifier.height(AppSpacing.lg))

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
                                            auth.clearError()
                                        }
                                        .padding(AppSpacing.xxs),
                                )
                            }
                        } else {
                            Text(
                                text = "邮箱地址",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                                color = TextSoft,
                            )
                            Spacer(Modifier.height(AppSpacing.xs))
                            AuthField(
                                value = loginEmail,
                                onValueChange = {
                                    loginEmail = it
                                    if (authError != null) auth.clearError()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = "输入注册/绑定的邮箱",
                                leadingIcon = Icons.Filled.Email,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            )

                            Spacer(Modifier.height(AppSpacing.xxl))

                            Text(
                                text = "验证码",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                                color = TextSoft,
                            )
                            Spacer(Modifier.height(AppSpacing.xs))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                            ) {
                                AuthField(
                                    value = loginCode,
                                    onValueChange = {
                                        if (it.length <= 6) loginCode = it
                                        if (authError != null) auth.clearError()
                                    },
                                    modifier = Modifier.weight(1f),
                                placeholder = "6 位数字",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                )

                                Box(
                                    modifier = Modifier
                                        .height(56.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (codeCountdown > 0 || isSendingCode) Stone100 else SurfaceSoft)
                                        .border(1.dp, if (codeCountdown > 0 || isSendingCode) Stone200 else PrimaryBrown, RoundedCornerShape(14.dp))
                                        .clickable(enabled = codeCountdown <= 0 && !isSendingCode) {
                                            auth.sendCode(loginEmail, scene = "login")
                                        }
                                        .padding(horizontal = AppSpacing.xl),
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

                            Spacer(Modifier.height(AppSpacing.lg))

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
                                            auth.clearError()
                                        }
                                        .padding(AppSpacing.xxs),
                                )
                            }
                        }

                        Spacer(Modifier.height(AppSpacing.page))

                        AnimatedVisibility(visible = authError != null) {
                            authError?.let { err ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(AppColors.errorSurface)
                                        .border(1.dp, AppColors.errorBorder, RoundedCornerShape(10.dp))
                                        .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                                ) {
                                    Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = AppColors.errorText, modifier = Modifier.size(16.dp))
                                    Text(text = err, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = AppColors.errorText)
                                }
                                Spacer(Modifier.height(AppSpacing.xl))
                            }
                        }

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
                                        auth.login(loginAccount, loginPassword)
                                    } else {
                                        auth.emailLogin(loginEmail, loginCode)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = AppColors.onAccent)
                            } else {
                                Text(
                                    text = "登录并进入 Melos",
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                                    color = AppColors.onAccent,
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "注册邮箱",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                            color = TextSoft,
                        )
                        Spacer(Modifier.height(AppSpacing.xs))
                        AuthField(
                            value = regEmail,
                            onValueChange = {
                                regEmail = it
                                if (authError != null) auth.clearError()
                            },
                            modifier = Modifier.fillMaxWidth(),
                                placeholder = "输入您的真实邮箱地址",
                            leadingIcon = Icons.Filled.Email,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        )

                        Spacer(Modifier.height(AppSpacing.xl))

                        Text(
                            text = "邮箱验证码",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                            color = TextSoft,
                        )
                        Spacer(Modifier.height(AppSpacing.xs))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                        ) {
                            AuthField(
                                value = regCode,
                                onValueChange = {
                                    if (it.length <= 6) regCode = it
                                    if (authError != null) auth.clearError()
                                },
                                modifier = Modifier.weight(1f),
                                placeholder = "6 位数字验证码",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )

                            Box(
                                modifier = Modifier
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (codeCountdown > 0 || isSendingCode) Stone100 else SurfaceSoft)
                                    .border(1.dp, if (codeCountdown > 0 || isSendingCode) Stone200 else PrimaryBrown, RoundedCornerShape(14.dp))
                                    .clickable(enabled = codeCountdown <= 0 && !isSendingCode) {
                                        auth.sendCode(regEmail, scene = "register")
                                    }
                                    .padding(horizontal = AppSpacing.xl),
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

                        Spacer(Modifier.height(AppSpacing.xl))

                        Text(
                            text = "用户名",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                            color = TextSoft,
                        )
                        Spacer(Modifier.height(AppSpacing.xs))
                        AuthField(
                            value = regUsername,
                            onValueChange = {
                                regUsername = it
                                if (authError != null) auth.clearError()
                            },
                            modifier = Modifier.fillMaxWidth(),
                                placeholder = "2-20 位（字母/数字/中文/下划线）",
                            leadingIcon = Icons.Filled.Person,
                        )

                        Spacer(Modifier.height(AppSpacing.xl))

                        Text(
                            text = "设置密码",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                            color = TextSoft,
                        )
                        Spacer(Modifier.height(AppSpacing.xs))
                        AuthField(
                            value = regPassword,
                            onValueChange = {
                                regPassword = it
                                if (authError != null) auth.clearError()
                            },
                            modifier = Modifier.fillMaxWidth(),
                                placeholder = "5-64 位密码",
                            leadingIcon = Icons.Filled.Lock,
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
                        )

                        Spacer(Modifier.height(AppSpacing.xl))

                        Text(
                            text = "音乐昵称 (选填)",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                            color = TextSoft,
                        )
                        Spacer(Modifier.height(AppSpacing.xs))
                        AuthField(
                            value = regNickname,
                            onValueChange = {
                                regNickname = it
                                if (authError != null) auth.clearError()
                            },
                            modifier = Modifier.fillMaxWidth(),
                                placeholder = "展示在个人主页的昵称",
                        )

                        Spacer(Modifier.height(AppSpacing.xxl))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { agreedToTerms = !agreedToTerms },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(if (agreedToTerms) AppColors.authCheck else Color.Transparent)
                                    .border(1.5.dp, if (agreedToTerms) AppColors.authCheck else Stone200, CircleShape),
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

                        Spacer(Modifier.height(AppSpacing.xxxl))

                        AnimatedVisibility(visible = authError != null) {
                            authError?.let { err ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(AppColors.errorSurface)
                                        .border(1.dp, AppColors.errorBorder, RoundedCornerShape(10.dp))
                                        .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                                ) {
                                    Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = AppColors.errorText, modifier = Modifier.size(16.dp))
                                    Text(text = err, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = AppColors.errorText)
                                }
                                Spacer(Modifier.height(AppSpacing.xl))
                            }
                        }

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
                                    auth.register(
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
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = AppColors.onAccent)
                            } else {
                                Text(
                                    text = "注册并开启音乐之旅",
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                                    color = AppColors.onAccent,
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
