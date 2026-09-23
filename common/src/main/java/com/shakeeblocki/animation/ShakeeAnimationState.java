package com.shakeeblocki.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.shakeeblocki.animation.physics.BlockPhysicsProperties;
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
 * Encapsulates the animation state and physical matrix transformations for a single animated block.
 * Uses analytical damped spring physics, material-aware cached properties, and reusable state.
 */
public final class ShakeeAnimationState {
    private static final int RESTORING_GRACE_TICKS = 2;
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
    private final BlockPhysicsProperties physics;

    // Cached pivot coordinates to eliminate Vec3 allocations in render loop
    private double pivotX = 0.5D;
    private double pivotY = 0.5D;
    private double pivotZ = 0.5D;

    private AnimationPhase phase = AnimationPhase.SIMULATING;
    private long lastRefreshTick;
    private long settlingStartTick = -1L;
    private int settlingDurationTicks;
    private long restoringStartTick = -1L;

    private float releaseStartHorizontalAngle;
    private float releaseStartVerticalAngle;
    private float releaseStartScale = 1.0F;
    private float currentScale = 1.0F;
    private float breakProgress = 0.0F;
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
        this.physics = BlockPhysicsProperties.forBlock(originalState);
        this.lastRefreshTick = startTick;
        recalculatePivot();
    }

    ShakeeAnimationState(ShakeeAnimationState source, BlockPos pos, BlockState state) {
        this.pos = pos.immutable();
        this.originalState = state;
        this.face = source.face;
        this.startTick = source.startTick;
        this.durationTicks = source.durationTicks;
        this.horizontalSign = source.horizontalSign;
        this.verticalSign = source.verticalSign;
        this.kind = source.kind;
        this.velocity = source.velocity;
        this.physics = BlockPhysicsProperties.forBlock(state);
        this.lastRefreshTick = source.lastRefreshTick;
        this.settlingStartTick = source.settlingStartTick;
        this.settlingDurationTicks = source.settlingDurationTicks;
        this.restoringStartTick = source.restoringStartTick;
        this.releaseStartHorizontalAngle = source.releaseStartHorizontalAngle;
        this.releaseStartVerticalAngle = source.releaseStartVerticalAngle;
        this.releaseStartScale = source.releaseStartScale;
        this.currentScale = source.currentScale;
        this.breakProgress = source.breakProgress;
        this.isDestroyed = source.isDestroyed;
        this.phase = source.phase;
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

    public AnimationPhase phase() {
        return this.phase;
    }

    public boolean usesCustomWorldRender() {
        return shouldUseCustomWorldRender(this.originalState);
    }

    public static boolean shouldUseCustomWorldRender(BlockState state) {
        if (state == null) return false;
        return state.getRenderShape() == RenderShape.MODEL
                && !state.hasBlockEntity()
                && !(state.getBlock() instanceof ChestBlock)
                && !(state.getBlock() instanceof SignBlock);
    }

    public void updateState(BlockState newState) {
        if (newState == this.originalState || newState == null || newState.isAir()) return;
        if (!newState.is(this.originalState.getBlock())) return;
        this.originalState = newState;
        recalculatePivot();
    }

    public void updateBreakProgress(float progress) {
        this.breakProgress = Math.clamp(progress, 0.0F, 1.0F);
    }

    public void refresh(long currentTick) {
        this.lastRefreshTick = currentTick;
        this.settlingStartTick = -1L;
        this.restoringStartTick = -1L;
        this.phase = AnimationPhase.SIMULATING;
    }

    public boolean isBreakingExpired(long currentTick) {
        if (this.kind != AnimationKind.BREAK) return false;
        return currentTick - this.lastRefreshTick > ShakeeConfig.get().breakingKeepAliveTicks;
    }

    public void beginBreakingRelease(long currentTick) {
        if (this.kind != AnimationKind.BREAK || this.phase == AnimationPhase.SETTLING) return;
        computeDynamicVibrationAngles(currentTick, 0.0F);
        this.releaseStartScale = this.currentScale;
        this.settlingStartTick = currentTick;
        this.settlingDurationTicks = Math.max(1, ShakeeConfig.get().breakingReturnTicks);
        this.phase = AnimationPhase.SETTLING;
    }

    public void markDestroyed(long currentTick) {
        if (this.kind != AnimationKind.BREAK || this.isDestroyed) return;
        this.isDestroyed = true;
        this.breakProgress = 1.0F;
        computeDynamicVibrationAngles(currentTick, 0.0F);

        ShakeeConfig config = ShakeeConfig.get();
        if (Math.abs(this.releaseStartHorizontalAngle) < 0.1F && Math.abs(this.releaseStartVerticalAngle) < 0.1F) {
            this.releaseStartHorizontalAngle = config.breakingHorizontalMaxAngle * 0.8F * this.horizontalSign;
            this.releaseStartVerticalAngle = config.breakingVerticalMaxAngle * 0.8F * this.verticalSign;
        }
        this.releaseStartScale = this.currentScale > 0.01F ? this.currentScale : 1.0F;
        this.settlingStartTick = currentTick;
        this.settlingDurationTicks = Math.max(2, config.breakingReturnTicks);
        this.phase = AnimationPhase.SETTLING;
    }

    public boolean isDestroyed() {
        return this.isDestroyed;
    }

    public boolean isBreakingReleasing() {
        return this.phase == AnimationPhase.SETTLING || this.phase == AnimationPhase.RESTORING;
    }

    public boolean isSettlingFinished(long currentTick) {
        if (this.settlingStartTick == -1L) return false;
        return currentTick - this.settlingStartTick >= this.settlingDurationTicks;
    }

    public boolean isAnimationFinished(long currentTick) {
        if (this.kind == AnimationKind.BREAK) return false;
        return currentTick - this.startTick >= this.durationTicks;
    }

    public void enterRestoringPhase(long currentTick) {
        if (this.phase != AnimationPhase.RESTORING) {
            this.phase = AnimationPhase.RESTORING;
            this.restoringStartTick = currentTick;
        }
    }

    public boolean isRestoringFinished(long currentTick) {
        return this.phase == AnimationPhase.RESTORING
                && (currentTick - this.restoringStartTick >= RESTORING_GRACE_TICKS);
    }

    /**
     * Applies dynamic physics-based matrix transformations to the target PoseStack.
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
        float t = Math.clamp(((currentTick - this.startTick) + tickDelta) / (float) this.durationTicks, 0.0F, 1.0F);

        // Underdamped harmonic ripple compression
        float bounce = this.physics.rippleSpring.evaluate(t * 1.5F, 1.0F, 0.0F);
        float scaleY = 1.0F - (bounce * 0.12F * config.neighborRippleIntensity);
        float scaleXZ = 1.0F + (bounce * 0.06F * config.neighborRippleIntensity);

        matrices.translate(this.pivotX, 0.0D, this.pivotZ);
        matrices.scale(scaleXZ, scaleY, scaleXZ);
        matrices.translate(-this.pivotX, 0.0D, -this.pivotZ);
    }

    private void applyPlacement(PoseStack matrices, long currentTick, float tickDelta) {
        ShakeeConfig config = ShakeeConfig.get();
        if (!config.enablePlacementAnimation) return;

        float rawTime = Math.clamp(((currentTick - this.startTick) + tickDelta) / (float) this.durationTicks, 0.0F, 1.0F);
        Easing easing = config.easing != null ? config.easing : Easing.EASE_IN_OUT;
        float t = easing == Easing.CONSTANT ? rawTime : easing.apply(rawTime);
        PlacementMode mode = config.placementMode != null ? config.placementMode : PlacementMode.EXPAND;
        if (mode == PlacementMode.MATERIAL_BASED) {
            mode = determineMaterialPlacementMode(this.originalState);
        }

        // Seamless water handling: if block is waterlogged, avoid scaling from 0 to preserve water geometry
        boolean isWaterlogged = this.originalState.hasProperty(BlockStateProperties.WATERLOGGED)
                && this.originalState.getValue(BlockStateProperties.WATERLOGGED);
        if (isWaterlogged && (mode == PlacementMode.EXPAND || mode == PlacementMode.TILT_AND_EXPAND || mode == PlacementMode.SPIN)) {
            mode = PlacementMode.TILT;
        }

        // Apply Player Momentum / Velocity Bias
        if (config.enableVelocityBias && this.velocity.lengthSqr() > 0.001D) {
            float decay = (1.0F - t) * (1.0F - t);
            float strength = config.velocityBiasStrength * decay * 25.0F;
            matrices.translate(this.pivotX, this.pivotY, this.pivotZ);
            matrices.last().rotate(Axis.XP.rotationDegrees((float) this.velocity.z * strength));
            matrices.last().rotate(Axis.ZP.rotationDegrees(-(float) this.velocity.x * strength));
            matrices.translate(-this.pivotX, -this.pivotY, -this.pivotZ);
        }

        switch (mode) {
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
        float factor;
        if (config.expandOvershoot) {
            // Elastic overshoot curve: smoothly expands from 0 to peak overshoot (~1.12) at t=0.55, then settles to 1.0
            float c1 = 1.70158F;
            float c3 = c1 + 1.0F;
            float inv = t - 1.0F;
            factor = Math.clamp(1.0F + c3 * inv * inv * inv + c1 * inv * inv, 0.0F, 1.25F);
        } else {
            // Smooth quadratic arrival
            float inv = 1.0F - t;
            factor = 1.0F - inv * inv;
        }

        float scale = Math.clamp(config.expandInitialScale + (1.0F - config.expandInitialScale) * factor, 0.001F, 1.25F);
        matrices.translate(this.pivotX, this.pivotY, this.pivotZ);
        matrices.scale(scale, scale, scale);
        matrices.translate(-this.pivotX, -this.pivotY, -this.pivotZ);
    }

    private void applySquashTransform(PoseStack matrices, float t) {
        // Juicy cartoon jelly bounce upon placement:
        // Starts at maximum impact compression, springs upwards, rebounds and settles smoothly
        float decay = (float) Math.exp(-3.2F * t);
        float oscillation = (float) Math.cos(t * Math.PI * 2.2F) * decay * 0.42F;

        float squash = Math.clamp(1.0F - oscillation, 0.4F, 1.4F);
        float bulge = Math.clamp(1.0F + (oscillation * 0.55F), 0.6F, 1.4F);

        matrices.translate(this.pivotX, 0.0D, this.pivotZ);
        matrices.scale(bulge, squash, bulge);
        matrices.translate(-this.pivotX, 0.0D, -this.pivotZ);
    }

    private void applyFallTransform(PoseStack matrices, float t) {
        float inv = 1.0F - t;
        float height = (inv * inv) * 0.65F;

        float bounce = Math.abs(this.physics.fallSpring.evaluate(t * 1.2F, 0.2F, 0.0F));

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
        matrices.last().rotate(Axis.YP.rotationDegrees(spinAngle));
        matrices.translate(-this.pivotX, -this.pivotY, -this.pivotZ);
    }

    private void applyTiltTransform(PoseStack matrices, ShakeeConfig config, float t) {
        float decay = this.physics.tiltSpring.evaluate(t * 1.2F, 1.0F, 0.0F);

        float horizontalOscillation = (float) Math.sin(config.horizontalCycles * Math.PI * t);
        float verticalOscillation = (float) Math.sin(config.verticalCycles * Math.PI * t);

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

        if (mode == BreakingMode.SQUASH_AND_STRETCH) {
            applyCartoonSquashBreaking(matrices, config, currentTick, tickDelta);
            return;
        }

        // Shrink / Collapse into center
        if (mode == BreakingMode.SHRINK || mode == BreakingMode.WOBBLE_AND_SHRINK || this.isDestroyed) {
            float scale;
            if (this.phase == AnimationPhase.SETTLING) {
                float t = Math.clamp(((currentTick - this.settlingStartTick) + tickDelta)
                        / (float) Math.max(1, this.settlingDurationTicks), 0.0F, 1.0F);
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

        // Dynamic Wobble rotation & physical impact recoil
        if (mode == BreakingMode.WOBBLE || mode == BreakingMode.WOBBLE_AND_SHRINK) {
            float horizontalAngle;
            float verticalAngle;
            float punch = 0.0F;

            if (this.phase == AnimationPhase.SETTLING) {
                float t = Math.clamp(((currentTick - this.settlingStartTick) + tickDelta)
                        / (float) Math.max(1, this.settlingDurationTicks), 0.0F, 1.0F);
                float decay = (1.0F - t) * (1.0F - t);
                horizontalAngle = this.releaseStartHorizontalAngle * decay;
                verticalAngle = this.releaseStartVerticalAngle * decay;
            } else {
                float age = (currentTick - this.startTick) + tickDelta;
                float loopTicks = Math.max(2, config.breakingLoopTicks);
                float loopT = (age % loopTicks) / loopTicks;

                float horizontalOscillation = (float) Math.sin(config.breakingHorizontalCycles * TWO_PI * loopT);
                float verticalOscillation = (float) Math.sin(config.breakingVerticalCycles * TWO_PI * loopT);

                // Strong visible tilt starting from 50% up to 100%
                float intensity = getBreakingIntensity();

                horizontalAngle = config.breakingHorizontalMaxAngle * horizontalOscillation * intensity;
                verticalAngle = config.breakingVerticalMaxAngle * verticalOscillation * intensity;

                // Physical impact recoil: pushes slightly inward upon every strike
                punch = (float) Math.sin(loopT * Math.PI) * (0.02F + 0.04F * this.breakProgress);

                if (config.breakingRandomizeDirection) {
                    horizontalAngle *= this.horizontalSign;
                    verticalAngle *= this.verticalSign;
                }
            }

            if (punch > 0.0005F) {
                Direction opposite = this.face.getOpposite();
                matrices.translate(
                        opposite.getStepX() * punch,
                        opposite.getStepY() * punch,
                        opposite.getStepZ() * punch
                );
            }

            applyRotation(matrices, horizontalAngle, verticalAngle);
        }
    }

    private void applyCartoonSquashBreaking(PoseStack matrices, ShakeeConfig config, long currentTick, float tickDelta) {
        float scaleY;
        float scaleXZ;
        float horizontalAngle = 0.0F;
        float verticalAngle = 0.0F;

        if (this.isDestroyed) {
            float t = Math.clamp(((currentTick - this.settlingStartTick) + tickDelta)
                    / (float) Math.max(1, this.settlingDurationTicks), 0.0F, 1.0F);
            if (t < 0.25F) {
                float sub = t / 0.25F;
                scaleY = 1.0F - (sub * 0.35F);
                scaleXZ = 1.0F + (sub * 0.45F);
            } else {
                float sub = (t - 0.25F) / 0.75F;
                float pop = (float) Math.exp(-sub * 4.0F);
                scaleY = (1.0F - sub) * (1.0F + sub * 2.0F) * pop;
                scaleXZ = (1.0F - sub) * 0.7F * pop;
            }
        } else if (this.phase == AnimationPhase.SETTLING) {
            float t = Math.clamp(((currentTick - this.settlingStartTick) + tickDelta)
                    / (float) Math.max(1, this.settlingDurationTicks), 0.0F, 1.0F);
            float decay = this.physics.breakingReleaseSpring.evaluate(t * 1.3F, 1.0F, 0.0F);

            scaleY = 1.0F + (this.releaseStartScale - 1.0F) * decay;
            scaleXZ = 1.0F - (this.releaseStartScale - 1.0F) * 0.5F * decay;
            horizontalAngle = this.releaseStartHorizontalAngle * decay;
            verticalAngle = this.releaseStartVerticalAngle * decay;
        } else {
            float tension = this.breakProgress * 0.28F;
            float age = (currentTick - this.startTick) + tickDelta;
            float heartbeat = (float) Math.sin(age * 1.8F) * tension;

            scaleY = Math.clamp(1.0F - tension + heartbeat, 0.4F, 1.4F);
            scaleXZ = Math.clamp(1.0F + (tension * 0.5F) - (heartbeat * 0.5F), 0.6F, 1.4F);
            this.currentScale = scaleY;

            float horizontalOscillation = (float) Math.sin(age * 1.5F);
            float verticalOscillation = (float) Math.cos(age * 1.6F);
            float tiltIntensity = (0.3F + 0.7F * this.breakProgress) * getBreakingIntensity() * 0.6F;

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
            matrices.last().rotate(axes.horizontalAxis().rotationDegrees(verticalAngle));
            matrices.last().rotate(axes.verticalAxis().rotationDegrees(horizontalAngle));
        }
        matrices.translate(-this.pivotX, -this.pivotY, -this.pivotZ);
    }

    private float getBreakingIntensity() {
        ShakeeConfig config = ShakeeConfig.get();
        Easing easing = config.breakingEasing != null ? config.breakingEasing : Easing.CONSTANT;
        return (0.5F + 0.5F * this.breakProgress) * easing.apply(this.breakProgress);
    }

    private void computeDynamicVibrationAngles(long currentTick, float tickDelta) {
        ShakeeConfig config = ShakeeConfig.get();
        float age = (currentTick - this.startTick) + tickDelta;
        float loopTicks = Math.max(2, config.breakingLoopTicks);
        float loopT = (age % loopTicks) / loopTicks;

        float horizontalOscillation = (float) Math.sin(config.breakingHorizontalCycles * TWO_PI * loopT);
        float verticalOscillation = (float) Math.sin(config.breakingVerticalCycles * TWO_PI * loopT);
        float intensity = getBreakingIntensity();

        this.releaseStartHorizontalAngle = config.breakingHorizontalMaxAngle * horizontalOscillation * intensity;
        this.releaseStartVerticalAngle = config.breakingVerticalMaxAngle * verticalOscillation * intensity;

        if (config.breakingRandomizeDirection) {
            this.releaseStartHorizontalAngle *= this.horizontalSign;
            this.releaseStartVerticalAngle *= this.verticalSign;
        }
    }

    private void applyRotation(PoseStack matrices, float horizontalAngle, float verticalAngle) {
        AxisPair axes = AxisPair.fromFace(this.face);
        matrices.translate(this.pivotX, this.pivotY, this.pivotZ);
        matrices.last().rotate(axes.horizontalAxis().rotationDegrees(verticalAngle));
        matrices.last().rotate(axes.verticalAxis().rotationDegrees(horizontalAngle));
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
