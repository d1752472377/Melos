# Aesthetic Music

一个本地优先的音乐播放器项目，包含：

- `android/`：原生 Android 客户端（Kotlin + Jetpack Compose + Media3）
- `backend/`：Node.js 后端（Fastify + SQLite），提供统一音乐 API、音源管理与用户数据同步

## 功能

- 聚合搜索 / 播放 / 歌词（酷我、网易云、ChKSz 等音源）
- 播放队列、收藏、最近播放、歌单、听歌统计
- 全屏播放器、底部悬浮导航、主题切换
- 后端管理后台：动态增删音源、启停、权重、密钥加密存储

## 项目结构

```
.
├── android/          # Android 客户端（Kotlin + Compose）
├── backend/          # Node.js 后端（Fastify + SQLite）
├── docs/             # 音源调研文档
├── icon/             # 多平台应用图标素材
└── README.md
```

## 快速开始

### 后端

```bash
cd backend
npm install
cp .env.example .env   # 填写 ENCRYPTION_KEY / ADMIN_PASSWORD 等
npm run seed           # 可选：写入种子音源
npm start              # 默认 http://127.0.0.1:8787
```

详细说明见 [`backend/README.md`](backend/README.md)。

### Android

用 Android Studio 打开 `android/` 目录直接运行，或：

```bash
cd android
gradle assembleDebug
```

默认连接后端地址为 `http://10.0.2.2:8787/`（模拟器访问宿主机）。真机调试请修改 `android/app/src/main/java/com/aesthetic/music/data/api/ApiClient.kt` 中的后端地址。

详细说明见 [`android/README.md`](android/README.md)。

## 注意事项

- 所有密钥只放在本地 `.env`，不会提交到 Git；请参考 `.env.example` 配置。
- `deploy/` 目录包含服务器部署脚本和敏感信息，已加入 `.gitignore`，请勿上传。
