package xyz.luobo.mindustry.common.blocks

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import xyz.luobo.mindustry.common.ModBlockEntityTypes
import xyz.luobo.mindustry.common.blockEntities.PowerNodeBlockEntity

class PowerNodeBlock: BaseEntityBlock(Properties.of()
    .lightLevel { state -> 15 }
    .strength(2.0f)
    .requiresCorrectToolForDrops()
) {

    companion object {
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

    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        blockEntityType: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        // 手动类型检查
        if (blockEntityType == ModBlockEntityTypes.POWER_NODE_BLOCK_ENTITY.get()) {
            // 确保只在服务器端返回服务器端 ticker
            if (level.isClientSide) {
                return null // 客户端不需要服务器端 ticker
            }
            // 关键：返回一个适配器，而不是直接转换函数引用
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

    override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, movedByPiston: Boolean) {
        super.onPlace(state, level, pos, oldState, movedByPiston)

        // 只在服务端执行，并且不是替换已有方块的情况
        if (!level.isClientSide && oldState.block != this) {
            val blockEntity = level.getBlockEntity(pos) as? PowerNodeBlockEntity
            blockEntity?.discoverNearbyNodes(level)
        }
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult {
        if (level.isClientSide) return InteractionResult.PASS
        val itemInHand = player.getItemInHand(InteractionHand.MAIN_HAND)
        if (itemInHand.isEmpty) {
            val blockEntity = level.getBlockEntity(pos) as? PowerNodeBlockEntity
            blockEntity?.toggleConnectionMode()
            return InteractionResult.SUCCESS
        }
        return InteractionResult.PASS
    }
}