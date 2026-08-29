# NeoForge 1.21.1 矿石世界生成调研（面向 MTurrets 一期：铜 / 铅 / 煤矿脉）

- 调研日期：2026-08-29
- 目标仓库：MTurrets（Kotlin + NeoForge，Minecraft 1.21.1，移植 Mindustry 玩法）
- 目标版本：NeoForge 21.1.248（ModDevGradle 2.0.144，`gradle/libs.versions.toml`）；游戏版本 1.21.1（数据版本构建日期 2024-08-08）
- 关联票：#29（本调研）；产出喂给 HITL 票「定矿脉生成与钻头方案」，本报告只陈述事实，不替用户做选择
- 范围：datagen `ConfiguredFeature`/`PlacedFeature` 挂生物群系 vs `BiomeModifier` 的 1.21.1 现状 → 矿石方块完整注册面（Block/BlockItem/loot/feature 绑定/blockstate/model/lang）→ 矿脉形状与高度分布参数惯例 → 互操作与 datagen 新鲜度陷阱
- 来源标注约定（沿用 docs/research 既有惯例）：
  - 【一手·原版数据】= 对本地 NeoForge 开发环境缓存中的官方 1.21.1 客户端 jar（`neoformruntime/artifacts/minecraft_1.21.1_client.jar`）直接解包检查 `data/minecraft/...` / `assets/minecraft/...` 得到的 JSON 原文，路径随文给出，可复验
  - 【一手·NeoForge 源码】= neoforged/NeoForge 仓库 `1.21.1` 分支源码，已实际抓取核实，给仓库相对路径
  - 【NeoForge 文档】= docs.neoforged.net 的 1.21.1 版本锁定文档页（`docs/1.21.1/...`；页面自带"NeoForged 1.21 - 1.21.1 已不再积极维护"横幅，但仍是对 1.21.1 最准确的官方文档）
  - 【wiki】= Minecraft Wiki（minecraft.wiki）条目，属社区整理的官方行为记录，用于原版数据 JSON 格式与数值的佐证
  - 【待核实/建议】= 未能拿到一手来源的论断，或属于工程惯例而非官方断言，明确标出

---

## 0. 结论速览

