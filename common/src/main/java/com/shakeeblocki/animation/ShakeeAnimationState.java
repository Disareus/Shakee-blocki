package com.shakeeblocki.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.shakeeblocki.config.ShakeeConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * Encapsulates the animation state and matrix transformations for a single animated block.
 * Optimized for zero runtime allocations during rendering and tick processing.
 */
public final class ShakeeAnimationState {
    private static final int END_HOLD_TICKS = 1;
    private static final double TWO_PI = Math.PI * 2.0;

    private final BlockPos pos;
    private BlockState originalState;
    private final Direction face;
    private final long startTick;
    private final int durationTicks;
    private final int horizontalSign;
    private final int verticalSign;
    private final AnimationKind kind;
    private final Vec3 velocity;

    // Cached pivot coordinates to eliminate Vec3 allocations in render loop
    private double pivotX = 0.5D;
    private double pivotY = 0.5D;
    private double pivotZ = 0.5D;

    private long lastRefreshTick;
    private long breakingReleaseStartTick = -1L;
    private int breakingReleaseDurationTicks;
    private float releaseStartHorizontalAngle;
    private float releaseStartVerticalAngle;
    private float releaseStartScale = 1.0F;
    private float currentScale = 1.0F;
    private float breakProgress = 0.0F;
    private long handoffTick = -1L;
    private boolean isDestroyed = false;

    public ShakeeAnimationState(
            BlockPos pos,
            BlockState originalState,
            Direction face,
            long startTick,
            int durationTicks,
            int horizontalSign,
            int verticalSign,
            AnimationKind kind
    ) {
        this(pos, originalState, face, startTick, durationTicks, horizontalSign, verticalSign, kind, Vec3.ZERO);
    }

    public ShakeeAnimationState(
            BlockPos pos,
            BlockState originalState,
            Direction face,
            long startTick,
            int durationTicks,
            int horizontalSign,
            int verticalSign,
            AnimationKind kind,
            Vec3 velocity
    ) {
        this.pos = pos.immutable();
        this.originalState = originalState;
        this.face = face != null ? face : Direction.UP;
        this.startTick = startTick;
        this.durationTicks = Math.max(1, durationTicks);
        this.horizontalSign = horizontalSign;
        this.verticalSign = verticalSign;
        this.kind = kind;
        this.velocity = velocity != null ? velocity : Vec3.ZERO;
        this.lastRefreshTick = startTick;
        recalculatePivot();
    }

    public BlockPos pos() {
        return this.pos;
    }

    public BlockState originalState() {
        return this.originalState;
    }

    public Direction face() {
        return this.face;
    }

    public AnimationKind kind() {
        return this.kind;
    }

    public long startTick() {
        return this.startTick;
    }

    public int horizontalSign() {
        return this.horizontalSign;
    }

    public int verticalSign() {
        return this.verticalSign;
    }

    public Vec3 velocity() {
        return this.velocity;
    }

    public boolean usesCustomWorldRender() {
        return this.originalState.getRenderShape() == RenderShape.MODEL
                && !(this.originalState.getBlock() instanceof ChestBlock)
                && !(this.originalState.getBlock() instanceof SignBlock);
    }

    public void updateState(BlockState newState) {
        if (newState == null || newState.isAir()) return;
        if (!newState.is(this.originalState.getBlock())) return;
        this.originalState = newState;
        recalculatePivot();
    }

    public void updateBreakProgress(float progress) {
        this.breakProgress = Math.clamp(progress, 0.0F, 1.0F);
    }

    public void refresh(long currentTick) {
        this.lastRefreshTick = currentTick;
        this.breakingReleaseStartTick = -1L;
        this.handoffTick = -1L;
    }

    public boolean isBreakingExpired(long currentTick) {
        if (this.kind != AnimationKind.BREAK) return false;
        return currentTick - this.lastRefreshTick > ShakeeConfig.get().breakingKeepAliveTicks;
    }

    public void beginBreakingRelease(long currentTick) {
        if (this.kind != AnimationKind.BREAK || this.breakingReleaseStartTick != -1L) return;
        computeBreakingAngles(currentTick, 0.0F);
        this.releaseStartScale = this.currentScale;
        this.breakingReleaseStartTick = currentTick;
        this.breakingReleaseDurationTicks = Math.max(1, ShakeeConfig.get().breakingReturnTicks);
    }

