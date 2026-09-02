package com.shakeeblocki.animation;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.core.BlockPos;

/**
 * Thread-safe registry governing block render suppression.
 * Prevents duplicate or z-fighting geometry between vanilla/Sodium chunk meshing
 * and Shakee Blocki's dynamic animated render passes.
 */
public final class ShakeeRenderSuppressor {
    private static final LongSet SUPPRESSED = LongSets.synchronize(new LongOpenHashSet());

    private ShakeeRenderSuppressor() {}

    public static void suppress(long posLong) {
        SUPPRESSED.add(posLong);
    }

    public static void suppress(BlockPos pos) {
        if (pos != null) {
            SUPPRESSED.add(pos.asLong());
        }
    }

    public static boolean release(long posLong) {
        return SUPPRESSED.remove(posLong);
    }

    public static boolean release(BlockPos pos) {
        return pos != null && SUPPRESSED.remove(pos.asLong());
    }

    public static boolean isSuppressed(long posLong) {
        return SUPPRESSED.contains(posLong);
    }

    public static boolean isSuppressed(BlockPos pos) {
        return pos != null && SUPPRESSED.contains(pos.asLong());
    }

    public static void clear() {
        SUPPRESSED.clear();
    }
}
