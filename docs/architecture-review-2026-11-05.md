# Melos Android 架构复审（2026-11-05）

> 范围：`android/app/src/main/java`，191 个 Kotlin 文件、约 54,000 行。阶段 0–3 已完成；本复审在其基础上进行，不重新设计整体架构。

## 1. 当前目录结构

| 目录 | 文件数 | 行数 | 职责 |
|---|---:|---:|---|
| `data/api`、`data/api/platform` | 6 | ~3,050 | Retrofit 接口、DTO、四平台协议实现 |
| `data/settings` | 19 | ~2,700 | SharedPreferences / JSON 持久化 |
| `data/source`、`data/userapi` | 12 | ~2,490 | 音源路由、插件注册表、QuickJS 运行时 |
| `data/sync`、`data/cache` | 5 | ~950 | 云同步模型/合并、缓存统计 |
| `player`、`service` | 21 | ~5,400 | 播放器、媒体会话、通知、日志、更新 |
| `ui/theme` | 5 | ~466 | Color / GzColorScheme / Theme / Type |
| `ui/components` | 20 | ~4,715 | 设计系统 token、通用控件、媒体组件 |
| `ui/shell` | 20 | ~2,107 | 路由 wiring、全局浮层、壳层状态 |
| `ui/home` | 9 | ~3,747 | 首页各版块 |
| `ui/library` | 5 | ~2,231 | 收藏/历史/歌单导入/歌曲集合骨架 |
| `ui/player` + `lyrics` | 8 | ~3,946 | 全屏播放器、歌词、队列、中控台 |
| `ui/profile` | 5 | ~3,155 | 个人中心、资料编辑、云同步页 |
| `ui/playlist`、`ui/artist`、`ui/search`、`ui/fm`、`ui/guess`、`ui/rank`、`ui/sheet`、`ui/settings`、`ui/stats`、`ui/source` | 43 | ~11,000 | 各垂直功能 |

阶段 3 后 `ui/shell` 从 29 文件降到 20 文件；`ui/profile` 从 8 文件降到 5 文件；`ui/favorites` 目录已并入 `ui/library`。

## 2. 文件拆分过细的具体案例

### 2.1 已处理：`ui/shell` 路由文件

复审前 29 个文件、平均 72 行，其中 14 个是 35–70 行的纯 wiring。已按路由族合并：

- `MainTabDestinations.kt`：Home / PlaylistTab / ProfileTab / Fm。
- `DetailDestinations.kt`：PlaylistDetail / AlbumDetail / ArtistDetail / ArtistAllSongs。
- `LibraryListDestinations.kt`：Favorites / History。
- `CacheDestinations.kt`：CacheList / Downloads。
- `AccountDestinations.kt`：ProfileEdit / CloudManage。
- `SettingsDestinations.kt`：设置主页 + 6 个子页。

这些函数仍各自独立，只是不再各占一个文件。剩余 20 个 shell 文件中，`SearchDestination`、`RanksDestination`、`SheetSquareDestination`、`GuessDestination`、`ListeningStatsDestination`、`PluginManageDestination` 仍是 35–69 行的独立路由；它们分别对应不同功能入口，**建议保留独立文件**，不再为了数量继续合并。

### 2.2 已处理：`data/settings` 的 Store 模板

`FavoriteStore`、`FavoritePlaylistStore`、`HistoryStore`、`RecentSearchStore`、`GuessHistoryStore`、`PlayerSessionStore`、`CloudDownloadStore` 原先各自复制 `prefs + writer + load/save/clear`。

已新增 `data/settings/PrefsJson.kt`：只提供 `prefs()`、`load()`、`save()`、`clear()`、`runAsync()` 与 `jsonType<T>()` 顶层函数，**没有引入泛型 Store 类**。各 Store 保留自己的键、容量与数据语义，重复模板减少约 150 行。`CloudDownloadStore.save` 保持同步写入，避免 `apply()` 先读后写看到旧镜像；其余 Store 使用共享单线程写入器。

### 2.3 可以保留的小文件

- `ui/settings/QualitySettingsScreen.kt`（57）、`PlaybackSettingsScreen`（65）、`LyricSettingsScreen`（68）、`DownloadSettingsScreen`（79）、`AppearanceSettingsScreen`（65）：每页是独立导航目标，且内容/文案不同，不建议合并。
- `ui/shell/ShellNav.kt`（18）、`ShellActions.kt`（20）：公共参数 holder，减少每个 destination 的回调数量，保留。
- `ui/search/SearchType.kt`（5）：只是一个枚举；**已并入（第八批）** `SearchController.kt`，不再单独占文件，包级引用不变。
- `data/AppJson.kt`（19）、`BestEffort.kt`（29）、`UserNotice.kt`（24）：单一职责的小工具，保留。
- `ui/theme/Type.kt`：**已删除（第八批）**。`AppTypography` 只有注释引用、没有实际调用，`AppShapes` 本就不存在；Theme 继续使用 M3 默认 Typography，并更新了回退说明。

