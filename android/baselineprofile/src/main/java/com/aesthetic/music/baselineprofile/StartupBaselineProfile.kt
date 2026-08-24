package com.aesthetic.music.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline Profile 生成器：在真机上运行一次，把启动与首页滑动的热路径预编译进 APK。
 *
 * 生成命令（连接真机/模拟器后）：
 *   .\gradlew.bat :app:generateReleaseBaselineProfile
 *
 * 产物自动合并进 :app 的 src/main/baseline-prof.txt 并随 release APK 打包，
 * 消除冷启动 + 首次滑动时 Compose 运行时的 JIT 预热抖动。
 */
@RunWith(AndroidJUnit4::class)
class StartupBaselineProfile {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(packageName = "com.aesthetic.music", includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()

            // 首页上下滑动两轮：覆盖 LazyColumn/LazyRow 组合路径与 Coil 封面加载路径
            repeat(2) {
                device.swipe(
                    device.displayWidth / 2,
                    (device.displayHeight * 3) / 4,
                    device.displayWidth / 2,
                    device.displayHeight / 4,
                    20,
                )
                device.waitForIdle()
            }
            // 滑回顶部，覆盖反向复用路径
            repeat(2) {
                device.swipe(
                    device.displayWidth / 2,
                    device.displayHeight / 4,
                    device.displayWidth / 2,
                    (device.displayHeight * 3) / 4,
                    20,
                )
                device.waitForIdle()
            }
        }
    }
}
