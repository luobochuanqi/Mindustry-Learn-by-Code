package xyz.luobo.mturrets

import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.core.Direction
import net.minecraft.world.level.GameType
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.InteractionHand
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.ClipContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.CollisionContext
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.items.ItemHandlerHelper
import net.minecraft.world.level.material.Fluids
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import xyz.luobo.mturrets.common.ModBlocks
import xyz.luobo.mturrets.common.ModEntities
import xyz.luobo.mturrets.common.ModItems
import xyz.luobo.mturrets.common.items.Materials
import xyz.luobo.mturrets.common.machines.drill.DrillBE
import xyz.luobo.mturrets.common.power.BatteryBE
import xyz.luobo.mturrets.common.machines.kiln.KilnBE
import xyz.luobo.mturrets.common.turrets.DuoTurretBE
import xyz.luobo.mturrets.common.turrets.ScatterTurretBE
import xyz.luobo.mturrets.core.combat.BulletType
import xyz.luobo.mturrets.core.structure.StructuralBlock

/**
 * GameTest 回归套件
 * 只断言外部行为(伤害/能量/合成产出),不触碰内部字段(spec #5 测试决策)
 *
 * 全部用例使用原版 empty3x3 模板,坐标约束在 3x3 内
 *
 * 对空用例(#55)各自独占 batch(批名=方法名):批次严格串行、批内并发,独占批运行时
 * 世界内无邻居炮台/悬空靶,根除共享世界互扰(邻例炮台抢靶、流弹烧弹药)。
 * 新对空用例沿用此约定;与邻居无关的本例自持校准(如 hoverGhast 的区块边界防盲)不受此影响。
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(MTurrets.MOD_ID)
object ModGameTests {

    /** 给目标防火(幽灵/恶魂/僵尸,避免日光/环境燃烧干扰伤害断言) */
    private fun fireproof(entity: net.minecraft.world.entity.LivingEntity) {
        entity.addEffect(MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 600, 0))
    }

    /** 向方块位置的能量能力注入能量,返回实际注入量 */
    private fun injectEnergy(helper: GameTestHelper, pos: BlockPos, amount: Int): Int {
        val cap = helper.level.getCapability(
            Capabilities.EnergyStorage.BLOCK,
            helper.absolutePos(pos),
            null
        ) ?: throw IllegalStateException("no energy capability at $pos")
        return cap.receiveEnergy(amount, false)
    }

    /** 向方块位置的物品能力插入物品 */
    private fun insertItem(helper: GameTestHelper, pos: BlockPos, slot: Int, stack: ItemStack) {
        val cap = helper.level.getCapability(
            Capabilities.ItemHandler.BLOCK,
            helper.absolutePos(pos),
            null
        ) ?: throw IllegalStateException("no item capability at $pos")
        val rest = cap.insertItem(slot, stack, false)
        if (!rest.isEmpty && rest.count == stack.count) {
            helper.fail("item rejected at slot $slot: $stack")
        }
    }

    /** 模拟玩家在方块位手持物品右键(新 Duo 供弹走块交互面,无 capability 注入口)。 */
    private fun mockUseOn(helper: GameTestHelper, pos: BlockPos, stack: ItemStack): ItemInteractionResult {
        val state = helper.getBlockState(pos)
        val player = helper.makeMockPlayer(GameType.SURVIVAL)
        val hit = BlockHitResult(Vec3.ZERO, Direction.UP, helper.absolutePos(pos), false)
        return state.useItemOn(stack, helper.level, player, InteractionHand.MAIN_HAND, hit)
    }

    /** 统计某物品在 pos 附近的掉落实体总数(拆机折回断言用)。 */
    private fun countDrops(helper: GameTestHelper, pos: BlockPos, item: Item): Int =
        helper.getEntities(EntityType.ITEM, pos, 4.0)
            .sumOf { (it as? ItemEntity)?.item?.let { s -> if (s.`is`(item)) s.count else 0 } ?: 0 }

    /**
     * 以真实玩家拆方块语义拆掉结构(第 3 参 drop=true 走 loot → 控制器物品掉落):
     * GameTestHelper.destroyBlock 固定 drop=false,只触发 onRemove 散落而 loot 不跑。
     */
    private fun breakForDrops(helper: GameTestHelper, pos: BlockPos) {
        helper.level.destroyBlock(helper.absolutePos(pos), true)
    }

    /** ① 伤害 + 弹尽停火 + 拆机零折回:1 铜(2 单位)命中 2 发(9×2=18),僵尸余 2 HP 后不再掉血。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 300)
    fun duoShootsExactDamageThenRunsDry(helper: GameTestHelper) {
        val turretPos = BlockPos(1, 1, 1)
        helper.setBlock(turretPos, ModBlocks.DUO_BLOCK.get())

        val copper = ModItems.getMaterial(Materials.COPPER).get()
        val stack = ItemStack(copper, 1)
        if (mockUseOn(helper, turretPos, stack) != ItemInteractionResult.CONSUME) {
            helper.fail("ammo load must consume the held stack")
        }
        if (!stack.isEmpty) helper.fail("1 copper must be fully loaded")

        val zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, BlockPos(1, 1, 2))
        fireproof(zombie)

        helper.runAfterDelay(60) {
            if (zombie.health != 2f) {
                helper.fail("exactly 2 shots of 9 damage expected (zombie at 2 HP), got ${zombie.health}")
            }
        }
        helper.runAfterDelay(150) {
            // 2 单位打尽:100 tick 窗口内不再掉血
            if (zombie.health != 2f) {
                helper.fail("turret must stop firing after ammo runs out, zombie at ${zombie.health}")
            }
        }
        helper.runAfterDelay(160) { breakForDrops(helper, turretPos) }
        helper.succeedWhen {
            val copper = ModItems.getMaterial(Materials.COPPER).get()
            if (countDrops(helper, turretPos, copper) != 0) {
                helper.fail("empty magazine must refund 0 copper on teardown")
            }
        }
    }

    /** ② 部分装弹(#46,取代 #31 整堆拒收):空仓收 51 铜(102 单位 > cap 100)按整件向下取整——
     * 收 50、手持留 1、弹仓满;再 1 铜(已满)拒收。倍率 2,折算单位 100/2=50 件恰满。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 100)
    fun duoPartiallyLoadsAmmoOverCapacity(helper: GameTestHelper) {
        val turretPos = BlockPos(1, 1, 1)
        helper.setBlock(turretPos, ModBlocks.DUO_BLOCK.get())

        val copper = ModItems.getMaterial(Materials.COPPER).get()

        val over = ItemStack(copper, 51)
        if (mockUseOn(helper, turretPos, over) != ItemInteractionResult.CONSUME || over.count != 1) {
            helper.fail("over-cap stack must be partially loaded, 51 -> 50 accepted, 1 left, count=${over.count}")
        }
        val extra = ItemStack(copper, 1)
        if (mockUseOn(helper, turretPos, extra) != ItemInteractionResult.FAIL || extra.count != 1) {
            helper.fail("full magazine must reject any more ammo")
        }
        helper.succeed()
    }
    /** ⑥ 自动化供弹(#73):溜槽/漏斗经标准 IItemHandler capability 向炮台供弹,按整件折算单位、
     * 部分收退回余量;与手持右键装弹同一折算口径。Magazine 单位账分账、LIFO。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 100)
    fun duoAutoFeedsAmmoViaCapability(helper: GameTestHelper) {
        val turretPos = BlockPos(1, 1, 1)
        helper.setBlock(turretPos, ModBlocks.DUO_BLOCK.get())
        val copper = ModItems.getMaterial(Materials.COPPER).get()
        // 经能力注入 51 铜(倍率 2→unit cap 100):整件折算收 50、退回 1(与 #46 手持口径一致)
        val cap = helper.level.getCapability(
            Capabilities.ItemHandler.BLOCK, helper.absolutePos(turretPos), null
        )!!
        val rest = cap.insertItem(0, ItemStack(copper, 51), false)
        if (rest.count != 1) helper.fail("51 copper must load 50 (2×50=100 units), leave 1, got ${rest.count}")

        // 已满:再投整件拒收(全退回)
        val over = cap.insertItem(0, ItemStack(copper, 4), false)
        if (over.count != 4) helper.fail("full magazine must reject all, got ${over.count}")

        // 不可退弹(ADR-0009 / Mindustry ItemTurret.removeStack=0):能力提取恒返回空
        if (!cap.extractItem(0, 64, false).isEmpty) helper.fail("turret must not allow ammo extraction")
        helper.succeed()
    }

    /** ⑦ 自动化供弹拒收非弹药(#73):能力槽面只收本炮台弹药,其它物品整件退回。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 100)
    fun duoAutoFeedRejectsNonAmmo(helper: GameTestHelper) {
        val turretPos = BlockPos(1, 1, 1)
        helper.setBlock(turretPos, ModBlocks.DUO_BLOCK.get())
        // Duo 只吃铜;铅/沙均拒
        val cap = helper.level.getCapability(
            Capabilities.ItemHandler.BLOCK, helper.absolutePos(turretPos), null
        )!!
        val lead = ModItems.getMaterial(Materials.LEAD).get()
        if (cap.insertItem(0, ItemStack(lead, 3), false).count != 3) {
            helper.fail("non-ammo lead must be fully rejected")
        }
        if (cap.insertItem(0, ItemStack(Items.SAND, 2), false).count != 2) {
            helper.fail("non-ammo sand must be fully rejected")
        }
        helper.succeed()
    }

    /** ⑧ 自动化供弹 Scatter 成员格路由(#73):经成员格能力注入口也折算入锚点弹仓。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 100)
    fun scatterMemberAutoFeedsAmmo(helper: GameTestHelper) {
        val anchorPos = BlockPos(1, 1, 1)
        val memberPos = BlockPos(2, 1, 2)
        helper.setBlock(anchorPos, ModBlocks.SCATTER.get())
        val lead = ModItems.getMaterial(Materials.LEAD).get()

        // 成员格能力注入口在结构成型(延迟一 tick)后才路由回锚点;等成型后注入
        helper.runAfterDelay(5) {
            val cap = helper.level.getCapability(
                Capabilities.ItemHandler.BLOCK, helper.absolutePos(memberPos), null
            )
            if (cap == null) {
                helper.fail("scatter member must expose item capability after forming")
                return@runAfterDelay
            }
            // 10 铅(倍率 4→unit cap 100):整件折算 10×4=40 单位,满额收
            val rest = cap.insertItem(0, ItemStack(lead, 10), false)
            if (!rest.isEmpty) helper.fail("member feed must fully load 10 lead, left ${rest.count}")
            helper.succeed()
        }
    }

    /** ③a 只打 Monster:僵尸与牛同框,牛不掉血、僵尸掉血。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 200)
    fun duoLeavesFriendlyMobsUntouched(helper: GameTestHelper) {
        val turretPos = BlockPos(1, 1, 1)
        helper.setBlock(turretPos, ModBlocks.DUO_BLOCK.get())
        mockUseOn(helper, turretPos, ItemStack(ModItems.getMaterial(Materials.COPPER).get()))

        val zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, BlockPos(1, 1, 2))
        fireproof(zombie)
        val cow = helper.spawnWithNoFreeWill(EntityType.COW, BlockPos(0, 1, 2))
        val cowHealth = cow.health

        helper.runAfterDelay(100) {
            if (cow.health != cowHealth) {
                helper.fail("friendly cow must not be damaged, hp=${cow.health}")
            }
            if (zombie.health >= 20f) {
                helper.fail("zombie must be damaged while cow stands untouched")
            }
            helper.succeed()
        }
    }

    /** ③b 仅有友好生物:牛(最近的实体)永不受伤;弹仓 2 单位被射程内(共享测试世界)的怪物尽数消耗 → 折回 0。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 200)
    fun duoDoesNotFireAtFriendlyOnly(helper: GameTestHelper) {
        val turretPos = BlockPos(1, 1, 1)
        helper.setBlock(turretPos, ModBlocks.DUO_BLOCK.get())
        mockUseOn(helper, turretPos, ItemStack(ModItems.getMaterial(Materials.COPPER).get()))
        val cow = helper.spawnWithNoFreeWill(EntityType.COW, BlockPos(1, 1, 2))
        val cowHealth = cow.health

        helper.runAfterDelay(80) {
            if (cow.health != cowHealth) {
                helper.fail("nearest friendly must never be shot, hp=${cow.health}")
            }
            breakForDrops(helper, turretPos)
        }
        helper.succeedWhen {
            // 测试世界内 33 用例并行、结构间距 8 格而 Duo 射程 20:射程内必有他例怪,
            // 本单位只放牛时弹仓仍会被(远处的)怪物合法消耗——本断言锁「无一发落在牛身上」。
            val copper = ModItems.getMaterial(Materials.COPPER).get()
            if (countDrops(helper, turretPos, copper) != 0) {
                helper.fail("2 units spent on non-friendly targets in 80 ticks, expected 0 copper refund")
            }
        }
    }
    /**
     * ③d 视线闸门(#43):墙后僵尸无视线→炮台不得锁定;拆墙恢复视线后→该僵尸被锁定。
     *
     * 布局(4×4 模板,确定性):所有元素在结构内 [0,3]³,barrier 隔离壳在 [-1,4] 外缘,不干扰。
     *  - 炮台 (2,1,0),Duo(1×1,对空+对地),中心 (2.5,1,0.5);
     *  - 墙 (2,1,2):枪口(muzzle,中心外推外接圆半径≈1.46)→僵尸胸口的视线射线穿过 (2,1,2)
     *    → 墙在时 BLOCK(无视线);拆墙后该格 air → 射线直达僵尸 → MISS(有视线);
     *  - 僵尸 (2,1,3)(结构内缘,非 barrier 壳):no-free-will 固定不动,巨额生命防他例流弹击杀;
     *  - 地板 (2,0,0)/(2,0,3) 让炮台与僵尸落脚不悬空;
     *  - 断言按僵尸身份(===/!==)隔离:共享世界 44 用例并行,他例怪在他例 barrier 壳内,
     *    本僵尸是本炮台唯一在结构内的 Monster,无论墙在与否都应是「最近有效目标」——墙在时被
     *    LOS 排除,墙去时纳入。
     */
    @JvmStatic
    @GameTest(template = "empty4x4", timeoutTicks = 200)
    fun duoDoesNotAcquireThroughWalls(helper: GameTestHelper) {
        val turretPos = BlockPos(2, 1, 0)
        helper.setBlock(BlockPos(2, 0, 0), Blocks.STONE) // 炮台脚下地板
        helper.setBlock(BlockPos(2, 0, 3), Blocks.STONE) // 僵尸脚下地板
        helper.setBlock(turretPos, ModBlocks.DUO_BLOCK.get())
        mockUseOn(helper, turretPos, ItemStack(ModItems.getMaterial(Materials.COPPER).get()))
        val turret = helper.level.getBlockEntity(helper.absolutePos(turretPos)) as DuoTurretBE
        helper.setBlock(BlockPos(2, 1, 2), Blocks.STONE) // 墙:muzzle(z≈1.46)→僵尸胸口的射线穿过此格
        val zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, BlockPos(2, 1, 3))
        fireproof(zombie)
        zombie.getAttribute(Attributes.MAX_HEALTH)?.baseValue = 1_000_000.0
        zombie.health = 1_000_000f

        fun diag(): String {
            val center = helper.absolutePos(turretPos).center
            val chest = zombie.position().add(0.0, zombie.boundingBox.getYsize() * 0.5, 0.0)
            val hz = Vec3(chest.x - center.x, 0.0, chest.z - center.z).normalize()
            val muzzle = center.add(hz.x * 0.957, 0.44 - 0.5, hz.z * 0.957)
            val clip = helper.level.clip(
                ClipContext(muzzle, chest, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty())
            )
            val tgt = turret.target
            return "wall@(2,1,2)=${helper.getBlockState(BlockPos(2, 1, 2)).isAir}" +
                " zbCell@(2,1,3)=${helper.getBlockState(BlockPos(2, 1, 3)).isAir}" +
                " clip=${clip?.type} zAlive=${zombie.isAlive}" +
                " target=${if (tgt == null) "null" else tgt::class.simpleName}"
        }

        // 阶段 1(墙在):LOS 被挡 → 不得锁定;随后拆墙
        helper.runAfterDelay(60) {
            if (turret.target === zombie) helper.fail("wall-up [${diag()}]")
            helper.setBlock(BlockPos(2, 1, 2), Blocks.AIR)
        }
        // 阶段 2(拆墙后):视线恢复 → 该僵尸被锁定
        helper.runAfterDelay(90) {
            if (turret.target !== zombie) helper.fail("wall-down [${diag()}]")
            helper.succeed()
        }
    }

    /** ③c 扫掠命中(漏怪修复):3 格/tick 弹一发——tick 末静态盒必漏(整段跨过僵尸),起止并集盒必中;
     * 伤害恰一次(20-6=14)兼防双结算。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 100)
    fun bulletSweptProbeHitsMidTickTarget(helper: GameTestHelper) {
        helper.setBlock(BlockPos(1, 0, 1), Blocks.STONE)
        val zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, BlockPos(1, 1, 1))
        fireproof(zombie)
        // 清甲 + 地板:消除甲减免与重力两个无关变量,伤害断言保持精确(20-6=14)
        zombie.getAttribute(Attributes.ARMOR)?.baseValue = 0.0
        val bullet = helper.spawn(ModEntities.TURRET_BULLET.get(), BlockPos(1, 2, -1))
        bullet.init(BulletType(damage = 6f, speed = 3f, lifetime = 3), Vec3(0.0, 0.0, 1.0))

        helper.runAfterDelay(10) {
            if (zombie.health != 14f) {
                helper.fail("fast bullet must hit its mid-tick target exactly once, hp=${zombie.health}")
            }
            helper.succeed()
        }
    }
    /** ④a 无水基准:20 铜(40 单位),60 tick 窗口内 8 发(6.7t 装填)→ 拆机折回 16 铜。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 150)
    fun duoDryReloadBaseline(helper: GameTestHelper) {
        val turretPos = BlockPos(1, 1, 1)
        helper.setBlock(turretPos, ModBlocks.DUO_BLOCK.get())
        mockUseOn(helper, turretPos, ItemStack(ModItems.getMaterial(Materials.COPPER).get(), 20))
        val zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, BlockPos(1, 1, 2))
        fireproof(zombie)
        // 990 吸收甲:扛住整个观察窗口不倒地,窗口只数发数不数死亡
        zombie.addEffect(MobEffectInstance(MobEffects.ABSORPTION, 20 * 600, 199))

        helper.runAfterDelay(60) { breakForDrops(helper, turretPos) }
        helper.succeedWhen {
            val copper = ModItems.getMaterial(Materials.COPPER).get()
            val refunded = countDrops(helper, turretPos, copper)
            if (refunded != 16) {
                helper.fail("dry baseline: 8 shots expected, refund=${refunded}")
            }
        }
    }

    /** ④b 满水窗口对照:60 tick 内 12 发(6.7t ÷ 1.5)→ 拆机折回 14 铜,发数多于无水基准。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 150)
    fun duoCoolantAcceleratesReload(helper: GameTestHelper) {
        val turretPos = BlockPos(1, 1, 1)
        helper.setBlock(turretPos, ModBlocks.DUO_BLOCK.get())
        mockUseOn(helper, turretPos, ItemStack(ModItems.getMaterial(Materials.COPPER).get(), 20))
        fillKilnTank(helper, turretPos)
        val zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, BlockPos(1, 1, 2))
        fireproof(zombie)
        zombie.addEffect(MobEffectInstance(MobEffects.ABSORPTION, 20 * 600, 199))

        helper.runAfterDelay(60) { breakForDrops(helper, turretPos) }
        helper.succeedWhen {
            val copper = ModItems.getMaterial(Materials.COPPER).get()
            val refunded = countDrops(helper, turretPos, copper)
            if (refunded >= 16) {
                helper.fail("cooled reload must outpace dry baseline in 60 ticks, refund=$refunded (dry=16)")
            }
            if (refunded != 14) {
                helper.fail("coolant window: 12 shots expected, refund=$refunded")
            }
        }
    }

    /** ⑥ 1×1 蓝图管线:成型 BE 成立且不盖任何成员格(偏移集只含锚点)。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 100)
    fun duoFormsAs1x1Anchor(helper: GameTestHelper) {
        val turretPos = BlockPos(1, 1, 1)
        helper.setBlock(turretPos, ModBlocks.DUO_BLOCK.get())
        helper.succeedWhen {
            helper.assertBlockPresent(ModBlocks.DUO_BLOCK.get(), turretPos)
            if (helper.level.getBlockEntity(helper.absolutePos(turretPos)) !is DuoTurretBE) {
                helper.fail("duo anchor must host its block entity")
            }
            for (offset in listOf(BlockPos(1, 0, 0), BlockPos(0, 0, 1), BlockPos(1, 0, 1))) {
                if (!helper.getBlockState(turretPos.offset(offset)).isAir) {
                    helper.fail("1x1 duo must not form members at $offset")
                }
            }
        }
    }

    // 电网(#30,ADR-0007):电池充放、节点链输送、棕停断电、断中格分裂

    /** 电池对外充放各限 200 FE/次,容量 80,000;节点零储能(能力缺席)。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 100)
    fun batteryChargesAndDischargesThroughCapability(helper: GameTestHelper) {
        val batteryPos = BlockPos(1, 1, 1)
        helper.setBlock(batteryPos, ModBlocks.BATTERY.get())

        if (storedEnergy(helper, batteryPos) != 0) helper.fail("battery must start empty")
        if (injectEnergy(helper, batteryPos, 5000) != 200) {
            helper.fail("battery charge must be capped at 200 per operation")
        }
        if (storedEnergy(helper, batteryPos) != 200) helper.fail("battery should hold 200 after capped charge")

        val cap = helper.level.getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(batteryPos), null)
            ?: throw IllegalStateException("no battery energy capability")
        if (cap.extractEnergy(5000, false) != 200) {
            helper.fail("battery discharge must be capped at 200 per operation")
        }
        if (storedEnergy(helper, batteryPos) != 0) helper.fail("battery should be empty after capped discharge")

        // 容量:分拍注入直到拒收,总容量恰为 80,000(#27 决议)
        val injected = injectEnergyUpTo(helper, batteryPos, 80_000 + 1000)
        if (injected != 80_000) helper.fail("battery capacity must be 80,000 FE, got $injected")

        // 节点纯导线:读不到储能(#30 验收:capability 缺席)
        val nodePos = BlockPos(0, 1, 1)
        helper.setBlock(nodePos, ModBlocks.POWER_NODE.get())
        if (helper.level.getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(nodePos), null) != null) {
            helper.fail("power node must expose no energy storage")
        }
        helper.succeed()
    }

    /** 电池 → 节点链 → 窑炉:能量经图流到机器,单电池扣账恰为配方耗能(500 → 余 100)。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 300)
    fun gridPowersKilnThroughNodeChain(helper: GameTestHelper) {
        val batteryPos = BlockPos(0, 1, 1)
        val node1 = BlockPos(1, 1, 1)
        val node2 = BlockPos(2, 1, 1)
        val kilnPos = BlockPos(2, 1, 2)
        helper.setBlock(batteryPos, ModBlocks.BATTERY.get())
        helper.setBlock(node1, ModBlocks.POWER_NODE.get())
        helper.setBlock(node2, ModBlocks.POWER_NODE.get())
        helper.setBlock(kilnPos, ModBlocks.KILN.get())

        insertItem(helper, kilnPos, 0, ItemStack(ModItems.getMaterial(Materials.LEAD).get()))
        insertItem(helper, kilnPos, 1, ItemStack(Items.SAND))
        fillKilnTank(helper, kilnPos)
        if (injectEnergyUpTo(helper, batteryPos, 600) < 600) helper.fail("battery refused injection")

        helper.succeedWhen {
            if (countItem(helper, kilnPos, ModItems.getMaterial(Materials.METAGLASS).get()) < 1) {
                helper.fail("kiln did not craft from grid power")
            }
            // 单电池:500 FE 经节点链被窑炉拉走,余量恰为 100(对外 capability 可观测)
            val left = storedEnergy(helper, batteryPos)
            if (left != 100) helper.fail("expected 100 FE left in battery after 500 FE craft, got $left")
        }
    }

    /** 低电量电池:满速 6 tick 耗尽 → 断电停、进度冻结不倒退、supplyRatio 归零;补电后续转。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 400)
    fun gridBrownoutStallsKilnWithProgressKept(helper: GameTestHelper) {
        val batteryPos = BlockPos(0, 1, 1)
        val kilnPos = BlockPos(1, 1, 1)
        helper.setBlock(batteryPos, ModBlocks.BATTERY.get())
        helper.setBlock(kilnPos, ModBlocks.KILN.get())

        insertItem(helper, kilnPos, 0, ItemStack(ModItems.getMaterial(Materials.LEAD).get()))
        insertItem(helper, kilnPos, 1, ItemStack(Items.SAND))
        fillKilnTank(helper, kilnPos)
        if (injectEnergyUpTo(helper, batteryPos, 30) < 30) helper.fail("battery refused injection")

        helper.runAfterDelay(200) {
            if (countItem(helper, kilnPos, ModItems.getMaterial(Materials.METAGLASS).get()) > 0) {
                helper.fail("low-power battery must not complete a craft")
            }
            if (storedEnergy(helper, batteryPos) != 0) {
                helper.fail("battery must be fully drained, got ${storedEnergy(helper, batteryPos)}")
            }
            val kiln = helper.getBlockEntity(kilnPos) as? KilnBE
            if (kiln == null || kiln.supplyRatio > 0f) {
                helper.fail("kiln must report supplyRatio 0 while cut off from power, got ${kiln?.supplyRatio}")
            }
            // 窑炉起始本地 0 FE:每 tick 全 5 FE 走电网,电池 30 FE 撑 6 tick 后归零,
            // 再申 1 次得 0 → 断电;进度冻结在 7(本地缓冲 500 不影响,起始即空)
            if (kiln?.progressPercent != 7) {
                helper.fail("kiln progress must freeze at 7%, got ${kiln?.progressPercent}")
            }
            if (injectEnergyUpTo(helper, batteryPos, 2000) < 2000) helper.fail("battery refused refill")
        }
        helper.succeedWhen {
            if (countItem(helper, kilnPos, ModItems.getMaterial(Materials.METAGLASS).get()) < 1) {
                helper.fail("kiln did not resume after battery refill")
            }
        }
    }

    /** 断中格:两侧图当场分离——被隔离电池冻结,另一侧电池继续供窑炉完成两炉。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 300)
    fun breakingNodeSplitsGrid(helper: GameTestHelper) {
        val batteryA = BlockPos(0, 1, 1)
        val nodeM = BlockPos(1, 1, 1)
        val batteryB = BlockPos(2, 1, 1)
        val kilnPos = BlockPos(2, 1, 2)
        helper.setBlock(batteryA, ModBlocks.BATTERY.get())
        helper.setBlock(nodeM, ModBlocks.POWER_NODE.get())
        helper.setBlock(batteryB, ModBlocks.BATTERY.get())
        helper.setBlock(kilnPos, ModBlocks.KILN.get())

        insertItem(helper, kilnPos, 0, ItemStack(ModItems.getMaterial(Materials.LEAD).get(), 4))
        insertItem(helper, kilnPos, 1, ItemStack(Items.SAND, 4))
        fillKilnTank(helper, kilnPos)
        if (injectEnergyUpTo(helper, batteryA, 600) < 600) helper.fail("battery A refused injection")
        if (injectEnergyUpTo(helper, batteryB, 1100) < 1100) helper.fail("battery B refused injection")

        var aAfterSplit = -1
        helper.runAfterDelay(50) {
            // 合网阶段:两电池并网供能,按余量比例分摊——双池同时下降
            val a = storedEnergy(helper, batteryA)
            val b = storedEnergy(helper, batteryB)
            if (a >= 600 || b >= 1100) helper.fail("grid must drain both batteries together, A=$a B=$b")
            helper.destroyBlock(nodeM)
            aAfterSplit = storedEnergy(helper, batteryA)
        }
        helper.runAfterDelay(230) {
            if (aAfterSplit < 0) helper.fail("split never happened")
            if (storedEnergy(helper, batteryA) != aAfterSplit) {
                helper.fail(
                    "isolated battery must freeze after split: " +
                        "before=${aAfterSplit} after=${storedEnergy(helper, batteryA)}"
                )
            }
            if (countItem(helper, kilnPos, ModItems.getMaterial(Materials.METAGLASS).get()) < 2) {
                helper.fail("kiln must keep crafting from the surviving grid after the split")
            }
            helper.succeed()
        }
    }

    /** 电源(#49,调试):常量 333,320 FE/t 生产,经电网维持窑炉满速——棕停 ratio 恒 1、持续产出。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 300)
    fun sourceSustainsKilnAtFullSpeed(helper: GameTestHelper) {
        val sourcePos = BlockPos(0, 1, 1)
        val kilnPos = BlockPos(1, 1, 1)
        helper.setBlock(sourcePos, ModBlocks.POWER_SOURCE.get())
        helper.setBlock(kilnPos, ModBlocks.KILN.get())

        insertItem(helper, kilnPos, 0, ItemStack(ModItems.getMaterial(Materials.LEAD).get(), 4))
        insertItem(helper, kilnPos, 1, ItemStack(Items.SAND, 4))
        fillKilnTank(helper, kilnPos)

        helper.succeedWhen {
            val kiln = helper.getBlockEntity(kilnPos) as? KilnBE
            if (countItem(helper, kilnPos, ModItems.getMaterial(Materials.METAGLASS).get()) < 1) {
                helper.fail("kiln did not craft from source power")
            }
            // 产量 333,320 FE/t 远超窑炉 ~5 FE/t 需求:无棕停,supplyRatio 恒 1
            if (kiln == null || kiln.supplyRatio < 1f) {
                helper.fail("kiln must run at full speed under source, got ratio=${kiln?.supplyRatio}")
            }
        }
    }

    /** 电源 + 空闲电池:无耗电方时,生产按空余容量比例充入电池直至满电。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 300)
    fun sourceChargesIdleBattery(helper: GameTestHelper) {
        val sourcePos = BlockPos(0, 1, 1)
        val batteryPos = BlockPos(1, 1, 1)
        helper.setBlock(sourcePos, ModBlocks.POWER_SOURCE.get())
        helper.setBlock(batteryPos, ModBlocks.BATTERY.get())

        if (storedEnergy(helper, batteryPos) != 0) helper.fail("battery must start empty")

        helper.succeedWhen {
            // 图内充电瞬时(镜像扣账),333,320 FE/t 一 tick 即灌满 80,000
            if (storedEnergy(helper, batteryPos) < BatteryBE.ENERGY_CAPACITY) {
                helper.fail("battery must reach full from source, got ${storedEnergy(helper, batteryPos)}")
            }
        }
    }

    // 燃烧发电机(#56):煤燃料槽 + 燃烧计时 → 20 FE/t 入电网(真实生产面)

    private val generatorCoal: net.minecraft.world.item.Item
        get() = ModItems.getMaterial(Materials.COAL).get()

    /** 燃料充足时,燃烧发电机(20 FE/t)经电网维持窑炉满速——棕停 ratio 恒 1、持续产出。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 300)
    fun generatorSustainsKilnAtFullSpeed(helper: GameTestHelper) {
        val genPos = BlockPos(0, 1, 1)
        val kilnPos = BlockPos(1, 1, 1)
        helper.setBlock(genPos, ModBlocks.COMBUSTION_GENERATOR.get())
        helper.setBlock(kilnPos, ModBlocks.KILN.get())

        insertItem(helper, kilnPos, 0, ItemStack(ModItems.getMaterial(Materials.LEAD).get(), 4))
        insertItem(helper, kilnPos, 1, ItemStack(Items.SAND, 4))
        fillKilnTank(helper, kilnPos)
        insertItem(helper, genPos, 0, ItemStack(generatorCoal, 8))

        helper.succeedWhen {
            val kiln = helper.getBlockEntity(kilnPos) as? KilnBE
            if (countItem(helper, kilnPos, ModItems.getMaterial(Materials.METAGLASS).get()) < 1) {
                helper.fail("kiln did not craft from generator power")
            }
            // 产量 20 FE/t 远超窑炉 ~5 FE/t 需求:无棕停,supplyRatio 恒 1
            if (kiln == null || kiln.supplyRatio < 1f) {
                helper.fail("kiln must run at full speed under generator, got ratio=${kiln?.supplyRatio}")
            }
        }
    }

    /** 发电机 + 空闲电池:无耗电方时,20 FE/t 生产按空余容量充入电池(实测 ~1600 FE/80 tick)。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 300)
    fun generatorChargesIdleBattery(helper: GameTestHelper) {
        val genPos = BlockPos(0, 1, 1)
        val batteryPos = BlockPos(1, 1, 1)
        helper.setBlock(genPos, ModBlocks.COMBUSTION_GENERATOR.get())
        helper.setBlock(batteryPos, ModBlocks.BATTERY.get())

        if (storedEnergy(helper, batteryPos) != 0) helper.fail("battery must start empty")
        insertItem(helper, genPos, 0, ItemStack(generatorCoal, 8))

        helper.succeedWhen {
            val e = storedEnergy(helper, batteryPos)
            // 20 FE/t × 80 tick ≈ 1600;取 1200 为下限(留 20 tick 余量),证明在充电
            if (e < 1200) helper.fail("generator must charge the battery at 20 FE/t, got $e")
        }
    }

    /** 燃料耗尽(40 tick/煤)后电网无生产、无电池 → 加工中的窑炉棕停 supplyRatio < 1。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 300)
    fun generatorBrownoutWhenFuelRunsOut(helper: GameTestHelper) {
        val genPos = BlockPos(0, 1, 1)
        val kilnPos = BlockPos(1, 1, 1)
        helper.setBlock(genPos, ModBlocks.COMBUSTION_GENERATOR.get())
        helper.setBlock(kilnPos, ModBlocks.KILN.get())

        insertItem(helper, kilnPos, 0, ItemStack(ModItems.getMaterial(Materials.LEAD).get(), 4))
        insertItem(helper, kilnPos, 1, ItemStack(Items.SAND, 4))
        fillKilnTank(helper, kilnPos)
        insertItem(helper, genPos, 0, ItemStack(generatorCoal, 1)) // 1 煤 = 40 tick = 800 FE < 一轮 500 FE×2

        helper.runAfterDelay(80) {
            val kiln = helper.getBlockEntity(kilnPos) as? KilnBE
            // 1 煤 40 tick 烧尽,窑炉(100 tick/轮)必然卡在加工中 → 断供棕停
            if (kiln == null || kiln.supplyRatio >= 1f) {
                helper.fail("kiln must brownout after fuel runs out, ratio=${kiln?.supplyRatio}")
            }
            helper.succeed()
        }
    }

    /** 燃料槽仅收煤:右键放铜整拒(手持不动),放煤收下。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 100)
    fun generatorRejectsNonCoal(helper: GameTestHelper) {
        val genPos = BlockPos(1, 1, 1)
        helper.setBlock(genPos, ModBlocks.COMBUSTION_GENERATOR.get())

        val copper = ItemStack(ModItems.getMaterial(Materials.COPPER).get(), 5)
        if (mockUseOn(helper, genPos, copper) == ItemInteractionResult.CONSUME || copper.count != 5) {
            helper.fail("generator must reject non-coal, count=${copper.count}")
        }
        val coal = ItemStack(generatorCoal, 5)
        if (mockUseOn(helper, genPos, coal) != ItemInteractionResult.CONSUME || coal.count != 0) {
            helper.fail("generator must accept coal, count=${coal.count}")
        }
        helper.succeed()
    }

    /** 拆机散落未燃储备:8 煤入槽,点燃弹 1(燃烧中,离开槽面),拆机散落余下 7。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 100)
    fun generatorScattersUnburntCoalOnTeardown(helper: GameTestHelper) {
        val genPos = BlockPos(1, 1, 1)
        helper.setBlock(genPos, ModBlocks.COMBUSTION_GENERATOR.get())
        insertItem(helper, genPos, 0, ItemStack(generatorCoal, 8))

        helper.runAfterDelay(5) { breakForDrops(helper, genPos) } // 让发电机点燃(弹 1)后拆机
        helper.succeedWhen {
            // 8 入槽 − 1 燃烧中 = 7 未燃散落(燃烧中煤不掉落,spec grill 定案)
            if (countDrops(helper, genPos, generatorCoal) != 7) {
                helper.fail("7 unburnt coal must scatter (burning coal excluded), got ${countDrops(helper, genPos, generatorCoal)}")
            }
        }
    }

    // 生产:窑炉(#33 新范式:蓝图管线 1×1 + datapack 配方 + 水/能量)

    /** 经流体能力向内罐灌水(GameTestHelper 无桶物品交互,走能力注入面) */
    private fun fillKilnTank(helper: GameTestHelper, pos: BlockPos) {
        val cap = helper.level.getCapability(
            Capabilities.FluidHandler.BLOCK,
            helper.absolutePos(pos),
            null
        ) ?: throw IllegalStateException("no fluid capability at $pos")
        val filled = cap.fill(FluidStack(Fluids.WATER, 1000), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE)
        if (filled <= 0) throw IllegalStateException("kiln tank refused water")
    }

    /** 窑炉注入限速 200 FE/t,循环注满 */
    private fun injectEnergyUpTo(helper: GameTestHelper, pos: BlockPos, amount: Int): Int {
        var injected = 0
        while (injected < amount) {
            val got = injectEnergy(helper, pos, amount - injected)
            if (got <= 0) break
            injected += got
        }
        return injected
    }

    private fun countItem(helper: GameTestHelper, pos: BlockPos, item: net.minecraft.world.item.Item): Int {
        val cap = helper.level.getCapability(
            Capabilities.ItemHandler.BLOCK,
            helper.absolutePos(pos),
            null
        ) ?: return 0
        var count = 0
        for (slot in 0 until cap.slots) {
            val stack = cap.getStackInSlot(slot)
            if (stack.`is`(item)) count += stack.count
        }
        return count
    }

    private fun storedEnergy(helper: GameTestHelper, pos: BlockPos): Int =
        helper.level.getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(pos), null)?.energyStored ?: -1

    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 300)
    fun kilnCraftsMetaglass(helper: GameTestHelper) {
        val kilnPos = BlockPos(1, 1, 1)
        helper.setBlock(kilnPos, ModBlocks.KILN.get())

        // 配方(生成 JSON):1 铅 + 1 原版沙 → 1 金属玻璃,100 tick,500 FE;水 50 mB/轮
        // 本地缓冲 = 一轮能耗(500,#49 grill 修订):注入 500 恰好撑满一轮
        insertItem(helper, kilnPos, 0, ItemStack(ModItems.getMaterial(Materials.LEAD).get()))
        insertItem(helper, kilnPos, 1, ItemStack(Items.SAND))
        fillKilnTank(helper, kilnPos)
        val injected = injectEnergyUpTo(helper, kilnPos, 500)
        if (injected != 500) helper.fail("kiln must accept 500 FE (one craft's energy): $injected")

        helper.succeedWhen {
            if (countItem(helper, kilnPos, ModItems.getMaterial(Materials.METAGLASS).get()) < 1) {
                helper.fail("kiln produced no metaglass")
            }
            // 能量按配方总耗扣除:500 - 500 = 0
            val left = storedEnergy(helper, kilnPos)
            if (left != 0) helper.fail("expected 0 FE left after 500 FE recipe, got $left")
        }
    }

    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 300)
    fun kilnStallsWithoutWaterThenResumes(helper: GameTestHelper) {
        val kilnPos = BlockPos(1, 1, 1)
        helper.setBlock(kilnPos, ModBlocks.KILN.get())

        insertItem(helper, kilnPos, 0, ItemStack(ModItems.getMaterial(Materials.LEAD).get()))
        insertItem(helper, kilnPos, 1, ItemStack(Items.SAND))
        injectEnergyUpTo(helper, kilnPos, 500)

        // 缺水:加工从不启动;40 tick 后确认零产出且能量分毫未耗(未开工)
        helper.runAfterDelay(40) {
            if (countItem(helper, kilnPos, ModItems.getMaterial(Materials.METAGLASS).get()) > 0) {
                helper.fail("kiln produced without water")
            }
            if (storedEnergy(helper, kilnPos) != 500) {
                helper.fail("kiln consumed energy without water: ${storedEnergy(helper, kilnPos)}")
            }
            fillKilnTank(helper, kilnPos)
        }
        helper.succeedWhen {
            if (countItem(helper, kilnPos, ModItems.getMaterial(Materials.METAGLASS).get()) < 1) {
                helper.fail("kiln did not resume after water refill")
            }
        }
    }

    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 300)
    fun kilnStallsWithoutEnergyKeepsProgress(helper: GameTestHelper) {
        val kilnPos = BlockPos(1, 1, 1)
        helper.setBlock(kilnPos, ModBlocks.KILN.get())

        insertItem(helper, kilnPos, 0, ItemStack(ModItems.getMaterial(Materials.LEAD).get()))
        insertItem(helper, kilnPos, 1, ItemStack(Items.SAND))
        fillKilnTank(helper, kilnPos)

        // 无能量:开工后因缺电停摆;30 tick 后零产出、水仍在罐(能量均摊未到结算不扣水扣料)
        helper.runAfterDelay(30) {
            if (countItem(helper, kilnPos, ModItems.getMaterial(Materials.METAGLASS).get()) > 0) {
                helper.fail("kiln produced without energy")
            }
            injectEnergyUpTo(helper, kilnPos, 600)
        }
        helper.succeedWhen {
            if (countItem(helper, kilnPos, ModItems.getMaterial(Materials.METAGLASS).get()) < 1) {
                helper.fail("kiln did not resume after energy injection")
            }
        }
    }

    /** 运转 hum 门控缝(#57):窑炉 isRunning 随加工态翻转——空闲 false、加工中 true、结算后 false。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 300)
    fun kilnIsRunningReflectsProcessing(helper: GameTestHelper) {
        val kilnPos = BlockPos(1, 1, 1)
        helper.setBlock(kilnPos, ModBlocks.KILN.get())
        val kiln = helper.getBlockEntity(kilnPos) as KilnBE
        if (kiln.isRunning) helper.fail("idle empty kiln must not be running")
        // 喂料开工:1 铅 + 1 沙 + 水 + 能量 → 进入加工(配方 100 tick)
        insertItem(helper, kilnPos, 0, ItemStack(ModItems.getMaterial(Materials.LEAD).get()))
        insertItem(helper, kilnPos, 1, ItemStack(Items.SAND))
        fillKilnTank(helper, kilnPos)
        injectEnergyUpTo(helper, kilnPos, 600)
        helper.runAfterDelay(50) {
            if (!(helper.getBlockEntity(kilnPos) as KilnBE).isRunning) {
                helper.fail("fed kiln must be running mid-process")
            }
        }
        helper.runAfterDelay(200) {
            if ((helper.getBlockEntity(kilnPos) as KilnBE).isRunning) {
                helper.fail("kiln must stop running after the recipe settles")
            }
            helper.succeed()
        }
    }

    /** 运转 hum 门控缝(#57):钻头 isRunning 随开采态翻转——采口无矿 false、有矿且 Buffer 空 true、Buffer 满 false。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 150)
    fun drillIsRunningReflectsMining(helper: GameTestHelper) {
        val orePos = BlockPos(1, 1, 1)
        val drillPos = BlockPos(1, 2, 1)
        helper.setBlock(drillPos, ModBlocks.DRILL.get())
        helper.setBlock(orePos, Blocks.STONE) // 采口无矿
        val drill = helper.getBlockEntity(drillPos) as DrillBE
        helper.runAfterDelay(5) {
            if (drill.isRunning) helper.fail("drill over stone (no ore) must not be running")
        }
        helper.setBlock(orePos, ModBlocks.ORE_COPPER.get()) // 补矿 → 空闲低频重扫(20t)后开工
        helper.runAfterDelay(35) {
            if (!drill.isRunning) helper.fail("drill over ore with empty buffer must be running")
        }
        // 塞满 Buffer → 满载停转 → isRunning 转 false
        repeat(20) { insertItem(helper, drillPos, it, ItemStack(ModItems.getMaterial(Materials.COPPER).get(), 64)) }
        helper.succeedWhen {
            if ((helper.getBlockEntity(drillPos) as DrillBE).isRunning) {
                helper.fail("drill with full buffer must stop running")
            }
        }
    }

    /** 客户端同步缝(#57):钻头 getUpdateTag 携带 isRunning(hum 客户端据此淡入淡出)。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 60)
    fun drillUpdateTagCarriesRunningState(helper: GameTestHelper) {
        val drillPos = BlockPos(1, 2, 1)
        val orePos = BlockPos(1, 1, 1)
        helper.setBlock(drillPos, ModBlocks.DRILL.get())
        helper.setBlock(orePos, ModBlocks.ORE_COPPER.get())
        val drill = helper.getBlockEntity(drillPos) as DrillBE
        val tag = drill.getUpdateTag(helper.level.registryAccess())
        if (!tag.contains("drill_is_running")) {
            helper.fail("drill update tag must carry drill_is_running for client hum sync")
        }
        helper.succeed()
    }
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 100)
    fun breakingKilnScattersBuffer(helper: GameTestHelper) {
        val kilnPos = BlockPos(1, 1, 1)
        helper.setBlock(kilnPos, ModBlocks.KILN.get())

        insertItem(helper, kilnPos, 0, ItemStack(ModItems.getMaterial(Materials.LEAD).get(), 4))
        helper.runAfterDelay(5) { helper.destroyBlock(kilnPos) }

        helper.succeedWhen {
            val lead = ModItems.getMaterial(Materials.LEAD).get()
            val drops = helper.getEntities(EntityType.ITEM, kilnPos, 3.0)
            if (drops.none { (it as? net.minecraft.world.entity.item.ItemEntity)?.item?.`is`(lead) == true }) {
                helper.fail("kiln did not scatter stored lead on teardown")
            }
        }
    }

    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 100)
    fun kilnFormsAs1x1Anchor(helper: GameTestHelper) {
        // 1×1 管线语义由真窑炉承载(#32 脚手架 blueprintForms1x1 退役至此)
        val kilnPos = BlockPos(1, 1, 1)
        helper.setBlock(kilnPos, ModBlocks.KILN.get())
        helper.succeedWhen {
            helper.assertBlockPresent(ModBlocks.KILN.get(), kilnPos)
            if (helper.level.getBlockEntity(helper.absolutePos(kilnPos)) !is KilnBE) {
                helper.fail("kiln anchor must host its block entity")
            }
            // 空偏移集:不盖任何成员格
            for (offset in listOf(BlockPos(1, 0, 0), BlockPos(0, 0, 1), BlockPos(1, 0, 1))) {
                if (!helper.getBlockState(kilnPos.offset(offset)).isAir) {
                    helper.fail("1x1 kiln must not form members at $offset")
                }
            }
        }
    }

    // 蓝图管线(ADR-0003,#32):2×2 成型

    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 100)
    fun blueprintForms2x2(helper: GameTestHelper) {
        val anchorPos = BlockPos(0, 1, 0)
        helper.setBlock(anchorPos, ModBlocks.TEST_STRUCTURE_ANCHOR_2X2.get())
        helper.succeedWhen {
            for (offset in listOf(BlockPos(1, 0, 0), BlockPos(0, 0, 1), BlockPos(1, 0, 1))) {
                val memberPos = anchorPos.offset(offset)
                val state = helper.getBlockState(memberPos)
                if (!state.`is`(ModBlocks.TEST_STRUCTURAL.get())) {
                    helper.fail("member not formed at $offset: $state")
                }
                if (helper.getLevel().getBlockEntity(helper.absolutePos(memberPos)) != null) {
                    helper.fail("member at $offset must not host a block entity")
                }
            }
            // 编码偏移可重算回锚点(成员零持久化引用)
            val diagonal = helper.getBlockState(anchorPos.offset(BlockPos(1, 0, 1)))
            val decodedAnchor = anchorPos.offset(BlockPos(1, 0, 1)).subtract(
                StructuralBlock.decodeOffset(diagonal)
            )
            if (decodedAnchor != anchorPos) {
                helper.fail("encoded offsets do not resolve anchor: $decodedAnchor")
            }
        }
    }

    // 蓝图管线:放置校验失败 → 回滚 + 退控制器物品

    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 100)
    fun blueprintRejectedWhenBlocked(helper: GameTestHelper) {
        val anchorPos = BlockPos(0, 1, 0)
        helper.setBlock(anchorPos.offset(BlockPos(1, 0, 0)), Blocks.STONE) // 挡住 +X 成员格
        helper.setBlock(anchorPos, ModBlocks.TEST_STRUCTURE_ANCHOR_2X2.get())
        helper.succeedWhen {
            if (!helper.getBlockState(anchorPos).isAir) {
                helper.fail("blocked placement must roll back the anchor")
            }
            if (!helper.getBlockState(anchorPos.offset(BlockPos(0, 0, 1))).isAir) {
                helper.fail("no partial members may form")
            }
            // 无玩家环境 → 非创造退还路径:锚点位掉落控制器物品
            val anchorItem = ModBlocks.TEST_STRUCTURE_ANCHOR_2X2.get().asItem()
            val drops = helper.getEntities(EntityType.ITEM, anchorPos, 3.0)
            if (drops.none { (it as? net.minecraft.world.entity.item.ItemEntity)?.item?.`is`(anchorItem) == true }) {
                helper.fail("controller item not refunded after rejected placement")
            }
        }
    }

    // 蓝图管线:成员破坏代理 → 整体拆除 + 掉控制器物品 + 内容物散落

    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 100)
    fun destroyMemberTearsDownStructure(helper: GameTestHelper) {
        val anchorPos = BlockPos(0, 1, 0)
        val memberPos = anchorPos.offset(BlockPos(1, 0, 1))
        helper.setBlock(anchorPos, ModBlocks.TEST_STRUCTURE_ANCHOR_2X2.get())
        helper.runAfterDelay(5) {
            // 成员格能力路由:经成员插入 → 存入锚点 BE 缓冲
            insertItem(helper, memberPos, 0, ItemStack(ModItems.getMaterial(Materials.LEAD).get(), 4))
            helper.destroyBlock(memberPos)
        }
        helper.succeedWhen {
            val cells = listOf(anchorPos, BlockPos(1, 0, 0), BlockPos(0, 0, 1), BlockPos(1, 0, 1))
                .map { anchorPos.offset(it) }
            if (cells.any { !helper.getBlockState(it).isAir }) {
                helper.fail("structure not fully torn down after member break")
            }
            val anchorItem = ModBlocks.TEST_STRUCTURE_ANCHOR_2X2.get().asItem()
            val lead = ModItems.getMaterial(Materials.LEAD).get()
            val drops = helper.getEntities(EntityType.ITEM, anchorPos, 4.0)
            if (drops.none { (it as? net.minecraft.world.entity.item.ItemEntity)?.item?.`is`(anchorItem) == true }) {
                helper.fail("controller item not dropped")
            }
            if (drops.none { (it as? net.minecraft.world.entity.item.ItemEntity)?.item?.`is`(lead) == true }) {
                helper.fail("buffer contents not scattered on teardown")
            }
        }
    }

    // 蓝图管线:直接拆锚点 → 成员同步清空

    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 100)
    fun destroyAnchorClearsMembers(helper: GameTestHelper) {
        val anchorPos = BlockPos(0, 1, 0)
        helper.setBlock(anchorPos, ModBlocks.TEST_STRUCTURE_ANCHOR_2X2.get())
        helper.runAfterDelay(5) { helper.destroyBlock(anchorPos) }
        helper.succeedWhen {
            if (!helper.getBlockState(anchorPos.offset(BlockPos(1, 0, 1))).isAir) {
                helper.fail("members not cleared after anchor removal")
            }
        }
    }
    // 蓝图管线:创造模式敲成员格 → 整体拆除、零掉落(回归 #32 修复)

    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 100)
    fun creativeMiningMemberLeavesNoDrops(helper: GameTestHelper) {
        val anchorPos = BlockPos(0, 1, 0)
        val memberPos = anchorPos.offset(BlockPos(1, 0, 1))
        helper.setBlock(anchorPos, ModBlocks.TEST_STRUCTURE_ANCHOR_2X2.get())
        helper.runAfterDelay(5) {
            // 模拟 ServerPlayerGameMode 创造路径:playerWillDestroy 先行,成员随后移除
            val memberState = helper.getBlockState(memberPos)
            memberState.block.playerWillDestroy(
                helper.level, helper.absolutePos(memberPos), memberState, helper.makeMockPlayer(GameType.CREATIVE)
            )
            helper.destroyBlock(memberPos)
        }
        helper.succeedWhen {
            val cells = listOf(anchorPos, BlockPos(1, 0, 0), BlockPos(0, 0, 1), BlockPos(1, 0, 1))
                .map { anchorPos.offset(it) }
            if (cells.any { !helper.getBlockState(it).isAir }) {
                helper.fail("creative member break must tear down the whole structure")
            }
            // 创造模式:控制器物品与内容物都不掉落
            val drops = helper.getEntities(EntityType.ITEM, anchorPos, 4.0)
            if (drops.isNotEmpty()) {
                helper.fail("creative break must not drop anything, found ${drops.size} items")
            }
        }
    }

    // 蓝图管线:相邻贴放被拒时,已放结构不受牵连(回归修复)

    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 100)
    fun rejectedNeighborPlacementDoesNotHarmExistingStructure(helper: GameTestHelper) {
        val anchorA = BlockPos(1, 1, 1)
        val membersA = listOf(BlockPos(2, 1, 1), BlockPos(1, 1, 2), BlockPos(2, 1, 2))
        helper.setBlock(anchorA, ModBlocks.TEST_STRUCTURE_ANCHOR_2X2.get())
        helper.runAfterDelay(5) {
            // B 锚 (0,1,1) 的 +X 成员格正是 A 的锚点 → 成型校验必失败 → 回滚
            helper.setBlock(BlockPos(0, 1, 1), ModBlocks.TEST_STRUCTURE_ANCHOR_2X2.get())
        }
        helper.succeedWhen {
            if (!helper.getBlockState(anchorA).`is`(ModBlocks.TEST_STRUCTURE_ANCHOR_2X2.get())) {
                helper.fail("existing structure's anchor was destroyed by a rejected neighbor placement")
            }
            val anyMemberGone = membersA.any { !helper.getBlockState(it).`is`(ModBlocks.TEST_STRUCTURAL.get()) }
            if (anyMemberGone) {
                helper.fail("existing structure's members were torn down by a rejected neighbor placement")
            }
            // 新放置的锚点已回滚消失
            if (!helper.getBlockState(BlockPos(0, 1, 1)).isAir) {
                helper.fail("blocked placement did not roll back its own anchor")
            }
        }
    }
    // 采矿:矿脉与钻头(#35 基础,#50 逻辑挖矿:4×4 柱域/四档节奏/无限矿/Lock)。钻头 2×2 角锚点 (+X/+Z)。

    /** ① + ③:钻头 40 tick 基础节奏产出 1 铜入 Buffer,采口矿格被吞并回填宿主石头。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 120)
    fun drillMinesOreIntoBufferAndRefillsStone(helper: GameTestHelper) {
        val orePos = BlockPos(1, 1, 1)
        val drillPos = BlockPos(1, 2, 1)
        helper.setBlock(orePos, ModBlocks.ORE_COPPER.get())
        helper.setBlock(drillPos, ModBlocks.DRILL.get())
        // 模板原点在绝对 y=-60:采口绝对 y<0 → 回填 deepslate(#35 规则;常规生存浅层为 stone)
        val expectedHost =
            if (helper.absolutePos(orePos).y < 0) Blocks.DEEPSLATE else Blocks.STONE
        helper.succeedWhen {
            if (countItem(helper, drillPos, ModItems.getMaterial(Materials.COPPER).get()) < 1) {
                helper.fail("drill produced no copper within base pace")
            }
            if (!helper.getBlockState(orePos).`is`(expectedHost)) {
                helper.fail("mined ore block must be refilled with the host stone for its depth")
            }
        }
    }

    /** ②a 基准:无水钻头按 40 tick 节奏——30 tick 窗口内零产出,随后产出(与 ②b 同窗口对照)。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 120)
    fun drillWithoutWaterMinesOnBasePace(helper: GameTestHelper) {
        val orePos = BlockPos(1, 1, 1)
        val drillPos = BlockPos(1, 2, 1)
        helper.setBlock(orePos, ModBlocks.ORE_COPPER.get())
        helper.setBlock(drillPos, ModBlocks.DRILL.get())
        helper.runAfterDelay(30) {
            if (countItem(helper, drillPos, ModItems.getMaterial(Materials.COPPER).get()) > 0) {
                helper.fail("dry drill must not finish an item within 30 ticks (40 tick base pace)")
            }
        }
        helper.succeedWhen {
            if (countItem(helper, drillPos, ModItems.getMaterial(Materials.COPPER).get()) < 1) {
                helper.fail("dry drill must produce within the 40 tick base pace")
            }
        }
    }

    /** ②b 加成:灌满水 → 25 tick/物品(×1.6),30 tick 窗口内产出——同窗口多于无水基准。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 120)
    fun drillAcceleratesWhenWaterBoosted(helper: GameTestHelper) {
        val orePos = BlockPos(1, 1, 1)
        val drillPos = BlockPos(1, 2, 1)
        helper.setBlock(orePos, ModBlocks.ORE_COPPER.get())
        helper.setBlock(drillPos, ModBlocks.DRILL.get())
        fillKilnTank(helper, drillPos)
        helper.runAfterDelay(30) {
            if (countItem(helper, drillPos, ModItems.getMaterial(Materials.COPPER).get()) < 1) {
                helper.fail("water-boosted drill must finish an item within 30 ticks (25 tick pace)")
            }
            helper.succeed()
        }
    }

    /** ④:Buffer 满载 → 停转:采口矿石不被吞、产出数不变(取出后自动续转由 ⑤ 覆盖)。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 120)
    fun drillStopsWhenBufferFull(helper: GameTestHelper) {
        val orePos = BlockPos(1, 1, 1)
        val drillPos = BlockPos(1, 2, 1)
        helper.setBlock(drillPos, ModBlocks.DRILL.get())
        // 20 槽 × 64:塞满 Buffer 后再放矿,采口不得被吞
        repeat(20) { insertItem(helper, drillPos, it, ItemStack(ModItems.getMaterial(Materials.COPPER).get(), 64)) }
        helper.setBlock(orePos, ModBlocks.ORE_COPPER.get())
        helper.runAfterDelay(60) {
            if (!helper.getBlockState(orePos).`is`(ModBlocks.ORE_COPPER.get())) {
                helper.fail("full buffer must not consume the ore block")
            }
            if (countItem(helper, drillPos, ModItems.getMaterial(Materials.COPPER).get()) != 1280) {
                helper.fail("full buffer must not grow")
            }
            helper.succeed()
        }
    }

    /** ⑤:空手右键取出(进玩家背包),补矿后续转;拆机 Buffer 散落。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 200)
    fun drillTakeoutThenScatterOnTeardown(helper: GameTestHelper) {
        val orePos = BlockPos(1, 1, 1)
        val drillPos = BlockPos(1, 2, 1)
        val copper = ModItems.getMaterial(Materials.COPPER).get()
        helper.setBlock(orePos, ModBlocks.ORE_COPPER.get())
        helper.setBlock(drillPos, ModBlocks.DRILL.get())
        helper.runAfterDelay(60) {
            if (countItem(helper, drillPos, copper) < 1) helper.fail("drill produced nothing to take")
            val state = helper.getBlockState(drillPos)
            val player = helper.makeMockPlayer(GameType.SURVIVAL)
            // 空手限定(spec):手持非流体物品右键不得触发取出(无放料通道,FAIL 挡住 useWithoutItem 回退)
            val hit = BlockHitResult(Vec3.ZERO, Direction.UP, helper.absolutePos(drillPos), false)
            val stickUse = state.useItemOn(
                ItemStack(Items.STICK), helper.level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit
            )
            if (stickUse != net.minecraft.world.ItemInteractionResult.FAIL) {
                helper.fail("non-fluid item use must be refused, got $stickUse")
            }
            if (countItem(helper, drillPos, copper) != 1) {
                helper.fail("non-empty hand must not trigger takeout")
            }
            // 空手右键:真实服务端链路先走 useItemOn(EMPTY) 并在空手分支消费取出
            val bareUse = state.useItemOn(
                ItemStack.EMPTY, helper.level, player,
                net.minecraft.world.InteractionHand.MAIN_HAND, hit
            )
            if (bareUse != net.minecraft.world.ItemInteractionResult.CONSUME) {
                helper.fail("empty-hand use must consume on the anchor, got $bareUse")
            }
            if (countItem(helper, drillPos, copper) != 0) {
                helper.fail("empty-hand use must transfer the whole buffer stack out")
            }
            if (!player.inventory.contains(ItemStack(copper))) {
                helper.fail("taken item must land in the player inventory")
            }
            // 补矿:采口已回填石头,放下新矿续转;待第二件产出后拆机
            helper.setBlock(orePos, ModBlocks.ORE_COPPER.get())
        }
        helper.runAfterDelay(150) {
            if (countItem(helper, drillPos, copper) < 1) {
                helper.fail("drill did not resume after ore refill")
            }
            helper.destroyBlock(drillPos)
        }
        helper.succeedWhen {
            val drops = helper.getEntities(EntityType.ITEM, drillPos, 4.0)
            if (drops.none { (it as? ItemEntity)?.item?.`is`(copper) == true }) {
                helper.fail("drill did not scatter buffer contents on teardown")
            }
        }
    }

    /** ⑥:2×2 成员格能力路由——经成员插入/取出解析回锚点 Buffer(真内容首验)。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 120)
    fun drillMemberRoutesToAnchorBuffer(helper: GameTestHelper) {
        val anchorPos = BlockPos(1, 2, 1)
        val memberPos = BlockPos(2, 2, 2)
        val lead = ModItems.getMaterial(Materials.LEAD).get()
        helper.setBlock(anchorPos, ModBlocks.DRILL.get())
        helper.runAfterDelay(5) {
            insertItem(helper, memberPos, 0, ItemStack(lead, 4))
            if (countItem(helper, anchorPos, lead) != 4) {
                helper.fail("member-inserted items must land in the anchor buffer")
            }
            val cap = helper.level.getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(memberPos), null)
                ?: throw IllegalStateException("no item capability at drill member")
            val extracted = cap.extractItem(0, 2, false)
            if (!extracted.`is`(lead) || extracted.count != 2) {
                helper.fail("member extraction must pull from the anchor buffer")
            }
            if (countItem(helper, anchorPos, lead) != 2) {
                helper.fail("anchor buffer must shrink after member extraction")
            }
            helper.succeed()
        }
    }

    /** ⑥b:成员格交互代理——右键成员 = 右键锚点:手持非流体 FAIL、空手取出 CONSUME(玩家视角成员与锚点无区别)。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 140)
    fun drillMemberForwardsInteractionToAnchor(helper: GameTestHelper) {
        val orePos = BlockPos(1, 1, 1)
        val drillPos = BlockPos(1, 2, 1)
        val memberPos = BlockPos(2, 2, 1)
        val copper = ModItems.getMaterial(Materials.COPPER).get()
        helper.setBlock(orePos, ModBlocks.ORE_COPPER.get())
        helper.setBlock(drillPos, ModBlocks.DRILL.get())
        helper.runAfterDelay(60) {
            if (countItem(helper, drillPos, copper) < 1) helper.fail("drill produced nothing to take")
            val memberState = helper.getBlockState(memberPos)
            if (memberState.block !is StructuralBlock) helper.fail("member block missing at $memberPos")
            val player = helper.makeMockPlayer(GameType.SURVIVAL)
            val hit = BlockHitResult(Vec3.ZERO, Direction.UP, helper.absolutePos(memberPos), false)
            // 手持非流体物品右键成员 → 与锚点一致地拒绝(useItemOn 转发命中锚点 FAIL 分支)
            val stickUse = memberState.useItemOn(
                ItemStack(Items.STICK), helper.level, player,
                net.minecraft.world.InteractionHand.MAIN_HAND, hit
            )
            if (stickUse != net.minecraft.world.ItemInteractionResult.FAIL) {
                helper.fail("member non-fluid use must refuse like the anchor, got $stickUse")
            }
            if (countItem(helper, drillPos, copper) != 1) {
                helper.fail("member stick use must not trigger takeout")
            }
            // 空手右键成员 → 经锚点取出(与主块同链路:useItemOn 空手分支)
            val bareUse = memberState.useItemOn(
                ItemStack.EMPTY, helper.level, player,
                net.minecraft.world.InteractionHand.MAIN_HAND, hit
            )
            if (bareUse != net.minecraft.world.ItemInteractionResult.CONSUME) {
                helper.fail("member empty-hand use must consume like the anchor, got $bareUse")
            }
            if (countItem(helper, drillPos, copper) != 0) {
                helper.fail("member empty-hand use must take out from the anchor buffer")
            }
            if (!player.inventory.contains(ItemStack(copper))) {
                helper.fail("member takeout must land in the player inventory")
            }
            helper.succeed()
        }
    }

    // ===== 钻头区域开采(#50,逻辑挖矿):4×4 柱域/分矿种 Reserve/四档节奏/无限矿/Lock 循环 =====

    /** 数据面(#52 迁移):Reserve 按 4×4 柱域分矿种计数(铜/铅各 1、煤 0)。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 100)
    fun drillReserveCountsRegionOresByType(helper: GameTestHelper) {
        val drillPos = BlockPos(1, 2, 1)
        helper.setBlock(BlockPos(1, 1, 1), ModBlocks.ORE_COPPER.get())
        helper.setBlock(BlockPos(1, 0, 1), ModBlocks.ORE_LEAD.get())
        helper.setBlock(drillPos, ModBlocks.DRILL.get())
        val copper = ModItems.getMaterial(Materials.COPPER).get()
        val lead = ModItems.getMaterial(Materials.LEAD).get()
        val coal = ModItems.getMaterial(Materials.COAL).get()
        helper.runAfterDelay(5) {
            val drill = helper.getBlockEntity(drillPos) as DrillBE
            if (drill.oreReserve(copper) != 1) helper.fail("copper reserve must count its own blocks, got ${drill.oreReserve(copper)}")
            if (drill.oreReserve(lead) != 1) helper.fail("lead reserve must count its own blocks, got ${drill.oreReserve(lead)}")
            if (drill.oreReserve(coal) != 0) helper.fail("coal reserve must be 0, got ${drill.oreReserve(coal)}")
            helper.succeed()
        }
    }

    /** 数据面(#52 迁移):采掘消耗 → 对应矿种 Reserve 当场递减到 0。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 120)
    fun drillReserveDecaysAfterMining(helper: GameTestHelper) {
        val orePos = BlockPos(1, 1, 1)
        val drillPos = BlockPos(1, 2, 1)
        val copper = ModItems.getMaterial(Materials.COPPER).get()
        helper.setBlock(orePos, ModBlocks.ORE_COPPER.get())
        helper.setBlock(drillPos, ModBlocks.DRILL.get())
        helper.succeedWhen {
            if (countItem(helper, drillPos, copper) > 0) {
                val drill = helper.getBlockEntity(drillPos) as DrillBE
                if (drill.oreReserve(copper) != 0) {
                    helper.fail("copper reserve must drop to 0 once its only ore is mined, got ${drill.oreReserve(copper)}")
                }
                helper.succeed()
            }
        }
    }

    /** Lock 目标过滤:锁定铅 → 浅处铜矿不碰,深处置铅矿被吞、回填宿主石头。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 150)
    fun drillLockTargetsOnlyLockedOre(helper: GameTestHelper) {
        val copperPos = BlockPos(1, 1, 1)
        val leadPos = BlockPos(1, 0, 1)
        val drillPos = BlockPos(1, 2, 1)
        val lead = ModItems.getMaterial(Materials.LEAD).get()
        helper.setBlock(copperPos, ModBlocks.ORE_COPPER.get())
        helper.setBlock(leadPos, ModBlocks.ORE_LEAD.get())
        helper.setBlock(drillPos, ModBlocks.DRILL.get())
        val expectedHost =
            if (helper.absolutePos(leadPos).y < 0) Blocks.DEEPSLATE else Blocks.STONE
        helper.runAfterDelay(10) {
            (helper.getBlockEntity(drillPos) as DrillBE).oreLock = lead
        }
        helper.succeedWhen {
            if (countItem(helper, drillPos, lead) > 0) {
                if (!helper.getBlockState(copperPos).`is`(ModBlocks.ORE_COPPER.get())) {
                    helper.fail("lead-locked drill must not touch the copper block")
                }
                if (!helper.getBlockState(leadPos).`is`(expectedHost)) {
                    helper.fail("mined lead block must be refilled with the host stone for its depth")
                }
                helper.succeed()
            }
        }
    }


    /** Lock 恢复:锁定铜、域内只有铅 → 停摆不吞铅;补一格铜 → 恢复且只吞铜。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 250)
    fun drillLockedIdleResumesWhenLockedOrePlaced(helper: GameTestHelper) {
        val copperPos = BlockPos(0, 1, 0)
        val leadPos = BlockPos(1, 1, 1)
        val drillPos = BlockPos(1, 2, 1)
        val copper = ModItems.getMaterial(Materials.COPPER).get()
        val lead = ModItems.getMaterial(Materials.LEAD).get()
        helper.setBlock(leadPos, ModBlocks.ORE_LEAD.get())
        helper.setBlock(drillPos, ModBlocks.DRILL.get())
        helper.runAfterDelay(10) {
            (helper.getBlockEntity(drillPos) as DrillBE).oreLock = copper
        }
        helper.runAfterDelay(40) {
            if (countItem(helper, drillPos, lead) > 0) {
                helper.fail("copper-locked drill must not mine lead")
            }
            helper.setBlock(copperPos, ModBlocks.ORE_COPPER.get())
        }
        helper.succeedWhen {
            if (countItem(helper, drillPos, copper) > 0) {
                if (!helper.getBlockState(leadPos).`is`(ModBlocks.ORE_LEAD.get())) {
                    helper.fail("resumed copper-locked drill must still leave lead untouched")
                }
                helper.succeed()
            }
        }
    }
    /** 穿孔零成本:石头下压矿 → 产出铜,石头格纹丝不动,矿格回填宿主石头。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 150)
    fun drillTunnelsThroughStoneToOre(helper: GameTestHelper) {
        val stonePos = BlockPos(1, 1, 1)
        val orePos = BlockPos(1, 0, 1)
        val drillPos = BlockPos(1, 2, 1)
        val copper = ModItems.getMaterial(Materials.COPPER).get()
        helper.setBlock(stonePos, Blocks.STONE)
        helper.setBlock(orePos, ModBlocks.ORE_COPPER.get())
        helper.setBlock(drillPos, ModBlocks.DRILL.get())
        val expectedHost =
            if (helper.absolutePos(orePos).y < 0) Blocks.DEEPSLATE else Blocks.STONE
        helper.succeedWhen {
            if (countItem(helper, drillPos, copper) > 0) {
                if (!helper.getBlockState(stonePos).`is`(Blocks.STONE)) {
                    helper.fail("tunneling must not touch the overlying stone block")
                }
                if (!helper.getBlockState(orePos).`is`(expectedHost)) {
                    helper.fail("mined ore under stone must be refilled with the host stone")
                }
                helper.succeed()
            }
        }
    }

    /** 四档节奏(顶速):13 块铜(≥13 档)→ 10t/物品;35t 窗口已 ≥3 件(基准 40t 档必为 0)。 */
    @JvmStatic
    @GameTest(template = "empty4x4", timeoutTicks = 200)
    fun drillSpeedScalesWithOreCount(helper: GameTestHelper) {
        val drillPos = BlockPos(1, 2, 1)
        val copper = ModItems.getMaterial(Materials.COPPER).get()
        // 域 = 锚点 4×4(x/z 0..3),13 块铺 y=1 一层(钻头 y=2,域内)
        for (i in 0 until 13) {
            helper.setBlock(BlockPos(i / 4, 1, i % 4), ModBlocks.ORE_COPPER.get())
        }
        helper.setBlock(drillPos, ModBlocks.DRILL.get())
        helper.runAfterDelay(35) {
            if (countItem(helper, drillPos, copper) < 3) {
                helper.fail("13-ore region must mine at the top pace (10 tick/item), got ${countItem(helper, drillPos, copper)} by t35")
            }
        }
        helper.succeedWhen {
            if (countItem(helper, drillPos, copper) >= 8) helper.succeed()
        }
    }


    /**
     * 水加成随档生效:13 块铜(顶速档)+ 满罐水 → 走 7t/物品水码表。
     * 断言不靠 tick 计时:水加成只在 boosted 分支扣罐(25mB/物品),干钻路径永不扣罐,
     * 故"产出铜的同时罐量下降"即证明走了水码表。
     */
    @JvmStatic
    @GameTest(template = "empty4x4", timeoutTicks = 200)
    fun drillWaterBoostAppliesToTopTier(helper: GameTestHelper) {
        val drillPos = BlockPos(1, 2, 1)
        val copper = ModItems.getMaterial(Materials.COPPER).get()
        for (i in 0 until 13) {
            helper.setBlock(BlockPos(i / 4, 1, i % 4), ModBlocks.ORE_COPPER.get())
        }
        helper.setBlock(drillPos, ModBlocks.DRILL.get())
        fillKilnTank(helper, drillPos)
        helper.succeedWhen {
            if (countItem(helper, drillPos, copper) > 0) {
                val tank = (helper.getBlockEntity(drillPos) as DrillBE).fluidCapability.currentFluid.amount
                if (tank >= 1000) {
                    helper.fail("water-boosted drill must drain the tank (25 mB/item), tank=${tank} — dry path never drains")
                }
                helper.succeed()
            }
        }
    }
    /** 无限矿(T=24):24 块铜 → 持续产出且矿格零消费(借鉴 Create 抽岩浆;isInfinite 数据面)。 */
    @JvmStatic
    @GameTest(template = "empty4x4", timeoutTicks = 500)
    fun drillInfiniteOreNeverConsumed(helper: GameTestHelper) {
        val drillPos = BlockPos(1, 2, 1)
        val copper = ModItems.getMaterial(Materials.COPPER).get()
        for (x in 0..3) for (z in 0..3) {
            helper.setBlock(BlockPos(x, 1, z), ModBlocks.ORE_COPPER.get())
        }
        for (x in 0..3) for (z in 0..1) {
            helper.setBlock(BlockPos(x, 0, z), ModBlocks.ORE_COPPER.get())
        }
        helper.setBlock(drillPos, ModBlocks.DRILL.get())
        helper.runAfterDelay(5) {
            val drill = helper.getBlockEntity(drillPos) as DrillBE
            if (drill.oreReserve(copper) != 24) helper.fail("expected 24 copper in the region, got ${drill.oreReserve(copper)}")
            if (!drill.isInfinite(copper)) helper.fail("24 ores must be in the infinite tier")
        }
        helper.succeedWhen {
            if (countItem(helper, drillPos, copper) >= 30) {
                if (countBlocksInDrillRegion(helper, drillPos, ModBlocks.ORE_COPPER.get()) != 24) {
                    helper.fail("infinite ore must be zero-consumption, region shrank to ${countBlocksInDrillRegion(helper, drillPos, ModBlocks.ORE_COPPER.get())}")
                }
                helper.succeed()
            }
        }
    }

    /** Lock 循环:手持任意模组矿石右键推进一步(铜→铅→煤→无→铜),手持物不消耗;木棍仍拒绝。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 100)
    fun drillLockCyclesViaHeldOreItem(helper: GameTestHelper) {
        val drillPos = BlockPos(1, 2, 1)
        val copper = ModItems.getMaterial(Materials.COPPER).get()
        val lead = ModItems.getMaterial(Materials.LEAD).get()
        val coal = ModItems.getMaterial(Materials.COAL).get()
        helper.setBlock(drillPos, ModBlocks.DRILL.get())
        helper.runAfterDelay(5) {
            val drill = helper.getBlockEntity(drillPos) as DrillBE
            // 同一条手持栈(铜)走完整个环:每步 CONSUME、手持不缩、锁按固定序推进(与手持种类无关)
            val held = ItemStack(copper, 4)
            val ring = listOf(copper, lead, coal, null, copper)
            for (expect in ring) {
                val result = mockUseOn(helper, drillPos, held)
                if (result != ItemInteractionResult.CONSUME) {
                    helper.fail("ore-item use must cycle the lock and consume the interaction, got $result")
                }
                if (held.count != 4) helper.fail("lock cycling must not consume the held item, left ${held.count}")
                if (drill.oreLock != expect) helper.fail("cycle must land on $expect, got ${drill.oreLock}")
            }
            if (mockUseOn(helper, drillPos, ItemStack(Items.STICK)) != ItemInteractionResult.FAIL) {
                helper.fail("non-ore non-fluid items must still be refused")
            }
            if (drill.oreLock != copper) helper.fail("refused use must not touch the lock")
            helper.succeed()
        }
    }

    /** 成员格转发切 Lock:右键成员格手持矿 → 锚点 Lock 推进(成员 = 锚点,无区别)。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 100)
    fun drillMemberCyclesLockViaAnchor(helper: GameTestHelper) {
        val drillPos = BlockPos(1, 2, 1)
        val memberPos = BlockPos(2, 2, 1)
        val copper = ModItems.getMaterial(Materials.COPPER).get()
        helper.setBlock(drillPos, ModBlocks.DRILL.get())
        helper.runAfterDelay(5) {
            val drill = helper.getBlockEntity(drillPos) as DrillBE
            val memberState = helper.getBlockState(memberPos)
            if (memberState.block !is StructuralBlock) helper.fail("member block missing at $memberPos")
            val player = helper.makeMockPlayer(GameType.SURVIVAL)
            val hit = BlockHitResult(Vec3.ZERO, Direction.UP, helper.absolutePos(memberPos), false)
            val result = memberState.useItemOn(
                ItemStack(copper), helper.level, player, InteractionHand.MAIN_HAND, hit
            )
            if (result != ItemInteractionResult.CONSUME) {
                helper.fail("member ore use must cycle the anchor lock, got $result")
            }
            if (drill.oreLock != copper) helper.fail("member use must land the lock on the anchor, got ${drill.oreLock}")
            helper.succeed()
        }
    }

    /** 空闲自愈:无矿停摆后玩家补矿 → 低频重扫(20t)发现目标,续转产出。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 200)
    fun drillIdleDetectsPlacedOre(helper: GameTestHelper) {
        val orePos = BlockPos(1, 1, 1)
        val drillPos = BlockPos(1, 2, 1)
        val copper = ModItems.getMaterial(Materials.COPPER).get()
        helper.setBlock(orePos, Blocks.STONE)
        helper.setBlock(drillPos, ModBlocks.DRILL.get())
        helper.runAfterDelay(40) {
            if ((helper.getBlockEntity(drillPos) as DrillBE).isRunning) {
                helper.fail("ore-free drill must stay idle")
            }
            if (countItem(helper, drillPos, copper) > 0) helper.fail("ore-free drill must not produce")
            helper.setBlock(orePos, ModBlocks.ORE_COPPER.get())
        }
        helper.succeedWhen {
            if (countItem(helper, drillPos, copper) > 0) helper.succeed()
        }
    }

    /** 统计钻头 4×4 域(dx/dz ∈ −1..2)内模板地板以上的某方块数(无限矿零消费断言用)。 */
    private fun countBlocksInDrillRegion(helper: GameTestHelper, drillPos: BlockPos, block: Block): Int {
        var n = 0
        for (y in (drillPos.y - 1) downTo 0) {
            for (x in -1..2) {
                for (z in -1..2) {
                    if (helper.getBlockState(drillPos.offset(x, y - drillPos.y, z)).`is`(block)) n++
                }
            }
        }
        return n
    }

    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 100)
    fun memberPickProxiesToAnchorItem(helper: GameTestHelper) {
        val drillPos = BlockPos(1, 2, 1)
        val memberPos = BlockPos(2, 2, 1)
        val drillItem = ModItems.DRILL_ITEM.get()!!
        helper.setBlock(drillPos, ModBlocks.DRILL.get())
        helper.runAfterDelay(5) {
            val memberState = helper.getBlockState(memberPos)
            if (memberState.block !is StructuralBlock) helper.fail("member block missing at $memberPos")
            // Jade 图标/创造拾取都走 picked result → clone item stack(成员代理回锚点,#59)
            val hit = BlockHitResult(Vec3.ZERO, Direction.UP, helper.absolutePos(memberPos), false)
            val picked = memberState.getCloneItemStack(
                hit, helper.level, helper.absolutePos(memberPos), helper.makeMockPlayer(GameType.CREATIVE)
            )
            if (!picked.`is`(drillItem)) helper.fail("member pick must return the anchor item, got ${picked.item}")
            helper.succeed()
        }
    }

    /** ⑦:镐子挖矿石 → 固定掉 1 对应材料物品(手挖语义,无 fortune/silk 分支)。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 100)
    fun miningOreDropsSingleMaterial(helper: GameTestHelper) {
        val orePos = BlockPos(1, 1, 1)
        helper.setBlock(orePos, ModBlocks.ORE_LEAD.get())
        val drops = Block.getDrops(
            helper.getBlockState(orePos),
            helper.level as ServerLevel,
            helper.absolutePos(orePos),
            null
        )
        val lead = ModItems.getMaterial(Materials.LEAD).get()
        if (drops.size != 1 || !drops[0].`is`(lead)) {
            helper.fail("pickaxe mining must drop exactly 1 lead item, got ${drops.map { it.item.toString() }}")
        }
        helper.succeed()
    }
    // ========== Scatter 2×2 防空炮(#34):蓝图管线 2×2 首验 + 双发点射/LIFO/溅射/破片/对空过滤 ==========

    /**
     * 悬空靶:恶魂(Monster、4×4 大箱体)悬停不落不烧,子弹 1.575 格/步 100% 命中——
     * 幽灵 0.9 格箱体在单步 1.575 下会整步越过(实体命中只查终点包围盒),对空断言会全数脱靶。
     * 位置放模板内 x=0.2(不得越出结构所在区块——先前放 -0.4 时每逢模板起点恰在区块边界,
     * 恶魂落入未加载的邻区块,t1 索敌看不到它,炮台转而瞄准他例的可见空中靶,断言全数失效)。
     * 首步落点 (-0.57,2.3,2) 落在 4×4 箱体内,溅射几何按此校准。
     */
    private fun hoverGhast(helper: GameTestHelper): net.minecraft.world.entity.monster.Ghast {
        val ghast = helper.spawnWithNoFreeWill(EntityType.GHAST, Vec3(0.2, 1.0, 2.0))
        ghast.isNoGravity = true
        fireproof(ghast)
        return ghast
    }

    /** ① 2×2 成型:锚点 + 3 成员(+X/+Z/+X+Z),成员无 BE、编码偏移可重算回锚点,锚点 BE 为 size=2。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 100)
    fun scatterFormsAs2x2(helper: GameTestHelper) {
        val anchorPos = BlockPos(0, 1, 0)
        helper.setBlock(anchorPos, ModBlocks.SCATTER.get())
        helper.succeedWhen {
            for (offset in listOf(BlockPos(1, 0, 0), BlockPos(0, 0, 1), BlockPos(1, 0, 1))) {
                val memberPos = anchorPos.offset(offset)
                val state = helper.getBlockState(memberPos)
                if (!state.`is`(ModBlocks.SCATTER_STRUCTURAL.get())) {
                    helper.fail("scatter member not formed at $offset: $state")
                }
                if (helper.getLevel().getBlockEntity(helper.absolutePos(memberPos)) != null) {
                    helper.fail("member at $offset must not host a block entity")
                }
            }
            val diagonal = helper.getBlockState(anchorPos.offset(BlockPos(1, 0, 1)))
            if (anchorPos.offset(BlockPos(1, 0, 1)).subtract(StructuralBlock.decodeOffset(diagonal)) != anchorPos) {
                helper.fail("encoded offsets do not resolve scatter anchor")
            }
            val be = helper.getLevel().getBlockEntity(helper.absolutePos(anchorPos))
            if (be !is ScatterTurretBE || be.spec.size != 2) {
                helper.fail("scatter anchor must host a size-2 turret BE")
            }
        }
    }

    /** ② 成员右键装弹:对 +X 成员格灌 1 铅 → 拆机折回 1 铅(4 单位/4),成员交互代理成功入账。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 100)
    fun scatterMemberLoadsAmmo(helper: GameTestHelper) {
        val anchorPos = BlockPos(1, 1, 1)
        val memberPos = BlockPos(2, 1, 1)
        helper.setBlock(anchorPos, ModBlocks.SCATTER.get())
        helper.runAfterDelay(5) {
            val lead = ModItems.getMaterial(Materials.LEAD).get()
            if (mockUseOn(helper, memberPos, ItemStack(lead, 1)) != ItemInteractionResult.CONSUME) {
                helper.fail("member ammo load must consume the held stack")
            }
            breakForDrops(helper, anchorPos)
        }
        helper.succeedWhen {
            val lead = ModItems.getMaterial(Materials.LEAD).get()
            if (countDrops(helper, anchorPos, lead) != 1) {
                helper.fail("member-loaded lead must refund 1 on teardown (4 units / 4)")
            }
        }
    }

    /**
     * ③ 双发点射:1 铅(4 单位)+ 悬空恶魂;首扳机 t6 首发出膛(t7 命中击杀),
     * t9 余弹仍在飞行(队列第 2 发,方向为扳机时刻锁定)——"一扳机两弹";
     * 拆机折回 = 0(4-1=3 → floor(3/4))——"一扳机扣 1 单位"。
     * NOTE: 每发间隔 2t、模板靶距上限下两弹同飞窗口不足 1 tick 不可观测,
     * 故用「首发命中 + 尾弹仍在飞」双证据替代(发数等价)。
     */
    @JvmStatic
    @GameTest(template = "empty3x3", batch = "scatterDoubleShotPerTrigger", timeoutTicks = 150)
    fun scatterDoubleShotPerTrigger(helper: GameTestHelper) {
        val turretPos = BlockPos(1, 1, 1)
        helper.setBlock(turretPos, ModBlocks.SCATTER.get())
        val lead = ModItems.getMaterial(Materials.LEAD).get()
        if (mockUseOn(helper, turretPos, ItemStack(lead, 1)) != ItemInteractionResult.CONSUME) {
            helper.fail("lead must load")
        }
        val ghast = hoverGhast(helper)
        helper.runAfterDelay(9) {
            // 恶魂已被首发直击+溅射击杀;队列第 2 发仍在飞行(首发 t7 已消失)。
            // 计数用 level 级 AABB:helper.getEntities 会与模板边界求交,出界子弹会被漏数
            if (!ghast.isRemoved && ghast.health >= 20f) {
                helper.fail("first shot must hit and kill the ghast, hp=${ghast.health}")
            }
            val bullets = helper.level.getEntities(
                ModEntities.TURRET_BULLET.get(),
                AABB(helper.absolutePos(turretPos)).inflate(4.0)
            ) { true }
            if (bullets.size != 1) {
                helper.fail("queued second shot must still be in flight, found ${bullets.size}")
            }
            breakForDrops(helper, turretPos)
        }
        helper.succeedWhen {
            if (countDrops(helper, turretPos, lead) != 0) {
                helper.fail("one trigger burns exactly 1 unit: floor((4-1)/4)=0 lead expected")
            }
        }
    }

    /** ⑤a 对空-only(无空中目标):僵尸落地不被索敌——t5 拆机(装填 6t 内不可能开火)足额折回 4 铅。 */
    @JvmStatic
    @GameTest(template = "empty3x3", batch = "scatterIgnoresGroundedOnly", timeoutTicks = 120)
    fun scatterIgnoresGroundedOnly(helper: GameTestHelper) {
        val turretPos = BlockPos(1, 1, 1)
        helper.setBlock(turretPos, ModBlocks.SCATTER.get())
        val lead = ModItems.getMaterial(Materials.LEAD).get()
        mockUseOn(helper, turretPos, ItemStack(lead, 4))
        val zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, BlockPos(0, 1, 2))
        fireproof(zombie)
        helper.runAfterDelay(5) { breakForDrops(helper, turretPos) }
        helper.runAfterDelay(40) {
            if (zombie.health != 20f) {
                helper.fail("grounded zombie must never be targeted, hp=${zombie.health}")
            }
        }
        helper.succeedWhen {
            if (countDrops(helper, turretPos, lead) != 4) {
                helper.fail("no air target → no trigger within the first reload → full lead refund expected")
            }
        }
    }

    /** ⑤b 对空+对地同框:恶魂掉血死亡、僵尸满血(溅射半径外的落地怪只被无视,不直接索敌)。 */
    @JvmStatic
    @GameTest(template = "empty3x3", batch = "scatterAirOnlySkipsGroundedAlongside", timeoutTicks = 120)
    fun scatterAirOnlySkipsGroundedAlongside(helper: GameTestHelper) {
        val turretPos = BlockPos(1, 1, 1)
        helper.setBlock(turretPos, ModBlocks.SCATTER.get())
        mockUseOn(helper, turretPos, ItemStack(ModItems.getMaterial(Materials.LEAD).get()))
        val ghast = hoverGhast(helper)
        // 僵尸放碰撞远角(距命中点 ~2.4 > 铅溅射 2):只验证"不被索敌",不吃溅射
        val zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, BlockPos(2, 1, 0))
        fireproof(zombie)
        helper.runAfterDelay(40) {
            if (!ghast.isRemoved && ghast.health >= 20f) {
                helper.fail("airborne ghast must be engaged")
            }
            if (zombie.health != 20f) {
                helper.fail("grounded zombie must stay full HP, hp=${zombie.health}")
            }
            helper.succeed()
        }
    }



    /**
     * ⑤c 对空谓词回归(用户实测 #53):no-AI 僵尸从不 move → onGround 恒 false,旧判据 !onGround
     * 把它当空中单位且比恶魂近 → 炮台打僵尸不打恶魂;修订为飞行怪谓词后:僵尸(重力束缚=地面单位)
     * 不占用对空槽,更远的恶魂被接战、僵尸满血。
     */
    @JvmStatic
    @GameTest(template = "empty3x3", batch = "scatterAirPredicateSkipsNearbyGroundUnit", timeoutTicks = 120)
    fun scatterAirPredicateSkipsNearbyGroundUnit(helper: GameTestHelper) {
        val anchorPos = BlockPos(0, 1, 0)
        helper.setBlock(anchorPos, ModBlocks.SCATTER.get())
        mockUseOn(helper, anchorPos, ItemStack(ModItems.getMaterial(Materials.LEAD).get()))
        helper.setBlock(BlockPos(1, 0, 2), Blocks.STONE)
        val zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, BlockPos(1, 1, 2))
        fireproof(zombie)
        // 恶魂 4×4 箱体放 (2,1,4):避开炮台 2×2 格体(防窒息)且比僵尸远(4.3 vs 1.9)
        val ghast = helper.spawnWithNoFreeWill(EntityType.GHAST, BlockPos(2, 1, 4))
        fireproof(ghast)
        helper.runAfterDelay(50) {
            if (!ghast.isRemoved && ghast.health >= 20f) {
                helper.fail("air turret must engage the farther flying ghast, not the nearer ground zombie, ghast hp=${ghast.health}")
            }
            if (zombie.health != 20f) {
                helper.fail("ground zombie must stay untouched, hp=${zombie.health}")
            }
            helper.succeed()
        }
    }

    /**
     * ⑤d 真实 AI 恶魂(用户实测 #53):真实 AI 下恶魂无重力靠 GhastMoveControl 推送,随机游荡目标
     * 偏低时 4×4 箱体贴地擦行 → onGround=true,旧判据整段放弃;飞行怪谓词与高度无关,低空必被接战。
     */
    @JvmStatic
    @GameTest(template = "empty3x3", batch = "scatterEngagesRealAiLowHoveringGhast", timeoutTicks = 150)
    fun scatterEngagesRealAiLowHoveringGhast(helper: GameTestHelper) {
        val anchorPos = BlockPos(0, 1, 0)
        helper.setBlock(anchorPos, ModBlocks.SCATTER.get())
        mockUseOn(helper, anchorPos, ItemStack(ModItems.getMaterial(Materials.LEAD).get(), 4))
        helper.setBlock(BlockPos(2, 0, 4), Blocks.STONE)
        // 真 AI(sans spawnWithNoFreeWill):会游荡、会贴地擦行;箱体 (2,1,4) 避开炮台格体
        val ghast = helper.spawn(EntityType.GHAST, BlockPos(2, 1, 4))
        fireproof(ghast)
        helper.runAfterDelay(60) {
            if (!ghast.isRemoved && ghast.health >= 20f) {
                helper.fail("real-AI ghast hovering at floor level must be engaged, hp=${ghast.health}")
            }
            helper.succeed()
        }
    }
    /** ⑨ 成员破坏 → 整体拆除:控制器物品 + 余量折回散落(4 铅 → 四格清空)。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 100)
    fun scatterMemberBreakTearsDown(helper: GameTestHelper) {
        val anchorPos = BlockPos(1, 1, 1)
        val memberPos = BlockPos(2, 1, 1)
        val lead = ModItems.getMaterial(Materials.LEAD).get()
        helper.setBlock(anchorPos, ModBlocks.SCATTER.get())
        helper.runAfterDelay(5) {
            mockUseOn(helper, anchorPos, ItemStack(lead, 4))
            helper.level.destroyBlock(helper.absolutePos(memberPos), true)
        }
        helper.succeedWhen {
            if (!helper.getBlockState(anchorPos).isAir) {
                helper.fail("breaking a member must tear down the anchor")
            }
            for (offset in listOf(BlockPos(1, 0, 0), BlockPos(0, 0, 1), BlockPos(1, 0, 1))) {
                if (!helper.getBlockState(anchorPos.offset(offset)).isAir) {
                    helper.fail("member not cleared at $offset")
                }
            }
            if (countDrops(helper, anchorPos, lead) != 4) {
                helper.fail("full magazine must refund 4 lead on member-break teardown")
            }
            val anchorItem = ModBlocks.SCATTER.get().asItem()
            val drops = helper.getEntities(EntityType.ITEM, anchorPos, 4.0)
            if (drops.none { (it as? ItemEntity)?.item?.`is`(anchorItem) == true }) {
                helper.fail("controller item must drop on member-break teardown")
            }
        }
    }

    /** ⑩ 窑炉产物自动弹出(#73):产物结算后向邻近标准 IItemHandler 容器转移(收得下则移出 Buffer)。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 300)
    fun kilnAutoEjectsProductToNeighbor(helper: GameTestHelper) {
        val kilnPos = BlockPos(1, 1, 1)
        val chestPos = BlockPos(2, 1, 1) // 东邻
        helper.setBlock(kilnPos, ModBlocks.KILN.get())
        helper.setBlock(chestPos, Blocks.CHEST)

        insertItem(helper, kilnPos, 0, ItemStack(ModItems.getMaterial(Materials.LEAD).get()))
        insertItem(helper, kilnPos, 1, ItemStack(Items.SAND))
        fillKilnTank(helper, kilnPos)
        injectEnergyUpTo(helper, kilnPos, 500)

        // 产物(金属玻璃)自动弹到邻箱:kiln Buffer 内不再有产物,箱子收到
        helper.succeedWhen {
            if (countItem(helper, kilnPos, ModItems.getMaterial(Materials.METAGLASS).get()) > 0) {
                helper.fail("kiln product must auto-eject to neighbor, still in buffer")
            }
            if (countContainerItem(chestPos, helper, ModItems.getMaterial(Materials.METAGLASS).get()) < 1) {
                helper.fail("neighbor chest must receive kiln product")
            }
        }
    }

    /** ⑪ 钻头产物自动弹出(#73):矿石产物向邻近容器转移。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 200)
    fun drillAutoEjectsOreToNeighbor(helper: GameTestHelper) {
        val orePos = BlockPos(1, 1, 1)
        val drillPos = BlockPos(1, 2, 1)
        val chestPos = BlockPos(0, 2, 1) // 西邻
        helper.setBlock(orePos, ModBlocks.ORE_COPPER.get())
        helper.setBlock(drillPos, ModBlocks.DRILL.get())
        helper.setBlock(chestPos, Blocks.CHEST)

        helper.succeedWhen {
            if (countItem(helper, drillPos, ModItems.getMaterial(Materials.COPPER).get()) > 0) {
                helper.fail("drill ore must auto-eject to neighbor, still in buffer")
            }
            if (countContainerItem(chestPos, helper, ModItems.getMaterial(Materials.COPPER).get()) < 1) {
                helper.fail("neighbor chest must receive drill ore")
            }
        }
    }

    /** ⑫ 产物弹出不丢物品(#73):邻容器满时产物留在 Buffer(满载停摆语义),取出/清空后继续。 */
    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 300)
    fun drillEjectKeepsProductWhenNeighborFull(helper: GameTestHelper) {
        val orePos = BlockPos(1, 1, 1)
        val drillPos = BlockPos(1, 2, 1)
        val chestPos = BlockPos(0, 2, 1)
        helper.setBlock(orePos, ModBlocks.ORE_COPPER.get())
        helper.setBlock(drillPos, ModBlocks.DRILL.get())
        // 满奶酪箱:填 27 格 × 64 铜 → 无产出主存量,判断用"产物不丢、留在 Buffer"
        helper.setBlock(chestPos, Blocks.CHEST)
        val copper = ModItems.getMaterial(Materials.COPPER).get()
        fillChest(helper, chestPos, ItemStack(copper, 64 * 27))

        // 邻箱满:矿石弹不出去 → 留在钻头 Buffer(sum 1)
        helper.succeedWhen {
            if (countItem(helper, drillPos, copper) < 1) {
                helper.fail("un-ejectable ore must stay in drill buffer (no loss)")
            }
        }
    }

    private fun fillChest(helper: GameTestHelper, pos: BlockPos, stack: ItemStack) {
        val cap = helper.level.getCapability(
            Capabilities.ItemHandler.BLOCK, helper.absolutePos(pos), null
        ) ?: throw IllegalStateException("no chest capability at $pos")
        // 跨槽堆叠直至放满(余量为感应满)
        ItemHandlerHelper.insertItemStacked(cap, stack, false)
    }

    /** 统计容器(pos 处 capability)中某物品总量(与机器的 countItem 分离,复用于箱子)。 */
    private fun countContainerItem(pos: BlockPos, helper: GameTestHelper, item: Item): Int {
        val cap = helper.level.getCapability(
            Capabilities.ItemHandler.BLOCK, helper.absolutePos(pos), null
        ) ?: return 0
        var count = 0
        for (slot in 0 until cap.slots) {
            val s = cap.getStackInSlot(slot)
            if (s.`is`(item)) count += s.count
        }
        return count
    }

}