| 问题 | 已核实答案 | 出处 |
| --- | --- | --- |
| 1.21.1 给已有群系加矿石，推荐哪条路 | **两者都要用，且是固定组合**：用 datagen 把 `ConfiguredFeature` + `PlacedFeature` 注册成动态注册表条目（`RegistrySetBuilder` + `DatapackBuiltinEntriesProvider`，落在 `data/<modid>/worldgen/{configured_feature,placed_feature}/`），再用 **`BiomeModifier`（`neoforge:add_features`，`step: "underground_ores"`）把它们挂到群系**（`data/<modid>/neoforge/biome_modifier/<name>.json`）。只注册 feature 不挂群系 = 不会生成；`BiomeModifier` 是 1.21.1 向"已有群系"注入 placed feature 的官方数据驱动机制。**没有"改原版 biome JSON"选项**——改 biome JSON 意味着覆盖原版群系定义，NeoForge 不提供该 datagen provider，且会产生与 mod 数据包的覆盖冲突 | 【NeoForge 文档】Biome Modifiers；【NeoForge 文档】Registries「Data Generation for Datapack Registries」 |
| datagen 挂载代码骨架 | `RegistrySetBuilder().add(Registries.CONFIGURED_FEATURE, …).add(Registries.PLACED_FEATURE, …).add(NeoForgeRegistries.Keys.BIOME_MODIFIERS, …)` → `DatapackBuiltinEntriesProvider`（`GatherDataEvent` 中 `event.includeServer()` 注册）。BiomeModifier 用内建 record `AddFeaturesBiomeModifier(biomes, features, step)`，类型 id `neoforge:add_features` | 【NeoForge 文档】Registries；【NeoForge 文档】Biome Modifiers「Datagenning Biome Modifiers」；【一手·NeoForge 源码】`src/main/java/net/neoforged/neoforge/common/world/BiomeModifiers.java`、`src/main/java/net/neoforged/neoforge/common/data/DatapackBuiltinEntriesProvider.java` |
| 矿石方块要注册些什么 | Block（`DeferredRegister.Blocks`）；BlockItem（**必须单独注册**，`registerSimpleBlockItem`）；方块 loot table（`BlockLootSubProvider`：silk touch 分支 + 掉落物 count + fortune + 爆炸衰减，落 `data/<modid>/loot_table/blocks/<block>.json`）；blockstate JSON（单 variant）；block model（`cube_all`）；item model（parent 指回 block model）；语言键（`block.<modid>.<name>`，`LanguageProvider`）；以及上述 configured/placed feature + biome modifier | 【NeoForge 文档】Blocks（BlockItem 单独注册）、Items、Loot Tables、Model Datagen、I18n；【一手·原版数据】见第 3 节逐个文件 |
| 原版矿石 feature 形状 | 主世界矿石全部是 `minecraft:ore` 类型（**ore blob**，椭球簇，`size` 决定块数上限，如 size=17→最多 37 块、size=20→52、size=10→16）；`minecraft:scattered_ore`（稀疏散布，块数 == size）在 1.21.1 **只被下界远古残骸使用**（size 2–3）。两种类型同属 `OreFeature` 族 | 【一手·原版数据】`data/minecraft/worldgen/configured_feature/ore_*.json` 全量扫描；【wiki】Ore (feature) |
| 高度分布与数量惯例 | placement 链固定四件套：`count`（每区块尝试次数）→ `in_square`（XZ 随机偏移）→ `height_range`（`uniform`/`trapezoid`/`triangle` 高度提供器）→ `biome`（群系过滤）。煤：size 17，上层 count 30 / uniform 136…世界顶、下层 count 20 / trapezoid 0–192 / 埋藏版 discard 0.5；铜：size 10 与 20，count 16，trapezoid −16…112 | 【一手·原版数据】`data/minecraft/worldgen/placed_feature/ore_coal_*.json`、`ore_copper*.json`；【wiki】Ore (feature) 分布表 |
| Mindustry 式"簇状矿"要改什么 | 把原版参数往"大 `size` + 低 `count` + 高 `discard_chance_on_air_exposure` + 窄高度带"方向调即是"一簇一大团"；若想要"稀疏小颗粒散布"，`scattered_ore` 类型（size 即块数）是现成实现，但 1.21.1 原版只在主世界之外用过它。参数选择见第 4 节事实表，具体取值留给 HITL | 【一手·原版数据】+【wiki】Ore (feature) |
| 互操作陷阱 | ① 不要重复把原版 `PlacedFeature` 用于多个 biome modifier / 或同时"biome JSON + modifier"双挂——会触发 **feature cycle violation 崩溃**，规避办法是把原版 feature 复制到自己的 namespace；② biome modifier JSON 会被同路径数据包覆盖（可用于关掉别人的修饰器，`neoforge:none`）；③ 矿石只在区块**创建时**生成，已生成区块不会补矿（原版如此，mod 亦同） | 【NeoForge 文档】Biome Modifiers 警告框；【一手·NeoForge 源码】`BiomeModifiers.java` javadoc；【wiki】Ore (feature)「Generates in existing chunks: No」 |
| datagen 新鲜度 | `runData` 运行配置已在本仓库配置好（`--mod`、`--all`、`--output src/generated/resources`、`--existing src/main/resources`，见 `build.gradle.kts`）；生成文件落入 `src/generated/resources` 并**随 git 提交、打包进 jar**。CI 里"重新 runData 后 git diff 应为空"的校验属工程惯例【建议】，不是 NeoForge 官方强制要求 | 【NeoForge 文档】Resources「Data Generation」；仓库 `build.gradle.kts` |

---

## 1. 世界生成管线与概念分层

1.21.1（乃至 1.18 以来的数据驱动世界生成）的世界生成对象分三层，全部是**动态（datapack）注册表**，以 JSON 数据包形式加载，代码只负责提供 codec 与（可选）datagen：

```
ConfiguredFeature（世界生成"做什么"）
   ↓ 被引用
PlacedFeature（"在哪、多少次、什么高度"——placement 修饰器链）
   ↓ 被引用
Biome json 的 features 列表（按 GenerationStep.Decoration 分桶）← 1.21.1 下由 BiomeModifier 追加
```

