# Mindustry Asset Index

Agent-facing map of `ref/mindustry/core/assets-raw/` and `ref/mindustry/core/assets/`, as of commit `dc32943`.

`ref/mindustry` is a **sparse-checkout**: `core/src`, `core/assets-raw`, `core/assets/sprites`, `core/assets/baseparts` only. Before concluding a Mindustry asset "doesn't exist", check *Not on disk*.

## The mental model

`assets-raw/` is the **design source** (unpacked PNG parts). `assets/` is what the game **consumes at runtime**. Neither contains the shipped atlas: it is generated at build time and never committed, so lookups in game code (`Core.atlas.find("<region>")`) resolve against a flat name space this clone cannot reproduce. Region name is a flat basename — which makes the naming contract below global, not per-directory.

## core/assets-raw (2,308 files)

| Path | Files | Contents |
| --- | --- | --- |
| `sprites/blocks/` | 1,661 | Block art, 18 domain dirs: `environment/` 644, `turrets/` 143, `distribution/` 129, `production/` 127, `logic/` 75, `props/` 74, `power/` 74, `liquid/` 68, `payload/` 58, `drills/` 50, `units/` 49, `walls/` 43, `fire/` 40, `storage/` 34, `defense/` 31, `sandbox/` 9, `campaign/` 9, `extra/` 4 |
| `sprites/units/` | 288 | Unit sprites + `weapons/` 96, `neoplasm/` 7 |
| `sprites/ui/` | 130 | UI chrome; own `pack.json` |
| `sprites/effects/` | 52 | Hit/explosion FX; whitespace kept (see `ignoredWhitespaceStrings`) |
| `sprites/items/` | 33 | `item-<name>.png` (22) + `liquid-<name>.png` (11). Only these two prefixes; fluids share the dir |
| `sprites/statuses/` | 20 | Status effect icons |
| `sprites/rubble/` | 11 | Scorch/debris; own `pack.json` |
| `sprites/shapes/` | 5 | Beam/particle shapes |
| `sprites/teams/` | 4 | Team markers |
| `sprites/pack.json` | 1 | Root packer config |
| `icons/` | 94 | Menu/editor icons. Marginal for block modding |
| `fontgen/` | 9 | IcoMoon `config.json` (~250 glyphs) + FontForge `merge.pe`. Irrelevant unless rebranding a font |

### Turret art comes in two shapes

Flat pairs — `<name>.png` plus `<name>-heat.png` (heat overlay shown while firing). Sizes are per-artwork, not a footprint formula: the 27 flat files span 32×32 (`arc`, `duo` base), 64×64, 96×96 and 128×128 (`meltdown`, `spectre`).

Part directories — separated pieces the game can move. `turrets/duo/` holds `duo.png` (base), `duo-barrel-l.png`, `duo-barrel-r.png`, `duo-preview.png`, all 32×32. The barrel split drives Duo's recoil: `DrawTurret` + `RegionPart("-barrel-l"/"-barrel-r")` in `content/Blocks.java`. Turrets root is 27 flat files + 14 part dirs. Suffix vocabulary across the tree: `-heat`, `-outline`, `-top`, `-bottom`, `-rotator`, `-<n>`.

### Naming contract

`sprites/pack.json`: `combineSubdirectories: true` + `flattenPaths: true` collapse every subdirectory into one flat region namespace in a 4096×4096 atlas. **Consequence: basenames must be unique across the entire `sprites/` tree** — two `glow.png` in different folders collide. `duplicatePadding` extends edge pixels; `stripWhitespaceCenter: true` trims surrounding transparency; `ignoredWhitespaceStrings: ["effects/"]` opts FX art out of trimming.

Overrides cascade, most specific wins: `sprites/rubble/pack.json`, `sprites/ui/pack.json`, and `sprites/blocks/environment/pack.json`, which drops to 2048×2048 with `stripWhitespaceCenter: false` so floor/ore autotiles keep edge pixels.

### Packing

Upstream runs `gradlew tools:pack`, writing `assets-raw/sprites_out/`. `core/build.gradle` consumes it: `dependsOn ":tools:pack"` (line 351), `from 'assets-raw/sprites_out'` (line 360). The `tools/` module is *not on disk* here, so packing is not reproducible from this clone.

## core/assets (225 files on disk)

| Path | Files | Contents |
| --- | --- | --- |
| `baseparts/` | 210 | `.msch` = zlib-compressed **schematics** (magic `msch\x01` then `78 9c`), read by `mindustry.ai.BaseRegistry` to lay out campaign AI bases; 86 named (`strong_duos.msch`) + 124 numeric. Despite the directory name, these are not skeletal animation |
| `sprites/` | 13 | Standalone runtime textures kept out of the atlas: `space.png` (1.1 MB), `rays.png`, `clouds.png`, `fog.png`, `noise.png`, `noiseAlpha.png`, `distortAlpha.png`, `caustics.png`, `error.png`, `logo.png` (768×107), `schematic-background.png`, `planets/serpulo.png`, `planets/erekir.png` |
| `contributors` | 1 | Plain-text credits, shown by `AboutDialog` |
| `logicids.dat` | 1 | Logic instruction name→ID map for `mindustry.logic.GlobalVars` |

Nothing here is reusable art for a block mod. `baseparts/` answers "what does a campaign base contain"; `sprites/` is engine-internal. Turret, material, fluid and machine art lives in `assets-raw/sprites/`.

## Not on disk

Excluded by sparse-checkout (~62 MB upstream): `assets/music` 28.5 MB/16 files, `assets/sounds` 7.1/205 `.ogg`, `assets/maps` 8.0, `assets/bundles` 7.1/36, `assets/fonts` 6.9, `assets/cubemaps` 1.1, `assets/icons` 0.9, plus `planets/`, `shaders/`, `bloomshaders/`, `cursors/`, `scripts/`, the `tools/` module, and any generated `sprites_out/`.

```bash
git -C ref/mindustry sparse-checkout add core/assets/sounds
```

MTurrets ships no `.ogg` and no fonts, so these stay out by design.
