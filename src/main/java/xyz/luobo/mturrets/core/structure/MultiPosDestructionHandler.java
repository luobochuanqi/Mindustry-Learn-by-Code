package xyz.luobo.mturrets.core.structure;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 多方块破坏裂纹代理(#42,Create MultiPosDestructionHandler 同款):实现方声明
 * 「某格收到破坏进度时,还应同步显示裂纹的其余格」。仅客户端 LevelRenderer
 * mixin 调用;签名引用 client 类与 Create 一致,服务端不触达即不加载。
 */
public interface MultiPosDestructionHandler {
	/**
	 * 返回可变集合,须包含调用格本身(mixin 会将其移除);单格结构返回 null。
	 */
	@Nullable
	Set<BlockPos> getExtraPositions(ClientLevel level, BlockPos pos, BlockState blockState, int progress);
}
