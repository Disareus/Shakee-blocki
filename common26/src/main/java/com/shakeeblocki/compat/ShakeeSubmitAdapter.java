package com.shakeeblocki.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;

/**
 * Version-volatile submit pipeline adapter shared by the 26.x builds.
 *
 * Minecraft 26.2 changed the signature of {@code SubmitNodeCollector#submitMovingBlock}
 * (added the outline color parameter), so a direct call cannot be compiled against a
 * single Minecraft version. Implementations live in their own compat packages and are
 * resolved reflectively once at startup so that a single unified jar can cover the
 * whole 26.1.2-26.3 range:
 * <ul>
 *     <li>{@code com.shakeeblocki.compat261.ShakeeSubmitAdapter261} — compiled only in the
 *         fabric-26.1.2 module (two-argument call for Minecraft 26.1.x).</li>
 *     <li>{@code com.shakeeblocki.compat262.ShakeeSubmitAdapter262} — compiled only in the
 *         fabric-26.3 module (three-argument call for Minecraft 26.2+).</li>
 * </ul>
 */
public interface ShakeeSubmitAdapter {
    void submitMovingBlock(SubmitNodeCollector submitter, PoseStack poseStack, MovingBlockRenderState state);

    /** Cached instance; resolved once on first use (render thread). */
    static ShakeeSubmitAdapter get() {
        return Holder.INSTANCE;
    }

    final class Holder {
        private Holder() {}
        private static final ShakeeSubmitAdapter INSTANCE = create();
    }

    private static ShakeeSubmitAdapter create() {
        String className = ShakeeMinecraftCompat.isAtLeast26_2()
                ? "com.shakeeblocki.compat262.ShakeeSubmitAdapter262"
                : "com.shakeeblocki.compat261.ShakeeSubmitAdapter261";
        try {
            return (ShakeeSubmitAdapter) Class.forName(className)
                    .getDeclaredConstructor()
                    .newInstance();
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IllegalStateException(
                    "Shakee Blocki: could not create submit adapter '" + className
                            + "'. This unified jar supports Minecraft 26.1.2-26.3 only.", e);
        }
    }
}
