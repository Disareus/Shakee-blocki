package com.shakeeblocki.animation;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;

/**
 * Thread-safe registry governing block render suppression.
 * Each animated state contributes one ownership count per rendered position.
 */
public final class ShakeeRenderSuppressor {
    private static final Long2IntMap SUPPRESSED = Long2IntMaps.synchronize(new Long2IntOpenHashMap());

    private ShakeeRenderSuppressor() {}

    public static boolean suppress(long posLong) {
        int count = SUPPRESSED.get(posLong);
        SUPPRESSED.put(posLong, count + 1);
        return count == 0;
    }

    public static boolean suppress(BlockPos pos) {
        return pos != null && suppress(pos.asLong());
    }

    public static boolean release(long posLong) {
        int count = SUPPRESSED.get(posLong);
        if (count <= 1) {
            return SUPPRESSED.remove(posLong) > 0;
        }
        SUPPRESSED.put(posLong, count - 1);
        return false;
    }

    public static boolean release(BlockPos pos) {
        return pos != null && release(pos.asLong());
    }

    public static boolean isSuppressed(long posLong) {
        return SUPPRESSED.containsKey(posLong);
    }

    public static boolean isSuppressed(BlockPos pos) {
        return pos != null && isSuppressed(pos.asLong());
    }

    public static void clear() {
        SUPPRESSED.clear();
    }
}