- `ConfiguredFeature`：`type`（feature 类型 id，硬编码不可新增）+ `config`（取决于类型）。存在 `data/<namespace>/worldgen/configured_feature/`。【wiki】Configured feature
- `PlacedFeature`：`feature`（引用的 configured feature）+ `placement`（修饰器列表，按顺序应用：`count`、`in_square`、`height_range`、`biome`、`rarity_filter`、`block_predicate_filter` 等）。存在 `data/<namespace>/worldgen/placed_feature/`。【wiki】Placed feature
- 群系（biome）定义里按 `GenerationStep.Decoration` 分步罗列 placed feature，世界生成时按步骤顺序跑。【NeoForge 文档】Biome Modifiers 的步骤表（`underground_ores` = "The step for all Ores and Veins to be added to. This includes Gold, Dirt, Granite, etc."，即矿石所在步骤，全步骤顺序：`raw_generation` → `lakes` → `local_modifications` → `underground_structures` → `surface_structures` → `strongholds` → **`underground_ores`** → `underground_decoration` → `fluid_springs` → `vegetal_decoration` → `top_layer_modification`）

datapack 注册表的 JSON 落盘路径规则：【NeoForge 文档】Registries
- 原版注册表：`data/<modid>/worldgen/...`（如 `worldgen/configured_feature`、`worldgen/placed_feature`）
- NeoForge 额外注册表：`data/<modid>/neoforge/<registry_path>`（如 `neoforge/biome_modifier`）

## 2. 注入矿石的推荐路径（1.21.1 现状）

### 2.1 为什么是"Register + BiomeModifier"组合

- **只注册 ConfiguredFeature/PlacedFeature 不会生成任何东西**。feature 要被群系的 features 列表引用（直接写出，或以 biome modifier 方式追加）才会在世界生成时运行。NeoForge 1.21.1 官方文档把"datagen 注册 feature"与"biome modifier 挂到群系"写成两段互补的流程：前者见 Registers「Data Generation for Datapack Registries」（`RegistrySetBuilder` + `DatapackBuiltinEntriesProvider`，示例即是 `Feature.ORE + OreConfiguration` + `Registries.CONFIGURED_FEATURE`），后者见 Worldgen「Biome Modifiers」。
- **向"已有"的主世界群系加东西，官方机制就是 BiomeModifier**（`neoforge:add_features` / `neoforge:remove_features` 等内建修饰器）。反复制、不覆盖原版 biome JSON。
- 结论：1.21.1 的推荐做法 = **datagen 注册两者（feature 对 + biome modifier 对）**，一次 `RegistrySetBuilder` 全部产出。这不是"二选一默认可"，而是固定组合；问题里"挂生物群系还是 BiomeModifier"的答案：**由 BiomeModifier 挂群系**（而不是手写 biome JSON）。

### 2.2 datagen 代码骨架（源自官方文档示例）

```java
// 1) 定义 ResourceKey
public static final ResourceKey<ConfiguredFeature<?, ?>> ORE_COPPER = ResourceKey.create(
    Registries.CONFIGURED_FEATURE, ResourceLocation.fromNamespaceAndPath(MOD_ID, "ore_copper"));
public static final ResourceKey<PlacedFeature> PLACED_ORE_COPPER = ResourceKey.create(
    Registries.PLACED_FEATURE, ResourceLocation.fromNamespaceAndPath(MOD_ID, "ore_copper"));
public static final ResourceKey<BiomeModifier> BM_ORE_COPPER = ResourceKey.create(
    NeoForgeRegistries.Keys.BIOME_MODIFIERS, ResourceLocation.fromNamespaceAndPath(MOD_ID, "ore_copper"));

// 2) RegistrySetBuilder（configured + placed + biome modifier 全部放进一个 builder）
new RegistrySetBuilder()
    .add(Registries.CONFIGURED_FEATURE, bootstrap -> bootstrap.register(ORE_COPPER,
        new ConfiguredFeature<>(Feature.ORE,
            new OreConfiguration(List.of(
                // 目标标签 + 放入的方块（stone → 浅层变体，deepslate → 深层变体）
                OreConfiguration.target(TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES), MY_COPPER_ORE.get().defaultBlockState()),
                OreConfiguration.target(TagMatchTest(BlockTags.DEEPSLATE_ORE_REPLACEABLES), MY_DEEP_COPPER_ORE.get().defaultBlockState())),
                10, 0.0f))))  // size, discardChanceOnAirExposure
    .add(Registries.PLACED_FEATURE, bootstrap -> {
        HolderGetter<ConfiguredFeature<?, ?>> cf = bootstrap.lookup(Registries.CONFIGURED_FEATURE);
        bootstrap.register(PLACED_ORE_COPPER, new PlacedFeature(cf.getOrThrow(ORE_COPPER),
            List.of(
                CountPlacement.of(16),                     // 每区块尝试次数
                InSquarePlacement.spread(),               // XZ 0..15 随机
                HeightRangePlacement.triangle(            // 高度分布：三角 / 梯形 / 均匀
                    VerticalAnchor.absolute(-16), VerticalAnchor.absolute(112)),
                BiomeFilter.biome())));                    // 群系过滤（必须）
    })
    .add(NeoForgeRegistries.Keys.BIOME_MODIFIERS, bootstrap -> {
        HolderGetter<Biome> biomes = bootstrap.lookup(Registries.BIOME);
        HolderGetter<PlacedFeature> pf = bootstrap.lookup(Registries.PLACED_FEATURE);
        bootstrap.register(BM_ORE_COPPER,
            new AddFeaturesBiomeModifier(
                biomes.getOrThrow(Tags.Biomes.IS_OVERWORLD),  // 或单个群系 id / 群系 list / 自己的 tag
                HolderSet.direct(pf.getOrThrow(PLACED_ORE_COPPER)),
                GenerationStep.Decoration.UNDERGROUND_ORES));
    });

// 3) GatherDataEvent 注册 provider
event.getGenerator().addProvider(event.includeServer(),
    output -> new DatapackBuiltinEntriesProvider(output, event.getLookupProvider(),
        BUILDER, Set.of(MOD_ID)));
```

