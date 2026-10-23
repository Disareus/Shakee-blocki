package com.shakeeblocki.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.shakeeblocki.animation.ShakeeAnimationManager;
import com.shakeeblocki.animation.ShakeeAnimationState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Port note: 1.21.1 still takes the entity + pos/state parameters directly; newer
 * versions moved to a BlockOutlineRenderState agnostic architecture.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererOutlineMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Unique
    private boolean pushedOutlinePose;

    @Inject(method = "renderHitOutline", at = @At("HEAD"))
    private void applyOutlineAnimation(
            PoseStack poseStack,
            VertexConsumer builder,
            Entity entity,
            double camX,
            double camY,
            double camZ,
            BlockPos pos,
            BlockState state,
            CallbackInfo ci
    ) {
        this.pushedOutlinePose = false;
        if (!ShakeeAnimationManager.hasActiveAnimations() || this.minecraft.level == null) {
            return;
        }

        ShakeeAnimationState animation = ShakeeAnimationManager.getVisualAnimation(this.minecraft.level, pos);
        if (animation == null) {
            return;
        }

        poseStack.pushPose();
        this.pushedOutlinePose = true;

        Vec3 offset = state.getOffset(this.minecraft.level, pos);
        float tickDelta = this.minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        poseStack.translate(offset.x, offset.y, offset.z);
        animation.applyLocal(poseStack, ShakeeAnimationManager.getClientTicks(), tickDelta);
        poseStack.translate(-offset.x, -offset.y, -offset.z);
    }

    @Inject(method = "renderHitOutline", at = @At("RETURN"))
    private void popOutlineAnimation(
            PoseStack poseStack,
            VertexConsumer builder,
            Entity entity,
            double camX,
            double camY,
            double camZ,
            BlockPos pos,
            BlockState state,
            CallbackInfo ci
    ) {
        if (this.pushedOutlinePose) {
            poseStack.popPose();
            this.pushedOutlinePose = false;
        }
    }
}
