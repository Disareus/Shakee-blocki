package com.shakeeblocki.animation;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * Handles custom world rendering for blocks undergoing animated transformations.
 * Uses reusable render states to avoid GC allocations during render frames.
 */
public final class ShakeeAnimationRenderer {
    private static final double MAX_RENDER_DISTANCE_SQ = 64.0D * 64.0D;

    // Single reusable instance for synchronous client block tesselation
    private static final ReusableMovingBlockRenderState REUSABLE_STATE = new ReusableMovingBlockRenderState();
    private static final RandomSource RANDOM_SOURCE = RandomSource.createThreadSafe();

    private ShakeeAnimationRenderer() {}

    public static void render(WorldRenderContext context) {
        if (!ShakeeAnimationManager.hasActiveAnimations()) return;

        Minecraft client = Minecraft.getInstance();
        ClientLevel world = client.level;
        if (world == null) return;

        PoseStack poseStack = context.matrices();
        if (poseStack == null) return;

        MultiBufferSource consumers = context.consumers();
        if (consumers == null) {
            consumers = client.renderBuffers().bufferSource();
        }

        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera != null && camera.isInitialized() ? camera.position() : Vec3.ZERO;
        float tickDelta = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        long currentTicks = ShakeeAnimationManager.getClientTicks();
        BlockRenderDispatcher blockRenderer = client.getBlockRenderer();

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

            RenderType renderType = ItemBlockRenderTypes.getMovingBlockRenderType(state);
            VertexConsumer buffer = consumers.getBuffer(renderType);
            BlockStateModel model = blockRenderer.getBlockModel(state);

            RANDOM_SOURCE.setSeed(state.getSeed(pos));
            List<BlockModelPart> parts = model.collectParts(RANDOM_SOURCE);

            REUSABLE_STATE.setup(world, pos, state);
            blockRenderer.getModelRenderer().tesselateBlock(
                    REUSABLE_STATE,
                    parts,
                    state,
                    pos,
                    poseStack,
                    buffer,
                    false,
                    OverlayTexture.NO_OVERLAY
            );

            poseStack.popPose();
        }
    }

    public static final class ReusableMovingBlockRenderState extends MovingBlockRenderState {
        private ClientLevel world;

        public void setup(ClientLevel world, BlockPos pos, BlockState state) {
            this.world = world;
            this.randomSeedPos = pos;
            this.blockPos = pos;
            this.blockState = state;
            this.biome = world.getBiome(pos);
            this.level = world;
        }

        @Override
        public float getShade(Direction direction, boolean shade) {
            return this.world != null ? this.world.getShade(direction, shade) : 1.0F;
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return this.world != null ? this.world.getLightEngine() : null;
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
            return this.world != null ? this.world.getBlockTint(pos, colorResolver) : -1;
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
