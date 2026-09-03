package xyz.luobo.mturrets.mixin.client;

import java.util.Set;
import java.util.SortedSet;

import com.google.common.collect.Sets;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.level.block.state.BlockState;

import xyz.luobo.mturrets.core.structure.BlockDestructionProgressExtension;
import xyz.luobo.mturrets.core.structure.MultiPosDestructionHandler;

/**
 * 多方块破坏裂纹代理(#42,Create 同款):任一格收到破坏进度时,把同一进度对象
 * 挂到全结构其余格上同步渲染裂纹。原版一个 breaker 只对应一个 pos,故经
 * BlockDestructionProgress 附加位置集,removeProgress 时一并清理。
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
	@Shadow
	private ClientLevel level;

	@Shadow
	@Final
	private Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress;

	@Inject(method = "destroyBlockProgress(ILnet/minecraft/core/BlockPos;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/BlockDestructionProgress;updateTick(I)V", shift = Shift.AFTER), locals = LocalCapture.CAPTURE_FAILHARD)
	private void mturrets$onDestroyBlockProgress(int breakerId, BlockPos pos, int progress, CallbackInfo ci, BlockDestructionProgress progressObj) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof MultiPosDestructionHandler handler) {
			Set<BlockPos> extraPositions = handler.getExtraPositions(level, pos, state, progress);
			if (extraPositions != null) {
				extraPositions.remove(pos);
				((BlockDestructionProgressExtension) progressObj).mturrets$setExtraPositions(extraPositions);
				for (BlockPos extraPos : extraPositions) {
					destructionProgress.computeIfAbsent(extraPos.asLong(), l -> Sets.newTreeSet()).add(progressObj);
				}
			}
		}
	}

	@Inject(method = "removeProgress(Lnet/minecraft/server/level/BlockDestructionProgress;)V", at = @At("RETURN"))
	private void mturrets$onRemoveProgress(BlockDestructionProgress progress, CallbackInfo ci) {
		Set<BlockPos> extraPositions = ((BlockDestructionProgressExtension) progress).mturrets$getExtraPositions();
		if (extraPositions != null) {
			for (BlockPos extraPos : extraPositions) {
				long l = extraPos.asLong();
				Set<BlockDestructionProgress> set = destructionProgress.get(l);
				if (set != null) {
					set.remove(progress);
					if (set.isEmpty()) {
						destructionProgress.remove(l);
					}
				}
			}
		}
	}
}
