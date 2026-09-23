package com.shakeeblocki.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shakeeblocki.animation.ShakeeAnimationManager;
import com.shakeeblocki.animation.ShakeeAnimationState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class WorldRendererOutlineMixin {
    @Inject(
            method = "submitBlockOutline",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V",
                    shift = At.Shift.AFTER
            )
    )
    private void applyOutlineAnimation(
            PoseStack poseStack,
            SubmitNodeCollector submitter,
            LevelRenderState levelRenderState,
            CallbackInfo ci
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ShakeeAnimationManager.hasActiveAnimations() || minecraft.level == null || levelRenderState == null || levelRenderState.blockOutlineRenderState == null) {
            return;
        }

        BlockOutlineRenderState state = levelRenderState.blockOutlineRenderState;
        BlockPos pos = state.pos();
        ShakeeAnimationState animation = ShakeeAnimationManager.getVisualAnimation(minecraft.level, pos);
        if (animation == null) {
            return;
        }

        BlockState blockState = minecraft.level.getBlockState(pos);
        Vec3 offset = blockState != null ? blockState.getOffset(pos) : Vec3.ZERO;
        float tickDelta = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        poseStack.translate(offset.x, offset.y, offset.z);
        animation.applyLocal(poseStack, ShakeeAnimationManager.getClientTicks(), tickDelta);
        poseStack.translate(-offset.x, -offset.y, -offset.z);
    }
}
