# melos-backend (Go / Gin)

Melos 音乐平台后端的 **Go 重写版**，与 `../backend`（Node + Fastify）**接口与行为完全一致**，
Android 客户端与 `music-admin` 无需改动即可对接。存储使用本地 SQLite。

- Web 框架：**Gin**
- 存储：**SQLite**（`modernc.org/sqlite`，纯 Go，`CGO_ENABLED=0` 可直接交叉编译）
- 密码哈希：**scrypt**（N=16384, r=8, p=1, keyLen=64），参数与 Node `crypto.scrypt` 默认值一致
  ⇒ 现有 `backend/data/music.db` 可直接复用，老账号密码照样能登录
- 登录态：不透明随机 token（32 字节 base64url），服务端只存 `sha256(token)`，登出即物理删除

---

## 快速开始

```bash
cd backend-go
cp .env.example .env        # 全部可选，默认即可跑
go run .                    # 默认 http://0.0.0.0:8787
```

首次启动会自动建表、创建内置管理员 `admin / 12346`（可用 `ADMIN_INITIAL_PASSWORD` 覆盖）、
并清理过期会话与 180 天前的听歌明细。

构建与测试：

```bash
go build ./...
go vet ./...
go test ./...               # 已移植 smoke.mjs + client-contract.mjs 的全部断言
```

Docker：

```bash
docker build -t melos-backend .
docker run -d -p 8787:8787 -v /var/data/melos:/app/data --name melos-backend melos-backend
```

---

## 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `PORT` | `8787` | 监听端口 |
| `HOST` | `0.0.0.0` | 监听地址 |
| `DB_URL` | `file:data/music.db` | 本地 SQLite 路径（仅支持本地文件） |
| `SESSION_TTL_DAYS` | `90` | 登录 token 有效期（天） |
| `MAX_SESSIONS_PER_USER` | `10` | 单用户最大并发会话数，超出淘汰最久未活动 |
| `ADMIN_INITIAL_PASSWORD` | `12346` | 内置 admin 初始密码 |
| `TRUST_PROXY` | `0` | 反向代理后设为 `1`，正确识别客户端 IP |
| `RATE_LIMIT_DISABLED` | `0` | 设为 `1` 关闭全局限速 |
| `MAIL_SERVICE_URL` | `https://mail.dovislab.com` | 自建邮件服务地址 |
| `MAIL_ADMIN_AUTH` | `REDACTED` | 邮件服务管理密钥（`x-admin-auth`） |
| `MAIL_FROM_ADDRESS` | `melos@dovislab.com` | 发件邮箱 |
| `MAIL_FROM_NAME` | `Melos 音乐` | 发件人名称 |
| `APK_DIR` | `data/apk` | APK 文件存放目录 |
| `APK_MAX_MB` | `200` | 单个 APK 上传上限（MB） |

---

## API 一览

通用约定：统一信封 `{code, msg, data}`，**HTTP 状态码 = code**；需要鉴权的接口使用
`Authorization: Bearer <token>`。响应体使用 `PureJSON`（不做 HTML 转义，与 Node 一致）。

### 健康与元数据（免鉴权）

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/` | 服务元数据（name/version/endpoints） |
| `GET` | `/api/health` | 探活，返回 `{code,msg,data:{ok,time,version}}` |

### 认证与个人中心

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| `POST` | `/api/auth/send-code` | 否 | 发送 6 位邮箱验证码（同邮箱+场景 60s 冷却，5 分钟有效） |
| `POST` | `/api/auth/email-login` | 否 | 验证码登录；邮箱不存在则自动开户 |
| `POST` | `/api/auth/register` | 否 | 注册（**需 email + 验证码**），成功即登录 |
| `POST` | `/api/auth/login` | 否 | 用户名**或邮箱**登录 |
| `POST` | `/api/auth/logout` | 是 | 登出当前设备（token 立即失效） |
| `POST` | `/api/auth/logout-all` | 是 | 登出全部设备 |
| `GET` | `/api/me` | 是 | 当前用户资料 |
| `PUT` | `/api/me` | 是 | 改昵称/头像/背景/邮箱（邮箱：缺省=不改，`""`=解绑） |
| `PUT` | `/api/me/password` | 是 | 改密码（校验旧密码，踢出其它设备） |
| `POST` | `/api/me/email/bind` | 是 | 校验验证码后绑定邮箱 |
| `POST` | `/api/me/email/unbind` | 是 | 校验验证码后解绑邮箱 |
| `GET` | `/api/me/sessions` | 是 | 活跃设备列表（含 `current`） |
| `DELETE` | `/api/me/sessions/:id` | 是 | 移除指定设备会话 |

### 多端同步（LWW + 墓碑）

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/sync/pull?since=&eventSince=` | 增量拉取（`since=0` 为全量，自动过滤墓碑） |
| `POST` | `/api/sync/push` | 上传增量（集合：favorites / history / playlists / playlistSongs / scripts / downloads / playEvents） |

