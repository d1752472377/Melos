package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Stable
import com.aesthetic.music.ui.AppTab

@Stable
class ShellNav(
    val navigate: (String) -> Unit,
    val navigateSingleTop: (String) -> Unit,
    val popBack: () -> Unit,
    val switchTab: (route: String, tab: AppTab) -> Unit,
    /** 回退到栈里已有的某个页面；没有就新建。用于"识曲结果点歌 → 回到搜索页"这类跨页跳转 */
    val popTo: (String) -> Unit,
)