    public void markDestroyed(long currentTick) {
        if (this.kind != AnimationKind.BREAK) return;
        this.isDestroyed = true;
        this.breakProgress = 1.0F;
        computeBreakingAngles(currentTick, 0.0F);

        ShakeeConfig config = ShakeeConfig.get();
        if (Math.abs(this.releaseStartHorizontalAngle) < 0.1F && Math.abs(this.releaseStartVerticalAngle) < 0.1F) {
            this.releaseStartHorizontalAngle = config.breakingHorizontalMaxAngle * 0.8F * this.horizontalSign;
            this.releaseStartVerticalAngle = config.breakingVerticalMaxAngle * 0.8F * this.verticalSign;
        }
        this.releaseStartScale = this.currentScale > 0.01F ? this.currentScale : 1.0F;
        this.breakingReleaseStartTick = currentTick;
        this.breakingReleaseDurationTicks = Math.max(2, config.breakingReturnTicks);
    }

    public boolean isDestroyed() {
        return this.isDestroyed;
    }

    public boolean isBreakingReleasing() {
        return this.breakingReleaseStartTick != -1L;
    }

    public boolean isBreakingReleaseFinished(long currentTick) {
        if (this.breakingReleaseStartTick == -1L) return false;
        return currentTick - this.breakingReleaseStartTick >= this.breakingReleaseDurationTicks + END_HOLD_TICKS;
    }

    public boolean isAnimationFinished(long currentTick) {
        if (this.kind == AnimationKind.BREAK) return false;
        return currentTick - this.startTick >= this.durationTicks + END_HOLD_TICKS;
    }

    public void beginHandoff(long currentTick) {
        if (this.handoffTick == -1L) {
            this.handoffTick = currentTick + 2;
        }
    }

    public boolean isInHandoff() {
        return this.handoffTick != -1L;
    }

    public boolean isHandoffFinished(long currentTick) {
        return this.handoffTick != -1L && currentTick >= this.handoffTick;
    }

    /**
     * Applies the active animation transformations directly to the target PoseStack.
     */
    public void applyLocal(PoseStack matrices, long currentTick, float tickDelta) {
        switch (this.kind) {
            case PLACE -> applyPlacement(matrices, currentTick, tickDelta);
            case BREAK -> applyBreaking(matrices, currentTick, tickDelta);
            case RIPPLE -> applyRipple(matrices, currentTick, tickDelta);
        }
    }

    private void applyRipple(PoseStack matrices, long currentTick, float tickDelta) {
        ShakeeConfig config = ShakeeConfig.get();
        if (!config.enableNeighborRipple) return;

        float t = Math.clamp(((currentTick - this.startTick) + tickDelta) / (float) this.durationTicks, 0.0F, 1.0F);
        float decay = (1.0F - t) * (1.0F - t);
        float bounce = (float) Math.sin(t * Math.PI * 2.0F) * 0.08F * config.neighborRippleIntensity * decay;

        matrices.translate(0.0D, bounce, 0.0D);

        float squashY = 1.0F + (bounce * 0.5F);
        float squashXZ = 1.0F - (bounce * 0.25F);
        matrices.translate(this.pivotX, this.pivotY, this.pivotZ);
        matrices.scale(squashXZ, squashY, squashXZ);
        matrices.translate(-this.pivotX, -this.pivotY, -this.pivotZ);
    }

