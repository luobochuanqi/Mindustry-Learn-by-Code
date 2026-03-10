package xyz.luobo.mindustry.core

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mindustry.core.machine.BaseMachineBE
import xyz.luobo.mindustry.core.turret.bullet.BulletType
import xyz.luobo.mindustry.core.turret.config.TurretConfig
import xyz.luobo.mindustry.core.turret.entity.BaseTurretBlockEntity

/**
 * 基类迁移指南
 * 展示如何使用新的 MindustryModBlockEntity 基类创建机器和炮台
 */

// ========== 示例 1: 创建简单的机器 ==========

/**
 * 示例：简单的加工机器
 * - 1个输入槽，1个输出槽
 * - 10000 FE 能量容量
 * - 2 FE/tick 消耗
 * - 100 ticks 加工时间
 */
class SimpleMachineBE(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState
) : BaseMachineBE(type, pos, state) {

    // 必须实现的配置属性
    override val itemSlotCount: Int = 2 // 1输入 + 1输出
    override val energyCapacity: Int = 10000
    override val maxProgress: Int = 100
    override val energyPerTick: Int = 2

    // 可选：自定义输入/输出速率
    override val maxEnergyReceive: Int = 100
    override val maxEnergyExtract: Int = 0

    // 定义槽位功能
    override fun isInputSlot(slot: Int): Boolean = slot == 0
    override fun isOutputSlot(slot: Int): Boolean = slot == 1

    // 检查是否可以工作
    override fun canWork(): Boolean {
        val inputStack = itemHandler.getStack(0)
        val outputStack = itemHandler.getStack(1)

        // 检查输入不为空且输出未满
        return !inputStack.isEmpty && (outputStack.isEmpty || outputStack.count < 64)
    }

    // 完成工作逻辑
    override fun finishWork() {
        val inputStack = itemHandler.getStack(0)
        val outputStack = itemHandler.getStack(1)

        // 消耗1个输入物品
        itemHandler.extractItem(0, 1, false)

        // 产生1个输出物品
        if (outputStack.isEmpty) {
            itemHandler.setStack(1, ItemStack(Items.IRON_INGOT, 1))
        } else {
            itemHandler.setStack(1, ItemStack(Items.IRON_INGOT, outputStack.count + 1))
        }
    }
}

// ========== 示例 2: 创建弹药炮台 ==========

/**
 * 示例：基础弹药炮台
 * - 使用铜锭作为弹药
 * - 每秒发射2次
 * - 射程15格
 */
class BasicTurretBE(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState
) : BaseTurretBlockEntity(type, pos, state) {

    // 炮台配置
    override val config: TurretConfig = TurretConfig.builder(
        identifier = "basic_turret",
        description = "基础弹药炮台"
    )
        .range(15f)
        .reloadTime(10f)  // 10 ticks = 2 shots per second
        .inaccuracy(2f)
        .maxAmmo(200)
        .targetAir(true)
        .targetGround(true)
        .build()

    // 旋转速度
    protected val rotationSpeed: Float = 180f

    // 尝试射击（抽象方法实现）
    override fun tryShoot(level: Level, pos: BlockPos) {
        if (currentTarget != null && canAttack()) {
            fireProjectile(level, pos, currentTarget!!)
            consumeAmmo()
        }
    }

    // 发射投射物
    override fun fireProjectile(level: Level, pos: BlockPos, target: LivingEntity) {
        // 发射投射物逻辑
        // 例如：创建火焰弹或箭矢
    }
}

// ========== 示例 3: 创建激光炮台 ==========

/**
 * 示例：激光炮台
 * - 消耗电力
 * - 持续造成伤害
 * - 红色激光
 */
