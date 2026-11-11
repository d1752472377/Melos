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

## 已知但本次未动的问题（需要设计取舍）

1. **播放事件合并后其他设备拉不到**：合并只 `UPDATE` 原行的 `listened_ms`，
   `id` 不变，而 `/api/sync/pull` 用 `id > eventSince` 游标。要修得改成
   「合并即 copy-on-write」或按 `update_seq` 增量下发，属于协议改动。
2. **「一次播放」三套口径**：概览/活跃统计按 `song_id` + 10 秒窗口，
   播放记录 tab 按 `platform,song_id,device_id` + 15 秒窗口，同一份数据会给出不同数字。
3. **统计类查询吞掉 SQL 错误**：`AdminUserOverview`、`GetGlobalSyncStats`、
   `GetClientLogStats` 用 `_ =` 忽略错误，DB 异常时接口回 200 + 全 0。
4. **设备名 join 错表**：`play_events.device_id` 是客户端设备 id，
   `sessions.id` 是服务端 UUID，两者永不相等，所以「设备」列只有 id。
5. **`send-code` 先落库后发信**：发信失败时代码行已存在，立刻重试会命中 60 秒冷却。