出处：【NeoForge 文档】Registries（步骤 1–3 的完整示例，含 `Feature.ORE`、`OreConfiguration`、`DatapackBuiltinEntriesProvider` 注册）；【NeoForge 文档】Biome Modifiers「Add Features / Datagenning」（`AddFeaturesBiomeModifier` 用法、`step` 字段、`#c:is_overworld` 标签写法）。示例中用到的 `CountPlacement`/`InSquarePlacement`/`HeightRangePlacement`/`VerticalAnchor`/`BiomeFilter` 为原版 `net.minecraft.world.level.levelgen.placement.OrePlacement` 帮手方法的产物，与原版 placed_feature JSON 一一对应（见第 4 节原版 JSON 原文）。写出的 biome modifier JSON：

```json
{ "type": "neoforge:add_features",
  "biomes": "#minecraft:is_overworld",
  "features": "yourmodid:ore_copper",
  "step": "underground_ores" }
```

### 2.3 挂载细节与边界

- `neoforge:add_features` 的 `biomes` 可接受：单个群系 id、群系 id 列表、`#` 生物群系标签；`features` 可接受单个 placed feature id、列表、或 placed feature 标签。【NeoForge 文档】Biome Modifiers；【一手·NeoForge 源码】`BiomeModifiers.java` 的 `AddFeaturesBiomeModifier` javadoc。
- 主世界全体群系的现成标签：`#minecraft:is_overworld`（1.21.1 值列表覆盖 61 个主世界群系，含洞穴群系，见原版 `data/minecraft/tags/worldgen/biome/is_overworld.json`）。【一手·原版数据】
- 移除别人/原版的 feature 用 `neoforge:remove_features`（`steps` 可选，缺省删全部步骤）。【NeoForge 文档】Biome Modifiers「Remove Features」
- 自定义 biome modifier 需要把 `MapCodec` 静态注册进 `NeoForgeRegistries.BIOME_MODIFIER_SERIALIZERS`（key：`biome_modifier_serializers`）；`BiomeModifier` 本身是动态注册表（key：`biome_modifier`），两者都存在于 1.21.1 分支【一手·NeoForge 源码】`src/main/java/net/neoforged/neoforge/registries/NeoForgeRegistries.java:36,51,61`。
- **警告（官方原话要点）**：往生物群系里加原版 PlacedFeature 要小心，可能触发 feature cycle violation（两个群系在同一个 `GenerationStep` 内拥有相同两个 feature 但顺序不同 → 崩溃）；同一个 PlacedFeature 不应出现在多个 biome modifier 里；原版 PlacedFeature 不应同时被"biome JSON 引用"和"biome modifier 添加"；确需如此时，**把原版 PlacedFeature 复制到自己的 namespace 下是绕开问题的最简单办法**。【NeoForge 文档】Biome Modifiers 警告框；【一手·NeoForge 源码】`BiomeModifiers.java`「Be wary of using this to add vanilla PlacedFeatures to biomes…」
- biome modifier JSON 可以被数据包以**完全相同的路径**覆盖，`neoforge:none` 类型用于"无操作"，是官方向第三方提供的关掉某个修饰器的方式。【NeoForge 文档】Biome Modifiers「None」

