package com.aesthetic.music.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.ui.components.textHigh
import java.util.Calendar

/*
 * ============================================================================
 * GZ Music 首页顶栏 —— 逐值还原
 * ============================================================================
 *
 * 来源：gz_decompiled/sources/com/gzmusic/app/feature/home/HomeScreenKt.java
 *      `HomeTopBar` = 行 9571..9624（全文仅 54 行，**不含任何时段问候**）
 *
 * 重要事实（与任务书假设不同，已在报告中说明）：
 *   GZ 的手机首页顶栏 HomeTopBar **只有一行标题「发现好音乐」**。
 *   它没有问候语、没有日期、没有搜索/设置按钮、没有任何背景/渐变/毛玻璃层，
 *   也没有任何可点击回调（HomeScreenKt.java:9620-9623 只挂了一个
 *   `ComposeNodeLifecycle`/`updateScope` 兼容块 `np6VarV.d = new t(i2, 6)`，
 *   那是 JADX 为 `Composer.endRestartGroup` 还原出的空壳，不是业务回调）。
 *   搜索栏是**兄弟节点**，与顶栏平级（HomeScreenKt.java:8698 -> 8714
 *   `HomeTopBar(...)` 之后 `SearchBar(...)`），不在 HomeTopBar 内部。
 *
 * 「时段问候 / 时段场景」在 GZ 里真实存在，但**不在顶栏**，而在首页 ViewModel 与
 * 统计页：见本文件 resolveTimeScene() / resolveTimeSlotLabel()。顶栏渲染时不消费它。
 *
 * ---------------------------------------------------------------------------
 * 反编译符号解码（均为本次在 defpackage/ 下逐类核对，非猜测）
 * ---------------------------------------------------------------------------
 *   hp1.z0(m, f)               = m.then(<ModifierNodeElement>)          hp1.java (z0 方法体)
 *   hp1.v                       = (WindowInsets) -> insets.statusBars    hp1.java 字段
 *                                 (hp1.v = new ln8(7); ln8.java case 7 -> ((vv8)obj).f;
 *                                  vv8.java -> .f = cb6.d(1, "statusBars"))
 *                               => 合起来就是 Modifier.statusBarsPadding()
 *   hp1.r0(m, a, b, c, d)      = Modifier.padding(start=a, top=b, end=c, bottom=d)
 *   hp1.s0(m, a, b, c, d, mask) = 同上（带默认参数掩码）
 *   ij7.g(m, f)                = Modifier.fillMaxWidth(f)
 *                                 (ij7.java g -> new wk2(uy1.d, f); uy1.d = "Horizontal")
 *   ez3(w, fill)               = Modifier.weight(w, fill)
 *                                 (ez3 -> j05 element; fz3 O() -> fy6{a=w,b=fill} = RowColumnParentData)
 *   gy6.a(h, v, c, i)          = remember(h, v) { RowMeasurePolicy(h, v) }
 *                                 (iy6.java:177 toString = "RowMeasurePolicy(horizontalArrangement=…, verticalAlignment=…)")
 *   aa1.a(v, h, c, i)          = remember(v, h) { ColumnMeasurePolicy(v, h) }
 *                                 (ba1.java:177 toString = "ColumnMeasurePolicy(verticalArrangement=…, horizontalAlignment=…)")
 *   zh.b                       = Arrangement.Start   (g10.java toString default 分支；g10 implements i10 = Arrangement.Horizontal)
 *   zh.d                       = Arrangement.Top     (h10.java toString default 分支；h10 implements k10 = Arrangement.Vertical)
 *   il0.f0                     = Alignment.CenterVertically (wg0.java toString = "Vertical(bias=…)"；il0.f0 = new wg0(0.0f))
 *   il0.h0                     = Alignment.Start    (vg0.java toString = "Horizontal(bias=…)"；il0.h0 = new vg0(-1.0f))
 *   c88.b(text, modifier, color, fontStyle, fontSize, fontWeight, fontFamily,
 *         letterSpacing, textDecoration, lineHeight, textAlign, …)
 *                              = androidx.compose.material3.Text(...)
 *   co7.j(int)                 = int.sp              (co7.java j -> m(i, 4294967296L) = TextUnitType.Sp)
 *   co7.i(double)              = double.sp           (co7.java i -> m((float)d, 4294967296L))
 *   sq2                        = androidx.compose.ui.text.font.FontWeight (sq2.java:39-49 把 int 当 weight 存储)
 *   sq2.X                      = FontWeight(800)      (sq2.java:27-34: X = new sq2(800))
 * ============================================================================
 */

