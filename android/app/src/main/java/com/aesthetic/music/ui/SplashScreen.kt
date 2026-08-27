package com.aesthetic.music.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.BuildConfig
import com.aesthetic.music.R
import kotlinx.coroutines.delay

/**
 * 品牌开屏页：图标 + 品牌语，固定展示 3 秒后淡出。
 *
 * 背景：后端部署在 Vercel（链路较慢），冷启动时首页各区块还在并发拉取，
 * 刚进入会出现"加载中"空态。3 秒开屏正好覆盖网络往返窗口——
 * 开屏期间 AppShell 的会话恢复与内容加载仍在后台并行进行，
 * 淡出后进入主页面时内容基本就绪，感知上是"秒开"。
 *
 * 主题：固定品牌深色（与 launcher 图标底色 #171310 一致），
 * 与 Android 12+ 系统启动画面（values-v31 themes.xml 同色）无缝衔接。
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(true) }

    // 固定展示 3 秒后开始淡出
    LaunchedEffect(Unit) {
        delay(3000)
        visible = false
    }
    // 等淡出动画（400ms）播完再通知父级移除，避免画面闪断
    LaunchedEffect(visible) {
        if (!visible) {
            delay(450)
            onFinished()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(400)),
        exit = fadeOut(animationSpec = tween(400)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF26262B), Color(0xFF17171A), Color(0xFF121214)),
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 品牌图标（launcher 同款 vector：深暖黑圆角底 + 金色声波音符）
                Image(
                    painter = painterResource(R.drawable.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier.size(104.dp),
                )

                Spacer(Modifier.height(30.dp))

                Text(
                    text = "AESTHETIC",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 5.sp,
                    color = Color(0xFFF2B25C),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "MUSIC PLAYER",
                    fontSize = 11.sp,
                    letterSpacing = 4.sp,
                    color = Color(0xFF9E9891),
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "发现好音乐",
                    fontSize = 13.sp,
                    color = Color(0xFFE8E4DF).copy(alpha = 0.75f),
                )
            }

            // 底部版本号
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                color = Color(0xFF6F6A64),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp),
            )
        }
    }
}