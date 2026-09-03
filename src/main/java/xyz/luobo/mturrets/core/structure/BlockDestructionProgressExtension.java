package xyz.luobo.mturrets.core.structure;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;

/**
 * 给原版 BlockDestructionProgress 附加的额外裂纹位置集(#42),
 * 由 LevelRendererMixin 写入、removeProgress 时同步清理。
 */
public interface BlockDestructionProgressExtension {
	@Nullable
	Set<BlockPos> mturrets$getExtraPositions();

	void mturrets$setExtraPositions(@Nullable Set<BlockPos> positions);
}
