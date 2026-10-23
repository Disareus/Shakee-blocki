package com.shakeeblocki.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.Version;

/**
 * Detects the running Minecraft version for the 26.x unified build.
 * Used to gate mixin classes and submit adapters compiled for different
 * render APIs (26.1.x vs 26.2+).
 */
public final class ShakeeMinecraftCompat {
    private static final boolean AT_LEAST_26_2 = detect();

    private ShakeeMinecraftCompat() {}

    public static boolean isAtLeast26_2() {
        return AT_LEAST_26_2;
    }

    private static boolean detect() {
        try {
            return FabricLoader.getInstance().getModContainer("minecraft")
                    .map(container -> {
                        ModMetadata metadata = container.getMetadata();
                        if (metadata == null) return false;
                        try {
                            // Version parsing must tolerate custom builds ("26.2-dev", "26.1.2", ...).
                            return Version.parse(metadata.getVersion().getFriendlyString())
                                    .compareTo(Version.parse("26.2")) >= 0;
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .orElse(false);
        } catch (Exception e) {
            // If detection fails treat as legacy so legacy mixins at least warn visibly.
            return false;
        }
    }
}
