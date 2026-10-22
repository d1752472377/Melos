package com.aesthetic.music.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * 让当前界面保持常亮（`View.keepScreenOn`）。
 *
 * ## 为什么要这个
 * 歌词页是"用眼睛看"的页面：用户会把手机放在支架上跟着唱、或者躺着看滚动歌词，
 * 屏幕一熄就得抬手点一下。国内主流播放器（网易云、QQ 音乐）的歌词页/全屏播放页
 * 都默认常亮，属于该场景的标准行为，所以这里也做上。
 *
 * ## 为什么用 `view.keepScreenOn` 而不是 `FLAG_KEEP_SCREEN_ON`
 * 两者等价（`keepScreenOn` 本质就是给这个 View 加 `FLAG_KEEP_SCREEN_ON`），
 * 但 View 上的开关是**随组合生命周期自动收尾**的：离开歌词页时 [onDispose] 会把它复原，
 * 不需要再去 Window 上手动清 flag——少一个"忘了清"的机会。
 *
 * 注意 [onDispose] 里恢复的是**进入前的值**，而不是无脑置 false：
 * 万一外层（比如别的页面）本来就要求常亮，不能把人家关掉。
 *
 * @param enabled 是否常亮；默认 true
 */
@Composable
fun KeepScreenOn(enabled: Boolean = true) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        val previous = view.keepScreenOn
        if (enabled) view.keepScreenOn = true
        onDispose {
            if (enabled) view.keepScreenOn = previous
        }
    }
}
