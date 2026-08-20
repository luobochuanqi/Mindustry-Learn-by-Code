# AGENTS.md

MTurrets 是 Kotlin NeoForge 模组(MC 1.21.1),把 Mindustry 的炮台、机器与材料玩法移植进 Minecraft。仓库处于 2026 年全面翻新期:决策见 `docs/adr/0001-renovation-decisions.md`,执行清单在 issue tracker(#5)。

## 翻新基线

翻新范围内已确认、但尚未落地的决策由翻新执行统一推进;其他任务保持代码现状:

- mod id / 命名空间 / 包名仍是 `mindustry`——迁移到 `mturrets` 未执行,代码里见到 `mindustry` 是现状,不是错误
- 构建仍是 Groovy DSL,依赖仍含 LDLib2/JEI 与调试项——裁剪与 Kotlin DSL 迁移未执行
- `src/generated/resources` 仍被 gitignore 忽略、CI 不跑 `runData`——入库未执行

## 构建与运行

- `gradlew build` 构建;`gradlew runData` 重新生成 datagen 产物
- `gradlew runClient` 冒烟客户端;`gradlew runGameTestServer` 跑 NeoForge 游戏测试(测试用例尚未建立)
- JDK 21;settings.gradle 中的国内镜像仓库是既有环境,保留

## 语言与命名

- 仓库工作语言:中文(注释、提交信息、文档)。玩家可见文本走语言文件:zh_cn.json 手工维护,en_us.json 由 datagen 生成
- 新增玩家可见内容需要补 zh_cn.json,并用 `runData` 刷新 en_us.json
- 领域词汇以 `CONTEXT.md` 词表为准,不另造同义词

## Agent skills

### Issue tracker

Issues and specs live as GitHub issues, operated via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Five canonical triage labels, each string equal to its name: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `CONTEXT.md` plus `docs/adr/` at the repo root. See `docs/agents/domain.md`.