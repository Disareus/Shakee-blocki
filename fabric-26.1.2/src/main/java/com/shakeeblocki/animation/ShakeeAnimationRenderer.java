package com.shakeeblocki.animation;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * Handles custom world rendering for animated blocks in Minecraft 26.1.2.
 * Uses a pooled render state list to eliminate per-frame object allocations.
 */
public final class ShakeeAnimationRenderer {
    private static final double MAX_RENDER_DISTANCE_SQ = 64.0D * 64.0D;
    private static final List<WorldAwareMovingBlockRenderState> STATE_POOL = new ArrayList<>();

    private ShakeeAnimationRenderer() {}

    public static void render(LevelRenderContext context) {
        if (!ShakeeAnimationManager.hasActiveAnimations()) return;

        Minecraft client = Minecraft.getInstance();
        ClientLevel world = client.level;
        if (world == null) return;

        PoseStack poseStack = context.poseStack();
        SubmitNodeCollector submitter = context.submitNodeCollector();
        float tickDelta = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 cameraPos = context.levelState().cameraRenderState != null && context.levelState().cameraRenderState.pos != null
                ? context.levelState().cameraRenderState.pos
                : Vec3.ZERO;
        long currentTicks = ShakeeAnimationManager.getClientTicks();

        int poolIndex = 0;

        for (ShakeeAnimationState animation : ShakeeAnimationManager.activeAnimations()) {
            if (!animation.usesCustomWorldRender()) continue;

            BlockPos pos = animation.pos();

            // Distance culling: skip animation calculation if block is too far from camera
            double dx = (double) pos.getX() + 0.5D - cameraPos.x;
            double dy = (double) pos.getY() + 0.5D - cameraPos.y;
            double dz = (double) pos.getZ() + 0.5D - cameraPos.z;
            if (dx * dx + dy * dy + dz * dz > MAX_RENDER_DISTANCE_SQ) {
                continue;
            }

            BlockState state = animation.originalState();

            poseStack.pushPose();
            poseStack.translate(
                    (double) pos.getX() - cameraPos.x,
                    (double) pos.getY() - cameraPos.y,
                    (double) pos.getZ() - cameraPos.z
            );

            animation.applyLocal(poseStack, currentTicks, tickDelta);

            WorldAwareMovingBlockRenderState renderState;
            if (poolIndex < STATE_POOL.size()) {
                renderState = STATE_POOL.get(poolIndex);
                renderState.setup(world, pos, state);
            } else {
                renderState = new WorldAwareMovingBlockRenderState(world, pos, state);
                STATE_POOL.add(renderState);
            }
            poolIndex++;

            submitter.submitMovingBlock(poseStack, renderState);
            poseStack.popPose();
        }
    }

    private static final class WorldAwareMovingBlockRenderState extends MovingBlockRenderState {
        private ClientLevel world;

        private WorldAwareMovingBlockRenderState(ClientLevel world, BlockPos pos, BlockState state) {
            setup(world, pos, state);
        }

        public void setup(ClientLevel world, BlockPos pos, BlockState state) {
            this.world = world;
            this.randomSeedPos = pos;
            this.blockPos = pos;
            this.blockState = state;
            this.biome = world.getBiome(pos);
            this.cardinalLighting = world.cardinalLighting();
            this.lightEngine = world.getLightEngine();
        }

        @Override
        public CardinalLighting cardinalLighting() {
            return this.world != null ? this.world.cardinalLighting() : null;
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return this.world != null ? this.world.getLightEngine() : null;
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver color) {
            return this.world != null ? this.world.getBlockTint(pos, color) : -1;
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return this.world != null ? this.world.getBlockEntity(pos) : null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return pos.equals(this.blockPos) ? this.blockState : (this.world != null ? this.world.getBlockState(pos) : this.blockState);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return this.getBlockState(pos).getFluidState();
        }

        @Override
        public int getHeight() {
            return this.world != null ? this.world.getHeight() : 256;
        }

        @Override
        public int getMinY() {
            return this.world != null ? this.world.getMinY() : 0;
        }
    }
}