// ============================================================================
// 一、时段场景（纯函数，可测试）
// ============================================================================

/**
 * 首页「时段场景」。
 *
 * 逐值对应 GZ `defpackage/pd3.java` 的三个字段：
 *  - [key]      = `pd3.a`（网络请求用的时段键）
 *  - [category] = `pd3.b`（场景名）
 *  - [title]    = `pd3.c`（场景标题）
 * GZ 由 `defpackage/me3.java:1031-1043` 的 `l(me3)` 依据
 * `Calendar.getInstance().get(11)`（= `Calendar.HOUR_OF_DAY`，me3.java:1032）生成。
 */
data class HomeTimeScene(
    val key: String,
    val category: String,
    val title: String,
)

/**
 * GZ `defpackage/me3.java:1031-1043`（HomeViewModel 的 `l(me3)`）逐分支还原。
 *
 * 原始判断顺序（务必按序短路，本实现用 when 保持同一顺序）：
 * ```
 * int i = Calendar.getInstance().get(11);          // me3.java:1032  HOUR_OF_DAY
 * if (6 <= i && i < 11)  return pd3("morning",   "清晨",   "清晨时光");  // me3.java:1033-1035
 * if (11 <= i && i < 14) return pd3("noon",      "午休",   "午休小憩");  // me3.java:1036-1038
 * if (14 > i || i >= 18) return (18 > i || i >= 23)
 *                          ? pd3("night",   "夜晚", "夜晚好梦")        // me3.java:1040
 *                          : pd3("evening", "散步", "晚风散步");        // me3.java:1040
 * return pd3("afternoon", "下午茶", "下午茶歇");                        // me3.java:1042
 * ```
 * 展开后的完整小时 → 场景对照：
 * | hour      | key         | category | title      |
 * |-----------|-------------|----------|------------|
 * | 0..5      | `night`     | 夜晚     | 夜晚好梦   |
 * | 6..10     | `morning`   | 清晨     | 清晨时光   |
 * | 11..13    | `noon`      | 午休     | 午休小憩   |
 * | 14..17    | `afternoon` | 下午茶   | 下午茶歇   |
 * | 18..22    | `evening`   | 散步     | 晚风散步   |
 * | 23        | `night`     | 夜晚     | 夜晚好梦   |
 *
 * `me3.l()` 的结果被写入 `me3.e0`（`me3.java:1862-1864`，`w(boolean)` 内），
 * 并由 `me3.java:1881` 的 `iq3.e(this.p0, pd3VarL.a)` 做「同时段才复用缓存」判断。
 * 全仓检索确认：**没有任何 Composable 读取 `me3.e0`**，也没有渲染这三个文案，
 * 也没有为这 5 个时段键定义任何配色。
 */
fun resolveTimeScene(hour: Int): HomeTimeScene = when {
    // me3.java:1033-1035
    hour in 6..10 -> HomeTimeScene(key = "morning", category = "清晨", title = "清晨时光")
    // me3.java:1036-1038
    hour in 11..13 -> HomeTimeScene(key = "noon", category = "午休", title = "午休小憩")
    // me3.java:1039-1041  —— 条件 `14 > i || i >= 18` 且 `18 > i || i >= 23`
    hour in 18..22 -> HomeTimeScene(key = "evening", category = "散步", title = "晚风散步")
    // me3.java:1040 的 night 分支（hour < 14 中未被前面吃掉的部分 0..5，或 hour >= 23）
    hour < 6 || hour >= 23 -> HomeTimeScene(key = "night", category = "夜晚", title = "夜晚好梦")
    // me3.java:1042 —— 落到这里只剩 14..17
    else -> HomeTimeScene(key = "afternoon", category = "下午茶", title = "下午茶歇")
}

