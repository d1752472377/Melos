package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Stable
import com.aesthetic.music.ui.AppTab

/**
 * 壳层导航回调集合：把 `navController` 与 `viewModel.switchTab` 收敛到一个 holder，
 * 让各 destination 只需接收一个 [ShellNav] 而不必逐个透传 navController。
 */
@Stable
class ShellNav(
    val navigate: (String) -> Unit,
    /** 带 launchSingleTop 的导航（用于搜索等可重复进入的页面） */
    val navigateSingleTop: (String) -> Unit,
    val popBack: () -> Unit,
    /** 切换底部 Tab：同时更新 ViewModel 的 currentTab 与导航栈 */
    val switchTab: (route: String, tab: AppTab) -> Unit,
)
