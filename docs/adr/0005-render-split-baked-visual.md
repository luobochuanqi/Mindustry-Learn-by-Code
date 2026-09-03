# ADR-0005: 渲染分工——外壳 baked model、动件 Flywheel 单路、角度只发目标角

状态：accepted(2026-08 敲定);**2026-09 修订(#42):炮台外壳改由 visual 全模型渲染**,见文末修订节

落实 ADR-0002 的渲染基座为具体分工：结构外壳（含逐成员格）全部走普通 baked model 方块渲染（每格渲染自己的外壳几何，区块合并/视锥剔除/光照免费）；只有动件（炮座 yaw、炮管 pitch、自旋部件）进 Flywheel Visual + instance。不建"无 Flywheel"回退路径——Flywheel 是 jarJar 内嵌必装依赖，回退即死代码（Create 的双路是给可卸载依赖写的，我们没这需求）。

同步契约：服务器从不上网瞬时角度。匀速件只同步速度标量，客户端按 `renderTime × speed` 积分推导（Create getAngleForBe 铁律）；瞄准件（任意目标角）同步**目标 yaw/pitch**（低频，update tag），客户端向目标角插值收敛；开火判定留在服务器，视觉可骗人。

## 后果

- Flywheel 缺席情形（他人环境冲突）未兜底：接受。
- 一期不做 GUI，机器/炮台状态展示走 Jade 悬浮（Flywheel 之外唯一客户端集成依赖）。

## 修订(2026-09,#42 全模型资产架构)

炮台(Duo/Scatter)的「结构外壳走逐格 baked blockstate 模型」决策作废,改按 Create 大水车:锚点/结构格方块侧渲染为空(锚点 `ENTITYBLOCK_ANIMATED`、结构格 `INVISIBLE`,blockstate 仅保留 elementless 的 particle 模型供粒子取色),外壳几何进 visual 的静态 base 实例,与动件同一渲染路径。动因:物品模型要显示整座炮台,逐角基座模型无法复用为物品图;同一几何三条入口(世界/物品/裂纹代理)必须同源。机器类(钻头/电池/电力节点/窑炉/发电机)维持逐格 baked model 不迁移。多方块破坏裂纹由客户端 LevelRenderer mixin 代理全结构同步(Create MultiPosDestructionHandler 同款)。同步契约与「不建无 Flywheel 回退」不变。