/**
 * GZ `com/gzmusic/app/feature/stats/StatsScreenKt.java:6010-6024` 的 `a0(int)`。
 *
 * 同一套判断在 `HomeScreenKt.java:9980-9992` 被**内联**到「本周聆听」卡片里，
 * 用来给「峰值时段」取文案（HomeScreenKt.java:9993-9999 拼成 `"<时段> <hour> 点"`，
 * 例如 `"清晨 12 点"`）。两处字面量完全一致。
 *
 * ```
 * if (5 <= i && i < 9)   return "清晨";                                  // StatsScreenKt.java:6011-6013
 * if (9 <= i && i < 12)  return "上午";                                  // StatsScreenKt.java:6014-6016
 * if (12 <= i && i < 14) return "午间";                                  // StatsScreenKt.java:6017-6019
 * if (14 > i || i >= 18) return (18 > i || i >= 23) ? "深夜" : "傍晚";  // StatsScreenKt.java:6020-6022
 * return "午后";                                                        // StatsScreenKt.java:6023
 * ```
 * | hour   | 文案 |
 * |--------|------|
 * | 0..4   | 深夜 |
 * | 5..8   | 清晨 |
 * | 9..11  | 上午 |
 * | 12..13 | 午间 |
 * | 14..17 | 午后 |
 * | 18..22 | 傍晚 |
 * | 23     | 深夜 |
 */
fun resolveTimeSlotLabel(hour: Int): String = when {
    // StatsScreenKt.java:6011-6013
    hour in 5..8 -> "清晨"
    // StatsScreenKt.java:6014-6016
    hour in 9..11 -> "上午"
    // StatsScreenKt.java:6017-6019
    hour in 12..13 -> "午间"
    // StatsScreenKt.java:6020-6022 的 "傍晚" 分支
    hour in 18..22 -> "傍晚"
    // StatsScreenKt.java:6021 的 "深夜" 分支
    hour < 5 || hour >= 23 -> "深夜"
    // StatsScreenKt.java:6023 —— 落到这里只剩 14..17
    else -> "午后"
}

/**
 * GZ 取当前小时的写法：`Calendar.getInstance().get(11)`
 * （me3.java:1032；11 == `Calendar.HOUR_OF_DAY`）。
 */
