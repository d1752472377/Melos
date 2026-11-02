# GZ Music 2.1.8 逆向分析报告

| 项 | 值 |
|---|---|
| 目标 | `GZ_Music-2.1.8-release.apk` |
| 包名 | `com.gzmusic.app` |
| 版本 | `2.1.8` (versionCode 38) |
| 大小 | 17,161,820 字节 |
| SHA-256 | `66E83C471A14CA4C2437D6234A72FF1CC09FF03C1315506AE3C8B3CE448A3202` |
| 分析日期 | 2026-10-13 |
| 分析工具 | androguard 4.1.4 · apksigner 36.0.0 · apk-reverse skill |
| 分析性质 | **纯静态**。未连接设备，未运行应用，未向任何服务端发起请求 |

**结论强度标注**（全文遵循）：

| 标注 | 含义 |
|---|---|
| `observed` | 有命令和输出佐证 |
| `inferred` | 由观测事实推导，推导步骤已写出 |
| `unverified` | 假设或转述，未经独立确认 |

---

## 1. 目标识别

`observed`：

```
package  : com.gzmusic.app
version  : 2.1.8 / 38
minSdk   : 24        targetSdk : 36
classes  : dex0 = 8386 类, dex1 = 7462 类, 合计 15848 类
activities: 1        services: 4      permissions: 17
```

组件清单（全部 `observed`，来自 manifest）：

| 类型 | 组件 |
|---|---|
| Application | `com.gzmusic.app.GZMusicApplication` |
| Activity | `com.gzmusic.app.MainActivity`（唯一，launchMode=singleTask） |
| Service | `com.gzmusic.app.core.media.MusicService`（foregroundServiceType=mediaPlayback） |
| Service | `com.gzmusic.app.core.media.PlaybackCaptureService`（exported=false） |
| Receiver | `com.gzmusic.app.feature.widget.NowPlayingWidgetProvider` |

`observed` 的第三方组件均为 AndroidX：`androidx.startup.InitializationProvider`、`androidx.core.content.FileProvider`、`androidx.room.MultiInstanceInvalidationService`、`androidx.profileinstaller.ProfileInstallReceiver`、`androidx.camera.core.impl.MetadataHolderService`。

## 2. 加固评估：无壳

`observed` —— 判定依据是三条独立证据，而非单一信号：

1. `application android:name="com.gzmusic.app.GZMusicApplication"` 是应用**自己的**类，不是第三方壳的入口类。
2. `appComponentFactory="androidx.core.app.CoreComponentFactory"` 是 AndroidX 标准实现，不是加固厂商的替换实现。
3. 两个 dex 用 androguard 完整解析，类数与 dex 头部 `class_defs_size` **精确吻合**（8386 / 7462），无解析失败。

`observed` —— **无 VMP / 无抽取壳**特征：`Lcom/gzmusic/app/` 下的类均为正常 Kotlin 编译产物，可见完整字段表与方法体；未发现「整类变成 native 声明」或「方法体为 `return-void` 桩」的形态。

`observed` —— R8 混淆**部分启用**：包名与类名保留（`com.gzmusic.app.core.data.remote.dto.*` 全部可读），但存在混淆后的短类名（`Lb97;`、`Lg97;`、`Lrb1;`、`Lnm2;`）。DTO 字段名**未被混淆**（`appVersion`、`deviceId`、`ip` 等均为原名）。

> **对复刻的意义**：这是最理想的目标形态——接口名、DTO 字段名、业务类名全部可读，无需脱壳、无需去混淆即可完成接口复刻。

## 3. 应用架构

`observed` —— 技术栈判定：

