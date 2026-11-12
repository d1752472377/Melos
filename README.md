# Melos

一个本地优先的 Android 音乐播放器，带网易云 / QQ 音乐 / 酷狗 / 酷我 四平台聚合搜索与播放、
歌词与评论浏览、听歌识曲，以及一套可自托管的账号与同步后端。

> 本项目不内置、不托管任何音频文件；播放能力来自第三方公开接口，仅供学习与个人使用。

## 仓库结构

```
.
├── android/        # Android 客户端（Kotlin + Jetpack Compose + Media3）
├── backend-go/     # Go 后端（Gin + SQLite）：账号、同步、版本分发、日志、反馈
├── music-admin/    # 管理后台（Vite + Ant Design + Refine）
├── scripts/chksz/  # 音源脚本工具（LX Music 自定义源协议）
├── docs/           # 架构、设计与维护文档
└── icon/           # 应用图标素材
```

## 快速开始

### Android 客户端

用 Android Studio 打开 `android/` 目录即可运行，或：

```bash
cd android
./gradlew :app:assembleDebug
```

正式打包需要自行准备 `android/keystore/aesthetic.jks` 与口令（见 `android/README.md`），
仓库不包含签名文件。

### 后端

```bash
cd backend-go
cp .env.example .env     # 按需修改 DB_URL / ADMIN_INITIAL_PASSWORD / 邮件服务等
go run .
```

默认监听 `:8787`。首次启动会自动建表并创建管理员账号，初始密码见 `.env.example`
（**部署后请立即修改**）。

### 管理后台

```bash
cd music-admin
cp .env.example .env
pnpm install && pnpm dev
```

生产构建 `pnpm build`，产物在 `dist/`，需要把 `/api/` 反向代理到后端。

## 第三方资源

| 资源 | 用途 | 许可 |
|---|---|---|
| [MiSans](https://hyperos.mi.com/font/) | 界面字体（内置 `res/font/misans_variable.ttf`） | 由小米提供，允许免费使用与嵌入，需注明来源 |
| [LX Music 核心](https://github.com/lyswhut/lx-music-desktop) | 自定义音源脚本宿主（`android/app/src/main/assets/script/lx-core.js`） | Apache-2.0 |

## 说明

- 仓库不包含服务器部署脚本、签名文件、运行时数据库与本机配置。
- 第三方闭源应用的逆向分析产物不在本仓库内。
- 使用第三方接口可能受其服务条款限制，请自行评估合规性。
