package com.shakeeblocki.mixin.sodium;

import com.shakeeblocki.animation.ShakeeAnimationManager;
import net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlockRenderContext.class)
public abstract class BlockOcclusionCacheMixin {
    @Shadow
    protected BlockPos pos;

    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void forceFaceNearInvisibleBlock(
            Direction direction,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!ShakeeAnimationManager.hasActiveAnimations() || direction == null || this.pos == null) {
            return;
        }

        BlockPos neighbor = this.pos.relative(direction);
        if (ShakeeAnimationManager.isAnimatedOrInvisible(neighbor)) {
            cir.setReturnValue(true);
        }
    }
}