## 3. 矿石方块的完整注册面

以原版 1.21.1 `copper_ore` 为基准逐一列出（每个文件均为直接从官方 jar 解出的原文结构）：

| 注册项 | 做法 / 产物路径 | 出处 |
| --- | --- | --- |
| Block | 代码注册：`DeferredRegister.createBlocks(modid)`（`DeferredRegister.Blocks`），`new Block(BlockBehaviour.Properties.of()...)`；方块只允许在注册期构造一次 | 【NeoForge 文档】Blocks |
| BlockItem | **必须单独注册**——方块不一定有物品（如火焰）；"inventory 里看到的方块其实是 BlockItem"。`DeferredRegister.Items#registerSimpleBlockItem(block)` 一键注册（名字取自方块注册名） | 【NeoForge 文档】Blocks（"A `BlockItem` must be registered separately"）与 Items |
| （可选）Block type | 自定义 Block 子类需为 `BlockBehaviour#codec` 注册 `MapCodec` 到 `BLOCK_TYPE` 注册表；CTRL+F 报告用。`Block`/`BlockBehaviour` 直用则无需 | 【NeoForge 文档】Blocks「Block Types」 |
| Loot table | datagen：`LootTableProvider` + `BlockLootSubProvider`（`dropSelf` / `createSingleItemTable` / `createSilkTouchOnlyTable`；富矿在原版是 alternatives + `createOreDrop` 语义）。产物：`data/<modid>/loot_table/blocks/<block>.json` | 【NeoForge 文档】Loot Tables「Datagen / BlockLootSubProvider」；【一手·原版数据】见下 |
| Configured/Placed feature | 见第 2.2 节；受 `#minecraft:stone_ore_replaceables` / `#minecraft:deepslate_ore_replaceables` 标签驱动替换（原版两标签值：stone/granite/diorite/andesite；deepslate/tuff） | 【一手·原版数据】`data/minecraft/tags/block/stone_ore_replaceables.json`、`deepslate_ore_replaceables.json` |
| Blockstate JSON | `assets/<modid>/blockstates/<block>.json`，单 variant：`{ "variants": { "": { "model": "modid:block/copper_ore" } } }` | 【一手·原版数据】`assets/minecraft/blockstates/copper_ore.json` |
| Block model JSON | `assets/<modid>/models/block/<block>.json`：`{ "parent": "minecraft:block/cube_all", "textures": { "all": "modid:block/copper_ore" } }` | 【一手·原版数据】`assets/minecraft/models/block/copper_ore.json` |
| Item model JSON | `assets/<modid>/models/item/<block>.json`：`{ "parent": "modid:block/copper_ore" }`（BlockItem 渲染指向方块模型）。datagen 用 `simpleBlockWithItem(block, model)` / `simpleBlockItem` | 【一手·原版数据】`assets/minecraft/models/item/copper_ore.json`；【NeoForge 文档】Model Datagen |
| 纹理 | `assets/<modid>/textures/block/<block>.png`（datagen 假定该路径） | 【NeoForge 文档】Model Datagen |
| 语言键 | 约定键名 `block.<modid>.<name>`（原版：`block.minecraft.copper_ore` → "Copper Ore"）；datagen 用 `LanguageProvider` 的 `add(block, "...")` | 【一手·原版数据】`assets/minecraft/lang/en_us.json`；【NeoForge 文档】I18n「Datagen」 |
| 标签（可选但有互操作价值） | 若放在 `#minecraft:stone_ore_replaceables` / `#minecraft:deepslate_ore_replaceables` 里，则其他 ore feature 也能替换你的石头类方块——原版矿石替换对象由这些标签定义 | 【一手·原版数据】两 tags 值 |

**原版 `copper_ore` loot table 原文**（`data/minecraft/loot_table/blocks/copper_ore.json`；注意 1.20.2+ 目录名是 `loot_table` 单数）——silk touch 分支掉落矿石本体，否则掉落 raw 物品并应用 fortune / 爆炸衰减；MTurrets 若引用 Mindustry "直接出铜/铅" 玩法则对应的是"非 silk touch 分支"产出形态：

