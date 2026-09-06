package xyz.luobo.mturrets.common.machines.kiln

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Fluids
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.items.ItemHandlerHelper
import xyz.luobo.mturrets.common.ModBlockEntityTypes
import xyz.luobo.mturrets.core.machine.HummingMachine
import xyz.luobo.mturrets.common.ModRecipeTypes
import xyz.luobo.mturrets.core.capability.impl.EnergyCapabilityImpl
import xyz.luobo.mturrets.core.capability.impl.FluidCapabilityImpl
import xyz.luobo.mturrets.core.capability.impl.ItemCapabilityImpl
import xyz.luobo.mturrets.core.power.PowerMemberBE
import xyz.luobo.mturrets.core.recipe.MachineRecipe
import xyz.luobo.mturrets.core.structure.Blueprint
import xyz.luobo.mturrets.core.structure.BlueprintAnchor
import xyz.luobo.mturrets.core.structure.BlueprintAnchorBlock
import kotlin.math.min

/**
 * 窑炉 BE(#33):铅 + 原版沙 + 水 → 金属玻璃。
 * Buffer 无固定槽位,只收配方原料/产物;内罐只收水,水为每轮预检的必需输入;
 * 能量按配方总耗随进度均摊、内账直扣(对外速率只约束注入),原料/水在结算时一次扣除。
 * 配方查找事件驱动 + 缓存(含负缓存;原料/水/能量变化时失效),每 tick 只推进进度(ADR-0006)。
 * 缺输入即停摆、进度不倒退,补足后续转(#27 棕停同型语义);中途换料致本单作废(能量沉没)。
 * 电网接入(#30,第一个需求方):无图时行为与 #33 完全一致(创造注入路径不变);
 * 贴网后本地缓冲不足部分每 tick 向图申领,按供电比例(棕停)分数推进进度——定点记账防漂移。
 */
