# melos-backend

音乐 App 后端，**职责单一**：只做「用户体系」+「多端数据同步」。不含任何音源聚合、歌词、管理后台、下载代理等逻辑（已全部移除）。

## 技术栈

- Node.js ≥ 18，ESM
- Fastify 5
- `@libsql/client`（SQLite 兼容；本地 `file:` 或远端 `libsql://` / `http://`，支持 Vercel/Turso）

## 目录结构

```
backend/
├── api/[...].js          # Vercel Serverless 入口
├── src/
│   ├── app.js            # 构建 Fastify 应用（不监听端口）
│   ├── start.js          # 本地/自托管入口
│   ├── lib/
│   │   ├── config.js     # 环境变量加载
│   │   ├── db.js         # 存储层：schema + 用户/会话/同步查询
│   │   ├── auth.js       # scrypt 密码 + 不透明 token + 会话中间件
│   │   └── rateLimit.js  # 每 IP 限速
│   └── routes/
│       ├── auth.js       # 注册/登录/登出/我/设备会话
│       └── sync.js       # 同步 pull/push
├── scripts/smoke.mjs     # 端到端冒烟测试
├── .env.example
└── vercel.json
```

## 快速开始

```bash
cd backend
npm install

# 1. 配置环境变量（可全留空，用默认值先跑通）
cp .env.example .env

# 2. 启动
npm start              # 或 npm run dev（--watch）
```

默认监听 `http://0.0.0.0:8787`。数据库默认落在 `data/music.db`（自动建库建表）。

## 环境变量（全部可选）

| 变量 | 默认 | 说明 |
|---|---|---|
| `PORT` | `8787` | 监听端口 |
| `HOST` | `0.0.0.0` | 监听地址（仅本机用 `127.0.0.1`） |
| `DB_URL` | `file:data/music.db` | libSQL 连接串 |
| `DB_AUTH_TOKEN` | 空 | 远端库（Turso/sqld）认证 token |
| `SESSION_TTL_DAYS` | `90` | 登录会话有效期（天） |
| `MAX_SESSIONS_PER_USER` | `10` | 单用户最多并发会话数 |
| `TRUST_PROXY` | 关 | 反代（Vercel/nginx）后置 `1`，用于限速识别真实 IP |
| `RATE_LIMIT_DISABLED` | 关 | 置 `1` 关闭限速（调试） |

## 认证模型

- 登录签发**不透明随机 token**（`crypto.randomBytes(32)`），服务端只存 SHA-256 哈希。
- **登出即销毁**：`logout` 删当前会话行，token 立刻失效；`logout-all` 删该用户全部会话。不存在“签出去收不回”的 JWT 堆积问题。
- 会话 90 天过期（可配），登录时顺带清理过期行；单用户最多 `MAX_SESSIONS_PER_USER` 个会话，超出淘汰最久未活动的。
- 客户端在所有需要鉴权的请求头带：`Authorization: Bearer <token>`。

## 同步模型（多端 last-write-wins + 墓碑）

- **版本号**：服务端为每次推送分配一个单调递增的 `version`（`sync_seq`），由数据库事务内原子自增。
- **按记录覆盖**：收藏/历史/歌单/歌单歌曲每条记录带 `update_seq`，谁后推送谁就是最新（**以服务端收到的时间/顺序为准**）。
- **删除 = 墓碑**：删除不物理清空，而是写一条 `deleted=true` 记录。设备 A 删除后，晚一步推送旧快照的设备 B 不会把已删项“复活”。
- **增量拉取**：客户端记住上次的 `version` 和 `nextEventId` 作游标；拉取只返回变化（含墓碑）。首次（`since=0`）为全量。
- **听歌统计**（`play_events`）是 append-only 幂等事件，按 `event_id`（客户端生成）去重，用 `nextEventId` 游标分页。

### 同步载荷（请求头需带 token）

精品字段约定：歌曲对象 `{ id, title, artist, album, artwork, platform }`。

#### `GET /api/sync/pull?since=<version>&eventSince=<eventId>`

