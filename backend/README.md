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
2. **多端数据同步**：基于单调递增版本号与 LWW（Last-Write-Wins）冲突仲裁机制，负责收藏歌曲、历史记录、自建歌单、收藏的网络歌单、听歌事件、用户上传的音源脚本与分设备下载记录的多端实时同步。
3. **后台运维支撑**：提供 RESTful 接口供 `music-admin` 运营端进行用户检索、状态变更、角色分配与密码重置。

> **说明**：音源聚合搜索、歌词拉取、音频流播放等逻辑均在客户端本地（Android 端）直接处理，本后端不代理音源流量或歌曲文件，极度轻量且易于维护。
> 用户自行上传的**音源脚本源码**属于「用户数据」：后端只按 LWW + 墓碑保存与同步这段文本，不解析、不执行、不做语义校验（执行与安全边界都在客户端）。

### 服务端到底保存 / 同步哪些数据

服务端只做两件事：**保存**用户数据、**同步**用户数据（不做任何业务计算）。

| 数据集 | 存什么 | 主键（服务端） | 同步方向 |
|---|---|---|---|
| 账号 | 用户名、密码哈希 + salt、昵称、头像、邮箱、角色、创建/更新时间 | `users.id` | 服务端权威，客户端只读展示 |
| 设备会话 | token 哈希、设备名、过期时间、最后使用时间 | `sessions.token_hash` | 服务端权威（客户端只保存 token 明文） |
| 收藏歌曲 `favorites` | 完整歌曲对象（JSON）+ 更新时间 + 墓碑 | `user_id + platform + song_id` | 双向 |
| 最近播放 `history` | 完整歌曲对象 + `playedAtMs` + 墓碑 | `user_id + platform + song_id` | 双向（客户端本地上限 100 条，淘汰**不产生**删除） |
| 歌单 `playlists` | `kind`(mine/fav) + 名称 + 封面 URL + 墓碑 | `user_id + playlist_id` | 双向 |
| 歌单曲目 `playlistSongs` | `position` + 完整歌曲对象 + 墓碑 | `user_id + playlist_id + platform + song_id` | 双向 |
| 音源脚本 `scripts` | 名称、描述、**源码原文**、是否启用 + 墓碑 | `user_id + script_id` | 双向 |
| 下载记录 `downloads` | 设备 id/名称 + 歌曲 id + 标题/歌手 + 体积 + 下载时间 + 墓碑 | `user_id + platform + song_id + device_id` | 双向（**只**同步"哪台设备下过哪首歌"） |
| 听歌事件 `play_events` | `eventId` + 歌曲 + 时长 + 时间 | `user_id + event_id`（唯一） | **只上行**（Append-Only；客户端不回读统计） |
| 歌词/搜索缓存 | — | — | **不保存**：本地缓存，无同步价值 |
| 音频文件、播放直链、本机路径 | — | — | **不保存**：体积大 / 会过期 / 换设备无意义 |