class KilnBE(pos: BlockPos, state: BlockState) :
    PowerMemberBE(ModBlockEntityTypes.KILN.get(), pos, state), BlueprintAnchor, HummingMachine {

    companion object {
        /** Buffer 槽位数:不设布局语义,余量即自动化余量;20 覆盖一期配方宽度。 */
        const val BUFFER_SLOTS = 20
        /** 内罐容量(mB):一桶水恰好充满 → 20 轮(#25 决议)。 */
        const val WATER_TANK_CAPACITY = 1000
        /** 每轮耗水(mB):机器语义(CONTEXT:Kiln 水=必需输入),不进配方 JSON。 */
        const val WATER_PER_CRAFT = 50
        /** 本地储能 = 一轮配方能耗(500 FE):Mindustry 非缓冲建筑只存 status×1、储能归电池,
         * 10,000 旧值是隐式大电池,违反 ADR-0007 "节点零储能、电池储能" 模型(#49 grill 修订)。
         * 500 覆盖单轮,本地缓冲先于电网消耗(外部 mod FE 仍可注入,能力保留)。 */
        const val ENERGY_CAPACITY = 500
        const val MAX_ENERGY_RECEIVE = 200
        /** 进度定点记账单位(棕停按 ratio 分数推进,定点防浮点漂移;满速 = 每 tick +UNIT)。 */
        const val PROGRESS_UNIT = 8
    }

    override val currentBlueprint: Blueprint
        get() = (blockState.block as BlueprintAnchorBlock).blueprint
    override val itemCapability: ItemCapabilityImpl =
        createItemCapability(slotCount = BUFFER_SLOTS, isValidItem = { _, stack -> isBufferItem(stack) })

    override val fluidCapability: FluidCapabilityImpl =
        createFluidCapability(
            capacity = WATER_TANK_CAPACITY,
            maxReceive = WATER_TANK_CAPACITY,
            maxExtract = 0,
            isValidFluid = { it.fluid == Fluids.WATER }
        )

    override val energyCapability: EnergyCapabilityImpl =
        createEnergyCapability(capacity = ENERGY_CAPACITY, maxReceive = MAX_ENERGY_RECEIVE, maxExtract = 0)

    /** 供电比例(0..1,公共只读视图,Jade #37 消费;同步到客户端)。 */
    var supplyRatio: Float = 1f


    private var cachedRecipe: MachineRecipe? = null
    /** 查找结果缓存有效位(含"无匹配"负缓存);输入/水/能量变化时失效。 */
    private var recipeKnown = false
    private var progress = 0
    /** 本轮加工总时长;随进度持久化,供客户端(Jade #37)换算百分比。 */
    private var processingTime = 0
    /** 本轮已扣 FE(均摊记账;持久化,防区块重载重复扣)。 */
    private var consumedEnergy = 0

    /** 进度百分比(定点进度换算,Jade #37 消费)。 */
    val progressPercent: Int
        get() = if (processingTime > 0) progress * 100 / (processingTime * PROGRESS_UNIT) else 0

    override fun onContentsChanged(slot: Int) {
        super.onContentsChanged(slot)
        invalidateRecipe()
    }

    override fun onFluidChanged() {
        super.onFluidChanged()
        invalidateRecipe()
    }

    // 能量变化不失效缓存:能耗不影响"哪个配方匹配",否则连续供电下空转机每 tick 重扫配方(ADR-0006)。
    // onEnergyChanged 不覆写,基类仍负责 setChanged + 节流同步。

    /**
     * 失效策略:空转(progress==0)随时可重查;加工中锁定已匹配配方(结算处重校验)。
     * 例外:cachedRecipe 为 null 而 progress>0 是"重载后原料不完整"的卡死态,
     * 放行重查使补料后续转(spec 故事 7 的停摆-续转语义)。
     */
    private fun invalidateRecipe() {
        if (progress == 0 || cachedRecipe == null) {
            cachedRecipe = null
            recipeKnown = false
        }
    }

    /** 配方表扫描(仅插入/取出校验时调用,事件驱动;一期单机器不预建缓存层)。 */
    private fun machineRecipes(): List<MachineRecipe> {
        val lv = level ?: return emptyList()
        return lv.recipeManager.getAllRecipesFor(ModRecipeTypes.MACHINE.get()).map { it.value }
    }

    /** Buffer 准入(CONTEXT:Buffer 只存配方出入物品):命中任一机器配方的原料或产物。 */
    fun isBufferItem(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        return machineRecipes().any { it.matches(stack) || it.produces(stack) }
    }

    /** 是否为任一机器配方的产物(右键取出的优先项,产物自动弹出 #73 的 isProduct 谓词)。 */
    private fun isProductItem(stack: ItemStack): Boolean = machineRecipes().any { it.produces(stack) }
    /** 右键取出的选择:产出优先,否则退回首个存货(玩家清仓通道,无 GUI)。 */
    fun takeBufferStack(): ItemStack? {
        val cap = itemCapability
        var fallback = -1
        for (slot in 0 until cap.slotCount) {
            val stack = cap.getStack(slot)
            if (stack.isEmpty) continue
            if (isProductItem(stack)) return cap.extractItem(slot, stack.count, false)
            if (fallback < 0) fallback = slot
        }
        return if (fallback >= 0) cap.extractItem(fallback, cap.getStack(fallback).count, false) else null
    }

    /**
     * 运行态(客户端 hum 消费,#57/#67):语义 = "本 tick 进度实际推进"——棕停慢速仍在转 → 响;
     * 完全断电/缺料缺水冻结(进度原地) → 淡出。服务端权威,切换时随 update tag 同步;
     * 唯一写入点 = [tickServer] 的进度差判定,各停摆分支无需各自维护标志。
     */
    override var isRunning: Boolean = false
        private set

    /** 仅在运行态切换时同步(避免每 tick 发包);客户端 hum 据此起停。 */
    private fun setRunning(running: Boolean) {
        if (running != isRunning) {
            isRunning = running
            syncData()
        }
    }

    /**
     * 服务端 tick:推进本体拆入 [tickProgress],运行态以进度差为准(#67)——
     * 断电/缺料/空转路径原地 return,进度不动即淡出;结算 tick 进度归零仍算"有推进"。
     */
    fun tickServer() {
        val lv = level ?: return
        val before = progress
        tickProgress(lv)
        setRunning(progress != before)
    }

    /**
     * 加工推进(#33/#30):空转查配方、水检过则开工;加工中按进度均摊扣能,满程结算产出。
     * 本地缓冲不足时向图申领:无图维持 #33 创造注入语义(停摆、进度保持);
     * 有图按供电比例棕停推进——定点记账防漂移。
     */
    private fun tickProgress(lv: Level) {
        // 产物自动弹出(#73):每 tick 把产物向四邻居标准 IItemHandler 转移,收不走留在 Buffer(满载停摆)。
        // 结算产出后下一 tick 自会弹出;转移零分配、单机低成本。
        if (xyz.luobo.mturrets.core.machine.ProductEjector.eject(lv, worldPosition, itemCapability, ::isProductItem)) {
            invalidateRecipe() // 产物移出可能腾出 Buffer 容量,配方缓存随之失效
        }
        if (progress == 0) {
            val recipe = lookupRecipe(lv) ?: return
            // 启动校验:水为必需输入,不足即不开工(停摆、不倒退)
            if (fluidCapability.currentFluid.amount < WATER_PER_CRAFT) return
            processingTime = recipe.processingTime
            consumedEnergy = 0
            progress = PROGRESS_UNIT
            supplyRatio = 1f
            setChanged()
            return
        }

        // 重载恢复路径:加工中的配方按仍完好的原料重新匹配(输入在结算前不扣)
        val recipe = cachedRecipe ?: lookupRecipe(lv) ?: return
        val timeFP = recipe.processingTime * PROGRESS_UNIT
        val due = recipe.energy * progress / timeFP - consumedEnergy
        var step = PROGRESS_UNIT
        if (due > 0) {
            if (energyCapability.currentEnergy < due) {
                val grid = graph
                if (grid == null) return // 无图:能量不足即 #33 停摆、进度保持
                val deficit = due - energyCapability.currentEnergy
                val granted = grid.requestDrain(lv.gameTime, deficit)
                if (granted <= 0) {
                    // 断电起始一脚同步:进度冻结后节流同步永不触发,不补这脚 Jade 供给行会一直显示旧比例
                    val enteredBrownout = supplyRatio > 0f
                    supplyRatio = 0f // 断电:进度保持不倒退
                    if (enteredBrownout) syncData() else setChanged()
                    return
                }
                // 图供能补齐缺口,本 tick 实耗 = 本地余量 + 图给量(棕停时给量 < 缺口)
                energyCapability.currentEnergy += granted
                val drawn = min(due, energyCapability.currentEnergy)
                energyCapability.currentEnergy -= drawn
                consumedEnergy += drawn
                supplyRatio = granted.toFloat() / deficit
                step = PROGRESS_UNIT * granted / deficit
            } else {
                energyCapability.currentEnergy -= due
                consumedEnergy += due
                supplyRatio = 1f
                setChanged()
            }
        }

        // 满程判定在推进后:progress 封顶到 timeFP,下一个 tick 在 progress==timeFP
        // 处计出最后一个 due(energy - consumed)并扣账,随后 settle —— 总耗恰为配方耗能
        if (progress < timeFP) {
            progress = min(progress + step, timeFP)
            if (progress % (PROGRESS_UNIT * 5) == 0) syncData()
            return
        }
        settle(lv, recipe)
    }

    /** 结算:换料破坏匹配或断水则本单作废(能量沉没);否则扣水扣料、产出入 Buffer。 */
    private fun settle(lv: Level, recipe: MachineRecipe) {
        val input = MachineRecipe.Input(bufferSnapshot())
        val valid = recipe.matches(input, lv) && fluidCapability.currentFluid.amount >= WATER_PER_CRAFT
        resetCycle()
        if (!valid) return
        recipe.consume(input)
        drainFluidInternal(WATER_PER_CRAFT)
        for (result in recipe.results) {
            val leftover = ItemHandlerHelper.insertItem(itemCapability, result.copy(), false)
            if (!leftover.isEmpty) {
                // Buffer 理论装不下时才发生:就地散落,确定性不丢失
                net.minecraft.world.Containers.dropItemStack(
                    lv, worldPosition.x + 0.5, worldPosition.y + 0.5, worldPosition.z + 0.5, leftover
                )
            }
        }
    }

    private fun resetCycle() {
        progress = 0
        processingTime = 0
        consumedEnergy = 0
        cachedRecipe = null
        recipeKnown = false
        setChanged()
    }

    private fun lookupRecipe(lv: Level): MachineRecipe? {
        if (!recipeKnown) {
            val stacks = bufferSnapshot()
            cachedRecipe = if (stacks.all { it.isEmpty }) null else {
                val input = MachineRecipe.Input(stacks)
                lv.recipeManager.getAllRecipesFor(ModRecipeTypes.MACHINE.get())
                    .firstOrNull { it.value.matches(input, lv) }
                    ?.value
            }
            recipeKnown = true
        }
        return cachedRecipe
    }

    private fun bufferSnapshot(): List<ItemStack> =
        (0 until itemCapability.slotCount).map { itemCapability.getStack(it) }
    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        tag.putInt("kiln_progress", progress)
        tag.putInt("kiln_time", processingTime)
        tag.putInt("kiln_energy_spent", consumedEnergy)
        tag.putFloat("kiln_ratio", supplyRatio) // 随 getUpdateTag 同步到客户端(#37 显示层取数)
        tag.putBoolean("kiln_is_running", isRunning)
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        progress = tag.getInt("kiln_progress")
        processingTime = tag.getInt("kiln_time")
        consumedEnergy = tag.getInt("kiln_energy_spent")
        supplyRatio = tag.getFloat("kiln_ratio").coerceIn(0f, 1f)
        isRunning = tag.getBoolean("kiln_is_running")
    }

    override fun getUpdatePacket(): ClientboundBlockEntityDataPacket =
        ClientboundBlockEntityDataPacket.create(this)

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag =
        saveWithoutMetadata(registries)
}
