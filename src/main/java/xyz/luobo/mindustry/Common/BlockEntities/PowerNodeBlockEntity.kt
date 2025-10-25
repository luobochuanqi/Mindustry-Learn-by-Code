package xyz.luobo.mindustry.Common.BlockEntities

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.network.Connection
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mindustry.Client.Renderers.LaserRenderer
import xyz.luobo.mindustry.Common.ModBlockEntities
import xyz.luobo.mindustry.Mindustry

class PowerNodeBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(ModBlockEntities.POWER_NODE_BLOCK_ENTITY.get(), pos, state) {

    // 存储相连的其他电力节点位置
    private val connectedNodes = mutableSetOf<BlockPos>()

    // 能量相关变量
    private var energyStored = 0
    private val maxEnergyStorage = 10000

    // 用于客户端渲染的标记
    var shouldRenderConnections = true

    // 每个节点独立的发现冷却计时器
    private var discoveryCooldown = 0
    private val DISCOVERY_INTERVAL = 20 // 每 20 ticks（约 1 秒）执行一次发现

    companion object {
        fun serverTick(level: Level, pos: BlockPos, state: BlockState, powerNodeBE: PowerNodeBlockEntity) {
            // 确保只在服务端执行
            if (level.isClientSide) return
            // 每隔一段时间执行一次发现逻辑
            if (powerNodeBE.discoveryCooldown <= 0) {
                powerNodeBE.discoverNearbyNodes(level)
                powerNodeBE.discoveryCooldown = powerNodeBE.DISCOVERY_INTERVAL // 重置计时器
            } else {
                powerNodeBE.discoveryCooldown-- // 递减计时器
            }

            // 定期验证连接是否仍然有效
            if (level.gameTime % 20L == 0L) {
                powerNodeBE.validateConnections()
            }
        }

        // 最大连接距离
        const val MAX_CONNECTION_DISTANCE = 5.0

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
            Mindustry.LOGGER.debug("Added connection from {} to {}", worldPosition, otherPos)
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

        val distance = worldPosition.distSqr(otherPos)
        if (distance > MAX_CONNECTION_DISTANCE * MAX_CONNECTION_DISTANCE) return false

        return level.getBlockEntity(otherPos) is PowerNodeBlockEntity
    }

    // 自动发现并连接附近的电力节点
    fun discoverNearbyNodes(level: Level) {
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

    // 能量传输逻辑
    fun transferEnergy() {
        level ?: return

        for (connectedPos in connectedNodes) {
            val targetEntity = level!!.getBlockEntity(connectedPos) as? PowerNodeBlockEntity ?: continue

            if (energyStored > 0 && targetEntity.canReceiveEnergy()) {
                val energyToTransfer = minOf(energyStored, ENERGY_TRANSFER_RATE, targetEntity.getEnergySpace())

                if (energyToTransfer > 0) {
                    energyStored -= energyToTransfer
                    targetEntity.receiveEnergy(energyToTransfer)
                    setChanged()
                }
            }
        }
    }

    // 能量相关方法
    fun getEnergyStored(): Int = energyStored
    fun getMaxEnergyStored(): Int = maxEnergyStorage
    fun getEnergySpace(): Int = maxEnergyStorage - energyStored

    fun canReceiveEnergy(): Boolean = energyStored < maxEnergyStorage

    fun receiveEnergy(amount: Int): Int {
        val energyReceived = minOf(amount, getEnergySpace())
        energyStored += energyReceived
        setChanged()
        return energyReceived
    }

    fun extractEnergy(amount: Int): Int {
        val energyExtracted = minOf(amount, energyStored)
        energyStored -= energyExtracted
        setChanged()
        return energyExtracted
    }

    // 验证所有连接是否仍然有效
    private fun validateConnections() {
        level ?: return

        val invalidConnections = connectedNodes.filter { pos ->
            !canConnectTo(pos, level!!) || level!!.getBlockEntity(pos) !is PowerNodeBlockEntity
        }

        invalidConnections.forEach { removeConnection(it) }
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
        tag.putInt("energy", energyStored)

        // 保存渲染标记
        tag.putBoolean("renderConnections", shouldRenderConnections)
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
        energyStored = tag.getInt("energy")

        // 加载渲染标记
        shouldRenderConnections = tag.getBoolean("renderConnections")
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
}