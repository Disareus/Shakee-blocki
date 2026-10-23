package com.shakeeblocki.platform;

import java.nio.file.Path;

/**
 * Minimal cross-platform adapter. Must be initialized exactly once during client
 * startup by the loader-specific entry point (Fabric initializer or NeoForge @Mod hook).
 */
public final class ShakeePlatform {
    private static volatile Path configDir = null;

    private ShakeePlatform() {}

    public static void init(Path configDirPath) {
        configDir = configDirPath;
    }

    public static Path configDir() {
        Path dir = configDir;
        if (dir == null) {
            throw new IllegalStateException("ShakeePlatform was not initialized by the loader entry point");
        }
        return dir;
    }
}
