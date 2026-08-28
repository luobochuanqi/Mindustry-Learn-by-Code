# ADR-0004: 基础 BlockEntity 采用瘦混合范式（继承树 + 三件套组合组件）

状态：accepted（2026-08 敲定）

BE 基类 `MTurretBE`（`xyz.luobo.mturrets.core.blockentity`，命名可按此惯例延伸）持骨架：tick 分层（机器 lazyTick、炮台每刻，基类提供双钩子）、结构 Health、单通道同步。同步契约照抄 Create 1.21.1 终态：`write(tag, registries, clientPacket)` 一通道 + `setChanged()+sendData()` 脏标记 + `writeSafe`；**不采用** LazyValue/EntityData（该移植版已自行删除，网上旧教程不可信）。物品缓冲、液体罐、能量存储做成正交组合组件，其余逻辑走继承。事件类视觉（开火、成型）不发包：update tag 带单调计数器字段，客户端见变化即播本地动画，丢包自愈。能力按 NeoForge 惯例每 BE 一个 `static registerCapabilities(RegisterCapabilitiesEvent)`，集中挂点。

## 理由

Create 组合式（SmartBE + BehaviourType map）的收益来自几十种 BE × 正交组合矩阵；MTurrets 复用几乎全在族内（炮台共享索敌/弹仓，机器共享缓冲/配方），族内复用是继承的最优场景。全盘移植会预付 BehaviourType 强转、延迟 add 生命周期补丁等胶水税，而需求不存在。横切三件套（槽/罐/能量）是唯一跨族正交面，做成组件即可。

## 后果

- 若未来出现跨族正交件复用（如"可搬运"落到所有方块），再评估引入 behaviour 层；重构面可预测。
- 弹仓/液罐同用分账模型（按种类各自计数），机器 Buffer 不设固定槽位布局。
