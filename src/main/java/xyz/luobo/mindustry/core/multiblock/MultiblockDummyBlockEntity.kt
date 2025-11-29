package xyz.luobo.mindustry.core.multiblock

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

/**
 * 多方块结构部件的方块实体
 * 存储控制器的位置信息
 */
open class MultiblockDummyBlockEntity(type: BlockEntityType<*>, pos: BlockPos, state: BlockState) :
    BlockEntity(type, pos, state) {

    private var controllerPos: BlockPos? = null

    /**
     * 设置控制器位置
     */
    fun setControllerPos(pos: BlockPos) {
        this.controllerPos = pos
    }

    /**
     * 获取控制器位置
     */
    fun getControllerPos(): BlockPos? {
        return this.controllerPos
    }

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        controllerPos?.let { pos ->
            tag.putInt("controller_x", pos.x)
            tag.putInt("controller_y", pos.y)
            tag.putInt("controller_z", pos.z)
        }
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        if (tag.contains("controller_x") && tag.contains("controller_y") && tag.contains("controller_z")) {
            controllerPos = BlockPos(
                tag.getInt("controller_x"),
                tag.getInt("controller_y"),
                tag.getInt("controller_z")
            )
        }
    }
}