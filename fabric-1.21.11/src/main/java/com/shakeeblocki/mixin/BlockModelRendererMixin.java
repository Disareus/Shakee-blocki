package com.shakeeblocki.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.shakeeblocki.animation.ShakeeAnimationManager;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ModelBlockRenderer.class)
public abstract class BlockModelRendererMixin {
    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true)
    private static void forceFaceNextToInvisibleBlock(
            BlockAndTintGetter level,
            BlockState state,
            boolean checkSides,
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
            BlockAndTintGetter level,
            List<BlockModelPart> parts,
            BlockState state,
            BlockPos pos,
            PoseStack poseStack,
            VertexConsumer consumer,
            boolean checkSides,
            int packedOverlay,
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