| 层 | 判定 | 证据 |
|---|---|---|
| UI | Jetpack Compose + Material3 | `META-INF/androidx.compose.*.version` 全族；`ComposableSingletons`、`*Kt` 类大量存在 |
| DI | Hilt / Dagger | `META-INF/com.google.dagger_hilt-android.version`；`*_HiltModules`、`*_Factory` 类 |
| 持久化 | Room | `androidx.room` 版本文件；`AppDatabase`、`AppDatabase_Impl`、`core/data/local/entity/*` |
| 设置存储 | DataStore Preferences | `META-INF/androidx.datastore_datastore-preferences.version` |
| 序列化 | kotlinx.serialization | `*$$serializer`、`*$Companion` 成对出现于每个 DTO |
| 播放 | AndroidX Media3 / ExoPlayer | `media3/exoplayer/*`、`media3/datasource/cache/*`、`LibflacAudioRenderer` |
| 网络 | **OkHttp 4.12.0（无 Retrofit、无 Ktor）** | `okhttp/4.12.0` 字符串；`retrofit2` 计数 = 0 |
| 音源解析 | **QuickJS JS 引擎** | `libquickjs.so`（4 个 ABI）+ `Lcom/dokar/quickjs/QuickJs;` |
| 图像 | Coil + androidx.palette | `androidx.palette_*`、相机 EXIF 库 |
| 相机 | androidx.camera | 用于封面拍照/裁剪 |

`observed` —— **不是 Flutter**。判定依据：无 `libflutter.so`、无 `libapp.so`、无 `flutter_assets/`。`libquickjs.so` 容易被误读为 Flutter 相关，实为 JS 引擎。

### 3.1 native 库清单

`observed` —— 4 个 ABI（arm64-v8a / armeabi-v7a / x86 / x86_64），每个 ABI 6 个 `.so`：

| 库 | arm64 大小 | 用途 |
|---|---|---|
| `libquickjs.so` | 0.85 MB | JS 引擎，承载音源插件 |
| `libflacJNI.so` | 0.37 MB | FLAC 解码（media3 扩展） |
| `libimage_processing_util_jni.so` | 0.03 MB | 图像处理 |
| `libandroidx.graphics.path.so` | 0.01 MB | 路径渲染 |
| `libdatastore_shared_counter.so` | 0.01 MB | DataStore 多进程计数 |
| `libsurface_util_jni.so` | <0.01 MB | Surface 工具 |

`observed` —— **无第三方加固/反调试 native 库**。没有已知加固厂商的 `.so`，没有自解密 loader。

## 4. API 接口面

### 4.1 基址

`observed` —— 出现在 `classes2.dex` 字符串表中的主机：

| 基址 | 用途（`inferred`） |
|---|---|
| `https://guazi.fun` | 主站；`https://guazi.fun/api/m7/health` 为健康检查 |
| `https://api.guazi.fun` | 主 API 域 |
| `https://apple.guazi.fun` | Apple Music 解析服务 |
| `https://spotify.guazi.fun` | Spotify 解析服务 |
| `http://192.168.1.100:4533` | Navidrome 默认地址（局域网自建） |
| `https://dav.example.com` | WebDAV 占位默认值 |

`observed` —— 私有云 API 前缀为 **`/gzm7/private/`**，服务端路径前缀为 **`/api/m7`**。

### 4.2 端点清单

`observed` —— 以下字符串直接来自 `classes2.dex` 字符串表。标注 `inferred` 的是由字符串片段推导出的完整路径。

**认证**

| 端点 | 强度 |
|---|---|
| `/auth/login` | observed |
| `/auth/register` | observed |
| `/auth/send-code` | observed |
| `/auth/me` | observed |
| `/auth/me/nickname` | observed |
| `/auth/change-email` | observed |
| `/auth/change-password` | observed |
| `/auth/reset-password` | observed |
| `/auth/qr-login/create` | observed |
| `/auth/qr-login/confirm` | observed |
| `/auth/qr-login/status` | observed |
| `/auth/qr-login/info` | observed |
| `/auth/qr-login/exchange` | observed |

**私有云内容（`/gzm7/private/`）**

| 端点 | 强度 |
|---|---|
| `/gzm7/private/search` | observed |
| `/gzm7/private/content/` | observed |
| `/gzm7/private/music/search` | observed |
| `/gzm7/private/music/url` | observed |
| `/gzm7/private/music/lyric` | observed |
| `/gzm7/private/music/batch` | observed |
| `/gzm7/private/music/request` | observed |
| `/gzm7/private/music/amll/lyric` | observed |
| `/gzm7/private/music/amll/search` | observed |
| `/gzm7/private/home/feed` | observed |
| `/gzm7/private/dynamic-cover/search` | observed |
| `/gzm7/private/lyrics-plugin` | observed |
| `/gzm7/private/data-plugin` | observed |
| `/gzm7/private/plugin/fetch` | observed |
| `/gzm7/private/stats/leaderboard` | observed |

