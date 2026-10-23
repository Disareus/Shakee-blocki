package com.shakeeblocki.compat261;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shakeeblocki.compat.ShakeeSubmitAdapter;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;

/**
 * Submit adapter for Minecraft 26.1.x (two-argument
 * {@code submitMovingBlock}). Compiled only inside the fabric-26.1.2
 * module; must not be referenced from code compiled against Minecraft
 * 26.2+, where the method gained the outline color parameter.
 */
public final class ShakeeSubmitAdapter261 implements ShakeeSubmitAdapter {
    @Override
    public void submitMovingBlock(SubmitNodeCollector submitter, PoseStack poseStack, MovingBlockRenderState state) {
        submitter.submitMovingBlock(poseStack, state);
    }
}