    private void applyPlacement(PoseStack matrices, long currentTick, float tickDelta) {
        ShakeeConfig config = ShakeeConfig.get();
        if (!config.enablePlacementAnimation) return;

        float t = Math.clamp(((currentTick - this.startTick) + tickDelta) / (float) this.durationTicks, 0.0F, 1.0F);

        PlacementMode mode = config.placementMode != null ? config.placementMode : PlacementMode.EXPAND;
        if (mode == PlacementMode.MATERIAL_BASED) {
            mode = determineMaterialPlacementMode(this.originalState);
        }

        // Seamless water handling: if block is waterlogged, avoid scaling from 0 (which leaves an empty cavity in water)
        // Instead, use TILT at 100% scale so water and block geometry blend seamlessly.
        boolean isWaterlogged = this.originalState.hasProperty(BlockStateProperties.WATERLOGGED)
                && this.originalState.getValue(BlockStateProperties.WATERLOGGED);
        if (isWaterlogged && (mode == PlacementMode.EXPAND || mode == PlacementMode.TILT_AND_EXPAND || mode == PlacementMode.SPIN)) {
            mode = PlacementMode.TILT;
        }

        // Apply Velocity Bias (player momentum leans the block)
        if (config.enableVelocityBias && this.velocity.lengthSqr() > 0.001D) {
            float decay = (1.0F - t) * (1.0F - t);
            float strength = config.velocityBiasStrength * decay * 25.0F;
            matrices.translate(this.pivotX, this.pivotY, this.pivotZ);
            matrices.mulPose(Axis.XP.rotationDegrees((float) this.velocity.z * strength));
            matrices.mulPose(Axis.ZP.rotationDegrees(-(float) this.velocity.x * strength));
            matrices.translate(-this.pivotX, -this.pivotY, -this.pivotZ);
        }

        // Mode specific transformation
        switch (mode) {
            case EXPAND -> applyExpandTransform(matrices, config, t);
            case SQUASH_AND_STRETCH -> applySquashTransform(matrices, t);
            case FALL -> applyFallTransform(matrices, t);
            case SPIN -> applySpinTransform(matrices, config, t);
            case TILT -> applyTiltTransform(matrices, config, t);
            case TILT_AND_EXPAND -> {
                applyExpandTransform(matrices, config, t);
                applyTiltTransform(matrices, config, t);
            }
            default -> applyExpandTransform(matrices, config, t);
        }
    }

    private void applyExpandTransform(PoseStack matrices, ShakeeConfig config, float t) {
        float scale;
        if (config.expandOvershoot) {
            float c1 = 1.70158F;
            float c3 = c1 + 1.0F;
            float inv = t - 1.0F;
            float factor = Math.clamp(1.0F + c3 * inv * inv * inv + c1 * inv * inv, 0.0F, 1.25F);
            scale = config.expandInitialScale + (1.0F - config.expandInitialScale) * factor;
        } else {
            float factor = 1.0F - (1.0F - t) * (1.0F - t);
            scale = config.expandInitialScale + (1.0F - config.expandInitialScale) * factor;
        }

        scale = Math.max(0.001F, scale);
        matrices.translate(this.pivotX, this.pivotY, this.pivotZ);
        matrices.scale(scale, scale, scale);
        matrices.translate(-this.pivotX, -this.pivotY, -this.pivotZ);
    }

    private void applySquashTransform(PoseStack matrices, float t) {
        float oscillation = (float) Math.sin(t * Math.PI * 2.5F);
        float decay = 1.0F - t;
        float squashFactor = oscillation * decay * 0.45F;

        float scaleY = Math.max(0.1F, 1.0F - squashFactor);
        float scaleXZ = Math.max(0.1F, 1.0F + (squashFactor * 0.55F));

        matrices.translate(this.pivotX, 0.0D, this.pivotZ);
        matrices.scale(scaleXZ, scaleY, scaleXZ);
        matrices.translate(-this.pivotX, 0.0D, -this.pivotZ);
    }

    private void applyFallTransform(PoseStack matrices, float t) {
        float inv = 1.0F - t;
        float height = (inv * inv) * 0.65F;
        float bounce = Math.abs((float) Math.sin(t * Math.PI * 2.0F)) * inv * 0.15F;

        matrices.translate(0.0D, height + bounce, 0.0D);

        if (t > 0.4F) {
            float landingT = (t - 0.4F) / 0.6F;
            float landingSquash = (float) Math.sin(landingT * Math.PI) * (1.0F - landingT) * 0.2F;
            matrices.translate(this.pivotX, 0.0D, this.pivotZ);
            matrices.scale(1.0F + landingSquash * 0.5F, 1.0F - landingSquash, 1.0F + landingSquash * 0.5F);
            matrices.translate(-this.pivotX, 0.0D, -this.pivotZ);
        }
    }

    private void applySpinTransform(PoseStack matrices, ShakeeConfig config, float t) {
        applyExpandTransform(matrices, config, t);

        float spinAngle = (1.0F - t) * (1.0F - t) * 360.0F * this.horizontalSign;
        matrices.translate(this.pivotX, this.pivotY, this.pivotZ);
        matrices.mulPose(Axis.YP.rotationDegrees(spinAngle));
        matrices.translate(-this.pivotX, -this.pivotY, -this.pivotZ);
    }

