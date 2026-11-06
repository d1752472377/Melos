# Melos UI 设计与组件分层规范

> 适用于 `android/app/src/main/java/com/aesthetic/music/ui`。目标是把已有视觉体系整理成可复用的三层，而不是新建一套平行设计系统。

## 1. 三层结构

```text
Design Tokens（视觉参数唯一来源）
        ↓
UI Primitives（少量基础控件：dialog / sheet / switch row / glass button / states）
        ↓
Feature Components（SongRow / PlaylistCard / HomeRecommend / PlayerSheet…）
        ↓
Screens（组合业务组件，展示 UI State，发出事件）
```

## 2. 第一层：Design Tokens

### 2.1 现有来源（优先复用）

| 文件 | 职责 |
|---|---|
| `ui/theme/Color.kt` | 全局语义色、平台品牌色、播放器/统计/首页等页面级静态色（`AppColors`） |
| `ui/theme/GzColorScheme.kt` | Material3 `lightColorScheme` / `darkColorScheme` |
| `ui/theme/GzTheme.kt` | `LocalAppIsDark`、`appIsDark()`、主题模式常量 |
| `ui/theme/Theme.kt` | 唯一主题入口 `AestheticMusicTheme`、全局 `ScaleIndication`；Typography / Shapes 使用 M3 默认值 |
| `ui/theme/AppDimens.kt` | `AppRadius` / `AppSpacing`：共享圆角与间距 token |
| `ui/components/GlassTokens.kt` | 玻璃表面/描边/文字色、`glassCard` 等 Modifier |

### 2.2 规则

1. 新代码必须优先使用上述 token，不在页面里新建 `Color(0x...)` 调色板。所有非动态颜色统一登记在 `Color.kt` 的 `AppColors`（按语义/品牌/页面分组）；页面里直接写颜色字面量视为遗漏。
2. 允许的例外：封面取色、均衡器频谱、播放器主题等**由内容动态生成**的颜色；这类颜色应集中在对应 feature 文件，并注明来源。
3. 圆角/间距统一使用 `AppRadius` / `AppSpacing`（`ui/theme/AppDimens.kt`）。页面里的 `padding`、`Spacer`、`Arrangement.spacedBy` 已统一替换为 token；只有 token 没有覆盖的特殊值（如 1–3dp 描边内缩、图标尺寸）才保留字面量。
4. 全局字体使用 Material3 默认 Typography（与 GZ 一致），不保留未启用的 `AppTypography` 副本；需要定制时直接在 `AestheticMusicTheme` 传入 `typography`，不要新增平行的全局字体表。

## 3. 第二层：UI Primitives

### 3.1 已有 primitives（直接复用）

| 文件 | 内容 |
|---|---|
| `ui/settings/SettingsKit.kt` | 设置页骨架、分组、导航行、开关行、分段行、选项列表、说明、值行、分隔线 |
| `ui/components/Buttons.kt` | `AppSolidButton`：主色/危险/禁用三态的实心按钮，可选前置图标 |
| `ui/components/States.kt` | `LoadingBox` / `ErrorBox` / `EmptyBox` / `DangerTextButton` / `DangerCapsuleButton` |
| `ui/components/Overlays.kt` | `AppDialog` / `AppConfirmDialog` / `AppBottomSheet` |
| `ui/components/LiquidGlassButton.kt` | `LiquidGlassButton`、玻璃背景 Modifier |
| `ui/components/LiquidGlassComponents.kt` | `LiquidGhostButton`、`LiquidBentoCard`、`LiquidCapsuleChip` |
| `ui/components/ListSearchBar.kt` | 列表页搜索框 |
| `ui/components/SelectionBottomSheet.kt` | 通用选择抽屉 |
| `ui/components/SecondaryPageTopBar.kt` | 二级页顶栏（目前 2 个调用方） |

### 3.2 规则

