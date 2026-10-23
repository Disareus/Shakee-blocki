package com.shakeeblocki.mixin;

import com.shakeeblocki.animation.ShakeeAnimationManager;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ModelBlockRenderer.class)
public abstract class BlockModelRendererMixin {
    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true)
    private void forceFaceNextToInvisibleBlock(
            BlockAndTintGetter level,
            BlockState state,
            Direction direction,
            BlockPos neighborPos,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!ShakeeAnimationManager.hasActiveAnimations()) {
            return;
        }

        if (ShakeeAnimationManager.isAnimatedOrInvisible(neighborPos)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "tesselateBlock", at = @At("HEAD"), cancellable = true)
    private void skipInvisibleAnimatedBlock(
            BlockQuadOutput output,
            float f,
            float g,
            float h,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            BlockStateModel model,
            long seed,
            CallbackInfo ci
    ) {
        if (!ShakeeAnimationManager.hasActiveAnimations()) {
            return;
        }

        if (!(level instanceof MovingBlockRenderState) && ShakeeAnimationManager.isInvisible(pos)) {
            ci.cancel();
        }
    }
}
