package com.shakeeblocki.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.shakeeblocki.animation.ShakeeAnimationManager;
import com.shakeeblocki.animation.ShakeeAnimationState;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Port note: 1.21.1 renders the destroy progress overlay inside LevelRenderer#renderLevel
 * through BlockRenderDispatcher#renderBreakingTexture (newer versions use
 * BlockBreakingRenderState). Pushing the pose here shakes the crumbling overlay too.
 */
@Mixin(BlockRenderDispatcher.class)
public class BlockDamageRenderMixin {

    @Inject(
            method = "renderBreakingTexture(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/neoforged/neoforge/client/model/data/ModelData;)V",
            at = @At("HEAD")
    )
    private void shakee$applyDamageAnimation(
            BlockState state,
            BlockPos pos,
            BlockAndTintGetter level,
            PoseStack poseStack,
            VertexConsumer consumer,
            ModelData modelData,
            CallbackInfo ci
    ) {
        poseStack.pushPose();
        if (!ShakeeAnimationManager.hasActiveAnimations()) {
            return;
        }

        ShakeeAnimationState animation = ShakeeAnimationManager.getBreaking(pos);
        if (animation == null) {
            return;
        }

        Vec3 offset = state.getOffset(level, pos);
        float tickDelta = net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        poseStack.translate(offset.x, offset.y, offset.z);
        animation.applyLocal(poseStack, ShakeeAnimationManager.getClientTicks(), tickDelta);
        poseStack.translate(-offset.x, -offset.y, -offset.z);
    }

    @Inject(
            method = "renderBreakingTexture(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/neoforged/neoforge/client/model/data/ModelData;)V",
            at = @At("RETURN")
    )
    private void shakee$popDamageAnimation(
            BlockState state,
            BlockPos pos,
            BlockAndTintGetter level,
            PoseStack poseStack,
            VertexConsumer consumer,
            ModelData modelData,
            CallbackInfo ci
    ) {
        poseStack.popPose();
    }
}
