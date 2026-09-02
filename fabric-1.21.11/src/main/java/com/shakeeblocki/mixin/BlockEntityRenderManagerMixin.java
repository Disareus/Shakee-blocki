package com.shakeeblocki.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shakeeblocki.animation.ShakeeAnimationManager;
import com.shakeeblocki.animation.ShakeeAnimationState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderManagerMixin {
    @Inject(method = "submit", at = @At("HEAD"))
    private void applyAnimation(
            BlockEntityRenderState renderState,
            PoseStack poseStack,
            SubmitNodeCollector submitter,
            CameraRenderState camera,
            CallbackInfo ci
    ) {
        poseStack.pushPose();
        if (!ShakeeAnimationManager.hasActiveAnimations() || renderState == null || renderState.blockPos == null || renderState instanceof ChestRenderState) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }

        ShakeeAnimationState animation = ShakeeAnimationManager.getForRender(client.level, renderState.blockPos);
        if (animation == null) {
            return;
        }

        float tickDelta = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        animation.applyLocal(poseStack, ShakeeAnimationManager.getClientTicks(), tickDelta);
    }

    @Inject(method = "submit", at = @At("RETURN"))
    private void popAnimation(
            BlockEntityRenderState renderState,
            PoseStack poseStack,
            SubmitNodeCollector submitter,
            CameraRenderState camera,
            CallbackInfo ci
    ) {
        poseStack.popPose();
    }
}