```json
{ "type": "minecraft:block",
  "pools": [ { "bonus_rolls": 0.0, "rolls": 1.0, "entries": [ {
    "type": "minecraft:alternatives",
    "children": [
      { "type": "minecraft:item",
        "conditions": [ { "condition": "minecraft:match_tool",
          "predicate": { "predicates": { "minecraft:enchantments": [
            { "enchantments": "minecraft:silk_touch", "levels": { "min": 1 } } ] } } } ],
        "name": "minecraft:copper_ore" },
      { "type": "minecraft:item",
        "functions": [
          { "add": false, "count": { "type": "minecraft:uniform", "max": 5.0, "min": 2.0 }, "function": "minecraft:set_count" },
          { "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops", "function": "minecraft:apply_bonus" },
          { "function": "minecraft:explosion_decay" } ],
        "name": "minecraft:raw_copper" } ] } ] } ],
  "random_sequence": "minecraft:blocks/copper_ore" }
```

## 4. 矿脉形状、数量与高度分布惯例（原版 1.21.1 实测值）

### 4.1 ore blob 与 scatter 两种形态

`minecraft:ore`（**ore blob**）＝椭球状簇；`minecraft:scattered_ore`（**scatter ore**）＝稀疏散布簇，每簇块数 == `size`。【wiki】Ore (feature)：1.21.1 全量 configured_feature 扫描显示 **scattered_ore 只被下界 `ore_ancient_debris_large`（size 3）/ `ore_ancient_debris_small`（size 2）使用（discard 1.0）**，主世界矿石全为 `ore` blob。【一手·原版数据】`data/minecraft/worldgen/configured_feature/ore_ancient_debris_*.json` 及全量扫描。

blob 的 `size` 与最大块数非线性（wiki 实测表节选）：size 3→4、4→5、9→13、10→16、12→23、17→37、20→52、33→160、64→864。【wiki】Ore (feature)（该表有原版数据注脚）

`discard_chance_on_air_exposure`：若 vein 任一块与空气相邻，整个 vein 以该概率丢弃（不替换）；水等非空气方块不算暴露。【wiki】Ore (feature)；原版用它做"埋藏型"矿：`ore_coal_buried`（丢矿不裸露）0.5、远古残骸 1.0、大多数矿石 0。

### 4.2 placement 链与高度提供器

所有原版矿石 placed feature 都是同一模板（顺序固定）：

```json
{ "feature": "minecraft:ore_copper_small",
  "placement": [
    { "type": "minecraft:count", "count": 16 },
    { "type": "minecraft:in_square" },
    { "type": "minecraft:height_range",
      "height": { "type": "minecraft:trapezoid",
        "max_inclusive": { "absolute": 112 }, "min_inclusive": { "absolute": -16 } } },
    { "type": "minecraft:biome" } ] }
```

- `count`：每区块尝试放置次数（0–4096，可叠加）。【wiki】Placed feature
- `in_square`：XZ 各 +0..15 随机，把位置散布到整个区块。【wiki】Placed feature
- `height_range`：高度提供器三种常用类型——`uniform`（均匀）、`trapezoid`（梯形近似三角）、`triangle`（三角），边界可用 `absolute`、`above_bottom`（相对世界底 y=−64）、`below_top`（相对世界顶 y=320）。【wiki】Placed feature（height provider）；原版 JSON 见下
- `biome`：位置所在群系必须包含该 placed feature 才通过（置于链尾）——**必带**；也意味着只有被 biome modifier 挂过的群系真正生成。【wiki】Placed feature

### 4.3 原版煤 / 铜 / 铁 / 钻石实测值（1.21.1 jar 直读）

| configured feature | 类型 | size | discard_on_air | placed feature | count/区块 | 高度分布 |
| --- | --- | --- | --- | --- | --- | --- |
| `ore_coal` | ore | 17 | 0.0 | `ore_coal_upper` | 30 | uniform 136 … below_top 0（≈320） |
| `ore_coal_buried` | ore | 17 | **0.5** | `ore_coal_lower` | 20 | trapezoid 0 … 192 |
| `ore_copper_small` | ore | 10 | 0.0 | `ore_copper` | 16 | trapezoid −16 … 112 |
| `ore_copper_large` | ore | 20 | 0.0 | `ore_copper_large` | 16 | trapezoid −16 … 112 |
| `ore_iron` | ore | 9 | 0.0 | `ore_iron_middle` | 10 | trapezoid −24 … 56 |
| `ore_iron_small` | ore | 4 | 0.0 | `ore_iron_small` | 10 | uniform above_bottom 0 … 72 |
| `ore_iron` | ore | 9 | 0.0 | `ore_iron_upper` | 90 | trapezoid 80 … 384 | 
| `ore_diamond` | ore | 8 | 0.5 | `ore_diamond` | 7 | trapezoid above_bottom −80 … 80（≈−64…64） |