fun currentHourOfDay(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

// ============================================================================
// 二、顶栏（手机）—— HomeScreenKt.java:9571-9624
// ============================================================================

/**
 * GZ 手机首页顶栏 `HomeTopBar`（HomeScreenKt.java:9571-9624）。
 *
 * 结构（三个 Group 由 `Composer.startReplaceableGroup` 还原，
 * RowMeasurePolicy 注册在外层 group HomeScreenKt.java:9582/9589，
 * ColumnMeasurePolicy 注册在内层 group HomeScreenKt.java:9603/9609）：
 *
 * ```
 * Row(                                          // 9576 / 9589
 *     Modifier
 *         .statusBarsPadding()                  // 9575
 *         .fillMaxWidth(1.0f)                   // 9575
 *         .padding(start=16.dp, top=12.dp, end=16.dp, bottom=2.dp),  // 9575
 *     verticalAlignment = Alignment.CenterVertically                 // 9576 (il0.f0)
 * ) {
 *     Column(Modifier.weight(1.0f, fill = true)) // 9598 / 9602
 *     {
 *         Text("发现好音乐", …)                  // 9613
 *     }
 * }
 * ```
 *
 * 调用点：HomeScreenKt.java:8698（`"topbar"` 槽位，位于 `SearchBar` 之上、与搜索栏平级；
 * 槽位注册见 HomeScreenKt.java:8918 -> ComposableSingletons$HomeScreenKt.java:28/31-38）。
 *
 * @param modifier 调用方追加的 Modifier。GZ 顶栏自身没有外部 modifier 参数
 *                 （HomeTopBar 的 Compose 签名只有 `yd1`(Composer) + `int`(变更掩码)，
 *                 HomeScreenKt.java:9571），此参数是本项目的工程惯例，默认不改动布局。
 */
@Composable
fun HomeHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            // HomeScreenKt.java:9575 —— hp1.z0(c05.a, hp1.v) == Modifier.statusBarsPadding()
            .statusBarsPadding()
            // HomeScreenKt.java:9575 —— ij7.g(…, 1.0f) == Modifier.fillMaxWidth(1.0f)
            .fillMaxWidth(1.0f)
            // HomeScreenKt.java:9575 —— hp1.r0(…, 16.0f, 12.0f, 16.0f, 2.0f)
            //                        == Modifier.padding(start=16.dp, top=12.dp, end=16.dp, bottom=2.dp)
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 2.dp),
        // HomeScreenKt.java:9576 —— gy6.a(zh.b, il0.f0)；il0.f0 = wg0(0.0f) = Vertical(bias=0.0)
        verticalAlignment = Alignment.CenterVertically,
        // HomeScreenKt.java:9576 —— zh.b = g10(3) = "Arrangement#Start"（Row 的默认值）
        horizontalArrangement = Arrangement.Start,
    ) {
        Column(
            // HomeScreenKt.java:9598 + 9602 —— rt0.g(…, 1.0f, true) => ez3(1.0f, true)
            //                               == Modifier.weight(1.0f, fill = true)
            modifier = Modifier.weight(1.0f, fill = true),
            // HomeScreenKt.java:9599 —— aa1.a(zh.d, il0.h0)；zh.d = h10() = "Arrangement#Top"（Column 默认值）
            verticalArrangement = Arrangement.Top,
            // HomeScreenKt.java:9599 —— il0.h0 = vg0(-1.0f) = Horizontal(bias=-1.0) = Alignment.Start（Column 默认值）
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                // HomeScreenKt.java:9613
                text = "发现好音乐",
                // HomeScreenKt.java:9613 —— GlassTokensKt.textHigh(oy2Var, 0)
                color = textHigh(),
                // HomeScreenKt.java:9613 —— co7.j(28) => 28.sp
                fontSize = 28.sp,
                // HomeScreenKt.java:9613 —— sq2.X => FontWeight(800)（sq2.java:34）
                fontWeight = FontWeight(800),
                // HomeScreenKt.java:9613 —— co7.i(-0.8d) => (-0.8).sp
                letterSpacing = (-0.8).sp,
                // HomeScreenKt.java:9613 —— modifier 实参为 null（Text 自身不带 modifier）
                // HomeScreenKt.java:9613 —— fontFamily = null、fontStyle = null、textDecoration = null、
                //                          lineHeight = 0(未指定)、textAlign = 0(未指定)
            )
        }
    }
}

// TODO(未确定): 任务书要求还原的「时段问候语」在 GZ 中不存在。
//   全仓检索（.java 全文匹配「夜深了 / 早上好 / 上午好 / 下午好 / 晚上好 / 中午好 /
//   凌晨 / 早安 / 晚安 / 你好 / 欢迎回来」）在 com/gzmusic/app/feature/home/ 下 0 命中；
//   HomeScreenKt.java 中所有中文字面量已逐条列出，与时段相关的只有
//   「清晨 / 上午 / 午间 / 午后 / 傍晚 / 深夜」（来自 StatsScreenKt.a0，
//   见 resolveTimeSlotLabel）与「清晨时光 / 午休小憩 / 下午茶歇 / 晚风散步 / 夜晚好梦」
//   （来自 me3.l()，见 resolveTimeScene）。顶栏不渲染其中任何一个。
//   因此本文件不产出任何「问候语」文案，避免脑补。
