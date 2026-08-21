package xyz.luobo.mturrets.common.blockEntities

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.network.Connection
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.energy.IEnergyStorage
import xyz.luobo.mturrets.common.ModBlockEntityTypes
import xyz.luobo.mturrets.core.MTurretsModBlockEntity
import xyz.luobo.mturrets.core.capability.IEnergyCapability
class PowerNodeBlockEntity(pos: BlockPos, state: BlockState) :
    MTurretsModBlockEntity(ModBlockEntityTypes.POWER_NODE_BLOCK_ENTITY.get(), pos, state), IEnergyStorage {
    // 存储相连的其他电力节点位置
    private val connectedNodes = mutableSetOf<BlockPos>()

    // 能量配置
    private val energyCapacity: Int = 24000  // 能量上限
    private val maxReceive: Int = 200  // 每次最多接收多少
    private val maxExtract: Int = 200  // 每次最多提取多少

    // 用于客户端渲染的标记
    var shouldRenderConnections = true
    var isAwaitingConnection = false

    // ========== Capability 配置 ==========

    /**
     * 能量 Capability
     */
    override val energyCapability by lazy {
        createEnergyCapability(
            capacity = energyCapacity,
            maxReceive = maxReceive,
            maxExtract = maxExtract
        )
    }

    /**
     * 便捷访问能量存储
     */
    private val energyStorage: IEnergyCapability
        get() = energyCapability

    companion object {
        /**
         * 服务端网络 tick:节点间按速率传输,并向相邻机器/炮台供电
         */
        fun serverTick(level: Level, pos: BlockPos, state: BlockState, powerNodeBE: PowerNodeBlockEntity) {
            // 确保只在服务端执行
            if (level.isClientSide) return

            // 周期性校验连接有效性(距离/连接数限制)
            if (level.gameTime % 100 == 0L) {
                powerNodeBE.validateConnections()
            }

            powerNodeBE.transferEnergyToNetwork(level)
        }

        // 最大连接距离
        const val MAX_CONNECTION_DISTANCE = 6.0

        // 最大连接数量
        const val MAX_CONNECTION_NUMBER = 10

        // 每tick传输的能量
        const val ENERGY_TRANSFER_RATE = 100
    }

    // ========== 能量传输 ==========

    /**
     * 向相连节点与相邻能量设备按速率传输能量
     * 节点间:走 connectedNodes(距离/数量受控)
     * 节点到设备:向 6 个相邻方块的能量能力注入(机器/炮台取电)
     */
    private fun transferEnergyToNetwork(level: Level) {
        if (energyStorage.currentEnergy <= 0) return

        // 1. 相连节点间传输
        for (otherPos in connectedNodes) {
            if (energyStorage.currentEnergy <= 0) break
            val target = level.getCapability(Capabilities.EnergyStorage.BLOCK, otherPos, null)
            if (target != null) {
                transferTo(target)
            }
        }

        // 2. 相邻机器/炮台供电(电力节点自身除外,节点走上面的网络通道)
        if (energyStorage.currentEnergy <= 0) return
        for (dir in Direction.entries) {
            if (energyStorage.currentEnergy <= 0) break
            val neighborPos = worldPosition.relative(dir)
            if (level.getBlockEntity(neighborPos) is PowerNodeBlockEntity) continue
            val target = level.getCapability(Capabilities.EnergyStorage.BLOCK, neighborPos, dir.opposite)
            if (target != null) {
                transferTo(target)
            }
        }
    }

    /**
     * 向目标存储传输能量,受本节点/目标节点速率与容量限制
     */
    private fun transferTo(target: IEnergyStorage) {
        val space = target.maxEnergyStored - target.energyStored
        val toSend = minOf(ENERGY_TRANSFER_RATE, energyStorage.currentEnergy, space)
        if (toSend <= 0) return

        val extracted = energyStorage.extractEnergy(toSend, false)
        if (extracted <= 0) return

        val accepted = target.receiveEnergy(extracted, false)
        // 理论上 accepted == extracted(空间已预先校验),差额返还
        if (accepted < extracted) {
            energyStorage.receiveEnergy(extracted - accepted, false)
        }
    }

    override fun onLoad() {
        super.onLoad()
    }

    override fun setRemoved() {
        for (otherPos in connectedNodes.toList()) {
            val blockEntity = level?.getBlockEntity(otherPos) as? PowerNodeBlockEntity
            blockEntity?.removeConnection(worldPosition)
        }
        super.setRemoved()
    }

    override fun onChunkUnloaded() {
        super.onChunkUnloaded()
    }

    // 获取所有连接的节点
    fun getConnectedNodes(): Set<BlockPos> = connectedNodes.toSet()

    // 添加连接
    fun addConnection(otherPos: BlockPos) {
        if (connectedNodes.add(otherPos)) {
            setChanged()
            syncToClient()
        }
    }

    // 移除连接
    fun removeConnection(otherPos: BlockPos) {
        if (connectedNodes.remove(otherPos)) {
            setChanged()
            syncToClient()
        }
    }

    // 清除所有连接
    fun clearConnections() {
        if (connectedNodes.isNotEmpty()) {
            connectedNodes.clear()
            setChanged()
            syncToClient()
        }
    }

    // 检查是否可以连接到指定位置
    fun canConnectTo(otherPos: BlockPos, level: Level): Boolean {
        if (worldPosition == otherPos) return false
        // 获取对方实体
        val otherBE = level.getBlockEntity(otherPos) as? PowerNodeBlockEntity ?: return false
        // 检查对方是否已达最大连接数
        if (otherBE.connectedNodes.size >= MAX_CONNECTION_NUMBER) return false
        // 检查自己是否已达到最大连接数
        if (connectedNodes.size >= MAX_CONNECTION_NUMBER) return false

        val distance = worldPosition.distSqr(otherPos)
        if (distance > MAX_CONNECTION_DISTANCE * MAX_CONNECTION_DISTANCE) return false

        return level.getBlockEntity(otherPos) is PowerNodeBlockEntity
    }

    // 在放置时 自动发现并连接附近的电力节点
    fun discoverAndConnectNearbyNodes(level: Level) {
        val radius = MAX_CONNECTION_DISTANCE.toInt()

        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    val checkPos = worldPosition.offset(x, y, z)

                    if (canConnectTo(checkPos, level)) {
                        addConnection(checkPos)

                        // 双向连接
                        (level.getBlockEntity(checkPos) as? PowerNodeBlockEntity)?.addConnection(worldPosition)
                    }
                }
            }
        }
    }

    // 验证所有连接是否仍然有效
    private fun validateConnections() {
        level ?: return

        val invalidConnections = connectedNodes.filter { pos ->
            !canConnectTo(pos, level!!) || level!!.getBlockEntity(pos) !is PowerNodeBlockEntity
        }

        invalidConnections.forEach { removeConnection(it) }
    }

    // 切换待连接状态
    fun toggleConnectionMode() {
        isAwaitingConnection = !isAwaitingConnection
        setChanged()
        syncToClient()
    }

    // 序列化数据
    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)

        // 保存连接信息
        val connectionsList = ListTag()
        connectedNodes.forEach { pos ->
            val posTag = CompoundTag()
            posTag.putInt("x", pos.x)
            posTag.putInt("y", pos.y)
            posTag.putInt("z", pos.z)
            connectionsList.add(posTag)
        }
        tag.put("connections", connectionsList)

        // 保存渲染标记
        tag.putBoolean("renderConnections", shouldRenderConnections)
        tag.putBoolean("awaitingConnection", isAwaitingConnection)

        // 能量数据由 ModBlockEntity 自动保存
    }

    // 反序列化数据
    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)

        // 加载连接信息
        connectedNodes.clear()
        val connectionsList = tag.getList("connections", Tag.TAG_COMPOUND.toInt())
        connectionsList.forEach { posTag ->
            if (posTag is CompoundTag) {
                val pos = BlockPos(
                    posTag.getInt("x"),
                    posTag.getInt("y"),
                    posTag.getInt("z")
                )
                connectedNodes.add(pos)
            }
        }

        // 加载渲染标记
        shouldRenderConnections = tag.getBoolean("renderConnections")
        isAwaitingConnection = tag.getBoolean("awaitingConnection")

        // 能量数据由 ModBlockEntity 自动加载
    }

    // 客户端同步数据包
    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag {
        val tag = CompoundTag()
        saveAdditional(tag, registries)
        return tag
    }

    override fun getUpdatePacket(): ClientboundBlockEntityDataPacket {
        return ClientboundBlockEntityDataPacket.create(this)
    }

    override fun onDataPacket(
        net: Connection,
        pkt: ClientboundBlockEntityDataPacket,
        lookupProvider: HolderLookup.Provider
    ) {
        loadAdditional(pkt.tag, lookupProvider)
        level?.sendBlockUpdated(worldPosition, blockState, blockState, 3)
    }

    // 同步到客户端
    private fun syncToClient() {
        level?.let { level ->
            level.sendBlockUpdated(worldPosition, blockState, blockState, 3)
            setChanged()
        }
    }

    // ========== IEnergyStorage 接口实现 ==========

    // 委托给 energyCapability
    override fun receiveEnergy(toReceive: Int, simulate: Boolean): Int {
        return energyStorage.receiveEnergy(toReceive, simulate)
    }

    override fun extractEnergy(toExtract: Int, simulate: Boolean): Int {
        return energyStorage.extractEnergy(toExtract, simulate)
    }

    override fun getEnergyStored(): Int {
        return energyStorage.currentEnergy
    }

    override fun getMaxEnergyStored(): Int {
        return energyStorage.energyCapacity
    }

    override fun canExtract(): Boolean {
        return energyStorage.canExtract()
    }

    override fun canReceive(): Boolean {
        return energyStorage.canReceive()
    }
}