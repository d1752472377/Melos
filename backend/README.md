# melos-backend

Melos 音乐平台的后端核心服务，提供**高可靠的用户体系**、**多端增量数据同步引擎**以及**管理后台接口支持**。

---

## 目录

- [一、核心定位与职责](#一核心定位与职责)
- [二、技术栈](#二技术栈)
- [三、工程目录结构](#三工程目录结构)
- [四、快速开始](#四快速开始)
- [五、环境变量全景](#五环境变量全景)
- [六、核心架构与设计原理](#六核心架构与设计原理)
  - [1. 认证与会话体系](#1-认证与会话体系)
  - [2. 多端同步引擎（LWW + 墓碑）](#2-多端同步引擎lww--墓碑)
  - [3. 防爆破与请求限流](#3-防爆破与请求限流)
  - [4. 用户资料字段](#4-用户资料字段)
- [七、API 接口全览](#七api-接口全览)
  - [1. 服务健康与探测](#1-服务健康与探测)
  - [2. 用户认证与个人中心（Auth / Me）](#2-用户认证与个人中心auth--me)
  - [3. 多端数据同步（Sync）](#3-多端数据同步sync)
  - [4. 管理员控制台（Admin）](#4-管理员控制台admin)
- [八、多端对接指南](#八多端对接指南)
  - [1. Android 客户端对接](#1-android-客户端对接)
  - [2. Web 管理后台（music-admin）对接](#2-web-管理后台music-admin对接)
- [九、生产部署指南](#九生产部署指南)
  - [1. PM2 / 自托管服务器部署](#1-pm2--自托管服务器部署)
  - [2. Docker 容器化部署](#2-docker-容器化部署)
  - [3. Vercel Serverless + Turso 部署](#3-vercel-serverless--turso-部署)
- [十、自动化测试](#十自动化测试)

---

## 一、核心定位与职责

本后端保持**高度专注与单一职责**，专为 Melos 生态打造：

1. **用户体系与设备会话**：注册、登录、密码修改、多设备并发登录管理、设备强制踢线。
2. **多端数据同步**：基于单调递增版本号与 LWW（Last-Write-Wins）冲突仲裁机制，负责收藏歌曲、历史记录、自建歌单与听歌事件的多端实时同步。
3. **后台运维支撑**：提供 RESTful 接口供 `music-admin` 运营端进行用户检索、状态变更、角色分配与密码重置。

> **说明**：音源聚合搜索、歌词拉取、音频流播放等逻辑均在客户端本地（Android 端）直接处理，本后端不代理音源流量或歌曲文件，极度轻量且易于维护。

---

## 二、技术栈

- **Runtime**：Node.js ≥ 18（原生 ESM 规范）
- **Web 框架**：Fastify 5（高性能、低开销）
- **存储引擎**：`@libsql/client`
  - 单机/本地环境：零配置的 SQLite（`file:` 协议）
  - 云原生/分布式环境：Turso Cloud 或自建 sqld（`libsql://` / `http://` 协议）

---

## 三、工程目录结构

```
backend/
├── api/
│   └── [...].js          # Vercel Serverless 无服务器入口适配
├── src/
│   ├── app.js            # Fastify App 实例构建、全局 CORS、多桶限流与错误处理
│   ├── start.js          # 本地/自托管生产运行入口（带启动过期数据自动清理）
│   ├── lib/
│   │   ├── config.js     # 环境变量读取与安全兜底转换
│   │   ├── db.js         # 数据访问层：Schema 定义、CRUD 及 LWW 事务同步引擎
│   │   ├── auth.js       # 密码哈希（scrypt）、不透明 Token 鉴权与防爆破
│   │   ├── seed.js       # 内置管理员账号自动初始化
│   │   └── rateLimit.js  # 独立桶滑动窗口 IP 限流器
│   └── routes/
│       ├── auth.js       # 认证、用户资料、密码修改与设备会话路由
│       ├── sync.js       # 增量拉取（pull）与推送（push）同步路由
│       └── admin.js      # 管理员专用用户管理路由（兼容 simple-rest）
├── scripts/
│   └── smoke.mjs         # 端到端自动化冒烟测试（49 项核心链路断言）
├── data/                 # 本地 SQLite 数据文件存放目录（git 忽略）
├── .env.example          # 环境变量示例模板
├── package.json          # 项目依赖与运行脚本
└── vercel.json           # Vercel 路由与地域配置
```

---

## 四、快速开始

### 1. 安装依赖

```bash
cd backend
npm install
```

### 2. 准备环境变量

复制模板配置文件：

```bash
# Linux / macOS:
cp .env.example .env

# Windows PowerShell:
Copy-Item .env.example .env

# Windows CMD:
copy .env.example .env
```

*本地开发全部留空即可正常启动，默认使用 SQLite 单机模式。*

### 3. 启动服务

```bash
# 开发模式（监听文件变动热重启）
npm run dev

# 生产/单次启动
npm start
```

服务启动后默认监听 `http://0.0.0.0:8787`。数据库文件默认保存在 `backend/data/music.db`，初次启动将自动建表并完成初始化。

### 4. 默认内置管理员

系统首次初始化时会自动创建超级管理员账号供 `music-admin` 登录：
- **用户名**：`admin`
- **初始密码**：`12346`（可在 `.env` 中通过 `ADMIN_INITIAL_PASSWORD` 自定义）
- **角色**：`admin`

---

## 五、环境变量全景

在 `.env` 文件中配置以下参数（全部可选，均有安全默认值）：

| 变量名 | 默认值 | 类型 | 说明 |
|---|---|---|---|
| `PORT` | `8787` | 整数 | 服务监听端口 |
| `HOST` | `0.0.0.0` | 字符串 | 监听网卡地址（仅本机访问可设为 `127.0.0.1`） |
| `DB_URL` | `file:data/music.db` | 字符串 | libSQL 数据库连接串（支持 `file:`、`http://`、`libsql://`） |
| `DB_AUTH_TOKEN` | 空 | 字符串 | 远程数据库连接密钥（Turso Cloud / sqld 开启认证时必填） |
| `SESSION_TTL_DAYS` | `90` | 整数 | 登录 Token 有效期（天），登出立即销毁 |
| `MAX_SESSIONS_PER_USER` | `10` | 整数 | 单用户最大允许并发会话数，超出淘汰最久未活动设备 |
| `ADMIN_INITIAL_PASSWORD` | `12346` | 字符串 | 首次建库时内置 `admin` 账号的初始密码 |
| `TRUST_PROXY` | 关（`0`） | 标识 | 处于反向代理（Nginx/Vercel）后设为 `1`，正确识别客户端 IP |
| `RATE_LIMIT_DISABLED` | 关（`0`） | 标识 | 设为 `1` 时完全停用接口限流（压测/本地调试时使用） |

---

## 六、核心架构与设计原理

### 1. 认证与会话体系

```
[客户端] --- Bearer Token ---> [Fastify preHandler]
                                     │
                             SHA-256(token)
                                     ▼
                        [sessions 表匹配 token_hash]
                                     │
                 ┌───────────────────┴───────────────────┐
                 ▼                                       ▼
            有效且未过期                             过期/已注销
                 │                                       │
     注入 req.user / req.session                   返回 401 拦截
```

- **不透明随机 Token**：登录/注册签发 32 字节高熵随机字符串（Base64URL），服务端仅存储其 SHA-256 哈希值，杜绝数据库泄露后 Token 直接可用的风险。
- **登出即物理销毁**：登出操作直接从 `sessions` 表中删除行，Token 立即在全网失效，从根源解决无状态 JWT 签发后无法撤销的问题。
- **多设备独立管理**：每台设备拥有独立的 `sessionId`，允许用户查看当前所有在线设备并单点踢出。
- **防爆破机制**：登录失败按 `用户名 + 客户端 IP` 独立统计，5 次失败锁定 15 分钟，内存自动清理过期 Key，防止撞库攻击与跨 IP 恶意锁定（DoS）。
- **邮箱绑定（仅登记）**：`users.email TEXT NOT NULL DEFAULT ''`，空串 = 未绑定。唯一性由**部分唯一索引** `idx_users_email ON users(email) WHERE email != ''` 保证（多个未绑定用户的空串互不冲突）；建表语句与新库直接生效，**老库**在启动迁移里用「`ALTER TABLE ... ADD COLUMN`（try/catch 容忍列已存在）+ `CREATE UNIQUE INDEX IF NOT EXISTS`」平滑升级，存量行由 DEFAULT 自动回填为 `''`。当前 libsql（`@libsql/client` 0.17.x）实测支持部分索引；万一更旧的引擎不支持，索引创建失败会被忽略，唯一性退化为主路径 `findUserByEmail` 应用层查重 + 并发竞态时数据库报错兜底转 `409`（极端情况下会少一层兜底，但不会静默双绑）。

### 4. 用户资料字段

- `username` 是登录名，带表级 `UNIQUE` 约束，**注册后不提供修改入口**（`PUT /api/me` 无此字段），改名诉求走 `nickname`。
- `createdAt`（毫秒时间戳，即注册日期）随 `GET /api/me` 返回；同时保留 `created_at` 同值别名，`music-admin` 前端按 snake_case 读取，Android 客户端按 camelCase 读取，互不破坏。
- `email` 语义详见[七、API 接口全览](#七api-接口全览)中「更新资料 PUT /api/me」一节；未做邮件验证，勿当作“已验证联系方式”使用。

### 2. 多端同步引擎（LWW + 墓碑）

多端同步采用业界成熟的**按记录 Last-Write-Wins（后写者胜）+ 墓碑标记**机制：

```
设备 A (手机) ──push 歌单 pl_1──┐
                               ├──> 服务端原子分配 version (sync_seq 自增) ──> 落库更新 update_seq
设备 B (平板) ──push 删除 pl_1──┘   (谁后到达谁最新，删除保留 deleted=1 墓碑)
```

1. **事务内版本递增**：每次有效的 `push` 变动都在单个数据库事务内完成，原子递增用户的 `sync_seq`（即 `version`），保证各实体所附带的 `update_seq` 单调严格自增。
2. **记录级覆盖**：收藏（`favorites`）、历史（`history`）、歌单（`playlists`）和歌单歌曲（`playlistSongs`）均以联合主键进行 upsert，后到的数据直接覆盖旧数据。
3. **墓碑化删除（Tombstone）**：删除操作不直接从数据库物理抹除，而是标记 `deleted = 1` 并更新序列号。当设备 A 删除了某首歌，稍后推送旧快照的设备 B 不会使已删项意外“复活”。
4. **增量拉取（Delta Pull）**：
   - 客户端携带上一次保存的 `since`（版本号）请求 `/api/sync/pull?since=xxx`；
   - 仅返回 `update_seq > since` 的新增、修改与删除墓碑；
   - **首次全量拉取优化**：当 `since=0` 时，服务端自动剔除已被标记删除的墓碑记录，新设备无需下载死数据。
5. **听歌统计幂等事件（Play Events）**：听歌记录为 Append-Only 模式，客户端生成 `eventId`，服务端通过唯一索引实现幂等去重；拉取时通过自增数字 ID（`nextEventId`）游标分页。

### 3. 防爆破与请求限流

- **独立命名限流桶**：
  - `auth` 桶：针对 `/api/auth/login` 与 `/api/auth/register`，限制 **10 次/分钟**；
  - `api` 桶：针对其他普通 `/api/*` 请求，限制 **120 次/分钟**；
  - 两者 Key 严格隔离（`${bucket}:${ip}:${slot}`），避免频繁的数据同步流量导致认证接口被误封。

---

## 七、API 接口全览

### 通用约定

- **基础前缀**：所有业务接口以 `/api` 开头。
- **鉴权方式**：需要鉴权的接口在请求头中携带：`Authorization: Bearer <token>`。
- **统一响应格式**：
  ```json
  {
    "code": 200,          // 业务状态码，成功为 200
    "msg": "提示文本",     // 失败或提示信息
    "data": { ... }       // 返回业务数据体
  }
  ```
- **常见状态码**：`200` 成功、`400` 参数错误、`401` 未登录/Token 过期、`403` 无权限（非管理员）、`409` 资源冲突（用户名已存在 / 邮箱已被其它账号绑定）、`429` 请求过频/账号锁定、`500` 内部错误。

---

### 1. 服务健康与探测

#### `GET /`
- **鉴权**：否
- **说明**：服务基础连通性检查与接口元数据发现。
- **响应示例**：
  ```json
  {
    "name": "melos-backend",
    "version": "2.0.0",
    "endpoints": [ "/api/auth/login", "/api/sync/pull", ... ]
  }
  ```

---

### 2. 用户认证与个人中心（Auth / Me）

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| `POST` | `/api/auth/register` | 否 | 注册新用户并自动登录（可选绑定邮箱） |
| `POST` | `/api/auth/login` | 否 | 用户登录，生成新设备会话 |
| `POST` | `/api/auth/logout` | 是 | 登出当前设备会话（立即销毁 Token） |
| `POST` | `/api/auth/logout-all` | 是 | 登出该用户的所有设备会话 |
| `GET` | `/api/me` | 是 | 获取当前登录用户个人资料（含 email / username / createdAt） |
| `PUT` | `/api/me` | 是 | 更新当前用户昵称、头像或邮箱绑定 |
| `PUT` | `/api/me/password` | 是 | 修改密码（需校验旧密码，修改后注销其余设备） |
| `GET` | `/api/me/sessions` | 是 | 获取当前用户所有活跃会话列表 |
| `DELETE` | `/api/me/sessions/:id` | 是 | 强制移除指定设备会话 |

#### 请求与响应详情

##### 注册 `POST /api/auth/register`
- **请求体**：
  ```json
  {
    "username": "user123",      // 必填，2-20 位（字母/数字/下划线/中文）
    "password": "mypassword",    // 必填，5-64 位
    "nickname": "听歌小王子",     // 可选，最长 24 字
    "deviceName": "Pixel 8",     // 可选，设备名称
    "email": "user@example.com"  // 可选，绑定邮箱；不传/传空 = 不绑定（行为与旧版一致）
  }
  ```
  邮箱格式非法返回 `400`；已被其它账号占用返回 `409`（此时不会创建用户）。
- **响应体**：
  ```json
  {
    "code": 200,
    "data": {
      "token": "dGhpcy1pcy1hLXRva2Vu...",
      "expiresAt": 1789000000000,
      "user": { "id": 1, "username": "user123", "nickname": "听歌小王子", "avatar": "", "role": "user", "email": "user@example.com", "created_at": 1789000000000, "createdAt": 1789000000000 }
    }
  }
  ```

##### 当前用户资料 `GET /api/me`
- **响应 `data` 字段**：

  | 字段 | 类型 | 说明 |
  |---|---|---|
  | `id` | 整数 | 用户 ID |
  | `username` | 字符串 | **登录用户名，只读**：注册后不可修改（`PUT /api/me` 不接受该字段） |
  | `nickname` | 字符串 | 昵称（展示名） |
  | `avatar` | 字符串 | 头像 URL |
  | `role` | 字符串 | `admin` / `user` |
  | `email` | 字符串 | 绑定邮箱，未绑定为空串 `""` |
  | `createdAt` | 整数 | **注册日期**（毫秒时间戳） |
  | `created_at` | 整数 | 同 `createdAt`，snake_case 兼容别名（music-admin 在用，两键永远同值） |

##### 更新资料 `PUT /api/me`
- **请求体字段（均可选，缺哪个不动哪个）**：

  | 字段 | 规则 | 说明 |
  |---|---|---|
  | `nickname` | ≤ 24 字 | 昵称 |
  | `avatar` | 字符串 | 头像 URL |
  | `email` | ≤ 120 字符，形如 `x@y.zz` | **邮箱绑定**：入库前统一 `trim + 转小写`；传**空串**表示解绑；字段缺省（`undefined`）表示不改动 |

- **邮箱错误语义**：
  - 格式非法（或超长）→ `400 邮箱格式不正确（示例：name@example.com）`；
  - 已被其它账号绑定 → `409 该邮箱已被其它账号绑定`；
  - 自己重复提交已绑定的邮箱 → 幂等成功，不算冲突。
- **⚠️ 限制（如实声明）**：当前邮箱**仅登记/绑定，未做任何邮件验证**——本后端没有 SMTP 配置，不存在也不假装存在验证码流程。响应 `msg` 中会带回这一提示。后续如需「已验证」状态，必须先配置 SMTP（或第三方邮件 API）再引入验证码，届时 `email` 的唯一性约束可继续沿用。

##### 修改密码 `PUT /api/me/password`
- **请求体**：
  ```json
  {
    "oldPassword": "mypassword",
    "newPassword": "newpassword123"
  }
  ```
- **响应体**：`{ "code": 200, "msg": "密码修改成功" }`

---

### 3. 多端数据同步（Sync）

标准歌曲对象定义：
```json
{
  "id": "186016",
  "platform": "netease",
  "title": "晴天",
  "artist": "周杰伦",
  "album": "叶惠美",
  "artwork": "https://..."
}
```

#### `GET /api/sync/pull`
- **鉴权**：是
- **参数**：
  - `since`：上一次同步返回的 `version`（默认 `0` 为全量同步）。
  - `eventSince`：上一次同步返回的 `nextEventId`（默认 `0`）。
- **响应示例**：
  ```json
  {
    "code": 200,
    "data": {
      "version": 42,
      "nextEventId": 108,
      "serverTime": 1789100000000,
      "favorites": [
        { "platform": "netease", "id": "186016", "song": { /* 歌曲对象 */ }, "updatedAtMs": 1789100000000, "deleted": false }
      ],
      "history": [
        { "platform": "netease", "id": "186016", "song": { /* ... */ }, "playedAtMs": 1789100000000, "updatedAtMs": 1789100000000, "deleted": false }
      ],
      "playlists": [
        { "id": "pl_1", "name": "我的最爱", "coverUrl": "", "updatedAtMs": 1789100000000, "deleted": false }
      ],
      "playlistSongs": [
        { "playlistId": "pl_1", "position": 0, "platform": "netease", "songId": "186016", "song": { /* ... */ }, "updatedAtMs": 1789100000000, "deleted": false }
      ],
      "playEvents": [
        { "eventId": "evt-uuid", "platform": "netease", "songId": "186016", "title": "晴天", "artist": "周杰伦", "listenedMs": 120000, "durationMs": 269000, "listenedAtMs": 1789100000000 }
      ]
    }
  }
  ```

#### `POST /api/sync/push`
- **鉴权**：是
- **说明**：提交客户端增量变动（按需传递各字段）：
- **请求体**：
  ```json
  {
    "favorites": {
      "upsert": [ { /* 完整歌曲对象 */ } ],
      "delete": [ { "platform": "netease", "id": "186016" } ]
    },
    "history": {
      "upsert": [ { /* 完整歌曲对象 + playedAtMs */ } ],
      "delete": [ { "platform": "netease", "id": "186016" } ]
    },
    "playlists": {
      "upsert": [ { "id": "pl_1", "name": "歌单名", "coverUrl": "" } ],
      "delete": [ { "id": "pl_1" } ]
    },
    "playlistSongs": {
      "upsert": [ { "playlistId": "pl_1", "position": 0, "song": { /* 歌曲对象 */ } } ],
      "delete": [ { "playlistId": "pl_1", "platform": "netease", "songId": "186016" } ]
    },
    "playEvents": [
      {
        "eventId": "客户端生成的UUID",
        "platform": "netease",
        "id": "186016",
        "title": "晴天",
        "artist": "周杰伦",
        "listenedMs": 120000,
        "durationMs": 269000,
        "listenedAtMs": 1789100000000
      }
    ]
  }
  ```
- **响应体**：
  ```json
  {
    "code": 200,
    "data": {
      "version": 43,
      "acceptedEvents": 1,
      "serverTime": 1789100005000
    }
  }
  ```

---

### 4. 管理员控制台（Admin）

> **权限要求**：请求头携带有效 Token，且该用户在数据库中的 `role` 字段必须为 `'admin'`。

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| `GET` | `/api/admin/users` | 管理员 | 分页查询用户列表（带 `x-total-count` 响应头） |
| `GET` | `/api/admin/users/:id` | 管理员 | 获取指定用户详情 |
| `POST` | `/api/admin/users` | 管理员 | 创建新用户（可指定角色） |
| `PUT` | `/api/admin/users/:id` | 管理员 | 编辑用户信息（昵称/头像/角色/重置密码） |
| `DELETE` | `/api/admin/users/:id` | 管理员 | 删除用户（级联清理其所有业务数据与会话） |

#### 查询参数说明（`GET /api/admin/users`）
- 支持常规分页参数：`page`（从 1 开始）、`limit`（每页条数，1-100）。
- 支持 Refine / simple-rest 参数：`_start`、`_end`。
- 检索与过滤：`search` 或 `q`（支持用户名、昵称与**邮箱**模糊匹配）、`role`（`admin` 或 `user`）。
- 响应头自动附加 `x-total-count: <总数>`。
- 列表与详情响应的每个用户对象均包含 `email` 字段（管理员可见；未绑定为空串），邮箱暂仅供查看，管理端不提供改绑入口。

---

## 八、多端对接指南

### 1. Android 客户端对接

1. 在 Android 项目中的配置类（如 `ApiClient.kt`）设置 Base URL：
   - 模拟器访问本机：`http://10.0.2.2:8787/api`
   - 真机在局域网内：`http://<电脑局域网IP>:8787/api`
2. **客户端同步节奏**：
   - **应用初次启动 / 登录后**：调用 `/api/sync/pull?since=0&eventSince=0` 全量写入本地 Room 数据库。
   - **用户收藏 / 播放 / 新建歌单时**：先更新本地 Room，再异步调用 `/api/sync/push` 将变动推至服务端。
   - **后台唤醒或下拉刷新**：调用 `/api/sync/pull?since=<本地最大version>&eventSince=<本地最大eventId>` 拉取增量更新并合入本地。

### 2. Web 管理后台（music-admin）对接

1. 打开根目录下的 `music-admin/` 项目。
2. 在 `music-admin/.env` 中配置后端地址：
   ```env
   VITE_API_URL=http://localhost:8787/api
   ```
3. 启动后台：
   ```bash
   pnpm install
   pnpm dev
   ```
4. 在登录页使用 `admin` / `12346` 登录，即可直接进行用户查询、账号禁用、密码重置及全局多端同步状态监控。

---

## 九、生产部署指南

### 1. PM2 / 自托管服务器部署

适合轻量云服务器（Ubuntu / Debian / CentOS）：

```bash
# 1. 克隆代码并进入目录
git clone <repo-url>
cd Melos/backend

# 2. 安装依赖
npm ci --production

# 3. 配置生产环境变量
cp .env.example .env
nano .env  # 修改 PORT、HOST=0.0.0.0、ADMIN_INITIAL_PASSWORD 等

# 4. 使用 PM2 启动保活
npm install -g pm2
pm2 start src/start.js --name melos-backend
pm2 save
pm2 startup
```

配合 Nginx 示例配置：
```nginx
server {
    listen 80;
    server_name api.music.yourdomain.com;

    location / {
        proxy_pass http://127.0.0.1:8787;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```
*注意：在 Nginx 反代后，请在 `.env` 中设置 `TRUST_PROXY=1`，确保 Fastify 能够正确识别客户端 IP 进行限流。*

---

### 2. Docker 容器化部署

在 `backend/` 目录下可使用轻量 Dockerfile：

```dockerfile
FROM node:20-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci --production
COPY . .
EXPOSE 8787
VOLUME ["/app/data"]
CMD ["node", "src/start.js"]
```

运行命令：
```bash
docker build -t melos-backend .
docker run -d \
  -p 8787:8787 \
  -v /var/data/melos:/app/data \
  --name melos-backend \
  --restart unless-stopped \
  melos-backend
```

---

### 3. Vercel Serverless + Turso 部署

1. **注册 Turso 并创建数据库**：
   ```bash
   turso db create melos-db
   turso db show melos-db --url
   turso db tokens create melos-db
   ```
2. **在 Vercel 中导入仓库**：
   - 根目录设置（Root Directory）选择：`backend`。
   - Framework Preset 选择：`Other`。
3. **设置 Vercel 环境变量**：
   - `DB_URL`：填写 Turso 返回的 URL（例如 `libsql://melos-db-username.turso.io`）。
   - `DB_AUTH_TOKEN`：填写生成的 Turso Token。
   - `TRUST_PROXY`：设置为 `1`。
   - `ADMIN_INITIAL_PASSWORD`：设置初始管理员密码。
4. **点击 Deploy**：
   - 部署成功后，Vercel 将通过 `api/[...].js` 接管所有 HTTP 请求，冷启动时自动在 Turso 远端库完成建表与管理员初始化。

---

## 十、自动化测试

项目内置完整的全链路端到端冒烟测试套件（基于 Fastify `app.inject()`，不占用系统端口，自动清理临时数据库）：

```bash
npm run smoke
```

### 测试范围覆盖（49 项断言）
- 服务元数据发现（`GET /`）
- 用户注册、重复注册 409 拦截、密码错误 401 拦截
- Token 鉴权、个人资料查询（`GET /api/me`，校验 email/username/createdAt 字段）
- 邮箱绑定全链路：成功绑定与大小写/空格归一化、非法格式 400、超长 400、未传字段不受影响、
  他人占用 409（注册与改资料两条路径）、空串解绑、解绑后他人可再绑定、自己重复提交幂等
- 收藏/历史/歌单数据 `push` 与版本号单调自增
- 全量拉取（`since=0`）与增量拉取（`since>0`）
- 删除墓碑标记传播与新设备首次拉取自动过滤废弃墓碑
- 听歌事件 `eventId` 幂等去重验证
- 多端 LWW 冲突仲裁（后到写入者获胜）
- 歌单删除级联墓碑化其下歌曲
- 会话注销、Token 即时失效与设备列表查询
- 内置超级管理员登录、普通用户访问管理员接口 403 拦截
- 管理员分页查询（含 email 字段可见性）、新建用户、更新用户角色与删除用户
- 修改密码验证旧密码、更新哈希与旧密码失效验证