**内容与配置**

| 端点 | 强度 |
|---|---|
| `/announcement/list`、`/announcement/latest`、`/announcement/banners` | observed |
| `/app-content/home_notices` | observed |
| `/app-content/home_daily_playlists` | observed |
| `/app-content/home_time_slots` | observed |
| `/config`、`/listen/public/config` | observed |
| `/search/hot`、`/search/suggest` | observed |
| `/cover/search` | observed |
| `/beta/status`、`/beta/apply`、`/beta/exit`、`/beta/version/check` | observed |
| `/invite/create`、`/invite/my`、`/invite/redeem`、`/invite/rules` | observed |
| `/vip/redeem` | observed |
| `/trial/apply` | observed |
| `/sponsor` | observed |
| `/track/report`、`/track/report` | observed |
| `/update`、`/android-upgrade`、`/android/version/check` | observed |
| `/avatar` | observed |

**同步与实时**

| 端点 | 强度 |
|---|---|
| `/sync/pull`、`/sync/push`、`/sync/status` | observed |
| `/listen/ws?token=` | observed |
| `/listen/room`、`/listen/room/join`、`/listen/room/leave`、`/listen/room/close` | observed |
| `/prepare/track-change` | observed |

**外部集成**

| 端点 | 强度 |
|---|---|
| `/baidupan/oauth/start`、`/baidupan/disconnect`、`/baidupan/dlink`、`/baidupan/files`、`/baidupan/me` | observed |
| `/api/apple/parse` | observed |
| `/api/spotify/parse` | observed |
| `/api/m7/health` | observed |
| `/lyrics-api`、`/lyrics-plugin/access` | observed |
| `/dl/playlist-covers/` | observed |
| `/stream/preview_` | observed |
| `/soda/access` | observed |

`observed` —— 第三方平台域名（客户端直连，非经自有后端）：

```
interface.music.163.com   (网易云)     cgi-bin/musicu.fcg?format=json
c.y.qq.com / i.y.qq.com / u.y.qq.com / shu.y.qq.com / szu.y.qq.com   (QQ音乐)
y.gtimg.cn/music/photo_new/T002R300x300M000%s.jpg
img1.kuwo.cn/star/albumcover/           (酷我)
pan.baidu.com                            (百度网盘)
music.apple.com / open.spotify.com
musicbrainz.org
```

### 4.3 服务端下发配置（重要）

`observed` —— 以下 DTO 证明**首页与大量 UI 由服务端配置驱动**：

| DTO | 字段 |
|---|---|
| `HomeNoticesContent` | `isPublished`, `items` |
| `HomeDailyPlaylistsContent` | `isPublished`, `items`, `playlists`, `snapshotEnabled` |
| `HomeTimeSlotsContent` | `isPublished`, `slots`, `snapshotEnabled` |
| `HomeTimeSlotConfig` | `playlists` |
| `Announcement` | `actionLabel`, `actionUrl`, `bannerEnabled`, `content`, `createdAt`, `id`, `isPinned`, `isPublished`, `noti*` |
| `AppNoticeItem` | `cover`, `id`, `pinned`, `title`, `url` |
| `PublicConfig` | （配置聚合） |
| `BetaStatus` | `appliedAt`, `eligible`, `isLifetime`, `isVip`, `note`, `reapplyAt`, `reapplyCooldownDays` |

`inferred` —— 存在 `snapshotEnabled` / `CategoryFeedSnapshot` / `HomeFeedSnapshot` / `Cached*` 等实体，说明客户端对服务端下发的首页配置做**本地快照缓存**。这与「改本地数据后行为被服务端覆盖」的失效模式一致，属 apk-reverse 技能 `server-config-and-updates.md` 覆盖的场景。

## 5. 数据结构

`observed` —— 从 `classes2.dex` 提取到 **150 个 DTO、706 个字段**，完整机读清单见
[gzmusic-dto-surface.json](E:\code\music\Melos\docs\gz-reverse\gzmusic-dto-surface.json)（150 个 DTO / 706 个字段）。

字段名**未混淆**，可直接作为复刻依据。代表性样本：