> 删除语义：任何用户可见的删除都走墓碑（`deleted=1`）而非物理删除，其余设备据此删除本地副本；
> **客户端本地容量淘汰（历史超 100 条、收藏超 1000 条等）不算用户删除**，不会写墓碑。

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
│   └── smoke.mjs         # 端到端自动化冒烟测试（90 项核心链路断言）
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
2. **记录级覆盖**：收藏（`favorites`）、历史（`history`）、歌单（`playlists`）、歌单歌曲（`playlistSongs`）、音源脚本（`scripts`）与下载记录（`downloads`）均以联合主键进行 upsert，后到的数据直接覆盖旧数据。

   > **歌单的 `kind`**：自建歌单（`mine`）与收藏的网络歌单（`fav`）共用 `playlists` 一张表，用 `kind` 区分。
   > 客户端本地用「`kind` + `id`」组成记录键，因此服务端**原样存回** `kind`，删除墓碑同样带 `kind`
   > （否则收藏歌单的删除会落到自建歌单上，表现为"删了一个，另一个不见了"）。
   > 服务端只接受 `mine` / `fav` 两个值，其余（含缺省、拼错、未来版本的新值）一律归一化为 `mine`——
   > 若原样透传脏值，同一歌单在两台设备上会被算成两条记录。老库升级时 `kind` 默认 `mine`，与历史数据语义一致，无需回填。
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
    "endpoints": [ "/api/health", "/api/auth/login", "/api/sync/pull", ... ]
  }
  ```

#### `GET /api/health`
- **鉴权**：否
- **说明**：探活接口，供客户端「测试服务器连通性」按钮使用。
  它必须在**未登录**时也能回答"这个地址是不是一个活着的 Melos 后端"：
  若用 `/api/me` 或 `/api/sync/pull` 探测，未登录必然 401，客户端无法区分
  「地址填错了」「服务没起来」与「地址对但还没登录」——而这正是配置页面最需要说清的三件事。
- **响应示例**：
  ```json
  { "code": 200, "msg": "ok", "data": { "ok": true, "time": 1789100000000, "version": "2.0.0" } }
  ```
- **实现注记**：返回标准 `{code,msg,data}` 信封而非裸对象——客户端响应模型只认这个形状，
  裸对象会因缺少 `code` 被当作失败（`GET /` 是给人看/做元数据发现的，两者用途不同）。

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
        { "id": "pl_1", "kind": "mine", "name": "我的最爱", "coverUrl": "", "updatedAtMs": 1789100000000, "deleted": false }
      ],
      "playlistSongs": [
        { "playlistId": "pl_1", "position": 0, "platform": "netease", "songId": "186016", "song": { /* ... */ }, "updatedAtMs": 1789100000000, "deleted": false }
      ],
      "scripts": [
        { "id": "sc_1", "name": "我的音源", "description": "", "code": "…脚本源码原文…", "enabled": true, "updatedAtMs": 1789100000000, "deleted": false }
      ],
      "downloads": [
        { "platform": "netease", "id": "186016", "deviceId": "dev-uuid", "deviceName": "Pixel 8", "title": "晴天", "artist": "周杰伦", "album": "叶惠美", "artwork": "https://...", "quality": "320k", "sizeBytes": 8123456, "downloadedAtMs": 1789100000000, "updatedAtMs": 1789100000000, "deleted": false }
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
- **体积约束**：单次请求体上限 4MB（Fastify `bodyLimit`）；单个音源脚本 `code` 上限 **1MB**，
  超限直接 `413` 并在 `msg` 里点名是哪个脚本（整批不落库，避免"半批成功"）。
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
      "upsert": [ { "id": "pl_1", "kind": "mine", "name": "歌单名", "coverUrl": "" } ],
      "delete": [ { "id": "pl_1", "kind": "mine" } ]
    },
    "playlistSongs": {
      "upsert": [ { "playlistId": "pl_1", "position": 0, "song": { /* 歌曲对象 */ } } ],
      "delete": [ { "playlistId": "pl_1", "platform": "netease", "songId": "186016" } ]
    },
    "scripts": {
      "upsert": [ { "id": "sc_1", "name": "我的音源", "description": "", "code": "…脚本源码…", "enabled": true } ],
      "delete": [ { "id": "sc_1" } ]
    },
    "downloads": {
      "upsert": [ { "platform": "netease", "id": "186016", "deviceId": "dev-uuid", "deviceName": "Pixel 8", "title": "晴天", "artist": "周杰伦", "album": "叶惠美", "artwork": "https://...", "quality": "320k", "sizeBytes": 8123456, "downloadedAtMs": 1789100000000 } ],
      "delete": [ { "platform": "netease", "id": "186016", "deviceId": "dev-uuid" } ]
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
- **脏数据语义**：条目缺**必填键**时该条被**整条跳过**（不返回 400、不写半条记录，同批其余合法条目照常落库）：
  - `scripts`：必须有 `id`；
  - `downloads`：必须同时有 `platform` + `id` + `deviceId`（缺任一段都跳过，尤其缺 `deviceId` 的删除指令绝不能退化成「删掉这首歌在所有设备上的记录」）。

#### 音源脚本（scripts）

用户自己上传/编辑的播放脚本，服务端**只保存源码文本并同步**：不解析、不执行、不做语义校验（执行与安全都在客户端）。

- 主键 `(user_id, script_id)`；`script_id` 由客户端生成并保持稳定，重命名/改代码都走同一个 id → 原地覆盖，不新增行。
- `code` 可能几万字符，因此**不建任何索引、也不参与任何 WHERE**：按内容检索在客户端本地库做；服务端只按主键读写整条记录。
- `enabled` 落库存 0/1，出参统一为布尔（与 `deleted` 同风格）；字段缺省时默认「启用」（避免用户推上来的脚本被静默当成停用）。
- 删除落成墓碑；**墓碑不下发 `code`**（只留主键 + `updatedAtMs` + `deleted`），既省带宽也避免已删内容回流，但键仍齐全（空串 / 0 / false），客户端反序列化不会缺字段。

#### 下载记录（downloads）

一行 = 「**某台设备磁盘上的一个文件**」，所以 `device_id` 必须进主键：

- 主键 `(user_id, platform, song_id, device_id)`。
- 若只按 `(user_id, platform, song_id)`：手机删掉本地文件写一条墓碑，会把平板上其实还在的文件记录一起删掉（多端互相误删）。
- 放进主键后，各设备只管自己那一条；LWW 是「按记录」生效的，记录边界就是这把联合主键，所以同一首歌在不同设备上互不覆盖、删除互不影响。

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
   - 客户端运行时可改：设置 → 「账号与同步」填地址并点「测试连通性」（走 `GET /api/health`，不要求先登录）。
2. **客户端同步节奏**：
   - **首次为该账号同步**：`/api/sync/pull?since=0&eventSince=0` 全量拉取（响应含 `scripts` 与 `downloads` 数组），写回本地存储。
   - **冷启动 / 登录后**：用本地保存的 `since` / `eventSince` 做增量拉取并合入本地。
   - **用户收藏 / 播放 / 新建歌单 / 增删改音源脚本 / 完成或删除一次下载时**：先更新本地，再异步（防抖 1.2s）调用 `/api/sync/push` 推送变动。
     推送下载记录变动时**必须带上本机稳定的 `deviceId`**（同一首歌在不同设备各自一条记录，缺 `deviceId` 的条目会被服务端整条丢弃）；
     脚本的 `id` 同样要保持稳定——改名/改代码都推同一个 `id`，服务端才是原地覆盖而不是新增一行；
     歌单要带 `kind`（`mine` / `fav`），删除墓碑也要带——客户端用「`kind` + `id`」组本地记录键。
   - **增量里出现的 `deleted: true`**（脚本与下载记录同样有墓碑）在本地删掉对应那一条；`id` / `kind` / `deviceId` 等定位键一定完整下发。
   - **超大脚本不上传**：服务端对单脚本 `code` 上限 1MB（超限整批 413，错误信息点名脚本）；
     客户端另设更小的自我约束 512KB（`SyncMapper.MAX_SCRIPT_BYTES`），超限脚本只留在本机并给用户一句提示——
     一个异常巨大的脚本若直接推，会把**同一次同步里其它所有数据**一起顶爆，用户看到的是"同步彻底坏了"却不知原因。

3. **客户端侧必须自己拿主意的几件事**（服务端不做决策，只保存与同步）：

   | 议题 | 客户端的做法 | 为什么这样定 |
   |---|---|---|
   | 服务器地址 vs 登录态 | 地址是**设备级**配置（`SyncServerStore`），token + 用户信息是**账号级**（`UserStore`） | 换账号不该让用户重填地址；退出登录也不该抹掉地址 |
   | 增量游标 / 基线 | 全部按 **userId 分桶**存放（`SyncStateStore` 里键名带 `u<id>.`） | 同一台设备换账号后若共用游标，会把 A 的版本号当 B 的起点，导致 B 少拉一段数据 |
   | 判断"我要推哪几条" | 保存**每条记录的内容指纹（SHA-256）基线**，只推指纹变了的记录 + 待删墓碑 | 逐字段比对要遍历两份数据；指纹基线是一次哈希，代价与实现都更简单 |
   | 一次同步的顺序 | 拉取 → 应用到本地（跳过"本地刚删、云端还没删"的键）→ 用应用后的本地状态重建基线 → 只推变化 → 保存新游标 | 否则"刚拉的远端数据"会在同一次同步里被当成"本地变化"再推回去，制造无意义的写入与冲突 |
   | 本地容量上限（历史 100 / 收藏 1000） | 淘汰只动本地，**绝不写墓碑** | 淘汰不是用户删除；写墓碑会把"另一台设备上还留着的收藏"也一起删掉 |
   | 退出登录 | 撤销服务端当前会话 + 清本地 token/user，**保留本地业务数据与基线** | 再登录就是增量续传，不需要重新全量下载 |
   | 退出并清空本机数据 | 清收藏 / 历史 / 歌单 / 收藏歌单 + 清该用户基线，**保留音源脚本** | 脚本是用户自己写的资产，不属于"云端数据"；误删代价高，故单独保留 |
   | 收到 401 | 仅在**本次请求确实带了 token** 时才判定登录失效并跳登录 | 配置页的探活请求本来就没带 token，误判会把用户踢出登录态 |
   | 听歌统计事件 | 只上行（append-only），**不**把云端事件拉回本地统计 | 统计以本机为准；回灌会让"播放次数"在两台设备上互相叠加 |

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

### 测试范围覆盖（102 项断言）
- 服务元数据发现（`GET /`）与探活接口 `GET /api/health`（未登录可访问 + `{code,msg,data}` 信封）
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
- **歌单类型 `kind`**：`mine` / `fav` 原样回传、缺省按 `mine`（兼容老客户端）、
  非法值（如 `FAVOURITE`）归一化为 `mine`、删除墓碑保留 `kind`
- **音源脚本 scripts**：长 `code` 原文回传、`enabled` 布尔语义（true/false/缺省启用/字符串 '0'）、
  同 id 覆盖且 `version` 游标只回一次、删除墓碑增量可见 + 全量不可见 + 墓碑不带载荷、
  **单脚本 >1MB 整批 413 且错误信息点名脚本（恰好 1MB 仍可入库）**
- **下载记录 downloads**：同曲不同 `deviceId` 各存一条互不覆盖、设备内更新只回该设备、
  删除只墓碑化对应设备（其他设备记录仍存活）
- **字段归一化**：负数 / 非数字 / null 的 `sizeBytes`、`downloadedAtMs`、`quality` 不落库成 NaN 或负值
- **脏数据跳过**：缺 `id` 的脚本、缺 `deviceId` 或歌曲 id 的下载整条丢弃（返回 200 而非 500，同批合法条目照常写入，不产生孤儿墓碑）
- **跨用户隔离**：B 拉不到 A 的脚本/下载记录，且两用户可复用同一 `script_id` 互不覆盖
- 会话注销、Token 即时失效与设备列表查询
- 内置超级管理员登录、普通用户访问管理员接口 403 拦截
- 管理员分页查询（含 email 字段可见性）、新建用户、更新用户角色与删除用户
- 管理员删除用户后 `user_scripts` / `downloads` 直接查库确认无残留（含墓碑）
- 修改密码验证旧密码、更新哈希与旧密码失效验证

### 客户端字段级契约（`npm run contract`，38 项断言）

冒烟测试验证的是**后端自己的语义**（LWW、墓碑、隔离）；契约脚本验证的是**两端字段名是否真的对得上**：

```bash
npm run contract
```

为什么需要单独测这一层：Android 端用 Gson 反射反序列化 wire 模型，**字段名差一个字母不会报错**，
那个字段只会静默变成 null / 默认值。典型后果是客户端的收藏列表同步回来是空的（`song` 为 null 被跳过）、
歌单全部被当成自建歌单（`kind` 缺失）、下载记录在别的设备上永远少一条（`deviceId` 缺失 → 主键拼不出来）。
这些都是"不报错但数据不对"的问题，只能在字段名对齐这一层卡住。

脚本以客户端真实请求体形状推送，拉取后逐字段核对服务端返回的键是否覆盖客户端声明的每一个键，并顺带验证：
`/api/health` 信封、`kind` 的 `mine`/`fav` 往返、墓碑保留定位键（`kind` / `deviceId`）、
增量游标不重复下发、全量拉取不含墓碑、跨账号隔离与未登录 401。

### 老库平滑升级检查（`npm run migrate-check`，6 项检查）

```bash
npm run migrate-check            # 默认检查 data/music.db 的副本
npm run migrate-check -- <path>  # 检查指定的库文件（同样只动副本）
```

`initDb` 里的 `ALTER TABLE ... ADD COLUMN` 靠 `try/catch` 吞掉 "duplicate column" 实现幂等，
**这条路径在全新临时库上永远测不到**（新库建表时就带上了列）；而线上真正会发生的是"老库缺列"。
本脚本复制一份真实库文件后跑升级 + 探活 + 一次真实 `pull`，确认新增列/表都补齐、老数据仍可正常同步。