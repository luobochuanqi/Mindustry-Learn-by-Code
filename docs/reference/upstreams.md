# 上游参考源码

`ref/mindustry` 与 `ref/create` 是只读浅克隆,已 gitignore,不入库;唯一例外是入库的 `ref/INDEX.md`。不用 submodule 的代价核算见 ADR-0001 §4。

```bash
# Mindustry:Java 源码 + 美术部件,排除历史、音乐、字体与地图
git clone --depth 1 --filter=blob:none --sparse https://github.com/Anuken/Mindustry ref/mindustry
git -C ref/mindustry sparse-checkout set core/src core/assets-raw core/assets/sprites core/assets/baseparts

# Create:锁定与本项目相同的 MC 1.21.1 分支
git clone --depth 1 --branch mc1.21.1/dev --filter=blob:none https://github.com/Creators-of-Create/Create ref/create

# Flywheel:官方分支名是 1.21.1/dev(不是 1.21.1);api/lib 层在 common/src/{api,lib},后端与实现在 backend/impl
git clone --depth 1 --branch 1.21.1/dev --filter=blob:none https://github.com/Engine-Room/Flywheel ref/flywheel

# Mindustry wiki:整站 HTML ~200MB 不可全拉。权威数据是 mkdocs 的 search_index.json(全文分段索引,~0.6MB);
# ref/wiki 只留手写 markdown(docs/,~0.2MB,已去图),原始索引存 ref/wiki-md/search_index.json,
# ref/wiki-md/ 为索引转写的 1166 页 markdown(~2.4MB)。重建:
git clone --depth 1 --no-checkout https://github.com/MindustryGame/wiki ref/wiki
git -C ref/wiki fetch --depth 1 origin gh-pages
# 从 origin/gh-pages 单独取 search/search_index.json 转 markdown;从 origin/master 取 docs/ 并删 docs/images
```

刷新:mindustry/create/flywheel 用 `git -C ref/<repo> fetch --depth 1 origin <branch> && git -C ref/<repo> reset --hard origin/<branch>`;wiki 是无历史快照,重新拉即可。

## 当前锚点

| 目录               | 上游                       | 分支          | commit    | 体积 |
| ------------------ | -------------------------- | ------------- | --------- | ---- |
| `ref/mindustry`    | Anuken/Mindustry           | master        | `dc32943` | 23 MB |
| `ref/create`       | Creators-of-Create/Create  | mc1.21.1/dev  | `0924e93` | 64 MB |
| `ref/flywheel`     | Engine-Room/Flywheel       | 1.21.1/dev    | `cbbc490` | 6 MB |
| `ref/wiki`         | MindustryGame/wiki         | master(docs/) | `5eed587` | 0.2 MB |
| `ref/wiki-md`      | MindustryGame/wiki(gh-pages `search_index.json` 转写) | — | `55b9450` | 2.4 MB |

`ref/wiki-md/` 按 wiki 目录分块(blocks / units / items / liquids / statuses / logic / modding / datapatches …),每页是"标题+压缩正文"的 markdown,数值表可读;原始 `search_index.json` 同目录保留,需要精确片段时直接查它。注意 mkdocs 搜索索引对个别"类参考"页(如 `Modding Classes/*`)只收录了片段,这类内容以 `ref/mindustry` 源码为准。
## 资产索引

找原版美术或改贴图 → `ref/INDEX.md`(两棵资产树的目录计量、region 命名契约、打包链、未克隆清单)。

## 许可证边界

- **Mindustry** 整体 GPL-3.0(单一根 `LICENSE`,素材不单独授权);本模组同为 GPL-3.0,可逐字复制。出处以 `textures/ATTRIBUTION.md` 的整体衍生声明覆盖,不逐份列举
- **Mindustry wiki**(MindustryGame/wiki)内容许可未在该仓库单独声明(无 LICENSE 文件,【一手】核实);只作调研引用、不复制进分发的 jar,风险为零。`ref/flywheel` 为 MIT,可自由借鉴

## 在线参考文档
Mindustry Javadoc <https://mindustrygame.github.io/docs/>;Wiki 已本地化(`ref/wiki-md/`),在线版 <https://mindustrygame.github.io/wiki/>(炮台页 `wiki/blocks/349-duo/` 等)。Flywheel 在线文档 <https://flywheel.engine-room.dev/>(其 `docs/` 目录即源,已含在 `ref/flywheel`)。
