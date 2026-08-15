# music-backend

音乐 App 后端：**音源管理 + 统一音乐业务 API**。

- App 端只认一套业务 API（搜索 / 取直链 / 取歌词），不感知具体音源。
- 管理端可动态增删音源、启用/禁用、调整权重、热切换，无需发版。
- 音源密钥 **AES-256-GCM 加密落库**，管理 API 只返回脱敏后的配置。

## 技术栈

- Node.js ≥ 22（使用内置 `node:sqlite`，零原生依赖）
- Fastify 5 + @fastify/cors
- @meting/core（网易云源）

## 目录结构

```
backend/
├── src/
│   ├── index.js            # 入口（启动 HTTP 服务）
│   ├── seed.js             # 种子数据（把 .env 里的密钥加密写入 DB）
│   ├── lib/
│   │   ├── config.js       # 环境变量加载与校验
│   │   ├── crypto.js       # AES-256-GCM 加密/解密/脱敏
│   │   ├── db.js           # SQLite 存储层
│   │   ├── auth.js         # 管理端鉴权（登录 + HMAC token）
│   │   └── registry.js     # 音源注册表（运行时状态 + 降级 + 健康度）
│   ├── sources/
│   │   ├── MusicSource.js  # 音源统一接口（抽象基类）
│   │   ├── kuwo.js         # 酷我源（妖狐 API）
│   │   ├── netease.js      # 网易云源（meting）
│   │   └── index.js        # 类型 → 实现 工厂
│   └── routes/
│       ├── music.js        # 业务 API（App 调用）
│       └── admin.js        # 管理 API（鉴权）
```

## 快速开始

```bash
cd backend
npm install

# 1. 配置环境变量
cp .env.example .env   # 然后编辑 .env，填入 ENCRYPTION_KEY / ADMIN_PASSWORD / 音源密钥

# 2. 写入种子音源（幂等）
npm run seed

# 3. 启动
npm start              # 或 npm run dev（--watch 热重载）
```

服务默认监听 `http://127.0.0.1:8787`（`.env` 里的 `PORT`）。

## 管理后台

启动后浏览器访问 **`http://127.0.0.1:8787/admin/`**，用 `.env` 里的 `ADMIN_USER` / `ADMIN_PASSWORD` 登录。

功能：音源列表、启用/禁用开关、权重调整、添加/编辑/删除音源、连通性测试、重载注册表。密钥字段在编辑时只显示脱敏占位（留空 = 不修改）。

纯静态单页（`public/admin/`），无构建，由 `@fastify/static` 托管。

## 密钥保护（重要）

1. **所有密钥只放 `.env`**，`.env` 已加入 `.gitignore`，只提交 `.env.example`（占位符）。
2. `ENCRYPTION_KEY` 是 32 字节 hex，用于加密音源密钥。生成方式：
   ```bash
   node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
   ```
3. 音源密钥（apiKey/secretKey/cookie）**加密后存入 SQLite**（`sources.config` 字段是密文）。
4. 管理 API 返回音源信息时，敏感字段**脱敏**（如 `zNPP****wZAp`），绝不明文回传。

## API 文档

### 业务 API（App 调用，无需鉴权）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/search?keyword=&limit=` | 聚合搜索所有健康源 |
| GET | `/api/song/url?platform=&id=&title=&quality=` | 取播放直链（quality: standard/high/lossless） |
| GET | `/api/lyric?platform=&id=&title=` | 取歌词 |
| GET | `/api/sources` | 音源健康状态列表 |

> `platform/id/title` 来自搜索结果的歌曲对象，App 原样回传即可。

### 管理 API（需鉴权）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/admin/login` | 登录，body `{username, password}`，返回 token |
| GET | `/admin/sources` | 音源列表（脱敏） |
| POST | `/admin/sources` | 新增音源 `{id, name, type, weight, config}` |
| PUT | `/admin/sources/:id` | 更新（启用/禁用/权重/密钥，config 增量合并） |
| DELETE | `/admin/sources/:id` | 删除音源 |
| GET | `/admin/sources/:id/test` | 连通性测试 |
| GET | `/admin/reload` | 重载注册表（增删改后一般已自动 reload） |

**鉴权方式**：除 `/admin/login` 外，管理 API 需请求头 `Authorization: Bearer <token>`。

```bash
# 登录拿 token
curl -X POST http://127.0.0.1:8787/admin/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"你的密码"}'

# 带 token 查看音源
curl http://127.0.0.1:8787/admin/sources -H 'Authorization: Bearer <token>'
```

## 音源类型

| type | 说明 | 需要的 config |
|---|---|---|
| `kuwo` | 酷我（妖狐 API） | `apiKey`, `secretKey`, `baseUrl`(可选), `defaultQuality`(可选) |
| `netease` | 网易云（meting） | `cookie`(可选) |

## 添加新音源

1. 在 `src/sources/` 下新建类，继承 `MusicSource`，实现 `search` / `getSongUrl` / `getLyric`。
2. 在 `src/sources/index.js` 的 `createSource` 里加一个 `case`，并在 `SUPPORTED_TYPES` 里登记类型。
3. 通过管理 API 添加该类型音源（或写进 `seed.js`）。

## 降级与健康度

- `registry.getSongUrl()` 优先用歌曲所属源，失败后按权重降级到其它源。
- 某源连续失败 5 次后进入 60 秒冷却（冷却期内跳过），自动恢复。
- 酷我源内置 QPS 限流 + 503 自动重试（见 `sources/kuwo.js`）。


