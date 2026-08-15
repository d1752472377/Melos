# Aesthetic Music — Android 音乐 App

原生 Kotlin + Jetpack Compose 实现的「柔和拟物」全功能播放器。

## 功能

| 模块 | 说明 |
|---|---|
| 主页（历史） | 顶部问候语 + 头像、2×2 封面拼图主卡片（播放全部/随机/搜索）、最近播放列表（当前播放项带音波动画） |
| 播放列表页 | 返回/排序顶栏、搜索框（实时连后端搜索）、带序号列表、红心收藏 |
| 个人中心 | 深棕渐变用户卡（VIP 标签 + 听歌统计）、音乐基因标签、设置菜单 |
| 底部悬浮 Dock | 白色胶囊导航：主页 / 列表 / 中央旋转唱片 / 时钟 / 个人 |
| 全屏播放器 | 从底部滑入：模糊背景 + 歌名 + 歌词高亮滚动 + 进度条 + 播放/切歌控制 |
| 主题 Sheet | 底部弹出，经典 / 封面卡片 / Apple Music 三选一 |

## 技术栈

- Kotlin 2.0 + Jetpack Compose（Material 3，BOM 2024.10.00）
- Media3 ExoPlayer 播放、Coil 图片加载
- Retrofit + OkHttp 连后端
- MVVM：ViewModel + StateFlow；播放器为全局单例 `PlayerManager`

## 构建 / 运行

```bash
cd android
gradle assembleDebug        # 构建 APK（产物在 app/build/outputs/apk/debug/）
gradle installDebug         # 安装到已连接的设备/模拟器
```

或用 Android Studio 打开 `android/` 目录直接 Run。

要求：JDK 17+（本机 21 可用）、Android SDK 35、Gradle 8.8。

## 连接后端

后端默认地址在 `data/api/ApiClient.kt`：

- **Android 模拟器**：`http://10.0.2.2:8787/`（默认，10.0.2.2 指向宿主机）
- **真机**：改成宿主机的局域网 IP，如 `http://192.168.1.100:8787/`

并确保后端已启动（`cd backend && npm start`），且手机与电脑同一网络。App 启动时会自动搜索默认关键词「周杰伦」填充历史记录。

## 目录结构

```
app/src/main/java/com/aesthetic/music/
├── MainActivity.kt          # 入口
├── data/
│   ├── model/Song.kt        # 歌曲模型
│   ├── api/MusicApi.kt      # Retrofit 接口 + 响应模型
│   ├── api/ApiClient.kt     # Retrofit/OkHttp 配置（后端地址）
│   └── MusicRepository.kt   # 数据仓库
├── player/
│   ├── PlayerManager.kt     # 全局播放器（Media3 + 队列 + 歌词）
│   └── Lyric.kt             # LRC 歌词解析
└── ui/
    ├── MainViewModel.kt     # 页面状态 + 搜索/历史/收藏
    ├── AppShell.kt          # 应用壳（页面切换 + 抽屉 + Sheet）
    ├── NavigationDock.kt    # 底部悬浮导航
    ├── theme/               # 配色/字体/主题
    ├── components/          # 封面/音波/旋转唱片
    ├── home/                # 主页
    ├── playlist/            # 播放列表 + 搜索
    ├── profile/             # 个人中心
    ├── player/              # 全屏播放器
    └── sheet/               # 主题选择
```
