# Android 架构精简计划（2026-11-05）

> 本文件是后续重构的唯一依据。任何自动化或 AI 执行前先读本文件。

## 总规则

**重构的首要目标是减少重复代码、无意义抽象和调用链，而不是追求架构模式的完整性、文件数量的减少或组件复用率的提高。**

具体约束：

1. 不为了“分层完整”新增 Controller / Manager / Repository / 接口。
2. 不为了减少文件数合并职责不同的文件。
3. 不为了复用率把只使用一次、未来也不确定的 UI 抽成公共组件。
4. 每一项删除/合并都要先确认调用点、反射、序列化/ProGuard 引用。
5. 先理清现有设计规范与重复样式，最后才抽基础控件；不能把现有混乱封装成更复杂的组件库。
6. 不一次性执行全部阶段，每个阶段单独提交、单独验证。

## 阶段顺序

1. **阶段 0：死代码清理**（不改变行为）
2. **阶段 1：消除重复实现**
3. **阶段 2：收敛 MainViewModel 与 Destination 层级**
4. **阶段 3：状态归位、目录整理、设计控件统一**

## 已确认的修正决定

- 删除死代码：支持，但逐项核验引用与反射后执行。
- 合并单曲操作抽屉：强烈支持。
- 收藏页/历史页：只抽取重复部分，不强制做“万能页面”。
- 删除 MainViewModel 转发：支持，但先检查生命周期与依赖关系。
- `JsonPrefsStore`：暂缓，先评估简单公共函数是否足够。
- `AppUpdateController`：支持，但避免为统一弹窗增加过多状态层。
- 合并专辑/歌单详情：支持，但先确认两种详情的数据语义。
- `SongCollectionScreen`：不允许变成二三十个参数的万能组件；只抽真正重复的搜索/排序/列表骨架。
- 按钮/开关等设计控件统一样式：放在最后阶段。

## 阶段 0 记录

执行前已保护工作区：

- 备份分支：`backup/pre-android-refactor-20261105`
- 备份提交：`518965e checkpoint: 架构重构前保护当前工作区修改`
- 阶段 0 分支：`refactor/android-phase-0`

已删除（整文件、确认无代码/反射/Keep 引用）：

- `ui/home/HomeCategories.kt`
- `ui/home/CategoryBlindBoxRow.kt`
- `ui/home/HomeLeaderboard.kt`
- `ui/home/HomeRandomSongs.kt`
- `ui/home/HomeRecommend.kt`
- `ui/home/HomeTodayFeatured.kt`
- `ui/sheet/ThemeSheet.kt`
- `ui/library/LibraryCloudMerge.kt`
- `ui/player/lyrics/LyricTimeEngine.kt`
- `ui/search/SearchHotSearch.kt`
- `ui/components/CardStyles.kt`
- `data/api/platform/PlatformMusicService.kt.bak`（历史备份文件）

已删除（活动文件内的死代码/死接线）：

- `HomeDailyHero` 中未被调用的 `HomePreviewSongRow` 与 `rowTitleColor`。
- `PlaylistScreen` 中未被调用的 `PlaylistHeaderCard` 与 `BottomSheetActionItem`。
- `FmScreen` 中未被调用的 `formatDuration`。
- 整条热搜链路：`HotWordDto`、`MusicRepository.hotSearchWords`、`PlatformMusicService.hotSearchWords`。
- `ShellActions.shareSongs` 与 `AppShell` 中对应的未使用实现。
- `MainViewModel` 中零引用/只写不读的成员：`showHistory`/`openHistory`/`closeHistory`、`fmStyles`、`fmStyle`、`user`、`closeArtist`、`openSheetCategory`、`fmStopDrift`、`renamePlaylist`、`sourceDeclaredSources`、`sourceDeclaredQualities`、`isTestingSync`、`saveSyncServerUrl`、`updateNickname`、`clearListeningStats`。
- `CacheState` 的 `count`、`size`（无消费者）。
- 一批 Screen 参数：`HomeScreen.homeCategories/favorites/onToggleFavorite`、`HomeDailyHero.onPlaySong`、`ArtistScreen.favorites`、`FavoritesScreen.onRemoveFavorite`、`CacheListScreen.onBack`、`CloudManageScreen` 的 12 个未读取参数、`PluginManageScreen.targets/detectProgress/lastDetectAt`、`ProfileScreen.cacheSize/isSyncConfigured`、`NavigationDock.onSheet/onPlaylist/containerColor`、`FmDestination.nav`、`ArtistAllSongsDestination.nav`。

确认后保留（有意图，不按死代码删除）：

- `ui/theme/Type.kt` 的 `AppTypography`（第八批复审时已删除：无实际引用，Theme 继续使用 M3 默认字体）。
- `GzTheme` 的 schedule 主题常量/函数（移植自 GZ，尚未接设置）。
- ~~`Color.kt` 的未使用色 token、`GlassTokens`/`StatsKit` 的未使用设计组件、`CoverTone` 的未使用导出、`Lyric.mergeRomanization`~~（第九、十一批复审核对引用后已清理，见架构复审文档第 2.3、2.7 节）。
- `AuthController.logoutAndClearLocalData`（产品 API，暂无 UI 入口；第十二批合并后仍保留）。
- `DeviceSyncAdapter`、`JsEngine` 接口（暂不作为本阶段清理目标）。

## 后续阶段

### 阶段 1：消除重复实现