```jsonc
{
  "code": 200,
  "data": {
    "version": 42,           // 存起来，下次作为 since
    "nextEventId": 108,      // 存起来，下次作为 eventSince
    "serverTime": 1787000000000,
    "favorites":  [ { "platform": "netease", "id": "186016", "song": {…} | null, "updatedAtMs": …, "deleted": false } ],
    "history":    [ { "platform": "netease", "id": "186016", "song": …, "playedAtMs": …, "updatedAtMs": …, "deleted": false } ],
    "playlists":  [ { "id": "pl_xx", "name": "…", "coverUrl": "…", "updatedAtMs": …, "deleted": false } ],
    "playlistSongs": [ { "playlistId": "pl_xx", "position": 0, "platform": "netease", "songId": "186016", "song": …, "updatedAtMs": …, "deleted": false } ],
    "playEvents": [ { "eventId": "…", "platform": "netease", "songId": "186016", "title": "…", "artist": "…", "listenedMs": 120000, "durationMs": 269000, "listenedAtMs": …, "updatedAtMs": … } ]
  }
}
```

> `song` 为 `null` 且 `deleted: true` 表示已删除，客户端据此移除本地记录。

#### `POST /api/sync/push`

只提交**变更**（也可全量快照，幂等）。每个实体按条 upsert/delete：

```jsonc
{
  "favorites":      { "upsert": [ {歌曲对象} ], "delete": [ { "platform": "…", "id": "…" } ] },
  "history":        { "upsert": [ { …歌曲对象, "playedAtMs": 1787000000000 } ], "delete": [ { "platform": "…", "id": "…" } ] },
  "playlists":      { "upsert": [ { "id": "pl_xx", "name": "…", "coverUrl": "…" } ], "delete": [ { "id": "pl_xx" } ] },
  "playlistSongs":  { "upsert": [ { "playlistId": "pl_xx", "position": 0, "song": {歌曲对象} } ],
                     "delete": [ { "playlistId": "pl_xx", "platform": "…", "songId": "…" } ] },
  "playEvents":     [ { "eventId": "uuid", "platform": "…", "id": "…", "title": "…", "artist": "…", "listenedMs": 120000, "durationMs": 269000, "listenedAtMs": 1787000000000 } ]
}
```

响应：`{ "code": 200, "data": { "version": 43, "acceptedEvents": 1, "serverTime": … } }`

### 客户端建议的同步节奏

1. 首次：`pull?since=0&eventSince=0` 全量落库。
2. 之后定时/主动：`pull?since=<上一次version>&eventSince=<上一次nextEventId>`。
3. 本地有变化时：`push` 增量。
4. 缓存满、长长期离线的兜底：隔一段时间做一次全量 `since=0` 校验。

## API 一览

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/api/auth/register` | 否 | 注册（返回 token） |
| POST | `/api/auth/login` | 否 | 登录（返回 token） |
| POST | `/api/auth/logout` | 是 | 登出当前会话 |
| POST | `/api/auth/logout-all` | 是 | 登出所有设备 |
| GET | `/api/me` | 是 | 当前用户 |
| PUT | `/api/me` | 是 | 改昵称/头像 |
| GET | `/api/me/sessions` | 是 | 当前设备列表 |
| DELETE | `/api/me/sessions/:id` | 是 | 移除某设备会话 |
| GET | `/api/sync/pull` | 是 | 增量拉取 |
| POST | `/api/sync/push` | 是 | 增量推送 |

所有响应统一 `{ code, msg?, data? }`；错误码：`400` 参数、`401` 未登录/过期、`409` 冲突、`429` 限流/锁定、`500` 内部。

## 测试

```bash
npm run smoke
```

`scripts/smoke.mjs` 用 `app.inject()`（不占端口）端到端验证：注册 → 登录 → push → pull 增量 → 墓碑删除 → 登出失效 → 跨设备最新覆盖。

## 部署到 Vercel（可选）

1. 仓库根目录设为 `backend`。
2. Framework Preset 选 `Other`，Build/Output 留空。
3. 环境变量：`DB_URL`（`libsql://…` Turso）+ `DB_AUTH_TOKEN`，并设 `TRUST_PROXY=1`。
4. 首次到 Turso 建库即可（本服务启动时自动建表，无需手动 seed）。

> 说明：本地 `file:` 模式下，多端同步要求各设备都连到**同一台固定服务器**；若需跨网络/多节点，用远端 libSQL（Turso/sqld）即可。