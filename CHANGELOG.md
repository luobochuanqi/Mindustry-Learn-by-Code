# Changelog

All notable player-facing changes to MTurrets are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
[SemVer](https://semver.org/).

## [Unreleased]

Current builds target Minecraft 1.21.1 on NeoForge 21.1.248.

### Added

**Turrets**

- **Duo** — twin-barrel turret fed with items. Copper is cheap and quick (5 damage), iron pierces up to two extra targets at 70% damage, gold hits hardest (12 damage) and flies fastest. Holds 30 rounds, reaches 20 blocks, tracks ground and air, and leads moving targets instead of shooting where they are now.
- **Arc** — instant lightning strike, 12 damage, 15-block range, draws power instead of ammo.
- **Meltdown** — continuous red beam, 60 damage per second, 25-block range, passes through every entity in its path without losing strength and sets them alight. Needs a warm-up before it fires and burns 300 FE/s, so it wants a real power network behind it.
- Turrets fire on hostile mobs and players; passive creatures and pets are never targeted.

**Production**

- **Kiln** — bakes lead + sand into metaglass (1:1:1) while powered. A blackout pauses it and keeps its progress; power returns and it resumes where it stopped. Breaking it returns what was inside.
- **Power Node** — stores energy and relays it to adjacent nodes, machines and turrets at up to 100 FE per tick, so a reactor can feed a turret across a base. Accepts and supplies standard NeoForge energy.
- 20 Mindustry materials (copper, lead, silicon, metaglass, graphite, surge alloy, plastanium, phase fabric, …) and 11 fluids, all under the MTurrets creative tab.

**Interface**

- Jade tooltips now show a turret's ammo or energy, the Kiln's progress and energy, and a Power Node's charge — just look at the block.

### Changed

- Arc and Meltdown are now actually visible — they used to render as nothing.

### Removed

- Debug and example items, plus the throwaway test screens, are gone from the creative tab.
