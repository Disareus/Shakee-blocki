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
import net.minecraft.world.level.block.BushBlock;
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
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
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
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

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

    public static ShakeeConfig get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
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

                if (needSave) {
                    save();
                }
                return;
            } catch (Exception e) {
                INSTANCE = new ShakeeConfig();
                save();
                return;
            }
        }
        INSTANCE = new ShakeeConfig();
        save();
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
                || block instanceof RedStoneWireBlock
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

    private boolean isCustomExcluded(Block block) {
        if (this.customExcludedBlocks == null || this.customExcludedBlocks.isEmpty()) {
            return false;
        }
        Object rawId = BuiltInRegistries.BLOCK.getKey(block);
        if (rawId == null) return false;

        String fullId = rawId.toString().toLowerCase();
        String path = fullId.contains(":") ? fullId.substring(fullId.indexOf(':') + 1) : fullId;

        for (String rawEntry : this.customExcludedBlocks) {
            if (rawEntry == null) continue;
            for (String entry : rawEntry.split("[,;\\s]+")) {
                String trimmed = entry.trim().toLowerCase();
                if (trimmed.isEmpty()) continue;
                if (trimmed.equals(fullId) || trimmed.equals(path)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void save() {
        try {
            File parent = CONFIG_FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(INSTANCE != null ? INSTANCE : new ShakeeConfig(), writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
