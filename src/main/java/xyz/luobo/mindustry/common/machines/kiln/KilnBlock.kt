package xyz.luobo.mindustry.common.machines.kiln

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mindustry.common.ModBlockEntityTypes
import xyz.luobo.mindustry.core.machine.BaseMachineBlock

class KilnBlock : BaseMachineBlock<KilnBE>(Properties.of()) {

    companion object {
        @JvmStatic
        val CODEC: MapCodec<KilnBlock> = simpleCodec(
            { KilnBlock() }, // 工厂函数，接收 Properties 并创建实例
            // 可选：为 codec 指定一个字段名，如果不指定，默认可能是 "properties" 或类似
            // 如果你的方块构造函数有其他参数，或者需要更复杂的序列化，这里需要调整
        )
    }

    override fun codec(): MapCodec<out BaseEntityBlock?> {
        return CODEC
    }

    override fun getBlockEntityType(): BlockEntityType<KilnBE> {
        return ModBlockEntityTypes.KILN_BLOCK_ENTITY.get()
    }

    override fun newBlockEntity(
        pos: BlockPos,
        state: BlockState
    ): BlockEntity? {
        return KilnBE(pos = pos, state = state)
    }
}