| DTO | 字段 |
|---|---|
| `DeviceInfo` | `appVersion`, `deviceId`, `ip` |
| `AuthData` | `proToken`, `token`, `user` |
| `AndroidUpdateInfo` | `changelog`, `channel`, `downloadUrl`, `fileHash`, `fileSize`, `flavor`, `hasUpdate`, `isBeta`, `isForce`, `p*` |
| `AppleTrack` | `album`, `appleId`, `artist`, `coverUrl`, `durationMs`, `isrc`, `link`, `previewUrl`, `title` |
| `BaiduPanFile` | `fsId`, `isdir`, `name`, `path`, `serverMtime`, `size` |
| `BaiduPanDlink` | `dlink`, `size`, `thumb` |
| `CoverSearchHit` | `album`, `artist`, `coverUrl`, `durationMs`, `id`, `source`, `title` |
| `DynamicCoverSearchResponse` | `access`, `previewFrameUrl`, `squareVideoUrl`, `tallVideoUrl` |
| `InviteRulesData` | `activityDays`, `allowPermanent`, `enabled`, `inviteeRewardDays`, `inviterRewardDays`, `maxMonths` |
| `LeaderboardItem` | `avatar`, `bestRank`, `isVip`, `nickname`, `playCount`, `playMs`, `rank`, `streakDays`, `userId`, `username` |

`observed` —— 音源适配 DTO 明确覆盖三家：`Netease*`（网易云，含 `NeteasePlaylistDetail`、`NeteaseSimiSong`、`NeteaseTopPlaylistResponse`）、`Qq*`（QQ音乐，含 `QqSongInfoEnvelope`、`QqSimilarEnvelope`）、以及通用 `SearchSong` / `SearchAlbum` / `SearchArtist` / `SearchPlaylistItem`。

## 6. LX 插件系统（核心架构）

`observed` —— `com.gzmusic.app.core.lx` 包下 **229 个类**（应用自身类总计 1561 个），这是本应用最具研究价值的部分。它把「音源」做成**可下发的 JavaScript 插件**，用 QuickJS 执行。

关键类：

| 类 | 职责（`inferred` 自类名与方法签名） |
|---|---|
| `LxJsRuntime` | JS 运行时封装；`evalRaw`、`dispatchRequestAsync`、`asyncFunction` 注入 |
| `LxHttpBridge` | JS → 原生 HTTP 桥；`request` 方法供脚本发起网络请求 |
| `LxNativeHost` | 向 JS 注入原生能力 |
| `DataPluginManager` | 数据插件生命周期（`refresh`） |
| `DataPluginVerifier` | 插件校验 |
| `DataPluginState` | 插件状态机（`Status`） |
| `LxScriptGate` | 脚本权限闸门 |
| `LxBootstrap` | 启动引导 |
| `LxPlaylistImporter` / `LxPlaylistParser` | 歌单导入与解析（`LxTrack`、`LxUserPlaylist`） |
| `LxKeyIssue` / `LxKeyIssueKind` / `LxKeyFailureException` | 「音源密钥」失效处理 |
| `LxPluginManager` / `LxPluginTier` / `LxPluginPriority` | 插件分级与排序 |
| `LxMaps` / `LxModelsKt` / `LxMusicInfoBuilder` | 数据模型与映射 |

`observed` —— 插件相关服务端端点：`/gzm7/private/lyrics-plugin`、`/gzm7/private/data-plugin`、`/gzm7/private/plugin/fetch`（DTO `FetchRemotePluginRequest{url}` / `FetchRemotePluginResponse{content}`），说明**插件内容由服务端下发或按 URL 拉取**。

`observed` —— 配套的 UI：`feature/player/LxKeyIssueDialogKt`（密钥问题弹窗）、`playlists/LxPlaylistImportUiKt`（歌单导入界面），证明该体系是**面向用户的一等功能**，非内部预留。

> **对 Melos 的意义**：这是 GZ Music 与 Melos 最大的架构差异。Melos 目前是后端固定音源；GZ Music 是「后端下发 JS 插件 + 客户端 QuickJS 执行」。若要复刻同类能力，需要 (a) 定义插件脚本协议，(b) 在客户端嵌入 JS 引擎，(c) 实现 `http` / 原生能力的桥接与权限闸门。`libquickjs.so` 对应的开源绑定是 `com.dokar.quickjs`（`observed`：字符串 `Lcom/dokar/quickjs/QuickJs;`）。

