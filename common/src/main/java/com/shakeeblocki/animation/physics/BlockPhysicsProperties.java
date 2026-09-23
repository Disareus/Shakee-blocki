package com.shakeeblocki.animation.physics;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Material-aware physical parameters for realistic spring-damper interactions.
 */
public final class BlockPhysicsProperties {
    private static final BlockPhysicsProperties ELASTIC = new BlockPhysicsProperties(14.0F, 0.35F);
    private static final BlockPhysicsProperties RIGID = new BlockPhysicsProperties(32.0F, 0.85F);
    private static final BlockPhysicsProperties SOLID = new BlockPhysicsProperties(24.0F, 0.65F);
    private static final BlockPhysicsProperties ORGANIC = new BlockPhysicsProperties(20.0F, 0.55F);
    private static final BlockPhysicsProperties SOFT = new BlockPhysicsProperties(18.0F, 0.95F);
    private static final BlockPhysicsProperties FRAGILE = new BlockPhysicsProperties(28.0F, 0.70F);
    private static final BlockPhysicsProperties DEFAULT = new BlockPhysicsProperties(22.0F, 0.60F);

    private final float stiffness;
    private final float dampingRatio;
    public final DampedSpring rippleSpring;
    public final DampedSpring fallSpring;
    public final DampedSpring tiltSpring;
    public final DampedSpring breakingReleaseSpring;

    private BlockPhysicsProperties(float stiffness, float dampingRatio) {
        this.stiffness = stiffness;
        this.dampingRatio = dampingRatio;
        this.rippleSpring = new DampedSpring(stiffness * 1.3F, 0.55F);
        this.fallSpring = new DampedSpring(stiffness, 0.60F);
        this.tiltSpring = new DampedSpring(stiffness, dampingRatio);
        this.breakingReleaseSpring = new DampedSpring(stiffness, 0.65F);
    }

    public static BlockPhysicsProperties forBlock(BlockState state) {
        SoundType sound = state.getSoundType();

        if (sound == SoundType.SLIME_BLOCK || sound == SoundType.HONEY_BLOCK) {
            return ELASTIC;
        } else if (sound == SoundType.METAL || sound == SoundType.ANVIL || sound == SoundType.NETHERITE_BLOCK) {
            return RIGID;
        } else if (sound == SoundType.STONE || sound == SoundType.DEEPSLATE || sound == SoundType.BASALT) {
            return SOLID;
        } else if (sound == SoundType.WOOD || sound == SoundType.BAMBOO || sound == SoundType.CHERRY_WOOD) {
            return ORGANIC;
        } else if (sound == SoundType.SAND || sound == SoundType.GRAVEL || sound == SoundType.MUD) {
            return SOFT;
        } else if (sound == SoundType.GLASS) {
            return FRAGILE;
        }

        return DEFAULT;
    }
}
