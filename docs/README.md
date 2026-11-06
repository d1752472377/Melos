# Melos 文档索引

面向日常维护的入口。先看这里，再按需进入具体文档。

| 文档 | 用途 |
|---|---|
| [Kotlin 文件组织与拆分规范](kotlin-file-organization.md) | 新增页面、Controller、组件时放哪里，什么时候该拆/合 |
| [UI 设计与组件分层规范](ui-design-system.md) | Design Token、共享 primitives、业务组件、颜色/间距/按钮规则 |
| [Android 架构复审（2026-11-05）](architecture-review-2026-11-05.md) | 阶段性复审结论、已处理项、稳定性/性能/工程化收尾记录 |
| [Android 重构计划](android-refactor-plan.md) | 阶段 0–3 与历次批次的执行记录 |
| [首页 Hero 规格](hero-banner-spec.md) | 首页 Hero 的版式与交互约定 |
| [音源调研报告](音源调研报告.md) | 音源插件/协议的调研背景 |

## 常用命令

```bash
cd android
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease   # 验证 R8 / ProGuard
./gradlew :app:lintDebug
```

## 维护约定

- 颜色：页面不写 `Color(0x...)`；静态颜色登记到 `ui/theme/Color.kt` 的 `AppColors`，封面取色等动态色除外。
- 间距/圆角：使用 `AppSpacing` / `AppRadius`；token 未覆盖的特殊值才保留字面量。
- 按钮：优先复用 `AppSolidButton`、`LiquidGlassButton`、`SettingsKit`、`States`；不为一次性场景新增公共组件。
- Controller：一个独立状态域一个 Controller；不为单次转发新增 Controller/Manager/Helper。
- 提交前至少跑 `compileDebugKotlin` 与 `testDebugUnitTest`；动到打包/反射/序列化时加跑 `assembleRelease`。