## 7. 鉴权与加密

`observed` —— 字符串表加密算法标记计数：

| 标记 | 次数 |
|---|---|
| `AES` / `aes` | 39 |
| `token` / `Token` / `TOKEN` | 40 |
| `sign` / `Sign` / `SIGN` | 16 |
| `Signature` / `signature` / `SIGNATURE` | 13 |
| `md5` / `MD5` | 9 |
| `HMAC` / `Hmac` | 5 |
| `SHA256` / `sha256` | 7 |
| `SHA1` / `sha1` | 5 |
| `encrypt` / `decrypt` | 11 |

`observed` —— 存在 Google Tink 引用（`classes2.dex` 中 `tink` 出现 17 次），包括 `type.googleapis.com/google.crypto.tink.ChaCha20Poly1305Key`、`HmacKey`、`XChaCha20Poly1305Key`。`inferred`：Tink 用于本地敏感数据加密（如账号凭据、百度网盘 token），而非网络签名。

`observed` —— `javax/crypto` 引用 8 次，`MessageDigest` 8 次。

**`observed` —— 关键结论：应用不使用自身签名证书作为密钥材料。**

| 标记 | 计数 |
|---|---|
| `toCharsString` | **0** |
| `signatures` | **0** |
| `GET_SIGNATURES` | **0** |
| `GET_SIGNING_CERTIFICATES` | **0** |
| `getPackageInfo` | 1（非签名用途） |

这一点直接决定重打包路线的可行性，见 §10。

`observed` —— **无证书固定**：`CertificatePinner` 计数 = 0，无 `network_security_config` 引用。

## 8. 数据流（`inferred`）

```
UI (Compose)
   ↓
ViewModel (Hilt 注入)
   ↓
Repository (core/data/)
   ├─→ Remote  (OkHttp → /gzm7/private/* 或 /auth/*)
   ├─→ Local   (Room: Cached*/Snapshot*/Entity*)
   └─→ Lx      (QuickJS 执行 JS 音源插件)
                  └─→ LxHttpBridge → OkHttp → 第三方平台
                                        (网易云 / QQ / 酷我 / Apple / Spotify / 百度网盘)
   ↓
Media3 / ExoPlayer  (MusicService + SimpleCache 缓存)
```

`observed` 支撑该图的证据：`core/data/remote/dto/`（150 DTO）、`core/data/local/entity/`（`CachedSong`、`CachedPlaylist`、`CategoryFeedSnapshot`、`HomeFeedSnapshot`、`NavidromeAccountEntity`、`WebDavAccountEntity`、`BaiduPanTrackEntity`）、`core/lx/`（227 类）、`core/media/MusicService`、`media3/datasource/cache/*`。

## 9. 更新与远程配置

`observed` —— 强制更新链路完整存在：

| 项 | 值 |
|---|---|
| 类 | `feature/update/UpdateCheckEntryKt`、`feature/update/UpdateDialogKt` |
| DTO | `AndroidUpdateInfo`（含 `hasUpdate`、`isForce`、`isBeta`、`fileHash`、`fileSize`、`channel`、`flavor`、`downloadUrl`） |
| 端点 | `/update`、`/android-upgrade`、`/android/version/check`、`/beta/version/check` |

`inferred` —— `isForce` 字段说明存在强制更新弹窗；`fileHash` 说明更新包做完整性校验。

`observed` —— **远程配置通道存在**：`/config`、`/listen/public/config`、`HomeTimeSlotConfig`（首页按时段变化）、`snapshotEnabled` + `Cached*Snapshot` 实体（本地快照）。这意味着任何本地修改都可能被服务端配置覆盖，是 `server-config-and-updates.md` 描述的持久性风险。

## 10. 重打包可行性结论

按 apk-reverse 技能 G1 的「离开重建 APK 路线」三个触发条件逐条核验：

| 触发条件 | 核验结果 | 强度 |
|---|---|---|
| (a) 多于一个必须全部通过的独立完整性校验 | **未发现**。无签名派生密钥、无证书固定、无 VMP | observed |
| (b) 抽取壳（方法体仅调用时存在） | **未发现**。类与方法体完整 | observed |
| (c) 任何方法体解码为私有操作码 | **未发现**。无 VMP 特征 | observed |