class LaserTurretBE(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState
) : BaseTurretBlockEntity(type, pos, state) {

    // 炮台配置
    override val config: TurretConfig = TurretConfig.laser(
        identifier = "laser_turret",
        description = "激光炮台"
    ).copy(
        range = 20f
    )

    // 激光子弹类型
    val laserBulletType = BulletType.laser(
        damagePerSecond = 15f,
        color = 0xFF0000
    )

    // 旋转速度
    protected val rotationSpeed: Float = 360f

    // 尝试射击
    override fun tryShoot(level: Level, pos: BlockPos) {
        if (currentTarget != null && canAttack()) {
            fireProjectile(level, pos, currentTarget!!)
        }
    }

    // 发射投射物（激光）
    override fun fireProjectile(level: Level, pos: BlockPos, target: LivingEntity) {
        // 自定义激光渲染和伤害
        // 例如：渲染激光束、播放音效等
    }
}

// ========== 示例 4: 创建高级机器（能量+液体）==========

/**
 * 示例：高级加工机器
 * - 使用能量和液体
 * - 多个输入输出槽
 */
class AdvancedMachineBE(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState
) : BaseMachineBE(type, pos, state) {

    // 配置
    override val itemSlotCount: Int = 5 // 3输入 + 2输出
    override val energyCapacity: Int = 20000
    override val maxProgress: Int = 200
    override val energyPerTick: Int = 5

    // 定义槽位功能
    override fun isInputSlot(slot: Int): Boolean = slot in 0..2
    override fun isOutputSlot(slot: Int): Boolean = slot in 3..4

    // 检查是否可以工作
    override fun canWork(): Boolean {
        val input1 = itemHandler.getStack(0)
        val input2 = itemHandler.getStack(1)
        val input3 = itemHandler.getStack(2)
        val output1 = itemHandler.getStack(3)
        val output2 = itemHandler.getStack(4)

        // 检查所有输入都不为空
        if (input1.isEmpty || input2.isEmpty || input3.isEmpty) {
            return false
        }

        // 检查输出槽未满
        if (output1.count >= 64 || output2.count >= 64) {
            return false
        }

        // 检查能量充足
        return energyCapability?.hasEnergy(energyPerTick) ?: false
    }

    // 完成工作逻辑
    override fun finishWork() {
        // 消耗输入
        itemHandler.extractItem(0, 1, false)
        itemHandler.extractItem(1, 1, false)
        itemHandler.extractItem(2, 1, false)

        // 产生输出
        val output1 = itemHandler.getStack(3)
        val output2 = itemHandler.getStack(4)

        if (output1.isEmpty) {
            itemHandler.setStack(3, ItemStack(Items.GOLD_INGOT, 1))
        } else {
            itemHandler.setStack(3, ItemStack(Items.GOLD_INGOT, output1.count + 1))
        }

        if (output2.isEmpty) {
            itemHandler.setStack(4, ItemStack(Items.DIAMOND, 1))
        } else {
            itemHandler.setStack(4, ItemStack(Items.DIAMOND, output2.count + 1))
        }
    }
}

// ========== 迁移步骤 ==========

/**
 * 旧代码迁移步骤：
 *
 * 1. 将继承从 BlockEntity 改为 MindustryModBlockEntity
 * 2. 移除手动管理的 energy/item/fluid 变量
 * 3. 实现必需的配置属性（itemSlotCount, energyCapacity 等）
 * 4. 重写槽位检查方法（isInputSlot, isOutputSlot）
 * 5. 更新数据保存/加载，移除手动保存的 capability 数据
 * 6. 更新代码以使用新的 API：
 *    - energyStorage.energyStored -> energyCapability?.currentEnergy
 *    - itemHandler.getStackInSlot(i) -> itemCapability?.getStack(i)
 *    - itemHandler.setStackInSlot(i, stack) -> itemCapability?.setStack(i, stack)
 *
 * 炮台系统迁移：
 * 1. 继承 BaseTurretBlockEntity 或其子类（ReloadTurretBlockEntity, ItemTurretBlockEntity, PowerTurretBlockEntity）
 * 2. 实现抽象方法 tryShoot() 和 fireProjectile()
 * 3. 使用 TurretConfig 定义炮台属性
 * 4. 使用 BulletType 定义弹药/攻击类型
 */
