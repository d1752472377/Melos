package com.aesthetic.music.data.settings

import android.util.Log

/**
 * 尽力而为的本地写入：失败只记录日志，不打断调用方。
 *
 * 为什么保留"吞掉异常"：设置、收藏、个人资料这类本地持久化失败，
 * 不应该让播放链路或界面崩溃，降级为"本次不生效"是正确的产品行为。
 *
 * 为什么必须留日志：此前这些位置一律写成 `catch (_: Exception) {}`，完全静默。
 * 一旦用户反馈"设置改了重启又变回去""收藏莫名其妙少了"，
 * 排查时既没有堆栈也没有时间点，只能靠猜。写入失败通常意味着
 * 存储空间不足、prefs 文件损坏或初始化时序出错——这些都需要被看见。
 *
 * 用法：
 * ```
 * fun setThemeMode(mode: ThemeMode) = bestEffort(TAG, "保存主题模式") {
 *     p()?.edit()?.putString(KEY_THEME_MODE, mode.name)?.apply()
 * }
 * ```
 */
internal inline fun bestEffort(tag: String, what: String, block: () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        Log.w(tag, "$what 失败（已降级为忽略本次写入）", e)
    }
}
