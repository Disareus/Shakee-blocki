package com.shakeeblocki.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.shakeeblocki.animation.ShakeeAnimationManager;
import com.shakeeblocki.animation.ShakeeAnimationState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.BlockOutlineRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class WorldRendererOutlineMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Unique
    private boolean pushedOutlinePose;

    @Inject(method = "renderHitOutline", at = @At("HEAD"))
    private void applyOutlineAnimation(
            PoseStack poseStack,
            VertexConsumer builder,
            double camX,
            double camY,
            double camZ,
            BlockOutlineRenderState state,
            int color,
            float width,
            CallbackInfo ci
    ) {
        this.pushedOutlinePose = false;
        if (!ShakeeAnimationManager.hasActiveAnimations() || this.minecraft.level == null || state == null) {
            return;
        }

        BlockPos pos = state.pos();
        ShakeeAnimationState animation = ShakeeAnimationManager.getVisualAnimation(this.minecraft.level, pos);
        if (animation == null) {
            return;
        }

        poseStack.pushPose();
        this.pushedOutlinePose = true;

        BlockState blockState = this.minecraft.level.getBlockState(pos);
        Vec3 offset = blockState.getOffset(pos);
        float tickDelta = this.minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        poseStack.translate(offset.x, offset.y, offset.z);
        animation.applyLocal(poseStack, ShakeeAnimationManager.getClientTicks(), tickDelta);
        poseStack.translate(-offset.x, -offset.y, -offset.z);
    }

    @Inject(method = "renderHitOutline", at = @At("RETURN"))
    private void popOutlineAnimation(
            PoseStack poseStack,
            VertexConsumer builder,
            double camX,
            double camY,
            double camZ,
            BlockOutlineRenderState state,
            int color,
            float width,
            CallbackInfo ci
    ) {
        if (this.pushedOutlinePose) {
            poseStack.popPose();
            this.pushedOutlinePose = false;
        }
    }
}
