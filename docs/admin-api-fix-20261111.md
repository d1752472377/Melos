# 管理端接口修复记录（2026-11-11）

## 现象

管理后台点「用户详情 → 用户脚本 → 新增脚本 → 保存」：

```
api-Da6p3AeE.js:1  POST http://39.96.39.134/api/admin/users/3/scripts 400 (Bad Request)
{"code":400,"msg":"脚本 id 不能为空"}
```

## 根因

管理端没有「客户端本地脚本 id」这种概念，新增时 `item` 为 `null`：

```tsx
const id = item?.scriptId;                                   // undefined
body: JSON.stringify({ id, name, code, ... })                // id 字段被 stringify 丢掉
```

后端 `handleAdminSaveScript` 只认路径参数和 body 里的 `id`，两者都没有就直接 400。
结果这条唯一的入口**完全不可用**；`import-url` 那条路径反倒会自己生成 id，
所以「从链接导入」能用、「手写/上传后保存」不能用，很容易被误判成权限问题。

## 已修复

### 后端 `backend-go`

| 位置 | 修复 |
| --- | --- |
| `internal/httpapi/admin_userwrite.go` | 创建脚本时服务端补 id：`admin_<毫秒>_<4位随机>`；支持 `id`/`scriptId`/`script_id` 别名；id 校验（长度、斜杠、控制字符）；空源码 400 |
| 同上 | 删除脚本/收藏/歌单/歌单歌曲/历史/下载：零命中返回 404（`store.ErrNoRows`），并回滚事务，避免空操作推进 `sync_seq` 让设备拉到空增量 |
| 同上 | 往不存在的歌单加歌回 404，不再写孤儿 `playlist_songs` 行 |
| `internal/store/admin_userwrite.go` | 删除类函数改为返回受影响行数；新增 `ErrNoRows` |
| `internal/store/admin_userdata.go` | 新增 `AdminPlaylistExists` |
| `internal/httpapi/admin_userdata.go` | 逐用户读接口统一走 `adminRequireUser`：用户不存在或 `:id` 非法回 404（之前返回空列表，管理端会误以为用户数据被清空） |
| `internal/httpapi/admin.go` | 管理端改用户支持 `email`（前端一直在提交，后端以前静默丢弃却回 200） |

### 顺带修掉的接口问题

| 问题 | 影响 | 修复 |
| --- | --- | --- |
| `FindSessionByTokenHash` 漏选 `u.last_active_at` | 每个鉴权请求都 `UPDATE users`（节流失效）；`/api/me` 恒返回 `lastActiveAt: 0` | 补列 + 补 Scan |
| `/api/me/sessions` 只给 `current` | Android 读 `isCurrent`，本机永不高亮，可误踢自己 | 两个字段都返回 |
| `DELETE /api/me/sessions/:id` 无脑 200 | 删不存在的设备也提示「已移除」 | 零命中 404 |
| `PATCH /api/admin/logs/:id` 非 bool 静默取反 | `{"resolved":"true"}` 反而标成未处理 | 非布尔回 400 |
| `sync/pull` 游标解析接受 `Inf` | `?since=1e400` → `int64(+Inf)`，游标语义失控、退回含墓碑的全量 | 拒绝 Inf/NaN，上限钳制 |
| `sync/push` 的 favorites/history 删除不校验主键 | `{"delete":[{}]}` 写入 `platform=""` 的垃圾墓碑并同步给所有设备 | 补主键校验 |

### 前端 `music-admin`

- `src/pages/user-detail/index.tsx`：新增脚本时生成 id 一并提交（后端仍可兜底）；
- 脚本表格新增「脚本 id」列，便于排查用户设备上的同步主键。

### 部署脚本

- `backend-go/.deploy/cleanup-deploy-admin.sh` 原来按 `index.html` 裁剪静态资源。
  Vite 的路由 chunk 是懒加载的、不出现在 `index.html` 里，这条规则会把它们全删掉
  （首页能开、每个子路由 404）。已改为「以本次构建产物为准，删掉构建里没有的文件」。

## 验证

- `go vet ./...`、`go test ./...` 全绿；新增 `internal/httpapi/admin_userwrite_test.go`
  覆盖：免 id 创建、id/源码校验、零命中 404（含 `sync_seq` 不变）、歌单加歌、
  读接口 404。