### 2.4 已合并/移动

- `ProfileFormat.kt` + `ProfileUtils.kt` → `ui/profile/ProfileUtils.kt`（两个 object 同域）。
- `FavoritesScreen.kt`、`HistoryScreen.kt`、`SongCollection.kt` → `ui/library/`。
- `DownloadsScreen.kt` → `ui/cache/`。
- 阶段 3 已移动：`AutoScrollController` + `centerItemIndex` → `ui/player/lyrics`；`FavoriteBurstButton` → `ui/player`；`MelosToastHost` → `ui/shell`；`PlaylistEditorSheet` → `ui/playlist`。

## 3. 可以合并但需要谨慎的候选

| 候选 | 理由 | 风险/决定 |
|---|---|---|
| `AlbumController` 并入 `PlaylistDetailController` | 两者都是“详情请求令牌 + songs + loading/error”，且专辑最终复用 `PlaylistDetailScreen` | **已完成（第四批）**：`openAlbum(AlbumItem)` + `retry()` 并入 `PlaylistDetailController`，删除 `AlbumController`；专辑/歌单共享同一 detail 状态与请求令牌 |
| `AuthController` + `ProfileController` | 都读写 `UserStore`，都存在用户资料状态 | **部分处理（第五批）**：删除 `ProfileController` 里无消费者的重复 `_user`，用户状态只保留 `AuthController.user`；完整合并因回调交错仍暂缓 |
| `data/settings` 7 个 Store | 模板重复 | 你已要求先评估简单公共函数；**暂缓** |
| `SearchType.kt` 并入 `SearchController` | 5 行枚举，单一使用域 | **已完成（第八批）**：枚举移入 `SearchController.kt`，删除独立文件 |

## 4. UI 与业务逻辑边界

### 4.1 已修正

- `PlayerScreen` 不再直接读写 `SettingsStore`，改为接收 `UserPrefs` 与回调；`SettingsController` 统一写。
- `CacheManageScreen` 不再直接读写缓存上限；`SettingsCacheDestination` 统一写并触发 `enforceCacheLimit`。
- `ProfileScreen` / `ProfileEditScreen` 不再直接把 URI 拷贝到私有目录；新增 `ProfileController.saveAvatarFromUri/saveBackgroundFromUri`。
- `PluginManageScreen` 不再自己读取脚本文件；新增 `SourceController.importFromUri`，UI 只上报选中的 URI。
- `PlaylistEditorSheet` 不再直接调用 `PlaylistCoverStore`；`PlaylistCoverStore.pick()` 统一“拷贝本机 + 生成同步副本”，`LibraryController.pickPlaylistCover/deletePlaylistCover` 负责业务入口，UI 只接收选择结果与删除回调。

### 4.2 仍存在的边界问题

1. `ui/stats/ListeningStatsScreen.kt`（81–98、222、549、584）：直接使用 `StatsStore.todayKey/shiftDay/HOUR_WINDOW_DAYS`。这些是纯静态日期/窗口计算，不涉及 I/O 或状态流转，**保留在 UI 可接受**。
2. `ui/cache/CacheListScreen.kt` / `DownloadsScreen.kt`：使用 `AudioCacheService.formatSize` 和 `CachedSong` 类型。格式化与只读展示，**可接受**。
3. `ui/home/HomeScreen.kt`（约 138–176）：从新歌/热歌构造候选池与 `songsMap`。属于版块展示选择，不是持久化/网络逻辑，**保留**。
4. `AppShell`：状态栏窗口控制、启动恢复、全局浮层。属于 composition root，更新弹窗已抽出，**保留但不再增长**。

### 4.3 边界结论

- Screen 不直接访问 Repository、网络、SharedPreferences、文件系统或系统服务；平台选择器读取 URI 可以留在 UI，但内容解析/落盘交给 Controller。
- Controller 负责业务状态、请求令牌、持久化与跨系统服务编排。
- Destination 只负责“Controller → Screen”的接线；只有 `MainActivity`/`AppShell` 持有 `MainViewModel`。
- 纯展示计算（时间问候、格式化、封面取色）可以留在 UI。

## 5. 现有 Design Tokens 与基础控件

### 5.1 Tokens