**结论（`observed` 支撑，`inferred` 判定）：重建并重签一个可安装 APK 的路线在技术上未被阻断。**

补充事实：

- `observed` —— 签名现状：APK Signature Scheme **v2 = true，v3 = true，v1 = false**。
- `observed` —— 签名者：`CN=GZ Music, OU=GZMusic, O=GZMusic, L=Beijing, ST=Beijing, C=CN`，RSA-2048。
  - 证书 SHA-256：`e430ac37cef16090ef319eaa36ab59046bb41c09443ca8fc0237ab891d158d79`
  - 证书 SHA-1：`b89781dc8bbcbae608d59572e39beb40ccc243b0`
  - 公钥 SHA-256：`6c3463c20a1092bd144a63582d7fb9364812e62c53ecd3999fc855473d03189e`
- `observed` —— `usesCleartextTraffic="true"`：应用允许明文 HTTP，这对本地代理抓包调试有利。

**未验证项（必须说明）**：本结论仅覆盖**静态**条件。以下无法在无设备条件下确认，标为 `unverified`：

1. 服务端是否校验客户端完整性（如请求签名绑定包名/证书）—— 静态未见，但服务端行为无法从 APK 判定。
2. 运行时是否存在服务端下发的 JS 插件级校验（`DataPluginVerifier` 的具体校验内容未反编译）。
3. 重签后 `proToken` / `token` 的服务端接受度。

## 11. 设备基线（分析期间接入）

`observed` —— 分析过程中接入一台真机，`preflight.py` 结果：

| 项 | 值 |
|---|---|
| 设备 | `24129PN74C`（Xiaomi，代号 `dada`），序列号 `33d8e86f` |
| 系统 | Android 16（SDK 36） |
| ABI | `arm64-v8a`（`abilist=[arm64-v8a]`，无 ARM 转译） |
| SELinux | Enforcing |
| **root** | **不可用**（`su: inaccessible or not found`） |
| 时钟漂移 | 1 秒（可接受） |
| 代理 | 未设置 |
| frida-server | 设备上无进程 |
| 存储 | 185G 可用 / 479G |

`observed` —— **设备上已安装的正是同一构建**：

```
versionName=2.1.8   versionCode=38   primaryCpuAbi=arm64-v8a
lastUpdateTime=2026-10-06 20:10:25   firstInstallTime=2026-08-17 09:04:30
Signatures: [E4:30:AC:37:CE:F1:60:90:EF:31:9E:AA:36:AB:59:04:6B:B4:1C:09:
             44:3C:A8:FC:02:37:AB:89:1D:15:8D:79]
```

该签名指纹与本地 APK 证书 SHA-256（§10）**逐字节一致**，故设备上的 `base.apk` 与本次分析的目标是同一构建。这满足 apk-reverse 技能 G4（基线与对照）中「已有未修改原件可作对照」的条件。

**root 缺失的后果**（`observed`，`preflight.py` 明确报告）：

- `frida_dynamic` / `frida_rpc` 能力在本机+本设备组合下**仍为 PARTIAL**：无 root 则无法 attach 目标进程，也无法向设备推送并以 root 启动 frida-server。
- 无法读取 `/data/data/com.gzmusic.app/`（DataStore、Room 数据库、百度网盘 token 等运行态数据）。
- 静态分析与 `adb` 层面的操作（安装、拉取、启动、截图、logcat）**不受影响**。

## 12. 与 Melos 现状对照

`observed` —— Melos 后端（`backend-go/`，Go + Gin）当前路由：

```
POST   /api/auth/send-code|login|email-login|register|logout|logout-all
GET    /api/me                              PUT /api/me
PUT    /api/me/password
POST   /api/me/email/bind|unbind
GET    /api/me/sessions                     DELETE /api/me/sessions/:id
GET    /api/sync/pull                       POST /api/sync/push
GET    /api/app/version/latest              GET /api/app/download/:file
GET    /api/health
       /api/admin/users|versions|sync/stats  (CRUD)
```

对照结论：

