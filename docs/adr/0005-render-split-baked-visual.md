# ADR-0005: 渲染分工——外壳 baked model、动件 Flywheel 单路、角度只发目标角

状态：accepted（2026-08 敲定）

落实 ADR-0002 的渲染基座为具体分工：结构外壳（含逐成员格）全部走普通 baked model 方块渲染（每格渲染自己的外壳几何，区块合并/视锥剔除/光照免费）；只有动件（炮座 yaw、炮管 pitch、自旋部件）进 Flywheel Visual + instance。不建"无 Flywheel"回退路径——Flywheel 是 jarJar 内嵌必装依赖，回退即死代码（Create 的双路是给可卸载依赖写的，我们没这需求）。

同步契约：服务器从不上网瞬时角度。匀速件只同步速度标量，客户端按 `renderTime × speed` 积分推导（Create getAngleForBe 铁律）；瞄准件（任意目标角）同步**目标 yaw/pitch**（低频，update tag），客户端向目标角插值收敛；开火判定留在服务器，视觉可骗人。

## 后果

- Flywheel 缺席情形（他人环境冲突）未兜底：接受。
- 一期不做 GUI，机器/炮台状态展示走 Jade 悬浮（Flywheel 之外唯一客户端集成依赖）。