| 文件 | 内容 | 使用建议 |
|---|---|---|
| `ui/theme/Color.kt` | 背景/表面/文字/强调色/FM/统计/榜单色 | 全局颜色唯一来源；不要再在页面新建平行调色板 |
| `ui/theme/GzColorScheme.kt` | Material3 light/dark ColorScheme | `MaterialTheme.colorScheme` 的来源 |
| `ui/theme/GzTheme.kt` | `LocalAppIsDark` / `appIsDark()` / schedule 常量 | 深浅色判断统一入口 |
| `ui/theme/Theme.kt` | `AestheticMusicTheme`、`ScaleIndication`、无 ripple | 唯一主题入口；Typography / Shapes 使用 M3 默认值 |
| `ui/theme/AppDimens.kt` | `AppRadius` / `AppSpacing` | 共享圆角/间距 token；已在 `SongCollection`、`States`、`Overlays`、`SettingsKit`、`ListSearchBar`、`SecondaryPageTopBar`、`SelectionBottomSheet` 采用 |
| `ui/components/GlassTokens.kt` | 玻璃背景/描边/文字色、`glassCard` 等 Modifier | GZ 风格卡片/玻璃控件来源 |

圆角/间距高频值：`14.dp` 44 处、`12.dp` 39 处、`20.dp` 38 处、`10.dp` 38 处、`8.dp` 31 处、`16.dp` 29 处。已新增 `AppRadius`/`AppSpacing` 并在共享 primitives 中使用；**没有一次性全量替换**页面级字面量，避免视觉回归。`ui/theme/Type.kt` 已删除，全局字体沿用 M3 默认值。

硬编码颜色集中在 `PlayerScreen`（26）、`ProfileScreen`（21）、`PlayerThemeViews`（26）、`EqualizerScreen`（24）、`LibraryBentoSection`（11）、`RankPage`（10）等；多数是封面/主题/可视化专用色，不应机械替换为全局 token。

### 5.2 已有基础控件

- `ui/settings/SettingsKit.kt`：`SettingsPageScaffold`、`SettingsSection`、`SettingsNavRow`、`SettingsSwitchRow`、`SettingsSegmentRow`、`SettingsOptionList`、`SettingsValueRow`、`SettingsNote`、`SettingsDivider`、`SettingsGap`。
- `ui/components/States.kt`：`LoadingBox`、`ErrorBox`、`EmptyBox`、`DangerTextButton`、`DangerCapsuleButton`。
- `ui/components/Overlays.kt`：`AppDialog`、`AppConfirmDialog`、`AppBottomSheet`。
- `ui/components/LiquidGlassButton.kt`、`LiquidGlassComponents.kt`、`LiquidGlassSegmentTabs.kt`：玻璃按钮、幽灵按钮、胶囊 chip、分段 tabs。
- `ui/components/ListSearchBar.kt`、`SelectionBottomSheet.kt`、`SecondaryPageTopBar.kt`、`KeepScreenOn.kt`、`ScaleIndication.kt`。

**没有统一的 AppButton / AppSwitch / AppTextField / AppChip**。目前按钮多为页面私有实现（如 `FmSolidButton`、`PrimaryWideButton`、`DangerWideButton`、`PillButton`、`GuessPlayAllButton`、`PlayerChip` 等），视觉差异真实存在。复审结论：**先不创建万能 AppButton**；优先复用 `LiquidGlassButton` / `SettingsKit` / `States` / `Overlays`，当同一形态出现第 3 个稳定调用点时再抽最小 primitives。

## 6. 重复定义与不统一样式

- 圆角/间距：见上，集中在 8/10/12/14/16/20/24dp；没有统一来源。
- 按钮：玻璃按钮、深色主按钮、白色次按钮、胶囊 chip、圆形图标按钮至少 5 类；不同页面的同名形态参数不完全一致。
- 颜色：主题色已集中在 `Color.kt`，但播放页/个人页/均衡器等仍有大量页面级 `Color(0x...)`，其中一些是封面动态色或可视化专用色，属于合理例外。
- 阴影/模糊：`iosGlassCard`、`glassCard`、`liquidGlassCardSurface` 三套玻璃外观并存；当前按页面历史沿用，暂不强行统一，避免视觉回归。

## 7. 建议目录结构

```text
data/
  model/ api/ api-platform/ settings/ source/ sync/ cache/ userapi/
player/ service/
ui/
  theme/                 # tokens + theme 入口
  components/            # design system + 通用 primitives + 通用媒体组件
  shell/                 # 路由 wiring、全局浮层、壳层 holder
  home/ library/ player/ playlist/ artist/ search/ fm/ guess/ rank/
  sheet/ settings/ stats/ profile/ account/ source/ cache/
```

原则：目录按**功能域**划分，不按 “screen / controller / component” 三层机械拆目录；`ui/shell` 只放跨功能路由与全局层。

## 8. 结论

- 真实复杂度主要来自：`data/settings` 的持久化模板、`ui/settings` 与 `ui/shell` 的少量重复 wiring、以及尚未统一的按钮/圆角样式。
- 阶段 2/3 已消除 MainViewModel 门面、单曲抽屉重复、收藏/历史重复和设置直写。
- 下一步不应该大规模移动文件；优先按本报告第 3、4 节做“确认后再动”的候选，以及按 UI 规范做增量统一。
