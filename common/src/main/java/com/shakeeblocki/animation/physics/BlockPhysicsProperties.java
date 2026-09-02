package com.shakeeblocki.animation.physics;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Material-aware physical parameters for realistic spring-damper interactions.
 */
public final class BlockPhysicsProperties {
    public final float stiffness;     // How snappy/rigid the block is
    public final float dampingRatio; // How quickly vibrations decay
    public final float mass;         // Mass scaling for inertia

    public BlockPhysicsProperties(float stiffness, float dampingRatio, float mass) {
        this.stiffness = stiffness;
        this.dampingRatio = dampingRatio;
        this.mass = mass;
    }

    public static BlockPhysicsProperties forBlock(BlockState state) {
        SoundType sound = state.getSoundType();

        if (sound == SoundType.SLIME_BLOCK || sound == SoundType.HONEY_BLOCK) {
            // Highly elastic, jelly-like oscillation
            return new BlockPhysicsProperties(14.0F, 0.35F, 0.8F);
        } else if (sound == SoundType.METAL || sound == SoundType.ANVIL || sound == SoundType.NETHERITE_BLOCK) {
            // Heavy, rigid, quick vibration dampening
            return new BlockPhysicsProperties(32.0F, 0.85F, 2.5F);
        } else if (sound == SoundType.STONE || sound == SoundType.DEEPSLATE || sound == SoundType.BASALT) {
            // Solid, crisp rebound
            return new BlockPhysicsProperties(24.0F, 0.65F, 1.5F);
        } else if (sound == SoundType.WOOD || sound == SoundType.BAMBOO || sound == SoundType.CHERRY_WOOD) {
            // Organic, warm, medium-bouncy
            return new BlockPhysicsProperties(20.0F, 0.55F, 1.0F);
        } else if (sound == SoundType.SAND || sound == SoundType.GRAVEL || sound == SoundType.MUD) {
            // Soft, heavily damped impact
            return new BlockPhysicsProperties(18.0F, 0.95F, 1.2F);
        } else if (sound == SoundType.GLASS) {
            // Fragile, sharp high-frequency recoil
            return new BlockPhysicsProperties(28.0F, 0.70F, 0.7F);
        }

        // Standard default physics
        return new BlockPhysicsProperties(22.0F, 0.60F, 1.0F);
    }
}