出处：【一手·原版数据】`data/minecraft/worldgen/{configured_feature,placed_feature}/ore_{coal,copper,iron,diamond}*.json`（`ore_iron_upper` 的 90/80–384 见同 jar 文件；wiki《Ore (feature)》分布表与上述数值一致，可交叉验证）。

惯例总结：**中浅层常见矿 = 大 size + 梯形/三角分布 + discard 0**（煤）；**深层/稀有矿 = 小 size + 大 count + 高 discard（防裸露）**（钻石）。煤同时配置了"浅层均匀撒点（30 次）"与"深层埋藏（trapezoid + discard 0.5）"两条通道。

### 4.4 Mindustry 式簇状矿对应的参数方向（事实，非定案）

- "簇状大矿团"：参照 `ore_coal` 但更极端——`size` 提到 33–64（对应 160–864 块上限），`count` 降到 1–4，高度带收窄（如 trapezoid 40–120），`discard_chance_on_air_exposure` 提高到 0.5–1.0 让矿团深埋不裸露。【一手·原版数据】+【wiki】Ore (feature) size→块数表
- "稀疏小颗粒"：`scattered_ore` 类型（块数即 size，2–16 皆可），1.21.1 原版仅下界用过；若用于主世界无任何机制阻碍，但属于没有先例的参数组合。【一手·原版数据】（全量扫描仅远古残骸）——**【待核实】** 是否有主流 mod 在主世界用 scattered_ore，未查证
- 高度参考系：主世界 y 为 −64…320（`above_bottom:0`=−64、`below_top:0`=320）。【wiki】Heightmap/坐标；【一手·原版数据】`ore_coal_upper` 的 `below_top` 用法

## 5. 互操作、性能与 datagen 新鲜度陷阱

### 5.1 互操作

- **feature cycle violation**：见 2.3 节官方警告——不要同一个 placed feature 多处挂载；与"复制到自己 namespace"的规避法。【NeoForge 文档】Biome Modifiers
- **与其他 mod / 原版矿石重叠**：群系修饰器只是往群系 feature 列表追加条目（`AddFeaturesBiomeModifier.modify` → `BiomeGenerationSettingsBuilder.addFeature(step, holder)`【一手·NeoForge 源码】`BiomeModifiers.java`），与原版矿石同 step 并列，无互斥机制；无需处理"重叠"，遇到要移除用 `neoforge:remove_features`。
- **已被生成区块不补矿**：feature 只在区块生成时执行（wiki infobox「Generates in existing chunks: No」）。若未来需要"老存档补矿"，需要额外手段（如区块加载时后处理/迁移），不在本次 range。【wiki】Ore (feature)
- **覆盖/禁用**：任何 biome modifier 可被同路径数据包文件覆盖；`neoforge:none` 为官方无操作类型（禁用它人修饰器的标准办法）。【NeoForge 文档】Biome Modifiers

### 5.2 性能注意（事实与建议）

- 每个"count 次尝试"都会跑目标替换检测；大 `size` + 大 `count` 直接放大单区块生成成本。原版最重参数（铁上层 90 次尝试）说明了"大 count"的代价边界，但性能影响没有官方基准数字【待核实】。
- `discard_chance_on_air_exposure` 降低"露天可见矿"比例的同时，也减少无效挖掘面，属玩法/表现参数而非性能参数。

### 5.3 datagen 新鲜度

- runData 是 NeoForge 标准做法：`GatherDataEvent` 注册 provider → Data run configuration 跑完写出。命令行参数 `--mod`/`--output`/`--existing`/`--includeClient`/`--includeServer`/`--all`；文档推荐 `--output` 用 `file('src/generated/resources').getAbsolutePath()`。【NeoForge 文档】Resources「Data Generation」
- 本仓库已按该推荐配好 `data` run（`--mod`、`--all`、`--output src/generated/resources/`、`--existing src/main/resources/`），且 `src/generated/resources` 已并入主 source set 资源（`mainSourceSet.resources.srcDir("src/generated/resources")`）——**生成文件提交进 git 并随 mod 打包**是当前仓库既定结构。【一手】仓库 `build.gradle.kts`
- 陷阱【建议，非官方断言】：忘跑 runData / 跑完不提交，会让"仓库里的生成文件"与"datagen 代码"失同步，他人 checkout 后拿到过期/缺失资源。CI 中"执行 runData 后 `git diff --exit-code`"作为门禁是常见工程实践（需人工决定是否引入）；每次改 feature/方块注册后提交生成文件即可。另注意：datagen provider 引用未注册对象会失败，先跑一次确保 JSON 全量产出。