- 单曲操作抽屉：抽 `SongActionsState`/统一宿主，替换 7 处级联；顺手修复歌手页下载空回调。
- 版本更新：抽一个轻量 `AppUpdateController` + 单个 `AppUpdateDialog`；不引入多层状态机。
- 下载缓存：统一 `PlayerManager`/`AudioCacheService` 的下载与副文件流程。
- 收藏/历史：只抽公共的搜索、排序、统计、列表骨架；页头与动作保留各自配置。
- 专辑/歌单详情：先核对 `AlbumItem`/`PlaylistItem`/`PlaylistDetailData` 语义，再决定是否合并 Controller。
- 设置 Store：先评估 1~2 个顶层公共函数能否消除模板，再决定是否引入 `JsonPrefsStore`。

#### 阶段 1 执行记录（2026-11-05）

- `5612179` 单曲操作抽屉：`SongActionsState` + `ui/shell/SongActionsHost`，7 处调用点统一；歌手页下载空回调已修复。
- `fe9b237` 收藏/历史：新增 `ui/components/SongCollection.kt` 共享骨架；两页保留标题、清空入口、排序项与空态，没有做万能页面。
- `933d78d` 版本更新：没有新增 Controller；`MainViewModel.checkForUpdate` + `isCheckingUpdate` 成为唯一状态源，`AppUpdateDialog` 统一渲染，删除 `UpdateLauncher`。
- `6d8d858` 下载缓存：手动下载与后台预缓存**没有整体合并**（一个需要明确失败提示，一个必须静默且可只预取直链）；只统一了 `PlayerManager.saveSidecarsAfterDownload`，并修正手动下载忽略「在线歌词」开关的漂移。

验证：每个提交前均通过 `:app:compileDebugKotlin` 与 `:app:testDebugUnitTest`（35 个测试）。未做真机手动冒烟。

### 阶段 2：收敛层级

- 先梳理 `MainViewModel` 与各 Controller 的生命周期、初始化顺序、跨域回调，再删除只读/单行转发。
- 保留跨域编排方法；不要为了“ViewModel 干净”把逻辑塞回 UI。
- Destination 先合并重复路由（专辑/歌单、简单设置页），不要一次性删除整个 shell 层。
- 搜索页先统一为一种模式：要么 Route 收集、Screen 纯 UI；要么 Screen 持 VM，不两边都做。

#### 阶段 2 执行记录（2026-11-05）

- `MainViewModel` 从 855 行收敛到约 330 行：**没有任何 `get()` 只读委托或单行转发函数**，只保留 Controller 组装、壳层状态（Tab/播放器浮层/页面背景/全局提示/更新/统计）和跨域编排（切 Tab、主动下载、检查更新等）。
- 各 Destination 改为直接接收自己需要的 Controller，例如 Home → `ContentController` + `PlaylistDetailController`；Fm → `FmController` + `LibraryController`；Profile → `AuthController` + `LibraryController` + `SourceController`（个人资料已并入 `AuthController`）。UI 层没有出现新的“万能上下文”。
- `SearchScreen` / `AuthScreen` 不再接收 `MainViewModel`，分别依赖 `SearchController` / `AuthController`。
- 主题与音质状态从 `MainViewModel` 收回 `SettingsController`（原先同一设置有两套入口）。
- `SongActionsHost` 不再依赖 `MainViewModel`，改用 `LibraryController` + `ShellActions`；`ShellActions` 增加 `downloadSong`。
- `PlaylistDetailController` 新增 `showLocalPlaylist(MyPlaylist)`，把本地歌单转换从 MainViewModel 收回控制器。
- 目前只有 `MainActivity` 与 `AppShell` 持有 `MainViewModel`；所有 Destination/Screen 都不再直接依赖它。

验证：`compileDebugKotlin` 与 `testDebugUnitTest`（35 个测试）通过。未做真机手动冒烟。专辑/歌单详情 Controller 的合并已在第四批完成。

### 阶段 3：状态与设计控件

- 把 PlayerScreen/CacheManageScreen 直接读写的设置并入 `SettingsController`/`UserPrefs`。
- 移动单消费者组件到对应领域包；`ui/components` 只放设计系统与通用工具。
- 最后再统一按钮/开关等基础控件，先盘点现有样式与重复实现。

#### 阶段 3 执行记录（2026-11-05）

- `UserPrefs` 新增播放页主题、歌词字号、歌词模糊、背景模糊、缓存上限；`SettingsController` 提供对应写入方法。
- `PlayerScreen`/`PlayerOverlay` 与 `CacheManageScreen` 不再直接读写 `SettingsStore`，改为接收状态与回调；`AudioCacheService.enforceCacheLimit` 改读 `readPrefs().cacheLimitBytes`。
- 组件归位：`AutoScrollController` 与 `centerItemIndex` 合并到 `ui/player/lyrics`；`viewportCenterY.kt` 中未使用的两个扩展删除；`FavoriteBurstButton` 移到 `ui/player`；`MelosToastHost` 移到 `ui/shell`；`PlaylistEditorSheet` 移到 `ui/playlist`。
- `assembleDebug` 与 35 个单测通过。

设计 Tokens 与基础按钮/开关的统一**未在本阶段仓促实施**：先按下一步架构复审盘点现有 `Color`/`GlassTokens`/`SettingsKit`/`LiquidGlass*` 与重复样式，再决定最小统一范围，避免把现有混乱封装成更复杂的组件库。

## 验证要求

- 每个阶段：`./gradlew :app:compileDebugKotlin --offline` + `./gradlew :app:testDebugUnitTest --offline`。
- 手动冒烟：登录、首页、搜索、歌手页、歌单/专辑详情、收藏、历史、播放器、设置、插件管理、检查更新。
- 每阶段单独提交；不把行为变更与纯清理混在一个提交里。
