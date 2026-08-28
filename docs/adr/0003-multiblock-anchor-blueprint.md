# ADR-0003: 多方块采用锚点 + 蓝图盖格范式（水车式），放弃逐块拼合

状态：accepted（2026-08 敲定）

结构 = 玩家放一个锚点格，服务端下一 tick 按蓝图（Kotlin 代码表：构件偏移集、逐格形状/模型变体、锚点位置）setBlock 出成员格；1x1 内容走同一管线（偏移集只含锚点），全项目只有一条放置代码路径。全部逻辑与状态（含共享 Health 单条血量）只在锚点 BE；成员格无 BE、无交互，只做碰撞箱、选中框与破坏/爆炸代理回锚点。能力按面路由：自动化可插任意成员面，成员 capability 注册内部解析锚点并按相对坐标判面归属。放置校验失败（障碍/空间）拒绝并提示，不做 ghost 预览。任何破坏路径（玩家挖、HP 归零、爆炸）都整体拆除：玩家拆掉一个控制器物品 + 内容物散落（HP 归零则内容物全毁，无维修，修理器属战役期）。活塞/水流拒绝移动成员格。

## Considered Options

- **Create 水箱式（ConnectivityHandler BFS 逐块成型）**：否决——自由尺寸对 Mindustry 移植是负债，形状语义应锁在蓝图里；BFS/分裂机制是纯成本。
- **风车式吸附缩放**（扇叶数量→性能）：一期无此需求，接口不预留。
- 破坏语义曾短暂定过"局部损伤"，已由 Health 单模型（扣总量、归零全毁）取代。

## 后果

- 成员格 `playerWillDestroy` 需裂缝/掉落代理回锚点（Create MultiPosDestructionHandler 先例）。
- 成型放下一 tick 是刻意的：避开 onPlace 内 setBlock 重入与区块加载级联（Create 水车同样延迟）。
- 参考实现：`ref/create` `content/kinetics/waterwheel/`（LargeWaterWheelBlock / StructuralBlock）。
