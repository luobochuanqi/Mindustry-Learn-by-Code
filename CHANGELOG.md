# 更新日志

本项目的所有重要变更记录于此。

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/),版本号遵循 [SemVer](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### 新增

- 三座炮台:Duo(物品弹药,提前量索敌)、Arc(耗能电弧)、Meltdown(持续光束,单通道伤害,点燃路径敌人)
- 服务端子弹实体:伤害、穿透、范围伤害,只命中敌对生物
- 电力网络:电力节点存储与限速传输(100 FE/tick),向相邻机器与炮台供电,支持标准能量能力注入/抽取
- 窑炉:铅 + 沙 → 金属玻璃(1:1:1),取电生产;断电停摆保持进度,恢复后续转;破坏时掉落内部物品
- Jade 支持:炮台弹药/能量、窑炉进度、节点能量悬浮显示
- GameTest 回归套件 6 例:炮台伤害、节点传输、窑炉合成、破坏掉落
- CI 流水线:datagen 新鲜度校验 → GameTest 回归 → 构建 → 产物上传
- 状态效果:BURNING/FREEZING/POISONED/SLOWED 映射原版效果

### 变更

- mod id、包名、资源命名空间由 `mindustry` 迁移为 `mturrets`
- 构建脚本迁移 Kotlin DSL,版本坐标收进版本目录;Gradle 升至 9.7.1(wrapper 带 sha256 校验)
- 依赖裁剪至最小集:KotlinForForge 5.12.0 / GeckoLib 4.9.2 / Jade 15.10.6
- NeoForge 升至 21.1.248,Kotlin 升至 2.4.10
- 方块实体 NBT 键名统一为小驼峰
- Arc/Meltdown 改用静态方块模型(Mindustry 贴图),Duo 保持 GeckoLib 动画渲染

### 移除

- 多方块结构框架与空转注册表(无实现者的脚手架)
- 调试物品(DebugBacon)、示例物品与 LDLib2 测试界面
- 废弃渲染器 MachineRenderer、PowerNodeBlockEntityRenderer
- 客户端子弹方案(ClientSideBullet),由服务端子弹实体取代
- JEI/LDLib2/Mekanism/Create/KubeJS/Rhino 等开发期依赖
