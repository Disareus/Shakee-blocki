package com.shakeeblocki.compat262;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shakeeblocki.compat.ShakeeSubmitAdapter;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;

/**
 * Submit adapter for Minecraft 26.2 and 26.3 (three-argument
 * {@code submitMovingBlock} with the outline color parameter).
 * Compiled only inside the fabric-26.3 module; must not be referenced
 * from code compiled against Minecraft 26.1.x.
 */
public final class ShakeeSubmitAdapter262 implements ShakeeSubmitAdapter {
    @Override
    public void submitMovingBlock(SubmitNodeCollector submitter, PoseStack poseStack, MovingBlockRenderState state) {
        // 26.2 added the outline color parameter; 0 keeps the previous no-outline
        // behavior used by the block animation renderer.
        submitter.submitMovingBlock(poseStack, state, 0);
    }
}
