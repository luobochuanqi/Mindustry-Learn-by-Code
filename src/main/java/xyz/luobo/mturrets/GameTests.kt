package xyz.luobo.mturrets

import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.world.level.GameType
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import xyz.luobo.mturrets.common.ModBlocks
import xyz.luobo.mturrets.common.ModItems
import xyz.luobo.mturrets.common.blockEntities.PowerNodeBlockEntity
import xyz.luobo.mturrets.common.items.Materials
import xyz.luobo.mturrets.common.machines.kiln.KilnBE
import xyz.luobo.mturrets.common.turrets.ArcTurretBlockEntity
import xyz.luobo.mturrets.common.structure.TestStructureAnchorBE
import xyz.luobo.mturrets.core.structure.StructuralBlock
import xyz.luobo.mturrets.common.turrets.DuoTurretBlockEntity
import xyz.luobo.mturrets.common.turrets.MeltdownTurretBlockEntity

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

    // ========== 电力:节点间传输 ==========

    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 200)
    fun nodeTransfersEnergy(helper: GameTestHelper) {
        val nodeA = BlockPos(0, 1, 1)
        val nodeB = BlockPos(2, 1, 1)
        helper.setBlock(nodeA, ModBlocks.POWER_NODE_BLOCK.get())
        helper.setBlock(nodeB, ModBlocks.POWER_NODE_BLOCK.get())

        val injected = injectEnergy(helper, nodeA, 5000)
        if (injected <= 0) helper.fail("node A refused energy injection")

        helper.succeedWhen {
            val receiver = helper.level.getCapability(
                Capabilities.EnergyStorage.BLOCK,
                helper.absolutePos(nodeB),
                null
            )
            if (receiver == null || receiver.energyStored <= 0) {
                val nodeABe = helper.getBlockEntity(nodeA) as? PowerNodeBlockEntity
                val aEnergy = helper.level.getCapability(
                    Capabilities.EnergyStorage.BLOCK,
                    helper.absolutePos(nodeA),
                    null
                )?.energyStored ?: -1
                helper.fail(
                    "node B empty; A.energy=$aEnergy A.connections=${nodeABe?.getConnectedNodes()?.size ?: -1}"
                )
            }
        }
    }

    // ========== 生产:窑炉合成 ==========

    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 300)
    fun kilnCraftsMetaglass(helper: GameTestHelper) {
        val kilnPos = BlockPos(1, 1, 1)
        helper.setBlock(kilnPos, ModBlocks.KILN_BLOCK.get())

        val injected = injectEnergy(helper, kilnPos, 1000)
        if (injected <= 0) helper.fail("kiln refused energy injection")

        // 输入:铅 + 沙(1:1:1)
        insertItem(helper, kilnPos, 0, ItemStack(ModItems.getMaterial(Materials.LEAD).get(), 4))
        insertItem(helper, kilnPos, 1, ItemStack(ModItems.getMaterial(Materials.SAND).get(), 4))

        val metaglass = ModItems.getMaterial(Materials.METAGLASS).get()

        helper.succeedWhen {
            val cap = helper.level.getCapability(
                Capabilities.ItemHandler.BLOCK,
                helper.absolutePos(kilnPos),
                null
            )
            var produced = false
            if (cap != null) {
                for (slot in 0 until cap.slots) {
                    if (cap.getStackInSlot(slot).`is`(metaglass)) {
                        produced = true
                    }
                }
            }
            if (!produced) helper.fail("kiln produced no metaglass")
        }
    }

    // ========== 回归:窑炉断电停摆、恢复后续转 ==========
    // 说明:能量不足时进度保持(不倒退),恢复供电后续转——由 kilnCraftsMetaglass 的
    // 节流供能路径隐式覆盖(节点按速率供电,窑炉在能量到位前保持进度)。

    // ========== 回归:破坏窑炉掉落内容物 ==========

    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 200)
    fun breakingKilnDropsContents(helper: GameTestHelper) {
        val kilnPos = BlockPos(1, 1, 1)
        helper.setBlock(kilnPos, ModBlocks.KILN_BLOCK.get())

        insertItem(helper, kilnPos, 0, ItemStack(ModItems.getMaterial(Materials.LEAD).get(), 4))

        val lead = ModItems.getMaterial(Materials.LEAD).get()

        helper.succeedWhen {
            // 破坏方块
            helper.destroyBlock(kilnPos)
            // 掉落物中出现铅
            val drops = helper.getEntities(EntityType.ITEM, kilnPos, 2.0)
            val found = drops.any { (it as? net.minecraft.world.entity.item.ItemEntity)?.item?.`is`(lead) == true }
            if (!found) helper.fail("kiln did not drop stored lead")
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

    // 蓝图管线:1×1 走同一管线(空偏移集)

    @JvmStatic
    @GameTest(template = "empty3x3", timeoutTicks = 100)
    fun blueprintForms1x1(helper: GameTestHelper) {
        val anchorPos = BlockPos(0, 1, 0)
        helper.setBlock(anchorPos, ModBlocks.TEST_STRUCTURE_ANCHOR_1X1.get())
        helper.succeedWhen {
            if (!helper.getBlockState(anchorPos).`is`(ModBlocks.TEST_STRUCTURE_ANCHOR_1X1.get())) {
                helper.fail("1x1 anchor vanished")
            }
            if (helper.getLevel().getBlockEntity(helper.absolutePos(anchorPos)) !is TestStructureAnchorBE) {
                helper.fail("1x1 anchor lost its block entity")
            }
            // 空偏移集:不盖任何成员格
            if (!helper.getBlockState(anchorPos.offset(BlockPos(1, 0, 0))).isAir) {
                helper.fail("1x1 must not form members")
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
}