| 能力域 | GZ Music | Melos 现状 | 差距 |
|---|---|---|---|
| 账号认证 | `/auth/*` + 二维码登录 | `/api/auth/*`，**无二维码登录** | 缺 qr-login 5 个端点 |
| 用户资料 | `/auth/me`、`/auth/me/nickname`、`/auth/change-*` | `/api/me` 系列 | 基本对齐 |
| 数据同步 | `/sync/pull`、`/sync/push`、`/sync/status` | `/api/sync/pull`、`/api/sync/push` | **缺 status** |
| 版本更新 | `/update`、`/android-upgrade`、`/android/version/check`、`/beta/version/check` | `/api/app/version/latest` | 命名与语义均不同；**缺 beta 通道** |
| 音乐内容 | `/gzm7/private/music/{search,url,lyric,batch,request}` | **无** | 未实现 |
| 搜索 | `/gzm7/private/search`、`/search/hot`、`/search/suggest` | **无** | 未实现 |
| 首页配置 | `/gzm7/private/home/feed`、`/app-content/home_*` | **无** | 未实现 |
| 封面 | `/cover/search`、`/gzm7/private/dynamic-cover/search` | **无** | 未实现 |
| 歌词 | `/lyrics-api`、`/gzm7/private/music/lyric`、`amll/*` | **无** | 未实现 |
| 插件 | `/gzm7/private/{data,lyrics}-plugin`、`plugin/fetch` | **无** | 未实现（核心架构差异） |
| 公告 | `/announcement/{list,latest,banners}` | **无** | 未实现 |
| 邀请/兑换 | `/invite/*`、`/vip/redeem`、`/trial/apply` | **无** | 未实现 |
| 一起听 | `/listen/ws`、`/listen/room/*` | **无** | 未实现 |
| 网盘 | `/baidupan/*` | **无** | 未实现 |
| 统计 | `/gzm7/private/stats/leaderboard` | **无** | 未实现 |

> **注意**：上表「GZ Music 列」的端点均来自 APK 字符串表（`observed`），**不代表这些端点当前在服务端可用**——未做任何网络请求验证。Melos 列来自源码（`observed`）。

## 13. 复现命令

```powershell
# 目标指纹
Get-FileHash "E:\code\music\Melos\GZ_Music-2.1.8-release.apk" -Algorithm SHA256

# 签名
& "F:\Android\Sdk\build-tools\36.0.0\apksigner.bat" verify --verbose --print-certs `
  "E:\code\music\Melos\GZ_Music-2.1.8-release.apk"

# 环境能力自检（apk-reverse 技能）
$env:PYTHONIOENCODING="utf-8"
$env:APKREV_TOOLS="F:\Android\Sdk\build-tools\36.0.0;F:\Android\Sdk\build-tools\35.0.0"
python "C:\Users\17524\.dsh\skills\apk-reverse\scripts\doctor.py"

# DTO 提取（本报告 §5 的来源）
python "F:\dgn\本机运维\work\dto.py"
```

## 14. 未做的事与边界

明确声明，避免把「未检查」读成「已确认无」：

| 项 | 状态 |
|---|---|
| 动态分析（Frida hook、运行时验证） | **未做** —— 无设备连接 |
| 网络抓包 / 实际请求验证 | **未做** —— 未向任何服务端发起请求 |
| 反编译方法体（jadx 级 Java 语义） | **未做** —— 仅到类/字段/字符串层 |
| `DataPluginVerifier` 校验逻辑细节 | **未做** |
| JS 插件脚本协议与示例 | **未做** —— 需运行应用或抓取下发内容 |
| 资源层（`resources.arsc`、Compose 主题值） | **未做** |
| 服务端接口实际可用性 | **未验证** |
| 重打包后的运行表现 | **未验证** —— 未构建、未安装 |

## 15. 建议的下一步

按性价比排序（`inferred`）：

1. **接入设备跑 `preflight.py` 建立基线**，然后用 `frida` hook `LxHttpBridge.request` —— 这一步能同时拿到「真实请求头/鉴权格式」和「JS 插件脚本内容」，是当前最大的信息缺口。
2. **抓取一次 `/gzm7/private/data-plugin` 响应** —— 直接得到插件脚本格式，这是复刻插件体系的前提。
3. **对 `AuthData` / `DeviceInfo` 的构造路径做反编译** —— 确认 `token` / `proToken` / `deviceId` 的生成与携带方式。
4. 补齐 apktool + jadx（各约 5 分钟），可显著提升后续方法体级分析效率。
