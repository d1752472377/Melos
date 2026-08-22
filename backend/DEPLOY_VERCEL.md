# Vercel 部署指南（music-backend）

> 目标：把 Fastify 后端以 Serverless 方式部署到 Vercel，函数跑在香港机房（hkg1），
> 数据库用 Turso Cloud（远端 libSQL）。音频直链纯透传，音频流不经过 Vercel。

## 架构要点

- **入口**：`api/[...].js` 把 Fastify 实例接到 Vercel Node handler（冷启动构建一次，之后复用）。
- **路由**：`vercel.json` 把 `/api/*` 和 `/admin/*` 全部 rewrite 到该函数。
- **数据库**：Vercel 文件系统只读，**必须用远端库**（Turso / 自建 sqld）。`src/app.js` 里已加保护：
  若在 Vercel 环境仍用 `file:` 会直接报错。
- **音频**：所有音源只返回直链 URL 字符串，客户端（Android ExoPlayer）直连源站播放，
  音频字节不经过 Vercel，只有几百字节 JSON 走函数。

## 二、Vercel 项目设置（关键）

1. **Import 仓库**：连接 GitHub 仓库 `d1752472377/Melos`。
2. **Root Directory 必须设为 `backend`**（`vercel.json`、`api/`、`package.json` 都在 backend 下）。
3. **Framework Preset**：选 `Other`（纯 Node，无框架）。
4. **Build Command**：留空（无构建步骤）。
5. **Output Directory**：留空。

## 三、环境变量（Project → Settings → Environment Variables）

| 变量 | 说明 | 示例 |
|---|---|---|
| `DB_URL` | Turso 连接串 | `libsql://music-<org>.turso.io` |
| `DB_AUTH_TOKEN` | Turso 数据库 token（必填） | `eyJ...` |
| `ENCRYPTION_KEY` | 32 字节 hex（音源密钥加密 + token 签名主密钥） | `64 个 hex 字符` |
| `ADMIN_USER` | 管理后台账号 | `admin` |
| `ADMIN_PASSWORD` | 管理后台密码 | 强密码 |
| `CHKSZ_API_KEY` | ChKSz 音源 key（可选，seed 用） | |
| `KUWO_API_KEY` / `KUWO_SECRET_KEY` | 酷我音源（可选） | |

> `ENCRYPTION_KEY` 必须与现有部署一致，否则已加密的音源配置和用户 token 全部失效。
> 生成：`node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"`

## 四、初始化数据库（首次）

Turso 建库后是空库，需要写入音源配置。两种方式：

1. **本地 seed**：本地 `.env` 配好 `DB_URL`/`DB_AUTH_TOKEN`/`CHKSZ_API_KEY` 后运行
   `npm run seed`，把音源写入远端库（幂等）。
2. **管理后台**：部署后访问 `https://<你的域名>/admin/`，登录后手动添加音源。

## 五、Region

`vercel.json` 已把函数 region 设为 `hkg1`（香港）。如需改，编辑 `vercel.json` 的 `regions` 字段。

## 六、验证

- `GET /` → 返回服务信息 JSON
- `GET /api/sources` → 返回音源列表（说明连上了 Turso）
- `GET /api/search?keyword=周杰伦` → 返回搜索结果
- `GET /admin/` → 管理后台页面

## 七、已知注意点

- **内存态按实例隔离**：音源健康统计、内容缓存（10 分钟 TTL）、kuwo 的 QPS 限速都是每实例一份，
  冷启动时重置。管理后台改音源后 `reload()` 只对当前实例生效，其他实例等冷启动后最终一致。
- **`@libsql/linux-x64-gnu`**：Turso 的 `libsql://` 走原生 binding，`vercel.json` 已 `includeFiles`
  确保打包。若改用 `https://` 连接串（纯 JS hrana 客户端）则无需此包。
- **收藏/历史同步**：`replaceFavorites`/`replaceHistory` 是逐条 INSERT，对远端库是 N 次网络往返，
  量大时偏慢（可后续改 `client.batch()`）。