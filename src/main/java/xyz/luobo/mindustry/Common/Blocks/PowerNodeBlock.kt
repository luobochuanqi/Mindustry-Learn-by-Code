package xyz.luobo.mindustry.Common.Blocks

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mindustry.Common.BlockEntities.PowerNodeBlockEntity
import xyz.luobo.mindustry.Common.ModBlockEntities

class PowerNodeBlock: BaseEntityBlock(Properties.of().strength(2.0f).requiresCorrectToolForDrops()) {
//    val CODEC: MapCodec<PowerNodeBlock?> = simpleCodec<PowerNodeBlock?>( { properties: Properties? -> PowerNodeBlock() })

    // 定义一个公共的、不可变的 CODEC 实例
    companion object {
        // 使用 simpleCodec 为 PowerNodeBlock 生成一个 MapCodec
        // { properties -> PowerNodeBlock(properties) } 是一个 lambda，作为创建实例的工厂
        @JvmStatic
        val CODEC: MapCodec<PowerNodeBlock> = simpleCodec(
            { PowerNodeBlock() }, // 工厂函数，接收 Properties 并创建实例
            // 可选：为 codec 指定一个字段名，如果不指定，默认可能是 "properties" 或类似
            // 如果你的方块构造函数有其他参数，或者需要更复杂的序列化，这里需要调整
        )
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? {
        return PowerNodeBlockEntity(pos = pos, state = state)
    }

//    @Suppress("UNCHECKED_CAST")
    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        blockEntityType: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        // **手动类型检查**
        if (blockEntityType == ModBlockEntities.POWER_NODE_BLOCK_ENTITY.get()) {
            // 确保只在服务器端返回服务器端 ticker
            if (level.isClientSide) {
                return null // 客户端不需要服务器端 ticker
            }
            // **关键：返回一个适配器，而不是直接转换函数引用**
            // 这个 lambda 或匿名类需要符合 BlockEntityTicker<T> 的签名
            // 由于 T 已经被 blockEntityType 限定，这里 T 就是 PowerNodeBlockEntity
            // 所以我们可以安全地将传入的 blockEntity 向下转换为 PowerNodeBlockEntity
            @Suppress("UNCHECKED_CAST") // 这是唯一需要抑制警告的地方，且在类型检查后是安全的
            val ticker = BlockEntityTicker<T> { level, pos, state, blockEntity ->
                // 在这个 lambda 内部，我们知道 blockEntityType 是 PowerNodeBlockEntity 的类型
                // 所以 blockEntity 实际上就是 PowerNodeBlockEntity
                val powerNodeBE = blockEntity as PowerNodeBlockEntity // 这里是安全的，因为上面检查了类型
                PowerNodeBlockEntity.serverTick(level, pos, state, powerNodeBE)
            }
            return ticker
        }
        return null
    }

    override fun codec(): MapCodec<out BaseEntityBlock?> {
        return CODEC
    }

    override fun getRenderShape(state: BlockState): RenderShape {
        return RenderShape.MODEL
    }
}