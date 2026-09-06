package xyz.luobo.mturrets.common.turrets

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mturrets.common.ModBlockEntityTypes
import xyz.luobo.mturrets.common.ModItems
import xyz.luobo.mturrets.common.ModSounds
import xyz.luobo.mturrets.common.items.Materials
import xyz.luobo.mturrets.core.combat.AmmoType
import xyz.luobo.mturrets.core.combat.BulletFx
import xyz.luobo.mturrets.core.combat.BulletType
import xyz.luobo.mturrets.core.combat.TurretBE
import xyz.luobo.mturrets.core.combat.TurretSpec

/**
 * Scatter 防空炮(#34):2×2 角锚点蓝图(+X/+Z 生长),蓝图管线对 2×2 的首验(ADR-0003)。
 * 数值表出自 #26 决议 + Mindustry 原值按 ADR-0009 量纲(时长 ×⅓、弹速 ×⅜、距离 ÷8):
 * range 24 格｜reload 6t 双发点射(shots=2/shotDelay 2t,点射共用一次扣账)｜inaccuracy 17°｜
 * shootCone 35°｜rotateSpeed 45°/t(上游 15×3,ADR-0009)｜cap 100 单位｜Health 200｜对空-only(FlyingMob/恼鬼/烈焰人,高度无关)｜
 * 不吃电。铅 1→4 单位/3 伤/1.575 格每 t/溅射 40.5 @ 2 格;玻璃 1→5 单位/3 伤/1.5 格每 t/
 * 溅射 45 @ 2.5 格 + 6 破片(5 伤/1.125 格每 t/7t)/reloadMultiplier 0.8;
 * 弹色取 Mindustry Items 配色(lead 8c7fa9/metaglass ebeef5)。Coolant 与 Duo 同语义(×1.5/10 mB 每发)。
 * 过强按 ADR-0006 分工在代码表调,不改骨架形状。
 */
class ScatterTurretBE(pos: BlockPos, state: BlockState) :
    TurretBE(
        ModBlockEntityTypes.SCATTER_BLOCK_ENTITY.get(), pos, state,
        TurretSpec(
            range = 24f,
            reloadTicks = 6f,
            shots = 2,
            shotDelay = 2f,
            inaccuracy = 17f,
            shootCone = 35f,
            rotateSpeed = 45f, // #75:上游 15×3 (ADR-0009 角速度×3)
            size = 2,
            targetAir = true,
            targetGround = false,
            maxAmmo = 100,
            health = 200,
            coolantReloadMultiplier = 1.5f,
            coolantPerShot = 10,
            // #63:中段炮管几何 y 10..16(轴 13/16=0.8125)相对结构中心层 0.5 → +0.3125
            muzzleHeight = 0.3125,
            ammoTypes = listOf(
                AmmoType(
                    item = ModItems.getMaterial(Materials.LEAD).get(),
                    bullet = BulletType(
                        damage = 3f,
                        speed = 1.575f,
                        lifetime = 20,
                        color = 0x8C7FA9,
                        bulletSize = 0.5f,
                        splashDamage = 40.5f,
                        splashRadius = 2f,
                        // 命中 FX(#62):flak 爆团(固定三色调色板,忽略 hitColor),对齐上游 FlakBulletType
                        hitEffect = BulletFx.FLAK
                    ),
                    unitMultiplier = 4
                ),
                AmmoType(
                    item = ModItems.getMaterial(Materials.METAGLASS).get(),
                    bullet = BulletType(
                        damage = 3f,
                        speed = 1.5f,
                        lifetime = 20,
                        color = 0xEBEEF5,
                        bulletSize = 0.5f,
                        reloadMultiplier = 0.8f,
                        splashDamage = 45f,
                        splashRadius = 2.5f,
                        // 命中 FX(#62):flak 爆团(固定三色调色板),对齐上游 FlakBulletType;
                        // 到寿消散用 RING(上游玻璃 despawnEffect = hitBulletColor)
                        hitEffect = BulletFx.FLAK,
                        despawnEffect = BulletFx.RING,
                        fragCount = 6,
                        fragBullet = BulletType(
                            damage = 5f,
                            speed = 1.125f,
                            lifetime = 7,
                            color = 0xEBEEF5,
                            bulletSize = 0.5f,
                            // 破片命中 FX(#62):上游 frag 默认 hitBulletSmall,对位 SMALL
                            hitEffect = BulletFx.SMALL
                        )
                    ),
                    unitMultiplier = 5
                )
            ),
            shootSound = { ModSounds.SHOOT_SCATTER.get() }
        )
    )