    private void applyTiltTransform(PoseStack matrices, ShakeeConfig config, float t) {
        float horizontalOscillation = (float) Math.sin(config.horizontalCycles * Math.PI * t);
        float verticalOscillation = (float) Math.sin(config.verticalCycles * Math.PI * t);
        float decay = config.easing.apply(t);

        float horizontalAngle = config.horizontalMaxAngle * horizontalOscillation * decay;
        float verticalAngle = config.verticalMaxAngle * verticalOscillation * decay;

        if (config.randomizeDirection) {
            horizontalAngle *= this.horizontalSign;
            verticalAngle *= this.verticalSign;
        }

        applyRotation(matrices, horizontalAngle, verticalAngle);
    }

    private static PlacementMode determineMaterialPlacementMode(BlockState state) {
        if (state == null) return PlacementMode.EXPAND;
        SoundType sound = state.getSoundType();

        if (sound == SoundType.SLIME_BLOCK || sound == SoundType.WOOL || sound == SoundType.GRASS || sound == SoundType.MOSS) {
            return PlacementMode.SQUASH_AND_STRETCH;
        }
        if (sound == SoundType.SAND || sound == SoundType.GRAVEL || sound == SoundType.SNOW) {
            return PlacementMode.FALL;
        }
        if (sound == SoundType.WOOD || sound == SoundType.BAMBOO || sound == SoundType.GLASS) {
            return PlacementMode.SPIN;
        }
        return PlacementMode.EXPAND;
    }

    private void applyBreaking(PoseStack matrices, long currentTick, float tickDelta) {
        ShakeeConfig config = ShakeeConfig.get();
        if (!config.enableBreakingAnimation) return;

        BreakingMode mode = config.breakingMode != null ? config.breakingMode : BreakingMode.WOBBLE;

        // 1. Cartoon Squash & Stretch breaking animation
        if (mode == BreakingMode.SQUASH_AND_STRETCH) {
            applyCartoonSquashBreaking(matrices, config, currentTick, tickDelta);
            return;
        }

        // 2. Shrink / Collapse into center transformation
        if (mode == BreakingMode.SHRINK || mode == BreakingMode.WOBBLE_AND_SHRINK || this.isDestroyed) {
            float scale;
            if (this.breakingReleaseStartTick != -1L) {
                float t = Math.clamp(((currentTick - this.breakingReleaseStartTick) + tickDelta)
                        / (float) Math.max(1, this.breakingReleaseDurationTicks), 0.0F, 1.0F);
                float ease = 1.0F - (1.0F - t) * (1.0F - t);
                scale = this.isDestroyed
                        ? this.releaseStartScale * (1.0F - ease)
                        : this.releaseStartScale + (1.0F - this.releaseStartScale) * ease;
            } else {
                float minScale = config.breakingMinScale;
                float shrinkFactor = 1.0F - this.breakProgress * (1.0F - minScale);
                scale = Math.clamp(shrinkFactor, minScale, 1.0F);
                this.currentScale = scale;
            }

            scale = Math.max(0.001F, scale);
            matrices.translate(this.pivotX, this.pivotY, this.pivotZ);
            matrices.scale(scale, scale, scale);
            matrices.translate(-this.pivotX, -this.pivotY, -this.pivotZ);
        }

        // 3. Wobble rotation transformation
        if (mode == BreakingMode.WOBBLE || mode == BreakingMode.WOBBLE_AND_SHRINK) {
            float horizontalAngle;
            float verticalAngle;

            if (this.breakingReleaseStartTick != -1L) {
                float t = Math.clamp(((currentTick - this.breakingReleaseStartTick) + tickDelta)
                        / (float) Math.max(1, this.breakingReleaseDurationTicks), 0.0F, 1.0F);
                float k = 1.0F - t;
                horizontalAngle = this.releaseStartHorizontalAngle * k;
                verticalAngle = this.releaseStartVerticalAngle * k;
            } else {
                float age = (currentTick - this.startTick) + tickDelta;
                float loopTicks = Math.max(1, config.breakingLoopTicks);
                float loopT = (age % loopTicks) / loopTicks;

                float horizontalOscillation = (float) Math.sin(config.breakingHorizontalCycles * TWO_PI * loopT);
                float verticalOscillation = (float) Math.sin(config.breakingVerticalCycles * TWO_PI * loopT);
                float amplitude = config.breakingEasing.apply(loopT);

                horizontalAngle = config.breakingHorizontalMaxAngle * horizontalOscillation * amplitude;
                verticalAngle = config.breakingVerticalMaxAngle * verticalOscillation * amplitude;

                if (config.breakingRandomizeDirection) {
                    horizontalAngle *= this.horizontalSign;
                    verticalAngle *= this.verticalSign;
                }
            }

            applyRotation(matrices, horizontalAngle, verticalAngle);
        }
    }

