package com.shakeeblocki.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.shakeeblocki.animation.BreakingMode;
import com.shakeeblocki.animation.Easing;
import com.shakeeblocki.animation.PlacementMode;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.GlowLichenBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WeightedPressurePlateBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ShakeeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("shakee_blocki.json").toFile();
    private static ShakeeConfig INSTANCE;

    // --- Placement Settings ---
    public boolean enablePlacementAnimation = true;
    public PlacementMode placementMode = PlacementMode.EXPAND;
    public int durationTicks = 6;
    public float horizontalMaxAngle = 15.0F;
    public float verticalMaxAngle = 15.0F;
    public float horizontalCycles = 1.5F;
    public float verticalCycles = 1.0F;
    public Easing easing = Easing.EASE_IN_OUT;
    public boolean randomizeDirection = true;

    // Expand Mode Specific
    public float expandInitialScale = 0.0F;
    public boolean expandOvershoot = true;

    // --- Physics & Interactions ---
    public boolean enableNeighborRipple = true;
    public float neighborRippleIntensity = 1.0F;
    public boolean enableVelocityBias = true;
    public float velocityBiasStrength = 1.0F;

    // --- Breaking Settings ---
    public boolean enableBreakingAnimation = true;
    public BreakingMode breakingMode = BreakingMode.WOBBLE_AND_SHRINK;
    public float breakingMinScale = 0.5F;
    public int breakingLoopTicks = 6;
    public int breakingStartDelayTicks = 0;
    public int breakingKeepAliveTicks = 2;
    public boolean breakingAnimateConnectedBlocks = true;
    public float breakingHorizontalMaxAngle = 8.0F;
    public float breakingVerticalMaxAngle = 4.0F;
    public float breakingHorizontalCycles = 1.5F;
    public float breakingVerticalCycles = 1.0F;
    public Easing breakingEasing = Easing.CONSTANT;
    public boolean breakingRandomizeDirection = true;
    public int breakingReturnTicks = 4;

    // --- Filters & Exclusions ---
    public boolean filterTechnicalBlocks = true;
    public boolean filterFoliageAndPlants = false;
    public List<String> customExcludedBlocks = new ArrayList<>();
    private transient Set<String> customExcludedIds = new HashSet<>();

    public static ShakeeConfig get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (var reader = Files.newBufferedReader(CONFIG_FILE.toPath(), StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                INSTANCE = GSON.fromJson(json, ShakeeConfig.class);
                if (INSTANCE == null) {
                    INSTANCE = new ShakeeConfig();
                }

                boolean needSave = false;
                if (!json.has("filterTechnicalBlocks")) {
                    INSTANCE.filterTechnicalBlocks = true;
                    needSave = true;
                }
                if (!json.has("filterFoliageAndPlants")) {
                    INSTANCE.filterFoliageAndPlants = false;
                    needSave = true;
                }
                if (!json.has("customExcludedBlocks") || INSTANCE.customExcludedBlocks == null) {
                    INSTANCE.customExcludedBlocks = new ArrayList<>();
                    needSave = true;
                }

                if (INSTANCE.sanitize()) {
                    needSave = true;
                }
                INSTANCE.rebuildCustomExclusionCache();
                if (needSave) {
                    save();
                }
                return;
            } catch (Exception e) {
                System.err.println("Shakee Blocki could not load its config; using defaults without overwriting the invalid file.");
                INSTANCE = new ShakeeConfig();
                return;
            }
        }
        INSTANCE = new ShakeeConfig();
        save();
    }

    private boolean sanitize() {
        boolean changed = false;
        int value;

        value = Math.clamp(durationTicks, 1, 40);
        changed |= value != durationTicks;
        durationTicks = value;
        value = Math.clamp(breakingLoopTicks, 1, 40);
        changed |= value != breakingLoopTicks;
        breakingLoopTicks = value;
        value = Math.clamp(breakingStartDelayTicks, 0, 20);
        changed |= value != breakingStartDelayTicks;
        breakingStartDelayTicks = value;
        value = Math.clamp(breakingKeepAliveTicks, 1, 20);
        changed |= value != breakingKeepAliveTicks;
        breakingKeepAliveTicks = value;
        value = Math.clamp(breakingReturnTicks, 1, 20);
        changed |= value != breakingReturnTicks;
        breakingReturnTicks = value;

        float floatValue;
        floatValue = safeFloat(horizontalMaxAngle, 0.0F, 90.0F, 15.0F);
        changed |= floatValue != horizontalMaxAngle;
        horizontalMaxAngle = floatValue;
        floatValue = safeFloat(verticalMaxAngle, 0.0F, 90.0F, 15.0F);
        changed |= floatValue != verticalMaxAngle;
        verticalMaxAngle = floatValue;
        floatValue = safeFloat(horizontalCycles, 0.1F, 10.0F, 1.5F);
        changed |= floatValue != horizontalCycles;
        horizontalCycles = floatValue;
        floatValue = safeFloat(verticalCycles, 0.1F, 10.0F, 1.0F);
        changed |= floatValue != verticalCycles;
        verticalCycles = floatValue;
        floatValue = safeFloat(expandInitialScale, 0.0F, 0.9F, 0.0F);
        changed |= floatValue != expandInitialScale;
        expandInitialScale = floatValue;
        floatValue = safeFloat(neighborRippleIntensity, 0.1F, 3.0F, 1.0F);
        changed |= floatValue != neighborRippleIntensity;
        neighborRippleIntensity = floatValue;
        floatValue = safeFloat(velocityBiasStrength, 0.1F, 5.0F, 1.0F);
        changed |= floatValue != velocityBiasStrength;
        velocityBiasStrength = floatValue;
        floatValue = safeFloat(breakingMinScale, 0.0F, 0.9F, 0.5F);
        changed |= floatValue != breakingMinScale;
        breakingMinScale = floatValue;
        floatValue = safeFloat(breakingHorizontalMaxAngle, 0.0F, 45.0F, 8.0F);
        changed |= floatValue != breakingHorizontalMaxAngle;
        breakingHorizontalMaxAngle = floatValue;
        floatValue = safeFloat(breakingVerticalMaxAngle, 0.0F, 45.0F, 4.0F);
        changed |= floatValue != breakingVerticalMaxAngle;
        breakingVerticalMaxAngle = floatValue;
        floatValue = safeFloat(breakingHorizontalCycles, 0.1F, 10.0F, 1.5F);
        changed |= floatValue != breakingHorizontalCycles;
        breakingHorizontalCycles = floatValue;
        floatValue = safeFloat(breakingVerticalCycles, 0.1F, 10.0F, 1.0F);
        changed |= floatValue != breakingVerticalCycles;
        breakingVerticalCycles = floatValue;

        if (placementMode == null) {
            placementMode = PlacementMode.EXPAND;
            changed = true;
        }
        if (easing == null) {
            easing = Easing.EASE_IN_OUT;
            changed = true;
        }
        if (breakingMode == null) {
            breakingMode = BreakingMode.WOBBLE_AND_SHRINK;
            changed = true;
        }
        if (breakingEasing == null) {
            breakingEasing = Easing.CONSTANT;
            changed = true;
        }
        return changed;
    }

    private static float safeFloat(float value, float min, float max, float fallback) {
        return Float.isFinite(value) ? Math.clamp(value, min, max) : fallback;
    }

    public boolean isBlockAllowed(Block block) {
        if (block == null) return false;

        if (this.filterTechnicalBlocks && isTechnicalBlock(block)) {
            return false;
        }

        if (this.filterFoliageAndPlants && isFoliageBlock(block)) {
            return false;
        }

        if (this.customExcludedBlocks != null && !this.customExcludedBlocks.isEmpty()) {
            if (isCustomExcluded(block)) {
                return false;
            }
        }

        return true;
    }

    public static boolean isTechnicalBlock(Block block) {
        return block instanceof BaseTorchBlock
                || block instanceof ButtonBlock
                || block instanceof LeverBlock
                || block instanceof BaseRailBlock
                || block == Blocks.REDSTONE_WIRE
                || block instanceof DiodeBlock
                || block instanceof PressurePlateBlock
                || block instanceof WeightedPressurePlateBlock
                || block instanceof TripWireBlock
                || block instanceof TripWireHookBlock
                || block instanceof ObserverBlock
                || block instanceof LightningRodBlock
                || block instanceof DaylightDetectorBlock
                || block instanceof HopperBlock
                || block instanceof DispenserBlock
                || block instanceof PistonBaseBlock
                || block instanceof SculkSensorBlock
                || block instanceof RedstoneLampBlock
                || block instanceof BellBlock
                || block instanceof ChainBlock
                || block instanceof LanternBlock;
    }

    public static boolean isFoliageBlock(Block block) {
        return block instanceof VegetationBlock
                || block instanceof LeavesBlock
                || block instanceof SugarCaneBlock
                || block instanceof CactusBlock
                || block instanceof BambooStalkBlock
                || block instanceof BambooSaplingBlock
                || block instanceof VineBlock
                || block instanceof GlowLichenBlock;
    }

    public void setCustomExcludedBlocks(List<String> blocks) {
        this.customExcludedBlocks = blocks == null ? new ArrayList<>() : new ArrayList<>(blocks);
        rebuildCustomExclusionCache();
    }

    private void rebuildCustomExclusionCache() {
        if (this.customExcludedIds == null) {
            this.customExcludedIds = new HashSet<>();
        } else {
            this.customExcludedIds.clear();
        }

        if (this.customExcludedBlocks == null) {
            return;
        }

        for (String rawEntry : this.customExcludedBlocks) {
            if (rawEntry == null) continue;
            for (String entry : rawEntry.split("[,;\\s]+")) {
                String normalized = entry.trim().toLowerCase(Locale.ROOT);
                if (!normalized.isEmpty()) {
                    this.customExcludedIds.add(normalized);
                }
            }
        }
    }

    private boolean isCustomExcluded(Block block) {
        if (this.customExcludedIds == null || this.customExcludedIds.isEmpty()) {
            return false;
        }

        Object rawId = BuiltInRegistries.BLOCK.getKey(block);
        if (rawId == null) return false;

        String fullId = rawId.toString().toLowerCase(Locale.ROOT);
        String path = fullId.indexOf(':') >= 0 ? fullId.substring(fullId.indexOf(':') + 1) : fullId;
        return this.customExcludedIds.contains(fullId) || this.customExcludedIds.contains(path);
    }

    public static void save() {
        if (INSTANCE != null) {
            INSTANCE.sanitize();
            INSTANCE.rebuildCustomExclusionCache();
        }
        try {
            File parent = CONFIG_FILE.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
                throw new IOException("Could not create config directory");
            }

            Path target = CONFIG_FILE.toPath();
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(INSTANCE != null ? INSTANCE : new ShakeeConfig(), writer);
            }

            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
