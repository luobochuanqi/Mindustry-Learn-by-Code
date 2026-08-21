# MTurrets

把 Mindustry 的炮台、机器与材料玩法带进 Minecraft 的 NeoForge 模组。

- Minecraft **1.21.1** / NeoForge **21.1.248**
- 语言:Kotlin(KotlinForForge 5.12+)
- 许可:**GNU GPL-3.0**(与 Mindustry 资产许可兼容)

## 功能一览

### 炮台

| 炮台 | 弹药/能源 | 特性 |
|---|---|---|
| **Duo** | 铜 / 铁 / 金锭 | 双管速射;铁弹穿透;提前量索敌,移动目标可被命中 |
| **Arc** | 电力(80 FE/发) | 瞬发电弧伤害;能量耗尽停火,恢复供电续战 |
| **Meltdown** | 电力(持续) | 持续光束灼烧路径上全部敌人并点燃,单一伤害通道 |

三座炮台共享方块基类;Duo 使用 GeckoLib 动画渲染,Arc/Meltdown 为静态模型。

### 电力网络

- **电力节点**:容量 24000 FE,距离 ≤6 格自动连线互传(限速 100 FE/tick),连接数上限 10
- 节点向相邻 6 格的机器与能量炮台自动供电
- 外部系统可经标准 Forge 能量能力(`Capabilities.EnergyStorage.BLOCK`)注入/抽取

### 生产

- **窑炉**:铅 + 沙 → 金属玻璃(1:1:1),取电生产(20 FE/个)
- 能量不足时停摆并保持进度,恢复供电后从当前进度续转
- 破坏方块时内部物品与产物全量掉落

### 材料与液体

20 种材料(铍、碳化物、相织物、浪涌合金……)与 11 种液体物品,完整中英文本地化。

### 玩家视角

- [Jade](https://modrinth.com/mod/jade) 悬浮显示:炮台弹药/能量、窑炉进度、节点能量
- 创造物品栏完整收录全部内容

## 快速开始

环境要求:**JDK 21**(Gradle wrapper 9.7.1 已自带,依赖走国内镜像)。

```bash
# 构建 jar(build/libs/mturrets-<版本>.jar)
./gradlew build

# 启动开发客户端
./gradlew runClient

# 专用服务器
./gradlew runServer

# 游戏测试回归套件(6 例)
./gradlew runGameTestServer

# 重新生成 datagen 产物(语言/模型;产物入库,CI 校验新鲜度)
./gradlew runData
```

Windows PowerShell 下使用 `.\gradlew.bat <任务>`。

### 调试提示

开发环境暂无发电来源,可用 `/data merge block <pos> {energy: <值>}` 直接为节点/炮台/窑炉充能。

## 测试与 CI

- GameTest 回归套件覆盖:炮台伤害(Duo/Arc/Meltdown)、节点传输、窑炉合成、破坏掉落;测试只断言外部行为
- CI(GitHub Actions):datagen 新鲜度校验 → GameTest 回归 → 构建 → 上传 jar

## 资产出处

`arc.png`、`meltdown.png`、`duo.png` 提取自 [Anuken/Mindustry](https://github.com/Anuken/Mindustry)(GPL-3.0),提取说明见 [`textures/ATTRIBUTION.md`](src/main/resources/assets/mturrets/textures/ATTRIBUTION.md)。本模组整体以 GPL-3.0 发布。

## 开发文档

- 架构决策:`docs/adr/`
- 领域词汇表:`CONTEXT.md`
