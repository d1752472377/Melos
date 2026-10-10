# 首页 Hero 横幅图规格（服务端渲染）

> 目标：让 Melos 的「发现好音乐」首页大卡与 GZ Music 的观感一致。
> 架构也照 GZ：**整卡是一张服务端生成的位图**，客户端只负责「把它铺进 20dp 圆角的卡里 + 画底部页码点」。

---

## 0. 为什么是服务端出图（结论与证据）

GZ 的做法不是 Compose 排版，而是服务端生成好的横幅图。三条独立证据：

1. **UI 结构里零文本节点**：`uiautomator dump` 中，搜索栏（y=434）到「猜你喜欢」（y=1157）之间**一个节点都没有**；同一次 dump 里其它区块的文字节点全都正常。
2. **图片挂掉时文字也一起消失**：`.shots/compare/gz_home.png`（2.1.8 模拟器实拍）里那张卡是**纯空底色**，而同屏心情电台/新碟首发数据正常。若标题是 Compose `Text`，图片失败它也会照常渲染。
3. **文案在源码里零命中**：`YOUR SOUND, YOUR MOMENT`、`让喜欢的声音，陪你久一点。` 在整个反编译产物里一个字都搜不到；`HomeScreenKt.DailyHeroPage`（3475-3587）只有 `CdPlaceholder` / 图片两个分支，`jp6` 的 name/trackCount 全程未被读取。

所以前端「抠值」这条路是走不通的 —— 那里没有值，只有像素。

---

## 1. 端点契约

`backend-go` 新增（与 `/api/app/version/latest` 同级，**无需登录**，但要限流）：

```
POST /api/home/hero-banner
Content-Type: application/json
```

请求体：

```json
{
  "title": "「婚礼现场」新人婚礼必备歌单",
  "coverUrl": "https://.../cover.jpg",
  "platform": "QQ音乐",
  "trackCount": 81,
  "tag": "歌单分享",
  "page": 1,
  "variant": 0,
  "width": 1080
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `title` | ✅ | 歌单名，服务端负责换行（最多 2 行，超出省略号） |
| `coverUrl` | ✅ | 方形封面地址。**服务端要抓它**，见 §4 SSRF 约束 |
| `platform` | ❌ | 平台名（QQ音乐 / 网易云音乐…），为空则不画平台行 |
| `trackCount` | ❌ | 曲目数，为空则不画「N 首」 |
| `tag` | ❌ | 左上角标签胶囊文案，默认「歌单分享」 |
| `page` | ❌ | 页码，默认 1；用于右下角 `Melos / 01` |
| `variant` | ❌ | 模板镜像，0 = 文案在左封面在右 / 1 = 封面在左文案在右，默认 0 |
| `width` | ❌ | 输出宽度 px，默认 1080。高度恒为 `round(width * 9 / 16)` |

响应：`image/webp`（首选）或 `image/png` 的二进制流。

响应头：

```
Content-Type: image/webp
Cache-Control: public, max-age=600, immutable
ETag: "<sha256(payload+assets)>"
X-Melos-Banner: v1
```

- 支持 `If-None-Match` → 命中返回 `304`。
- 失败语义：**任何一项画不出来都不要返回 4xx/5xx**，而是退化成「纯渐变底 + 标题」的最小卡（见 §5），因为客户端把它当图片用，500 会让卡直接空掉。
- 限流：按 IP 令牌桶（如 30 rps / 突发 60），渲染结果进 LRU 缓存，缓存命中不消耗算力。

> 客户端永远不因为横幅失败而白屏：请求失败时回退到 `PlaylistItem.coverUrl`（§6）。

---

## 2. 画布与几何

以 `width = 1080` 为基准（下述比例在任意宽度下等比缩放）。**比例尺 `s = width / 1080`**。

```
画布                                    1080 × 608   (= 16:9)
 圆角                                    由客户端裁 20dp（图上不画圆角）
 背景                                    封面主色渐变，见 §3
 内边距                                  左右 64 / 上 56 / 下 44

① 品牌行                                y = 56 起，高 40
   左：黑胶图标 40×40 + "GZ Music"→Melos 品牌名  字号 30 / Medium
   右："YOUR SOUND, YOUR MOMENT"→Melos 标语      字号 22 / 字距 +2（右对齐）

② 标签胶囊                               y = 168 起，高 48，圆角 24
   文案 字号 26 / Medium；内边距 左右 28
   底色：主色 @ 22%、文字色 @ 90%

③ 标题                                   y = 256 起，最多 2 行
   字号 60 / Bold / 行高 78 / 字距 -1
   宽度上限 = 文案列宽（见 §2.1）；超出 2 行用省略号

④ 平台行                                 y = 502 起，高 40
   色条 40×6 圆角 3（主色 @ 100%） + 间距 16 + 平台名 字号 30 / Regular

⑤ 封面（方形）
   宽高 = 352 × 352，圆角 24，带 1px @ 白 18% 描边
   variant 0 → x = 646（右）  variant 1 → x = 82（左）
   y = 168（与标签胶囊顶对齐）

⑥ 页脚                                   y = 552 起，高 30
   左：标语 字号 24 / Regular @ 主色深 70%
   右："{品牌名} / {page:02d}" 字号 24 / Regular，右对齐