    /**
     * Cartoon Squash & Stretch breaking animation:
     * - Mining in progress: Rhythmic squash/elastic compression with increasing tension.
     * - Mining released: Spring rebound back to rest.
     * - Destroyed: Anticipation squash followed by an explosive stretch and pop disappear.
     */
    private void applyCartoonSquashBreaking(PoseStack matrices, ShakeeConfig config, long currentTick, float tickDelta) {
        float scaleY;
        float scaleXZ;
        float horizontalAngle = 0.0F;
        float verticalAngle = 0.0F;

        if (this.isDestroyed) {
            // Cartoon Pop on destruction
            float t = Math.clamp(((currentTick - this.breakingReleaseStartTick) + tickDelta)
                    / (float) Math.max(1, this.breakingReleaseDurationTicks), 0.0F, 1.0F);

            if (t < 0.25F) {
                // Phase 1: Rapid anticipation squash
                float p = t / 0.25F;
                float ease = (float) Math.sin(p * Math.PI * 0.5);
                scaleY = 1.0F - 0.45F * ease;
                scaleXZ = 1.0F + 0.35F * ease;
            } else if (t < 0.6F) {
                // Phase 2: Overshoot elastic stretch & pop
                float p = (t - 0.25F) / 0.35F;
                float ease = (float) Math.sin(p * Math.PI);
                scaleY = 0.55F + 0.85F * p + ease * 0.35F;
                scaleXZ = 1.35F - 0.65F * p - ease * 0.25F;
            } else {
                // Phase 3: Collapse out
                float p = (t - 0.6F) / 0.4F;
                float ease = p * p;
                scaleY = Math.max(0.001F, 1.4F * (1.0F - ease));
                scaleXZ = Math.max(0.001F, 0.7F * (1.0F - ease));
            }
        } else if (this.breakingReleaseStartTick != -1L) {
            // Spring rebound back to rest
            float t = Math.clamp(((currentTick - this.breakingReleaseStartTick) + tickDelta)
                    / (float) Math.max(1, this.breakingReleaseDurationTicks), 0.0F, 1.0F);
            float decay = 1.0F - t;
            float oscillation = (float) Math.cos(t * Math.PI * 3.0F) * decay * 0.35F;

            scaleY = Math.max(0.1F, 1.0F + oscillation);
            scaleXZ = Math.max(0.1F, 1.0F - oscillation * 0.55F);

            horizontalAngle = this.releaseStartHorizontalAngle * decay;
            verticalAngle = this.releaseStartVerticalAngle * decay;
        } else {
            // Mining impact pulse with increasing tension as progress nears 100%
            float age = (currentTick - this.startTick) + tickDelta;
            float loopTicks = Math.max(1, config.breakingLoopTicks);
            float loopT = (age % loopTicks) / loopTicks;

            float pulse = (float) Math.sin(loopT * Math.PI * 2.0F);
            float intensity = 0.12F + 0.28F * this.breakProgress;
            float squashFactor = pulse * intensity;

            scaleY = Math.max(0.1F, 1.0F - squashFactor);
            scaleXZ = Math.max(0.1F, 1.0F + (squashFactor * 0.55F));

            // Subtle cartoon jiggle tilt
            float horizontalOscillation = (float) Math.sin(config.breakingHorizontalCycles * TWO_PI * loopT);
            float verticalOscillation = (float) Math.sin(config.breakingVerticalCycles * TWO_PI * loopT);
            float tiltIntensity = (0.3F + 0.7F * this.breakProgress) * 0.6F;

            horizontalAngle = config.breakingHorizontalMaxAngle * horizontalOscillation * tiltIntensity;
            verticalAngle = config.breakingVerticalMaxAngle * verticalOscillation * tiltIntensity;

            if (config.breakingRandomizeDirection) {
                horizontalAngle *= this.horizontalSign;
                verticalAngle *= this.verticalSign;
            }
        }

        matrices.translate(this.pivotX, this.pivotY, this.pivotZ);
        matrices.scale(scaleXZ, scaleY, scaleXZ);
        if (Math.abs(horizontalAngle) > 0.01F || Math.abs(verticalAngle) > 0.01F) {
            AxisPair axes = AxisPair.fromFace(this.face);
            matrices.mulPose(axes.horizontalAxis().rotationDegrees(verticalAngle));
            matrices.mulPose(axes.verticalAxis().rotationDegrees(horizontalAngle));
        }
        matrices.translate(-this.pivotX, -this.pivotY, -this.pivotZ);
    }

