# Aesthetic Music — Android 音乐 App

原生 Kotlin + Jetpack Compose 实现的「柔和拟物」全功能播放器。

## 功能

| 模块 | 说明 |
|---|---|
| 主页（历史） | 顶部问候语 + 头像、2×2 封面拼图主卡片（播放全部/随机/搜索）、最近播放列表（当前播放项带音波动画） |
| 播放列表页 | 返回/排序顶栏、搜索框（实时搜索）、带序号列表、红心收藏 |
| 搜索 | 歌曲 / 歌手 / 专辑 / 歌单 / 歌词多类型，含热门搜索与最近搜索 |
| 个人中心 | 深棕渐变用户卡（VIP 标签 + 听歌统计）、音乐基因标签、设置菜单 |
| 底部悬浮 Dock | 白色胶囊导航：主页 / 列表 / 中央旋转唱片 / 时钟 / 个人 |
| 全屏播放器 | 从底部滑入：模糊背景 + 歌名 + 歌词高亮滚动 + 进度条 + 播放/切歌控制 |
| 主题 Sheet | 底部弹出，经典 / 封面卡片 / Apple Music 三选一 |
| FM 电台 | 风格种子推荐 + 点赞/听完驱动的「漂移」推荐，附宝藏池 |
| 榜单 / 歌手 / 歌单广场 | 排行榜与分类歌单、歌手热歌、歌单标签筛选 |
| 音源脚本 | 导入 QuickJS 音源脚本以获取高品质直链与歌词，可切换/卸载 |
| 系统集成 | 悬浮胶囊、系统媒体通知、在线更新（下载校验后调系统安装器） |
| 私有云同步 | 登录后多端增量同步收藏/历史/歌单（LWW 冲突合并） |

## 技术栈

- Kotlin 2.0.21 + Jetpack Compose（Material 3，BOM 2024.10.00）
- Media3 ExoPlayer 播放、Coil 图片加载
- Retrofit + OkHttp 连后端；QuickJS 在本地沙箱执行音源脚本
- MVVM：ViewModel + StateFlow；播放器为全局单例 `PlayerManager`
- 构建：AGP 9.1.0 / Gradle 9.5.0 / JDK 17

## 模块

| 模块 | 说明 |
|---|---|
| `:app` | 应用主体（Compose UI + MVVM） |
| `:lyrics` | 歌词解析与渲染，支持 LRC / Enhanced LRC / 网易云 YRC / 酷狗 KRC / TTML 五种格式 |
| `:baselineprofile` | Baseline Profile 与滚动性能基准 |

## 构建 / 运行

```bash
cd android
gradle assembleDebug        # 构建 APK（产物在 app/build/outputs/apk/debug/）
gradle installDebug         # 安装到已连接的设备/模拟器
```

或用 Android Studio 打开 `android/` 目录直接 Run。

要求：JDK 17+（本机 21 可用）、Android SDK Platform 37（`compileSdk = 37`，
`targetSdk = 35`，`minSdk = 24`）、Gradle 9.5.0（由 wrapper 自动下载）。

### 测试与静态检查

```bash
cd android
./gradlew :lyrics:testDebugUnitTest   # 歌词解析器单元测试（纯 JVM，无需设备）
./gradlew :app:lintDebug              # Android Lint，报告在 app/build/reports/
```

CI（`.github/workflows/release.yml` 的 `quality` job）会在分支 push 与 PR 上
强制执行 lint + 单元测试；打 `v*` tag 或手动触发才构建签名包。

### 统一签名（release / OTA 覆盖安装）

`android/keystore/aesthetic.jks` 不入库，正式发布前需自行放置该文件，并在
`android/local.properties`（已 gitignore）里配置口令：

```properties
keystore.password=你的keystore口令
```

或设置环境变量 `KEYSTORE_PASSWORD`。两者都缺失时回退本机 debug 签名（仅限开发调试，
此时 release 包签名与线上不一致，无法覆盖安装）。

## 连接后端

音乐检索、榜单、歌词、播放直链等核心能力**完全在客户端独立运行**（直连平台公开协议
+ 本地 QuickJS 音源脚本），不依赖自建后端。只有「账号登录」与「多端数据同步」需要
连接用户的私有服务器。

