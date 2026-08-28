# ADR-0001: 2026 全面翻新的平台、身份与工程决策

状态:accepted(2026-08 敲定)

本 ADR 记录 MTurrets 2026 年全面翻新的决策集。该项目是新手期编写的 Mindustry 风格模组,存在架构混乱(核心玩法未完成、死脚手架、包环)与构建环境问题(版本全线滞后、CI 产物残缺、依赖虚设)。翻新范围明确为:构建环境现代化、现有功能收尾修复、mod 身份迁移与工程化补齐;架构重建与新内容不在本次范围内。以下五项决策共同构成本次翻新的基线,后续改动应以此为前提。

## 1. 目标平台:MC 1.21.1 单版本,构建参数化

MC 1.21.1 在 2026-03 被 NeoForge 26.1 取代前,一直是 NeoForge 官方稳定模组版本,依赖生态成熟。决定停留在 1.21.1,将 NeoForge 从 21.1.209 升至 21.1.248(顶配)并同步升级全部依赖(KotlinForForge、GeckoLib、Jade、Gradle、ModDevGradle);MC 与 Parchment 版本不动(Parchment 2024.11.17 已是 1.21.1 最后一版)。所有版本坐标收进 Gradle 版本目录(libs.versions.toml),使未来 MC 大版本升级退化为"改参数 + 修编译期 API"。明确否决:Stonecutter 多版本矩阵与按版本分叉维护——开发期多版本会让每个改动×N,且 KotlinForForge/GeckoLib 的版本兼容矩阵会锁死升级自由。

## 2. mod id 迁移:mindustry → mturrets

mod id、资源命名空间与包名(含语言键)全部迁移为 mturrets。代价是旧存档中已放置的方块与物品丢失——项目处于开发期、无存档兼容义务,接受该代价。动机:仓库已更名 MTurrets;避免与既有 Mindustry 模组生态的 id 冲突;名称与"玩法移植"定位一致。此决定不可逆性随发布增长,故在翻新窗口内完成。

## 3. 依赖裁剪至最小集

移除 LDLib2/Yoga(仅测试 UI 在使用、snapshots-only 分发、版本滞后)、JEI(代码零引用)、以及 Mekanism×4/Create/KubeJS/Rhino 调试依赖(代码零引用)。保留 GeckoLib(炮台动画)与 KotlinForForge(语言加载器);Jade 从"仅声明"改为真正实现(炮台/窑炉/电力节点的信息显示)。理由:运行时与构建面收到最小集,LDLib2 作为 UI 框架对单人开发的学习成本与构建负担不划算;将来若确需机器 UI 再重新评估。**GeckoLib 一项已被 ADR-0002 取代(Flywheel 接替其为动画基座)。**

## 4. Mindustry 开源资产保留并注明出处

贴图等资产继续保留在本仓库(含从 Mindustry 仓库提取的 Arc/Meltdown 炮台贴图),并新增出处说明。Mindustry 与 MTurrets 同为 GPL-3.0,资产引用合法。否决以 git 子模块方式引入整个 Mindustry 仓库(体积大、混入源码、增加构建链外部依赖)。本项目定位是"把 Mindustry 玩法移植到 Minecraft",不是完全原创,资产沿用与定位一致。

## 5. datagen 产物入库,CI 校验新鲜度

src/generated/resources 取消 gitignore 并提交入库;CI 在 build 前执行 runData 并以 git diff --exit-code 校验产物与代码同步。此前产物被 gitignore 且 CI 从不跑 runData,导致 CI 产出的 jar 缺失语言文件与全部物品/方块模型(发布即残缺)。入库+校验把该回归变成 CI 失败而非线上事故。