```

**文案列（title/platform/footer-left 的可用宽度）**

| variant | 文案列 x | 文案列宽 |
|---|---|---|
| 0（文案左） | 64 | 536 |
| 1（文案右） | 482 | 534 |

页码指示器**不画在图上** —— 由客户端叠在卡片底部居中（见 §6），所以 §2 的底部 44px 留白就是给它的。

---

## 3. 背景渐变（主色来源）

1. 取封面图，缩到 32×32，丢弃近黑/近白像素（HSV 的 v < 0.15 或 v > 0.95 且 s < 0.10），
   对剩余像素按 `(色相直方图加权 + 饱和度平方加权)` 求主色 → `tone`（HSV）。
2. 底 → 顶三段线性渐变（浅色主题）：

| 位置 | 颜色 |
|---|---|
| 0.00 | `tone` @ 0.26 |
| 0.55 | `tone` @ 0.12 |
| 1.00 | `tone` @ 0.02 |

深色主题各档 +0.14，并压在 `#1C1C1E` 上。渐变方向：**135°**（左上到右下）。

> 参考图与 GZ 实拍都是**相当饱和**的底色（不是 20% 档的淡灰）。上面三档是据此定的目标值；
> 若客户端侧要临时逼近，可用 `glassToneCard` 的高档，但正式以本节点为准。

---

## 4. 封面抓取与 SSRF 约束

- 只允许 `https`。
- **主机白名单**：从配置读 `BANNER_COVER_ALLOWED_HOSTS`（逗号分隔），默认放行 Melos 已知音源域名；不在白名单内直接跳过封面（退化为无封面版式：标题列宽 = 整幅内宽，封面位置留空）。
- 超时 3s，最大 8MB，只接受 `image/jpeg|png|webp`。
- 服务端**不缓存原图**，只缓存合成结果（key = 请求体 + 白名单版本 的 sha256），TTL 10 分钟，LRU 上限 512 条。

---

## 5. 退化与容错

| 情况 | 输出 |
|---|---|
| 封面抓不到 / 不在白名单 | 无封面版式：标题列宽 = 整幅内宽，其余元素位置不变 |
| 主色算不出来 | `tone = #8E8E93`（Melos 已有 `CoverToneFallback`） |
| 标题过长 | 2 行省略号；仍溢出则缩字号到 48 为止 |
| 字体缺失 | 用内置兜底字形，**绝不因为缺字返回错误** |
| 任何 panic | recover → 返回「纯渐变 + 标题」最小卡 |

---

## 6. 客户端要做的（这次已改）

`ui/home/HomeDailyHero.kt`：**把卡内排版全部去掉**，改成 GZ 的 `DailyHeroPage` 语义 ——

```
Box（20dp 圆角 + 21 高度 9:16 + 16dp 外边距）
 ├ HorizontalPager（沿用现有参数，不动）
 │   └ 每页 = 一张 AsyncImage(bannerUrl)，fillMaxSize + ContentScale.Crop
 │        bannerUrl 取不到 → cdPlaceholder 兜底（沿用现有实现）
 └ HomeHeroIndicator（沿用，BottomCenter + 12dp）
```

`bannerUrl` 的构造：

```
POST {bannerBaseUrl}/api/home/hero-banner      → 但 AsyncImage 只能 GET
```

> ⚠️ 因此**再加一个 GET 形态**，或把上面的 POST 改成 GET + query（参数少、无敏感信息）：
>
> ```
> GET /api/home/hero-banner?title=..&cover=..&platform=..&count=..&tag=..&page=..&variant=..&w=1080
> ```
> GET 形态对本功能更合适（可直接给 `AsyncImage` 当 URL 用，也能被 Coil 磁盘缓存）。§1 的表单字段一一对应成 query 参数即可，`title` 走 URL 编码，长度上限 256。
> POST 形态可保留给「标题过长/需要保密的场景」的后续扩展。

Coil 侧：`ImageRequest.Builder(...).data(bannerUrl).crossfade(200).memoryCacheKey(bannerUrl).diskCacheKey(bannerUrl).build()`。

---

## 7. 后端落地要点（Go 1.26 + Gin）

| 需要 | 方案 |
|---|---|
| 文字渲染（含中文） | `golang.org/x/image/font` + `font/opentype`。**必须自带中文字体**：`go:embed` 一份子集化的 Noto Sans SC（约 2–4MB，只保留常用字 + 拉丁） |
| 缩放 | `golang.org/x/image/draw`（`CatmullRom`） |
| 渐变 | 直接用 `image.RGBA` 逐行写像素（135° 线性插值），比引入绘图库更可控 |
| 圆角封面 | 先画到临时 RGBA，再用距离场生成 alpha 掩码做圆角裁切 |
| 缓存 | `hashicorp/golang-lru/v2`（或自写 `map` + `container/list`），key = 参数 sha256 |
| 主色提取 | 自带实现（`image/color` 转 HSV，按 §3 规则） |
| 中文字体子集 | 构建期用 `pyftsubset`/`fonttools` 生成，产物进仓库（避免运行时下载） |

---

## 8. 待定项（`TODO(未确定)`）

1. **文案**：GZ 的 `YOUR SOUND, YOUR MOMENT` / `让喜欢的声音，陪你久一点。` / `歌单分享` / `瓜子音乐` 是**瓜子音乐的品牌资产**，Melos 不能照抄。本规格里的品牌名/标语需由你定（占位：品牌名 `Melos`，标语待定）。标签胶囊「歌单分享」是通用词，可直接用。
2. **镜像规则**：实测到两种模板（封面右 / 封面左）同时存在，但**判定规则未知**。现以 `variant` 参数暴露，客户端暂定 `pageIndex % 2`；拿到判定依据后改为服务端按歌单 id 稳定推导。
3. **精确几何**：§2 的坐标是按 GZ 实拍（1080×2424，density 2.75）像素测量得到的，误差约 ±4px。有真值后替换。
4. **字体**：GZ 用的是哪套字体未确认（实拍观感接近思源黑体 / Noto Sans SC）。Melos 侧建议直接定 Noto Sans SC，与观感的差异可接受。
