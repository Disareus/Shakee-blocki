package com.shakeeblocki.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Handles custom world rendering for blocks undergoing animated transformations.
 * Uses a reusable render view to avoid GC allocations during render frames.
 */
public final class ShakeeAnimationRenderer {
    private static final double MAX_RENDER_DISTANCE_SQ = 64.0D * 64.0D;

    // Single reusable instance for synchronous client block tessellation
    private static final ReusableRenderView REUSABLE_VIEW = new ReusableRenderView();
    private static final RandomSource RANDOM_SOURCE = RandomSource.create();

    // Render-thread only flag: lets BlockModelRendererMixin distinguish our own
    // re-tessellation of animated blocks from the vanilla chunk meshing loop.
    private static boolean renderingAnimation = false;

    private ShakeeAnimationRenderer() {}

    public static boolean isRenderingAnimation() {
        return renderingAnimation;
    }

    @SuppressWarnings("unused")
    public static void clearPool() {
        REUSABLE_VIEW.reset();
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        if (!ShakeeAnimationManager.hasActiveAnimations()) return;

        Minecraft client = Minecraft.getInstance();
        ClientLevel world = client.level;
        if (world == null) return;

        PoseStack poseStack = event.getPoseStack();
        if (poseStack == null) return;

        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera != null && camera.isInitialized() ? camera.getPosition() : Vec3.ZERO;
        float tickDelta = client.getTimer().getGameTimeDeltaPartialTick(false);
        long currentTicks = ShakeeAnimationManager.getClientTicks();
        BlockRenderDispatcher blockRenderer = client.getBlockRenderer();
        MultiBufferSource consumers = client.renderBuffers().bufferSource();

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
            BakedModel model = blockRenderer.getBlockModel(state);

            REUSABLE_VIEW.setup(world, pos, state);
            long seed = state.getSeed(pos);
            RANDOM_SOURCE.setSeed(seed);

            buildBlock(blockRenderer, REUSABLE_VIEW, model, state, pos, poseStack, buffer, renderType);

            poseStack.popPose();
        }
    }

    // Separated so a future full model bypass (collectParts) can swap in without
    // touching the lifecycle code.
    private static void buildBlock(
            BlockRenderDispatcher dispatcher,
            BlockAndTintGetter view,
            BakedModel model,
            BlockState state,
            BlockPos pos,
            PoseStack poseStack,
            VertexConsumer buffer,
            RenderType renderType
    ) {
        renderingAnimation = true;
        try {
            dispatcher.getModelRenderer().tesselateBlock(
                    view,
                    model,
                    state,
                    pos,
                    poseStack,
                    buffer,
                    false,
                    RANDOM_SOURCE,
                    state.getSeed(pos),
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                    ModelData.EMPTY,
                    renderType
            );
        } finally {
            renderingAnimation = false;
        }
    }

    /**
     * Lightweight BlockAndTintGetter delegating to the real world while forcing the
     * original block state at the animated position (correct AO/light during BREAK animations).
     */
    public static final class ReusableRenderView implements BlockAndTintGetter {
        private ClientLevel world;
        private BlockState blockState;
        private BlockPos blockPos;

        public void setup(ClientLevel world, BlockPos pos, BlockState state) {
            this.world = world;
            this.blockPos = pos;
            this.blockState = state;
        }

        public void reset() {
            this.world = null;
            this.blockPos = null;
            this.blockState = null;
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
            return this.world != null ? this.world.getBlockTint(pos, colorResolver) : -1;
        }

        // 1.21.1: BlockGetter extends LevelHeightAccessor, requiring these two
        // (getMinY/getMaxY only exist in later Mojang mappings).
        @Override
        public int getHeight() {
            return this.world != null ? this.world.getHeight() : 384;
        }

        @Override
        public int getMinBuildHeight() {
            return this.world != null ? this.world.getMinBuildHeight() : -64;
        }

        // 1.21.1: shade resolution is a BlockAndTintGetter method; later versions
        // moved it into the render-state pipeline via the extension interface.
        @Override
        public float getShade(Direction direction, boolean shade) {
            return this.world != null ? this.world.getShade(direction, shade) : 1.0F;
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return this.world != null ? this.world.getLightEngine() : null;
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return this.world != null ? this.world.getBlockEntity(pos) : null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return pos.equals(this.blockPos)
                    ? this.blockState
                    : (this.world != null ? this.world.getBlockState(pos) : this.blockState);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return this.getBlockState(pos).getFluidState();
        }
    }
}
