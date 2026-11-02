# ChKSz 音源脚本

把 [ChKSz API](https://api.chksz.com)（多平台音乐解析服务）封装成 **LX Music 自定义源脚本**，
供 Melos App（或任何支持 LX 自定义源协议的客户端）解析网易云 / QQ / 酷狗的播放直链与歌词。

协议依据：[LX Music 官方文档 · 自定义源脚本编写说明](https://lxmusic.toside.cn/mobile/custom-source)。

## 文件说明

| 文件 | 用途 |
|------|------|
| `chksz-source.js` | **音源脚本模板**。`API_KEY` 是占位符，不要直接导入 App（会拿不到数据） |
| `chksz.mjs` | 命令行工具：生成脚本、测接口、搜歌、下载 |
| `chksz-http.mjs` | CLI 用的 HTTP 工具（带 Cloudflare 多 IP 兜底） |
| `sandbox-test.mjs` | 沙箱测试器：**不装 App** 就能按协议把脚本完整跑一遍 |
| `dist/chksz-source.local.js` | `build` 的产物，**导入 App 用这个**（Key 已注入，勿提交勿分享） |

## 快速开始

```bash
# 1. 给 Key（三选一）
export CHKSZ_API_KEY=chksz_xxx        # 环境变量
echo chksz_xxx > chksz.key           # 或者写进文件
node chksz.mjs ... --key chksz_xxx   # 或者命令行参数

# 2. 先看接口现在哪些能用（每个平台 1 次请求，共 3 次额度）
node chksz.mjs probe

# 3. 生成可导入 App 的脚本
node chksz.mjs build
# → dist/chksz-source.local.js

# 4.（可选）在电脑上先验证脚本能正常工作
node sandbox-test.mjs
```

导入 App：**插件管理 → 导入 → 选择文件** → 选中 `dist/chksz-source.local.js` →
导入后点「检测」确认。也可以把脚本放到可公网访问的地方用「导入 → 链接」。

## CLI 用法

```bash
node chksz.mjs search wy 晴天 --limit 5        # 网易云搜索（tx=QQ，kg=酷狗）
node chksz.mjs url wy 2652820720 --probe       # 取直链并探测可播放性
node chksz.mjs lyric wy 2652820720 --out x.lrc # 取歌词存文件
node chksz.mjs playlist 3778678                # 网易云歌单
node chksz.mjs download wy 2652820720 --out ./music  # 下载（默认 flac）
```

音质参数：网易云 `standard/exhigh/lossless/hires`（也认 `128k/320k/flac/flac24bit`）；
QQ/酷狗 `128k/320k/flac/hires/master`（音乐源原生值，服务端不做别名映射）。

## 脚本行为（额度经济）

ChKSz 免费额度 **50 次/天**（UTC+8 重置），限流 **20 次/分钟**。脚本按"薄客户端"设计：

- **正常一首歌 = 2 次额度**：取链 1 次 + 歌词 1 次（歌词缓存 1 小时，连播重听不重复扣）；
- 取链失败时最多追加 2 次请求，且只在两种**确定有意义**的场景追加：
  - HTTP 200 但无 url → 降一档音质再试 1 次（QQ 付费歌常见"flac 无、128k 有"）；
  - 404 未收录 → 按"歌名+歌手"回查 1 次（酷狗 FileHash 与 ChKSz 内部 id 不同义时的保底）；
- 其余一切失败（上游 5xx / 401 / 402 / 429 / 超时）**立即如实上抛，不本地重试**——
  上游熔断时换参数再打结果不会变，只会白烧额度；重试交给 App 的换源策略。

### 与两种宿主的兼容

| 字段 | 官方 LX Music | Melos App | 脚本的处理 |
|------|--------------|-----------|-----------|
| 音质 | `info.type`（值如 'flac'） | `info.quality`（`info.type` 是源类型 'music'） | quality 优先；`info.type` 仅当是合法音质值时采用 |
| 歌词返回 | `{lyric, tlyric, rlyric, lxlyric}` 对象 | 从返回对象里读 `lyric` 字段 | 返回官方结构（天然兼容） |
| actions 声明 | 非本地源固定 `['musicUrl']` | 只认 musicUrl | 按官方声明，lyric 调用不受影响 |

## 实测结论（2026-08-22）

| 接口 | 状态 | 说明 |
|------|------|------|
| `163_search` / `163_music` / `163_lyric` | ✅ 全通 | 搜索/直链(含 lossless)/歌词(含翻译) 验证可用，直链探测 HTTP 200 |
| `163_playlist` | 未验证 | 文档可用，CLI 已实现 |
| `qq_music` | ⚠️ 上游熔断 | 持续 502/503「上游连续失败，链路正在熔断恢复」；**不扣额度** |
| `kugou_music` | ⚠️ 上游熔断 | 同上 |

QQ/酷狗的解析逻辑已按文档实现并经过代码路径验证（错误处理正确：503 时只打 1 次请求、如实上抛），
上游恢复后即可用，随时 `node chksz.mjs probe` 确认。

## Key 安全

- Key 只存在 `dist/`（已 gitignore）和本地 `chksz.key`，模板不含真实 Key；
- CLI 日志与沙箱测试器的请求记录都会把 `apikey=***` 打码；
- `dist/chksz-source.local.js` 里有明文 Key：**别提交 Git、别发群、别截图**。
