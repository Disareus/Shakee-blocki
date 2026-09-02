package com.shakeeblocki.mixin.sodium;

import com.shakeeblocki.animation.ShakeeAnimationManager;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockRenderer.class)
public class BlockRendererMixin {
    @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void hideInvisibleBlock(
            BlockStateModel model,
            BlockState state,
            BlockPos pos,
            BlockPos origin,
            CallbackInfo ci
    ) {
        if (ShakeeAnimationManager.hasActiveAnimations() && ShakeeAnimationManager.isInvisible(pos)) {
            ci.cancel();
        }
    }
}
