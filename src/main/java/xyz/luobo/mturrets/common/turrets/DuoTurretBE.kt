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
 * Duo 炮台(重建,#31):1×1 蓝图锚点,新范式骨架首实例(ADR-0009)。
 * 数值全表出自 #28 决议:range 20 格｜reload 6.7t｜自制铜 1→2 单位、扳机 1 单位、cap 100｜
 * 9 伤｜弹速 0.94 格/t、寿命 20t｜双管交替(shots=1,totalShots%2 换管,纯视觉)｜inaccuracy 2°｜
 * shootCone 15°｜rotateSpeed 30°/t｜Health 250｜Coolant ×1.5、10 mB/发。
 * 过强按 ADR-0006 分工在代码表调,不改骨架形状。
 */
class DuoTurretBE(pos: BlockPos, state: BlockState) :
    TurretBE(
        ModBlockEntityTypes.DUO_BLOCK_ENTITY.get(), pos, state,
        TurretSpec(
            range = 20f,
            reloadTicks = 6.7f,
            shots = 1,
            shotDelay = 0f,
            inaccuracy = 2f,
            shootCone = 15f,
            rotateSpeed = 30f,
            maxAmmo = 100,
            health = 250,
            coolantReloadMultiplier = 1.5f,
            coolantPerShot = 10,
            // #63:炮管几何 y 7..11(轴 9/16=0.5625)相对块中心 0.5 → +0.0625
            muzzleHeight = 0.0625,
            ammoTypes = listOf(
                AmmoType(
                    item = ModItems.getMaterial(Materials.COPPER).get(),
                    bullet = BulletType(
                        damage = 9f,
                        speed = 0.94f,
                        lifetime = 20,
                        color = 0xFFD37F,
                        bulletSize = 0.5f,
                        // 命中 FX(#62):渐隐环,色取上游 hitColor = back 铜橙 #d39169(非渲染色 #FFD37F)
                        hitColor = 0xD39169,
                        // 到寿消散上游铜弹 = hitBulletColor(与命中同选型),对位 RING
                        despawnEffect = BulletFx.RING
                    ),
                    unitMultiplier = 2
                )
            ),
            shootSound = { ModSounds.SHOOT_DUO.get() }
        )
    )