后端地址不在代码里硬编码：`data/api/ApiClient.kt` 的 `baseUrl` 只是一个占位地址
（`http://127.0.0.1:8787/`，仅用于构造 Retrofit 实例），真实目标由 OkHttp 拦截器在
运行时按用户在「设置 → 私有云同步」中填写的地址动态改写 host / port，并自动附加
`Authorization: Bearer <token>`。

未配置私有服务器时，`/api/sync`、`/api/auth`、`/api/me` 三类请求会被拦截器直接拦下
并给出「请先在设置中填入后端地址与密钥」的友好提示，而不是抛出底层网络异常。

App 启动时会自动搜索默认关键词「周杰伦」填充历史记录。

## 目录结构

```
app/src/main/java/com/aesthetic/music/
├── MainActivity.kt          # 入口（最高刷新率、通知权限、JankStats）
├── MelosApplication.kt      # Application：统一初始化各 Store 与脚本引擎
├── data/
│   ├── model/Song.kt        # 歌曲模型
│   ├── AppJson.kt           # Gson 单例
│   ├── MusicRepository.kt   # 数据仓库（音乐能力 + 账号同步）
│   ├── api/
│   │   ├── MusicApi.kt      # Retrofit 接口 + DTO
│   │   ├── ApiClient.kt     # OkHttp 配置（动态后端地址 + Bearer 注入）
│   │   └── platform/PlatformMusicService.kt   # 直连平台公开协议
│   ├── userapi/
│   │   ├── UserApiEngine.kt # QuickJS 音源脚本执行引擎
│   │   ├── UserApiStore.kt  # 脚本持久化
│   │   └── CryptoUtils.kt
│   └── settings/            # 持久化 Store（SharedPreferences，全部异步 apply）
│       ├── SettingsStore.kt / UserStore.kt / SyncServerStore.kt
│       ├── PlaylistStore.kt / FavoriteStore.kt / FavoritePlaylistStore.kt
│       └── HistoryStore.kt / RecentSearchStore.kt / StatsStore.kt
│           PlayerSessionStore.kt / ScriptCatalogStore.kt
├── player/
│   ├── PlayerManager.kt     # 全局播放器（Media3 + 队列 + 播放模式 + 断点续播）
│   ├── AudioCacheService.kt # 音频缓存（索引常驻内存 + 超限自动清理）
│   └── Lyric.kt             # 歌词行模型
├── service/
│   ├── PlaybackService.kt   # Media3 MediaSessionService（系统媒体通知）
│   ├── FloatCapsuleManager.kt  # 系统悬浮胶囊（类灵动岛）
│   ├── UpdateService.kt     # 在线更新（下载 + 校验 + 调系统安装器）
│   ├── StatsTracker.kt      # 真实听歌统计
│   └── CrashHandler.kt      # 全局崩溃日志（自动轮转）
└── ui/
    ├── MainViewModel.kt     # 页面状态 + 搜索/历史/收藏/歌单/FM/同步/脚本
    ├── AppShell.kt          # 应用壳（页面切换 + 抽屉 + Sheet + Snackbar）
    ├── NavigationDock.kt    # 底部悬浮导航
    ├── SplashScreen.kt
    ├── navigation/          # 路由定义
    ├── theme/               # 配色/字体/主题
    ├── components/          # 封面/音波/旋转唱片/状态页/弹层/歌曲操作
    ├── home/                # 主页（分类/榜单/推荐/今日精选/随机）
    ├── playlist/            # 播放列表 + 歌单详情
    ├── search/              # 搜索（歌曲/歌手/专辑/歌单/歌词）
    ├── favorites/           # 收藏
    ├── rank/                # 排行榜
    ├── artist/              # 歌手详情
    ├── fm/                  # FM 电台
    ├── profile/             # 个人中心（资料/统计/云管理/下载/插件/历史）
    ├── settings/            # 设置
    ├── cache/               # 缓存管理
    ├── player/              # 全屏播放器
    └── sheet/               # 主题选择 / 歌单广场

lyrics/src/main/java/com/mocharealm/accompanist/lyrics/
├── core/parser/             # LRC / Enhanced LRC / YRC / KRC / TTML / AutoParser
├── core/model/              # 同步行、卡拉 OK 音节、元数据模型
├── core/utils/              # 时间戳解析、LRC 元数据、简易 XML
└── ui/                      # 歌词渲染（逐字高亮、呼吸点、缓动）
```