语义与 Node 版逐条对齐：单事务内递增 `sync_seq` 作为 `version`；按记录 LWW；
删除落 `deleted=1` 墓碑；歌单删除级联墓碑化其下歌曲；`kind` 白名单归一化（非 `fav` 一律 `mine`）；
downloads 以 `(platform, song_id, device_id)` 为主键（各设备互不覆盖）；
脚本 `code` 单个超 1MB 整批 `413` 并点名；缺必填键的条目整条跳过；数值/布尔做归一化。

### 管理端（需 `role=admin`）

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/admin/users` | 分页/搜索/角色筛选，响应头带 `x-total-count` |
| `GET` | `/api/admin/users/:id` | 用户详情 |
| `POST` | `/api/admin/users` | 创建用户（可指定角色） |
| `PUT` | `/api/admin/users/:id` | 改昵称/头像/角色、重置密码（踢出该用户会话） |
| `DELETE` | `/api/admin/users/:id` | 删除用户（级联清理全部业务数据与会话） |

### 版本分发（本次新增）

数据表 `app_versions`（`version_code` 唯一）。

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| `GET` | `/api/admin/versions` | 管理员 | 版本列表，`version_code DESC`，支持 `page`/`limit`（`pageSize`/`current`/`_start`/`_end` 兼容），响应头 `x-total-count` |
| `POST` | `/api/admin/versions` | 管理员 | **multipart/form-data** 上传 APK 并发布 |
| `PATCH` | `/api/admin/versions/:id` | 管理员 | 修改 `enabled` / `changelog` / `forceUpdate` |
| `DELETE` | `/api/admin/versions/:id` | 管理员 | 删除记录**及其本地 APK 文件** |
| `GET` | `/api/app/version/latest` | 免鉴权 | 返回 `enabled=1` 中最大的 `version_code`；无可用版本时 `data: null` |
| `GET` | `/api/app/download/:file` | 免鉴权 | 下载 APK（`application/vnd.android.package-archive`） |

`POST /api/admin/versions` 表单字段（同时接受 snake_case 与 camelCase）：

| 字段 | 必填 | 说明 |
|---|---|---|
| `file` | 是 | APK 文件本体 |
| `versionCode` / `version_code` | 是 | 正整数，重复返回 `409` |
| `versionName` / `version_name` | 是 | 版本名（如 `1.2.0`） |
| `changelog` | 否 | 更新日志 |
| `forceUpdate` / `force_update` | 否 | 是否强制更新（默认否） |
| `enabled` | 否 | 是否启用（默认启用） |
| `hashSha256` / `hash_sha256` | 否 | 不传则由服务端计算 |

示例：

```bash
curl -X POST http://localhost:8787/api/admin/versions \
  -H "Authorization: Bearer <admin-token>" \
  -F "file=@app-release.apk" \
  -F "versionCode=101" \
  -F "versionName=1.0.1" \
  -F "changelog=修复若干问题" \
  -F "forceUpdate=false"
```

响应中的 `apk_url` 为**相对路径**（`/api/app/download/<file>`），客户端按自己的服务端基址解析即可。
版本对象同时返回 snake_case 与 camelCase 两套键（`version_code`/`versionCode` 等），
`enabled`/`force_update` 统一为布尔值。

---

## 与 Node 版的差异（有意为之）

1. **不支持远端 libsql / Turso**：`DB_URL` 只接受本地 SQLite 文件；填 `libsql://`、`http(s)://`
   会在启动时直接报错。
2. **未提供 Vercel Serverless 入口**：本版是标准长驻服务（二进制 / Docker）。
3. **注册必须带 email + 验证码**：这是**当前 Node 代码的实际行为**（`src/routes/auth.js`），
   `backend/scripts/smoke.mjs` 与 `client-contract.mjs` 里的注册调用仍是旧写法（不带邮箱），
   因此在本仓库中它们是**过时**的；Go 版的测试已改为走验证码流程，覆盖范围不变。
4. 内存态限速 / 登录失败锁定与 Node 版一致，均为**单实例内存实现**，多实例部署需在网关层再加一层。

---

## 目录结构

```
backend-go/
├── main.go                       # 入口：配置 → 建库/迁移 → 内置管理员 → 启动清理 → 监听
├── Dockerfile
├── .env.example
└── internal/
    ├── config/                   # 环境变量读取（含 .env 解析）
    ├── security/                 # scrypt、token、登录失败锁定
    ├── middleware/               # 固定窗口 IP 限流
    ├── mail/                     # 自建邮件服务客户端
    ├── store/                    # SQLite：schema/迁移、用户会话、邮箱验证码、LWW 同步、版本管理
    └── httpapi/                  # Gin 路由：信封、CORS、鉴权、auth/me/sync/admin/versions
        ├── smoke_test.go         # 移植自 scripts/smoke.mjs
        ├── contract_test.go      # 移植自 scripts/client-contract.mjs
        └── versions_test.go      # 版本管理接口测试
```
