package xyz.luobo.mturrets

import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.core.Direction
import net.minecraft.world.level.GameType
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityType
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.block.Block
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.fluids.FluidStack
import net.minecraft.world.level.material.Fluids
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import xyz.luobo.mturrets.common.ModBlocks
import xyz.luobo.mturrets.common.ModItems
import xyz.luobo.mturrets.common.items.Materials
import xyz.luobo.mturrets.common.machines.kiln.KilnBE
import xyz.luobo.mturrets.common.turrets.ArcTurretBlockEntity
import xyz.luobo.mturrets.common.turrets.MeltdownTurretBlockEntity
import xyz.luobo.mturrets.core.structure.StructuralBlock

/**
  * LEGACY 基准:翻新期行为回归(Duo 吃原版铜锭等断言随新范式替换,#36 重建套件);旧用例在旧代码存续期内保持全绿。
 * GameTest 回归套件
 * 只断言外部行为(伤害/能量/合成产出),不触碰内部字段(spec #5 测试决策)
 *
 * 全部用例使用原版 empty3x3 模板,坐标约束在 3x3 内
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(MTurrets.MOD_ID)
object ModGameTests {

    /** 给测试僵尸防火,避免日光燃烧干扰伤害断言 */
    private fun fireproof(entity: net.minecraft.world.entity.monster.Zombie) {
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

    // ========== 战斗:Duo 子弹命中伤害 ==========

    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 300)
    fun duoShootsZombie(helper: GameTestHelper) {
        val turretPos = BlockPos(1, 1, 1)
        helper.setBlock(turretPos, ModBlocks.DUO_BLOCK.get())

        // 经外部物品能力供给弹药(不触碰内部字段)
        insertItem(helper, turretPos, 0, ItemStack(Items.COPPER_INGOT, 64))

        val zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, BlockPos(1, 1, 2))
        fireproof(zombie)
        val initialHealth = zombie.health

        helper.succeedWhen {
            if (!zombie.isRemoved && zombie.health >= initialHealth) {
                helper.fail("zombie not damaged by duo turret")
            }
        }
    }

    // ========== 战斗:Arc 耗能电弧伤害 ==========

    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 300)
    fun arcZapsZombie(helper: GameTestHelper) {
        val turretPos = BlockPos(1, 1, 1)
        helper.setBlock(turretPos, ModBlocks.ARC_BLOCK.get())

        val injected = injectEnergy(helper, turretPos, 5000)
        if (injected <= 0) helper.fail("arc refused energy injection")

        val zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, BlockPos(1, 1, 2))
        fireproof(zombie)
        val initialHealth = zombie.health

        helper.succeedWhen {
            if (!zombie.isRemoved && zombie.health >= initialHealth) {
                val arc = helper.getBlockEntity(turretPos) as? ArcTurretBlockEntity
                helper.fail(
                    "arc diag: energy=${arc?.currentEnergy} target=${arc?.currentTarget != null} " +
                        "reload=${arc?.reloadCounter} warmup=${arc?.warmup} " +
                        "curRot=${arc?.currentRotation} tgtRot=${arc?.targetRotation} " +
                        "zombieHp=${zombie.health}"
                )
            }
        }
    }

    // ========== 战斗:Meltdown 光束灼烧 ==========

    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 400)
    fun meltdownBurnsZombie(helper: GameTestHelper) {
        val turretPos = BlockPos(1, 1, 1)
        helper.setBlock(turretPos, ModBlocks.MELTDOWN_BLOCK.get())

        val injected = injectEnergy(helper, turretPos, 20000)
        if (injected <= 0) helper.fail("meltdown refused energy injection")

        val zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, BlockPos(1, 1, 2))
        fireproof(zombie)
        val initialHealth = zombie.health

        helper.succeedWhen {
            if (!zombie.isRemoved && zombie.health >= initialHealth) {
                helper.fail("zombie not damaged by meltdown beam")
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
            // 30 FE @ 5 FE/t = 6 个扣账 tick + 起始 tick,进度冻结在 7%:断电不倒退、补电后续转
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
        insertItem(helper, kilnPos, 0, ItemStack(ModItems.getMaterial(Materials.LEAD).get()))
        insertItem(helper, kilnPos, 1, ItemStack(Items.SAND))
        fillKilnTank(helper, kilnPos)
        val injected = injectEnergyUpTo(helper, kilnPos, 600)
        if (injected < 600) helper.fail("kiln refused energy injection: $injected/600")

        helper.succeedWhen {
            if (countItem(helper, kilnPos, ModItems.getMaterial(Materials.METAGLASS).get()) < 1) {
                helper.fail("kiln produced no metaglass")
            }
            // 能量按配方总耗扣除:600 - 500 = 100
            val left = storedEnergy(helper, kilnPos)
            if (left != 100) helper.fail("expected 100 FE left after 500 FE recipe, got $left")
        }
    }

    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 300)
    fun kilnStallsWithoutWaterThenResumes(helper: GameTestHelper) {
        val kilnPos = BlockPos(1, 1, 1)
        helper.setBlock(kilnPos, ModBlocks.KILN.get())

        insertItem(helper, kilnPos, 0, ItemStack(ModItems.getMaterial(Materials.LEAD).get()))
        insertItem(helper, kilnPos, 1, ItemStack(Items.SAND))
        injectEnergyUpTo(helper, kilnPos, 600)

        // 缺水:加工从不启动;40 tick 后确认零产出且能量分毫未耗(未开工)
        helper.runAfterDelay(40) {
            if (countItem(helper, kilnPos, ModItems.getMaterial(Materials.METAGLASS).get()) > 0) {
                helper.fail("kiln produced without water")
            }
            if (storedEnergy(helper, kilnPos) != 600) {
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
    // 采矿:矿脉与钻头(#35,ADR-0008 一期材料链)。采口 = 锚点正下方 1×1;钻头 2×2 角锚点 (+X/+Z)。

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
            // 1.21.1 的 Block#useWithoutItem 为 protected;公开入口是 BlockStateBase 的同名方法(经 BlockState 调用)
            state.useWithoutItem(
                helper.level, player,
                BlockHitResult(Vec3.ZERO, Direction.UP, helper.absolutePos(drillPos), false)
            )
            if (countItem(helper, drillPos, copper) != 0) {
                helper.fail("empty-hand use must transfer the whole buffer stack out")
            }
            if (!player.inventory.contains(ItemStack(copper))) {
                helper.fail("taken item must land in the player inventory")
            }
            // 补矿:采口已回填石头,放下新矿续转;待第二件产出后拆机
            helper.setBlock(orePos, ModBlocks.ORE_COPPER.get())
        }
        helper.runAfterDelay(120) {
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
}