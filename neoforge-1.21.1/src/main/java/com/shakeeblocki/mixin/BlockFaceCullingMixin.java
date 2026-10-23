package com.shakeeblocki.mixin;

import com.shakeeblocki.animation.ShakeeAnimationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Port note: 1.21.1 does static face culling in Block#shouldRenderFace - newer versions
 * moved the check into ModelBlockRenderer itself.
 */
@Mixin(Block.class)
public abstract class BlockFaceCullingMixin {

    @Inject(
            method = "shouldRenderFace(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void shakee$forceFaceNextToInvisibleBlock(
            BlockState state,
            BlockGetter level,
            BlockPos fromPos,
            Direction direction,
            BlockPos neighborPos,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!ShakeeAnimationManager.hasActiveAnimations()) {
            return;
        }
        if (ShakeeAnimationManager.isAnimatedOrInvisible(neighborPos)) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}
