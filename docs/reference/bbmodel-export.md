# bbmodel 导出清单(#42 全模型资产架构)

## 一个 bbmodel 导出的产物

工作区副本放仓库根 `assets/`(gitignored),只把导出产物提交进 `src/main/resources/assets/mturrets/`。

## 一个 bbmodel → 的产物

| 产物                      | 去处                                          | 消费方                                   |
| ------------------------- | --------------------------------------------- | ---------------------------------------- |
| `<name>_full.json`        | `models/block/turret/`                        | item 模型 parent(datagen 显式引用)      |
| `<name>_base.json`        | `models/block/turret/`                        | Flywheel partial,visual 静态基座实例    |
| 每活动件 1 份 json        | `models/block/turret/`(如 `_head`/`_mid`/`_barrel_left`) | Flywheel partial,visual 动画实例 |
| `<name>_parts.png`        | `textures/block/turret/`                      | 全部部件 json 共用一张组图               |

bbmodel 本身不入库(与 `.bbmodel` 一起留在 `assets/`)。

## 导出要点

- **部件组命名**:Blockbench 出块组(Order)必须叫 `base` / `head` / `mid` / `barrel_left` / `barrel_right`,导出的 per-part json 与 visual 里 `PartialModel` 的对应关系靠它人工核对。
- **几何原点**:模型以锚点格为原点格(0..16 一格),占地向 +X/+Z 生长;2×2 结构几何横跨 0..32(vanilla 模型允许 -16..48)。
- **枢轴一致性**:动件 json 的几何坐标必须与 visual 里的枢轴常量一致(如 Scatter 结构中心 = 锚点块内 (1,1),Duo 炮身 = (0.5, y, 0.5))。改几何要同步对 visual 常量。
- **贴图**:全部件共用一张 `<name>_parts.png`,per-part json 的 `textures."0"` 与 `particle` 都指向它;`texture_size` 与 bbmodel 的 UV 分辨率一致。
- **空模型**:结构格/锚点的 blockstate 用 datagen 生成的 elementless `_empty` 模型(只有 particle 贴图),不需要导出——不要把 bbmodel 里的基座导成 blockstate 模型。

## 物品 display(#42 定案)

物品模型引用 `<name>_full` + datagen 纯公式 display:vanilla 方块展示值 × `s = 1/占地边长`,不手调。
新炮台只需在 datagen 的映射表里登记 `名字 → (full 模型, 占地边长)`。

## 新增炮台的操作序

1. bbmodel 画完整模型(全几何),按组拆块。
2. 按上表导出 full/base/活动件 json + parts 贴图,复制进 `src/main/resources`。
3. datagen:物品模型映射表加一行;visual 加 partial 常量与实例;`_empty` blockstate 若占地不同无需改。
4. `runData` 刷新生成资源,`runClient` 冒烟看世界/物品/裂纹三处表现。
