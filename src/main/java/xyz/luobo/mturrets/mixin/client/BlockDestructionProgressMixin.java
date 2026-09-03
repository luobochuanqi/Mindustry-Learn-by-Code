package xyz.luobo.mturrets.mixin.client;

import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;

import xyz.luobo.mturrets.core.structure.BlockDestructionProgressExtension;
/** 给原版 BlockDestructionProgress 附加额外裂纹位置集的 mixin(#42),见 LevelRendererMixin。 */
@Mixin(BlockDestructionProgress.class)
public class BlockDestructionProgressMixin implements BlockDestructionProgressExtension {
	@Unique
	private Set<BlockPos> mturrets$extraPositions;

	@Override
	public Set<BlockPos> mturrets$getExtraPositions() {
		return mturrets$extraPositions;
	}

	@Override
	public void mturrets$setExtraPositions(@Nullable Set<BlockPos> positions) {
		mturrets$extraPositions = positions;
	}
}