    private void computeBreakingAngles(long currentTick, float tickDelta) {
        ShakeeConfig config = ShakeeConfig.get();
        float age = (currentTick - this.startTick) + tickDelta;
        float loopTicks = Math.max(1, config.breakingLoopTicks);
        float loopT = (age % loopTicks) / loopTicks;

        float horizontalOscillation = (float) Math.sin(config.breakingHorizontalCycles * TWO_PI * loopT);
        float verticalOscillation = (float) Math.sin(config.breakingVerticalCycles * TWO_PI * loopT);
        float amplitude = config.breakingEasing.apply(loopT);

        this.releaseStartHorizontalAngle = config.breakingHorizontalMaxAngle * horizontalOscillation * amplitude;
        this.releaseStartVerticalAngle = config.breakingVerticalMaxAngle * verticalOscillation * amplitude;

        if (config.breakingRandomizeDirection) {
            this.releaseStartHorizontalAngle *= this.horizontalSign;
            this.releaseStartVerticalAngle *= this.verticalSign;
        }
    }

    private void applyRotation(PoseStack matrices, float horizontalAngle, float verticalAngle) {
        AxisPair axes = AxisPair.fromFace(this.face);
        matrices.translate(this.pivotX, this.pivotY, this.pivotZ);
        matrices.mulPose(axes.horizontalAxis().rotationDegrees(verticalAngle));
        matrices.mulPose(axes.verticalAxis().rotationDegrees(horizontalAngle));
        matrices.translate(-this.pivotX, -this.pivotY, -this.pivotZ);
    }

    private void recalculatePivot() {
        this.pivotX = 0.5D;
        this.pivotY = 0.5D;
        this.pivotZ = 0.5D;

        BlockState state = this.originalState;
        if (state == null) return;

        if (state.getBlock() instanceof DoorBlock) {
            DoubleBlockHalf half = state.getValue(DoorBlock.HALF);
            Direction move = half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN;
            this.pivotX += move.getStepX() * 0.5D;
            this.pivotY += move.getStepY() * 0.5D;
            this.pivotZ += move.getStepZ() * 0.5D;
            return;
        }

        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            Direction move = ShakeeAnimationManager.getChestPartnerDirection(this.pos, state);
            if (move != null) {
                this.pivotX += move.getStepX() * 0.5D;
                this.pivotZ += move.getStepZ() * 0.5D;
            }
            return;
        }

        if (state.getBlock() instanceof BedBlock) {
            Direction dir = state.getValue(BedBlock.FACING);
            int sign = state.getValue(BedBlock.PART) == BedPart.FOOT ? 1 : -1;
            this.pivotX += sign * dir.getStepX() * 0.5D;
            this.pivotZ += sign * dir.getStepZ() * 0.5D;
        }
    }

    private record AxisPair(Axis horizontalAxis, Axis verticalAxis) {
        // Pre-allocated static axis pairs indexed by Direction.ordinal() for zero allocation lookups
        private static final AxisPair[] BY_FACE = new AxisPair[] {
                new AxisPair(Axis.XP, Axis.ZP), // DOWN (0)
                new AxisPair(Axis.XN, Axis.ZP), // UP (1)
                new AxisPair(Axis.ZN, Axis.YP), // NORTH (2)
                new AxisPair(Axis.ZP, Axis.YP), // SOUTH (3)
                new AxisPair(Axis.XN, Axis.YP), // WEST (4)
                new AxisPair(Axis.XP, Axis.YP)  // EAST (5)
        };

        private static AxisPair fromFace(Direction face) {
            return face != null ? BY_FACE[face.ordinal()] : BY_FACE[1];
        }
    }
}
