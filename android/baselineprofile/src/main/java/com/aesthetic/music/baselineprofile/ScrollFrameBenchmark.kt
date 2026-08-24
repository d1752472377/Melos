package com.aesthetic.music.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 滑动帧率基准（Macrobenchmark）：输出帧耗时百分位与掉帧统计，
 * 用于量化优化前后的滑动流畅度对比。
 *
 * 运行命令（连接真机后）：
 *   .\gradlew.bat :baselineprofile:connectedReleaseAndroidTest
 *
 * 建议分别对比 CompilationMode.None（无预编译，模拟首装）与
 * CompilationMode.Partial(baselineProfile)（带 Baseline Profile）的结果差值。
 */
@RunWith(AndroidJUnit4::class)
class ScrollFrameBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollHomeNoPrecompilation() = scrollHome(CompilationMode.None())

    @Test
    fun scrollHomeWithBaselineProfile() =
        scrollHome(CompilationMode.Partial(baselineProfileMode = androidx.benchmark.macro.BaselineProfileMode.Require))

    private fun scrollHome(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = "com.aesthetic.music",
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilationMode,
            iterations = 5,
            startupMode = StartupMode.WARM,
            setupBlock = {
                pressHome()
                startActivityAndWait()
            },
        ) {
            repeat(3) {
                device.swipe(
                    device.displayWidth / 2,
                    (device.displayHeight * 3) / 4,
                    device.displayWidth / 2,
                    device.displayHeight / 4,
                    20,
                )
                device.waitForIdle()
            }
            repeat(3) {
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
