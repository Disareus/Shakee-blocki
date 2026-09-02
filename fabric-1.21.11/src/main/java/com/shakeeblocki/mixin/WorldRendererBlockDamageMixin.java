package com.shakeeblocki.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.shakeeblocki.animation.ShakeeAnimationManager;
import com.shakeeblocki.animation.ShakeeAnimationState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.state.BlockBreakingRenderState;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class WorldRendererBlockDamageMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(
            method = "renderBlockDestroyAnimation",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V",
                    shift = At.Shift.AFTER
            )
    )
    private void applyDamageAnimation(
            PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource,
            LevelRenderState levelRenderState,
            CallbackInfo ci,
            @Local BlockBreakingRenderState state
    ) {
        if (!ShakeeAnimationManager.hasActiveAnimations() || this.minecraft.level == null || state == null || state.blockPos == null) {
            return;
        }

        ShakeeAnimationState animation = ShakeeAnimationManager.getBreaking(state.blockPos);
        if (animation == null) {
            return;
        }

        BlockState blockState = state.blockState;
        Vec3 offset = blockState != null ? blockState.getOffset(state.blockPos) : Vec3.ZERO;
        float tickDelta = this.minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        poseStack.translate(offset.x, offset.y, offset.z);
        animation.applyLocal(poseStack, ShakeeAnimationManager.getClientTicks(), tickDelta);
        poseStack.translate(-offset.x, -offset.y, -offset.z);
    }
}
