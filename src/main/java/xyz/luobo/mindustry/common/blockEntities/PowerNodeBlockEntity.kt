package xyz.luobo.mindustry.common.blockEntities

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.network.Connection
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.util.Mth
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.energy.IEnergyStorage
import xyz.luobo.mindustry.client.renderers.LaserRenderer
import xyz.luobo.mindustry.common.ModBlockEntityTypes
import kotlin.math.min

class PowerNodeBlockEntity(pos: BlockPos, state: BlockState):
    BlockEntity(ModBlockEntityTypes.POWER_NODE_BLOCK_ENTITY.get(), pos, state), IEnergyStorage {
    // 存储相连的其他电力节点位置
    private val connectedNodes = mutableSetOf<BlockPos>()

    // 能量相关变量
    private var energy = 0  // 当前存储的能量
    private val capacity: Int = 24000  // 能量上限
    private val maxReceive: Int = 200  // 每次最多接收多少
    private val maxExtract: Int = 200  // 每次最多提取多少

    // 用于客户端渲染的标记
    var shouldRenderConnections = true
    var isAwaitingConnection = false

    companion object {
        fun serverTick(level: Level, pos: BlockPos, state: BlockState, powerNodeBE: PowerNodeBlockEntity) {
            // 确保只在服务端执行
            if (level.isClientSide) return
        }

        // 最大连接距离
        const val MAX_CONNECTION_DISTANCE = 6.0
        // 最大连接数量
        const val MAX_CONNECTION_NUMBER = 10
        // 每tick传输的能量
        const val ENERGY_TRANSFER_RATE = 100
    }

    override fun onLoad() {
        super.onLoad()
        if (level?.isClientSide == true) {
            // 当方块实体加载时，添加到渲染列表
            LaserRenderer.addToRenderList(worldPosition)
        }
    }

    override fun setRemoved() {
        if (level?.isClientSide == true) {
            // 当方块实体被移除时，从渲染列表中移除
            LaserRenderer.removeFromRenderList(worldPosition)

        }
        for (otherPos in connectedNodes.toList()) {
            val blockEntity = level?.getBlockEntity(otherPos) as? PowerNodeBlockEntity
            blockEntity?.removeConnection(worldPosition)
        }
        super.setRemoved()
    }

    override fun onChunkUnloaded() {
        if (level?.isClientSide == true) {
            // 当区块卸载时，从渲染列表中移除
            LaserRenderer.removeFromRenderList(worldPosition)
        }
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

        // 保存能量
        tag.putInt("energy", energy)

        // 保存渲染标记
        tag.putBoolean("renderConnections", shouldRenderConnections)
        tag.putBoolean("awaitingConnection", isAwaitingConnection)
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

        // 加载能量
        energy = tag.getInt("energy")

        // 加载渲染标记
        shouldRenderConnections = tag.getBoolean("renderConnections")
        isAwaitingConnection = tag.getBoolean("awaitingConnection")
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

    // 能量相关方法

    override fun receiveEnergy(toReceive: Int, simulate: Boolean): Int {
        if (this.canReceive() && toReceive > 0) {
            val energyReceived = Mth.clamp(this.capacity - this.energy, 0, min(this.maxReceive, toReceive))
            if (!simulate) {
                this.energy += energyReceived
            }
            return energyReceived
        } else {
            return 0
        }
    }

    override fun extractEnergy(toExtract: Int, simulate: Boolean): Int {
        if (this.canExtract() && toExtract > 0) {
            val energyExtracted = min(this.energy, min(this.maxExtract, toExtract))
            if (!simulate) {
                this.energy -= energyExtracted
            }
            return energyExtracted
        } else {
            return 0
        }
    }

    override fun getEnergyStored(): Int {
        return energy
    }

    override fun getMaxEnergyStored(): Int {
        return capacity
    }

    override fun canExtract(): Boolean {
        return this.maxExtract > 0
    }

    override fun canReceive(): Boolean {
        return this.maxReceive > 0
    }
}