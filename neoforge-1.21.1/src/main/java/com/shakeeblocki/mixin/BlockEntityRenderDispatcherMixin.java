package com.shakeeblocki.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shakeeblocki.animation.ShakeeAnimationManager;
import com.shakeeblocki.animation.ShakeeAnimationState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Port note: 1.21.1 renders each block entity through BlockEntityRenderDispatcher#render
 * (newer versions moved to the submit/render-state pipeline). Single mixin covers all
 * block entities including chests - the manager resolves linked chest halves itself.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {

    @Inject(
            method = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("HEAD")
    )
    private void applyAnimation(
            BlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            CallbackInfo ci
    ) {
        poseStack.pushPose();
        if (!ShakeeAnimationManager.hasActiveAnimations() || blockEntity == null) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }

        ShakeeAnimationState animation = ShakeeAnimationManager.getForRender(client.level, blockEntity.getBlockPos());
        if (animation == null) {
            return;
        }

        float tickDelta = client.getTimer().getGameTimeDeltaPartialTick(false);
        animation.applyLocal(poseStack, ShakeeAnimationManager.getClientTicks(), tickDelta);
    }

    @Inject(
            method = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("RETURN")
    )
    private void popAnimation(
            BlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            CallbackInfo ci
    ) {
        poseStack.popPose();
    }
}
