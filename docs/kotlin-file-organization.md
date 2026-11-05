# Melos Kotlin 文件组织与拆分规范

> 适用于 `android/app/src/main/java`。目标是降低阅读和维护成本，不以文件数量或行数作为评价标准。

## 1. 基本原则

1. **按功能域分包**，不按 “screen / controller / component” 机械分层建目录。
2. 一个文件应是一个**内聚的阅读单元**：同一功能、同一数据结构族、或同一组共享控件。
3. 先判断“是否属于同一个功能”，再判断“是否值得独立文件”；不要为了“一个类一个文件”或“减少文件数”而拆/合。
4. 没有第二个真实调用方的公共抽象，不进入 `components` 或公共包。

## 2. 应该独立成文件

满足任一即可：

- 一个独立的页面/路由入口（`XxxScreen` 或 `XxxRoute`），有独立导航生命周期。
- 一个独立业务状态域（`XxxController`），持有自己的 StateFlow、请求令牌或持久化策略。
- 被 **两个以上真实功能** 使用的稳定控件/工具（例如 `SongRow`、`ListSearchBar`、`AppDialog`）。
- 纯逻辑且可单测的规则（例如 `PlaybackFailurePolicy`、`ProfileFormat`、`TokenFormat`）。
- 平台集成边界清晰、需要单独隔离的实现（例如 `HarloQuickJsEngine`、`AudioCacheService`）。
- 公共设计 token / theme 入口（`Color.kt`、`Theme.kt`、`GlassTokens.kt`）。

## 3. 应该保留在原文件

- 只被当前页面使用的私有 Composable、私有辅助函数、私有枚举/数据类。
- 页面的纯展示状态：展开/收起、选中的 tab、动画状态、输入框临时值。
- 同一业务的一组小 Composable（如首页的各版块、播放器的各 Sheet）：优先留在一个文件或用同域文件，不为每个 Composable 建文件。
- 只被一个 Store/Controller 使用的 DTO 或常量。
- 只有一个实现的接口：没有第二个实现或测试 fake 时，不要引入接口。

## 4. 多个相关 Composable 的组织

- 一个 Screen 文件应有一个公开入口 Composable，其余区块用 `private fun` 放在同一文件。
- 当文件超过约 800 行且存在**多个独立视觉区块**时，可按区块拆到同包文件（如 `HomeDailyHero`、`HomeEntryCard`）；拆分依据是“区块可独立理解/修改”，不是行数。
- 只使用一次、且没有明确复用价值的“复用组件”不要抽到 `components`；留在使用方文件中。

## 5. Controller / ViewModel 边界

- **Controller 按独立状态域划分**：一个 Controller 持有自己的一组状态、请求令牌、持久化/网络协调；不按每个页面拆 Controller。
- **不为一次调用创建 Controller/Manager/Helper**；如果只是转发一个函数，直接调用现有实现。
- **MainViewModel 只做组装根与跨域编排**：
  - 持有 Controller 实例与壳层状态（Tab、播放器浮层、全局提示、更新状态等）；
  - 保留真正跨域的动作（切 Tab、主动下载、检查更新、登录后清理等）；
  - 不逐成员转发 Controller 的 StateFlow 或方法。
- **Screen 不持有 Controller**。由 Destination/Route 组装 Controller 与 Screen；Screen 接收 UI State 与回调。
- 纯展示计算（时间问候、排序/过滤、格式化）可以留在 Screen/Destination；涉及持久化、网络、系统服务、业务状态流转的放 Controller。

## 6. 数据类、接口、工具

- 同一 API/存储域的 DTO 集中在一个 `*Models.kt` / 现有 API 文件；不为每个 data class 建文件。
- 纯格式化/校验逻辑可以独立文件并配套单测；如果只是两三行且只服务一个页面，留在页面或 Controller 文件。
- 工具对象（`AppJson`、`BestEffort`、`UserNotice`）独立文件是合理的，因为它们有跨模块调用。

## 7. 行数参考（不是规则）

- `< 60 行`：先确认它是否是独立路由、独立数据模型或独立纯逻辑；如果是，保留；否则考虑并入同域文件。
- `200–800 行`：正常区间。
- `> 800 行`：检查是否存在多个独立区块；有明确边界才拆，不为“看起来短”而拆。
- 任何拆分/合并都必须先确认引用、反射、序列化/ProGuard 与测试；不要机械执行阈值。

## 8. 目录约定

```text
data/
  model/ api/ api/platform/ settings/ source/ sync/ cache/ userapi/
player/ service/
ui/
  theme/        # tokens + 主题入口
  components/   # 设计系统 + 通用 primitives + 通用媒体组件
  shell/        # 跨功能路由 wiring、全局浮层、壳层 holder
  home/ library/ player/ playlist/ artist/ search/ fm/ guess/ rank/
  sheet/ settings/ stats/ profile/ account/ source/ cache/
```

- `ui/components` 不放入只服务单个业务域的组件（如歌单编辑表单、歌曲集合骨架）；它们应回到 `ui/playlist`、`ui/library` 等领域包。
- `ui/shell` 只放跨功能的路由/壳层；同族路由可合并到一个文件（如 `MainTabDestinations.kt`），但不同功能入口仍保留独立函数。
- 业务页面按功能域放置，不按“从哪个 Tab 进入”放置（例如收藏/历史在 `ui/library`，下载在 `ui/cache`）。

## 9. 新增/合并检查清单

- [ ] 这个文件是否只有一个公开职责？
- [ ] 是否有第二个真实调用方？没有就不进公共包。
- [ ] 是否只是转发一次调用？是就直接调用现有实现。
- [ ] 是否把纯展示状态放进了 Controller，或把持久化放进了 UI？
- [ ] 合并后是否仍能一眼找到对应功能？如果需要在 1000 行文件中跳转多次，可能不该合。
- [ ] 引用、测试、序列化、ProGuard 是否已确认？