1. **先找已有控件，再新建**；不要为按钮/开关再造一套平行体系。
2. 只有当同一形态出现**第 3 个稳定调用点**，或明确属于设计系统（dialog/sheet/loading/empty）时，才抽到 `components`。
3. 新 primitive 必须支持必要变体（尺寸、主/次/危险、启用/加载/选中），不要为了统一牺牲业务差异。
4. `SettingsKit` 只服务设置域，不强行推广到所有页面；`LiquidGlass*` 只用于玻璃拟态视觉，不要求所有按钮都玻璃化。
5. 页面私有按钮（`FmSolidButton`、`PrimaryWideButton`、`PillButton` 等）在只有本页使用时保留；出现稳定重复再抽。

### 3.3 当前不做的统一

- 不创建万能 `AppButton`：`AppSolidButton` 只覆盖“主色实心 / 危险 / 禁用”这一种形态，圆形播放按钮、FM 实心圆、渐变胶囊等仍保留各自实现。
- 不把所有按钮/开关改成同一种视觉。
- 不强制业务组件包裹某个通用 Card/Button。
- 不把 `SettingsKit` 提升为全局表单系统。

## 4. 第三层：Feature Components

- 位置：跟随功能域（`ui/home`、`ui/library`、`ui/playlist`、`ui/player`、`ui/source` 等），不放在 `ui/components`。
- 例子：`SongRow`、`QuadCover`、`SongCollection`、`PlaylistEditorSheet`、`CoverTone`、`HomeDailyHero`、`PlayerControlCenterSheet`。
- 允许各业务组件按场景独立设计；只共享稳定的视觉/交互部分（如 `SongCollection` 的公共列表骨架）。
- 只使用一次的组件不抽公共包；留在使用方文件或同域文件。

## 5. UI 与业务边界

### UI 层可以做

- 布局、样式、动画、纯展示状态（展开/选中/滚动/输入临时值）。
- 将 UI State 转为视觉；把用户操作转为事件/回调。
- 简单展示计算：时间问候、排序/过滤、格式化、封面取色。

### UI 层不应做

- 直接访问 Repository/网络、SharedPreferences、文件持久化、系统服务。
- 业务状态流转、同步/缓存/下载编排。
- 为了让 UI 调业务而增加的纯转发层。

### 已知例外与处理

- 系统选择器返回的 URI 读取可以留在 UI 的 ActivityResult 回调里；但**内容解析/落盘/业务校验**交给 Controller（已按此修正 Profile 图片、插件脚本导入）。
- `PlaylistEditorSheet` 目前的封面文件处理（`PlaylistCoverStore.savePickedImage/delete/encodeCoverDataUrl`）是已知例外：表单需要即时预览；后续迁移时通过回调交给歌单 Controller，不在组件内直接持久化。
- `ListeningStatsScreen` 使用 `StatsStore.todayKey/shiftDay` 等纯静态日期/窗口函数，可保留。
- `CacheListScreen`/`DownloadsScreen` 使用 `AudioCacheService.formatSize` 和 `CachedSong` 只读类型，可保留。

## 6. 页面组织

- `XxxScreen.kt`：一个公开入口 Composable + 私有区块；不持有 Controller。
- Route/Destination：读取 Controller 状态、组装回调；同族路由可合并文件（`MainTabDestinations.kt` 等）。
- 业务组件与 Screen 同域；共享 primitives 才放 `ui/components`。
- 不在 Screen 中直接 `collectAsState` 一个巨大的 ViewModel；只接收自己需要的 State 与回调。

## 7. 设计评审清单

- [ ] 颜色/文字/玻璃效果是否来自现有 token？
- [ ] 是否重复实现了已有 primitive？
- [ ] 新组件是否真的有第二个调用方？
- [ ] UI 是否直接做了持久化/网络/系统服务？
- [ ] 是否为了统一而牺牲了业务组件的独立视觉？
- [ ] 新增硬编码圆角/间距是否可以用现有值或后续 token 收敛？
- [ ] 是否保持了现有主题与交互行为？
