package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Stable
import com.aesthetic.music.ui.AppTab

@Stable
class ShellNav(
    val navigate: (String) -> Unit,
    val navigateSingleTop: (String) -> Unit,
    val popBack: () -> Unit,
    val switchTab: (route: String, tab: AppTab) -> Unit,
)
