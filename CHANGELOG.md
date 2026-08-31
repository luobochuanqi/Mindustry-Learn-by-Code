# Changelog

All notable player-facing changes to MTurrets are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
[SemVer](https://semver.org/).

## [Unreleased]

Current builds target Minecraft 1.21.1 on NeoForge 21.1.248.

### Added

**Turrets**

- **Duo** — twin-barrel turret fed with items. Copper is the ammo (1 item = 2 rounds, 9 damage per hit). Holds 100 rounds, reaches 20 blocks, tracks ground and air, and leads moving targets instead of shooting where they are now. Pouring water in speeds up reload by half.
- Turrets fire on hostile mobs; passive creatures, pets and players are never targeted.

**Production**

- **Kiln** — bakes lead + sand into metaglass (1:1:1) while powered. A blackout pauses it and keeps its progress; power returns and it resumes where it stopped. Breaking it returns what was inside.
- **Power Node** — stores energy and relays it to adjacent nodes, machines and turrets at up to 100 FE per tick, so a reactor can feed a turret across a base. Accepts and supplies standard NeoForge energy.
- 20 Mindustry materials (copper, lead, silicon, metaglass, graphite, surge alloy, plastanium, phase fabric, …) and 11 fluids, all under the MTurrets creative tab.

**Interface**

- Jade tooltips now cover every phase-1 structure: turrets show ammo and structure health, the Kiln shows progress and its power-supply ratio during brownouts, the Battery shows stored FE, and the Drill shows its ore lock, column reserves and buffer contents. Hovering a member block of a multiblock shows the same info as its anchor.

### Removed

- Arc and Meltdown are gone: the pre-renovation turret implementations and their shared framework were deleted. Power-fed turrets return with a rebuild on the new skeleton.

- Debug and example items, plus the throwaway test screens, are gone from the creative tab.