- 线上（100.64.0.9）用临时管理员账号逐项验证，不触碰真实用户数据：
  免 id 创建脚本 200 并生成 `admin_<ms>_<rand>`；缺行删除全 404；
  逐用户读接口对不存在的用户全 404；`/api/me` 的 `lastActiveAt` 有值；
  `sessions` 带 `isCurrent`；`since=1e400/NaN/-5` 均正常返回。
- 部署：二进制备份 `melos-backend.bak_20261111_135901`、数据库备份
  `data/music.db.bak_20261111_135901`、站点备份
  `music-admin.bak_20261111_140035.tar.gz`；pm2 `melos-backend` 已重启，
  `/api/health` 正常，所有前端 chunk 均 200。

## 第二批（同日，五个遗留问题的修复）

### 1. 继续播放的增量其他设备拉不到

**原因**：`applyPlayEvent` 把"连续播放"的新段合并进已有行，只改 `listened_ms`，
`id` 不变；而 `/api/sync/pull` 的播放事件游标是 `id > eventSince`。已经拉过该行的
设备永远拿不到增量（客户端还按 `eventId` 去重，更不会重算），管理端按设备统计也少算。

**修法**：合并只允许发生在**同一次 push 新建的行**上（`id > batchFloor`，批次水位在
事务内取 `MAX(id)`）。跨批次的每一段都独立成行 ⇒ 行号推进 ⇒ 其他设备下次 pull
必然拿到；同一批里的多段仍然合一行，重试靠 `event_id` + `merged_ids`（改为查最近 5 行，
比只看最新一行更稳）去重。行数增长可接受：线上抽样该用户现代客户端每天 3~7 行。

### 2. 「一次播放」三套口径

**修法**：`store.playSessionsCTE` 成为唯一定义（`(platform, song_id, device_id)` +
`playSessionGapMs=15s` 窗口，时长用"上一段结束时间"而不是上一段开始时间），
概览 `playSessions`、活跃统计每日播放数、播放记录 Tab 全部复用它。
线上用户 3 修复前概览 399 / 列表 345，修复后两边都是 345。

### 3. 统计查询吞掉 SQL 错误

`AdminUserOverview`、`GetGlobalSyncStats`、`GetClientLogStats` 改为逐条检查错误并
向上返回（handler 已有 `fail500`），DB 异常不再变成 200 + 全 0。

### 4. 设备名关联错表

`play_events.device_id` 是客户端设备 id，`sessions.id` 是服务端 UUID，永远匹配不上。
改为从 `downloads`（唯一同时存客户端设备 id 与名称的表）取最近一条非空名称，
会话列表与 `raw=1` 原始事件都带上。线上用户 3 的 100 条会话现在全部有设备名
（如 `Xiaomi 24129PN74C`）。

### 5. send-code 发信失败仍留冷却

保留"先落库后发信"（用行本身挡并发狂刷），但发信失败立即 `DeleteEmailCode` 删掉该行，
用户可马上重试；删除失败会写日志。顺带把 `PATCH /admin/logs/:id` 的 `resolved`
收紧为必须布尔（上一批已合并进同一次部署）。

## 第二批验证

- 新增 `internal/httpapi/play_sessions_test.go`：跨设备增量可见、重试幂等、
  长间隔另算一次播放、概览/列表/活跃统计三个数字一致、设备名解析、
  发信失败不留冷却、统计查询在 DB 关闭时返回错误。
- 线上（100.64.0.9，同一台 = 39.96.39.134）：
  用户 3 概览 `playSessions` 与播放记录 `total` 均为 345；设备名 100/100 有值；
  临时用户两段连续播放 → 第二次 pull 能拿到 `live-a2`，管理端 1 次播放 / 40000ms；
  接口耗时概览 0.22s、播放记录 0.46s、活跃统计 0.22s（16.7k 事件）。
- 部署：备份 `melos-backend.bak_20261111_145803`、`music.db.bak_20261111_145803`、
  `music-admin.bak_20261111_145915.tar.gz`；线上二进制 sha256 与本地
  `-trimpath` 重建一致（`96ce4a9d…`），即部署产物 = 仓库源码。

## 仍需注意

- **播放事件行数会增长**：现在"一批推送 = 一行"，现代客户端每天几行，可忽略；
  若还有旧版客户端按 1~2 秒逐条推送，行数会明显变多。`runPrune` 保留 180 天，
  需要时把 `PruneOldPlayEvents` 的保留天数调小，或按天聚合旧数据。
- 播放事件仍以自增 `id` 作游标（未引入第二套游标），因此"补齐历史"只能靠重新全量拉取；
  当前客户端每次都是增量拉取，够用。