## 6. 面向 MTurrets 的事实清单（供 HITL 决策）

1. 机制选择没有悬念：**Register+`BiomeModifier`（`add_features`/`underground_ores`）** 是 1.21.1 官方推荐组合；`#minecraft:is_overworld` 标签即可覆盖全部主世界群系（含洞穴）。
2. 每个矿 = 2 个动态注册表条目（configured + placed）+ 1 个 biome modifier 条目 + 方块注册面（Block、BlockItem、loot、blockstate、block/item model、lang、纹理）。
3. 矿石目标替换用原版标签 `#minecraft:stone_ore_replaceables` / `#minecraft:deepslate_ore_replaceables`（可二选一或并列；deepslate 目标用于深色变体，如无需深色变体可只挂 stone 标签）。
4. 参数起点（照抄原版即可跑）：煤 size 17 / count 20–30 / trapezoid 0–192 或 uniform 136–320；铜 size 10–20 / count 16 / trapezoid −16–112。Mindustry 簇状 = 增大 size、压低 count、提高 discard、收窄高度带（4.4 节）。
5. 已生成区块不补矿（原版机制），新世界才干净测试。
6. 每矿一个 placed feature 只挂一个 modifier（cycle 警告）；不用动原版任意文件。

## 7. 来源列表

**NeoForge 官方文档（1.21.1 版本锁定页）**
- Biome Modifiers：https://docs.neoforged.net/docs/1.21.1/worldgen/biomemodifier/
- Registries（含 Datapack Registries 与 Data Generation for Datapack Registries）：https://docs.neoforged.net/docs/1.21.1/concepts/registries/
- Resources / Data Generation（runData、provider 表、CLI 参数）：https://docs.neoforged.net/docs/1.21.1/resources/
- Blocks（含 BlockItem 单独注册）：https://docs.neoforged.net/docs/1.21.1/blocks/
- Items：https://docs.neoforged.net/docs/1.21.1/items/
- Loot Tables（Datagen / BlockLootSubProvider）：https://docs.neoforged.net/docs/1.21.1/resources/server/loottables/
- Model Datagen：https://docs.neoforged.net/docs/1.21.1/resources/client/models/datagen/
- I18n（LanguageProvider）：https://docs.neoforged.net/docs/1.21.1/resources/client/i18n/

**NeoForge 源码（分支 `1.21.1`，https://github.com/neoforged/NeoForge/tree/1.21.1）**
- `src/main/java/net/neoforged/neoforge/common/world/BiomeModifiers.java`（AddFeaturesBiomeModifier / RemoveFeaturesBiomeModifier 等内建 record 与 JSON 格式 javadoc）
- `src/main/java/net/neoforged/neoforge/registries/NeoForgeRegistries.java`（`Keys.BIOME_MODIFIER_SERIALIZERS`、`Keys.BIOME_MODIFIERS`）
- `src/main/java/net/neoforged/neoforge/common/data/DatapackBuiltinEntriesProvider.java`

**Minecraft Wiki**
- Configured feature：https://minecraft.wiki/w/Configured_feature
- Placed feature：https://minecraft.wiki/w/Placed_feature
- Ore (feature)（blob/scatter、size→块数表、主世界分布表、existing chunks: No）：https://minecraft.wiki/w/Ore_(feature)

**一手原版数据（1.21.1 官方客户端 jar 直读，路径见正文）**
- `data/minecraft/worldgen/configured_feature/ore_{coal,coal_buried,copper_small,copper_large,iron,iron_small,ancient_debris_large,ancient_debris_small}.json`
- `data/minecraft/worldgen/placed_feature/ore_{coal_upper,coal_lower,copper,copper_large,iron_middle,iron_small,iron_upper,diamond}.json`
- `data/minecraft/loot_table/blocks/copper_ore.json`；`data/minecraft/tags/block/{stone_ore_replaceables,deepslate_ore_replaceables}.json`；`data/minecraft/tags/worldgen/biome/is_overworld.json`
- `assets/minecraft/{blockstates/copper_ore.json, models/block/copper_ore.json, models/item/copper_ore.json, lang/en_us.json}`

---

- 本报告未运行任何构建/测试/格式化命令；仓库文件除本报告外未做任何改动。