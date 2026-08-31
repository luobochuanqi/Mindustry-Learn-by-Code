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

### Changed

- Bullets now render as proper two-layer shells (Mindustry's back/front sprite pair, tinted per ammo type) that face their direction of flight instead of flat white squares, and visibly shrink away right before despawning instead of popping out of existence.

### Removed

- Arc and Meltdown are gone: the pre-renovation turret implementations and their shared framework were deleted. Power-fed turrets return with a rebuild on the new skeleton.

- Debug and example items, plus the throwaway test screens, are gone from the creative tab.

### Fixed

- Faces of blocks next to turrets, machines and their member blocks are no longer culled away (structure blocks are non-occluding, like Create's multiblock parts).
- Jade tooltips now show the structure's model for multiblock member blocks: members register no item, so picking and the Jade icon proxy to the anchor's item stack (Create-style master proxy), instead of rendering an empty placeholder.
- Fast bullets can no longer slip clean through a thin or crossing monster between ticks: hit detection sweeps the whole tick's path. Splash damage and fragments erupt from the point where the bullet actually touches its target (a fast round no longer splashes a block behind it), and a bullet stopped by a wall still settles everything it passed through that tick.
- Scatter's air-only targeting now uses a flight-capability test (FlyingMob, no-gravity mobs, blazes) instead of "not touching the ground": hovering ghasts and phantoms are engaged even while low over the ground, and grounded monsters that jump or get knocked into the air no longer draw the anti-air turret's fire.
