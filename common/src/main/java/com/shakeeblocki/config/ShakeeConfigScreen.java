package com.shakeeblocki.config;

import com.shakeeblocki.animation.BreakingMode;
import com.shakeeblocki.animation.Easing;
import com.shakeeblocki.animation.PlacementMode;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ShakeeConfigScreen {
    public static Screen create(Screen parent) {
        ShakeeConfig config = ShakeeConfig.get();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("shakee_blocki.config.title"))
                .setSavingRunnable(ShakeeConfig::save);

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // --- Placement Category ---
        ConfigCategory placementCategory = builder.getOrCreateCategory(Component.translatable("shakee_blocki.config.category.placement"));

        placementCategory.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("shakee_blocki.config.placement.enable"),
                config.enablePlacementAnimation
        ).setDefaultValue(true)
         .setTooltip(Component.translatable("shakee_blocki.config.placement.enable.tooltip"))
         .setSaveConsumer(val -> config.enablePlacementAnimation = val)
         .build());

        placementCategory.addEntry(entryBuilder.startEnumSelector(
                Component.translatable("shakee_blocki.config.placement.mode"),
                PlacementMode.class,
                config.placementMode != null ? config.placementMode : PlacementMode.EXPAND
        ).setDefaultValue(PlacementMode.EXPAND)
         .setTooltip(Component.translatable("shakee_blocki.config.placement.mode.tooltip"))
         .setSaveConsumer(val -> config.placementMode = val)
         .build());

        placementCategory.addEntry(entryBuilder.startIntSlider(
                Component.translatable("shakee_blocki.config.placement.duration"),
                config.durationTicks,
                1, 40
        ).setDefaultValue(6)
         .setTooltip(Component.translatable("shakee_blocki.config.placement.duration.tooltip"))
         .setSaveConsumer(val -> config.durationTicks = val)
         .build());

        placementCategory.addEntry(entryBuilder.startFloatField(
                Component.translatable("shakee_blocki.config.placement.expand_initial_scale"),
                config.expandInitialScale
        ).setDefaultValue(0.0F)
         .setMin(0.0F).setMax(0.9F)
         .setTooltip(Component.translatable("shakee_blocki.config.placement.expand_initial_scale.tooltip"))
         .setSaveConsumer(val -> config.expandInitialScale = val)
         .build());

        placementCategory.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("shakee_blocki.config.placement.expand_overshoot"),
                config.expandOvershoot
        ).setDefaultValue(true)
         .setTooltip(Component.translatable("shakee_blocki.config.placement.expand_overshoot.tooltip"))
         .setSaveConsumer(val -> config.expandOvershoot = val)
         .build());

        placementCategory.addEntry(entryBuilder.startFloatField(
                Component.translatable("shakee_blocki.config.placement.horizontal_max_angle"),
                config.horizontalMaxAngle
        ).setDefaultValue(15.0F)
         .setMin(0.0F).setMax(90.0F)
         .setTooltip(Component.translatable("shakee_blocki.config.placement.horizontal_max_angle.tooltip"))
         .setSaveConsumer(val -> config.horizontalMaxAngle = val)
         .build());

        placementCategory.addEntry(entryBuilder.startFloatField(
                Component.translatable("shakee_blocki.config.placement.vertical_max_angle"),
                config.verticalMaxAngle
        ).setDefaultValue(15.0F)
         .setMin(0.0F).setMax(90.0F)
         .setTooltip(Component.translatable("shakee_blocki.config.placement.vertical_max_angle.tooltip"))
         .setSaveConsumer(val -> config.verticalMaxAngle = val)
         .build());

        placementCategory.addEntry(entryBuilder.startFloatField(
                Component.translatable("shakee_blocki.config.placement.horizontal_cycles"),
                config.horizontalCycles
        ).setDefaultValue(1.5F)
         .setMin(0.1F).setMax(10.0F)
         .setTooltip(Component.translatable("shakee_blocki.config.placement.horizontal_cycles.tooltip"))
         .setSaveConsumer(val -> config.horizontalCycles = val)
         .build());

        placementCategory.addEntry(entryBuilder.startFloatField(
                Component.translatable("shakee_blocki.config.placement.vertical_cycles"),
                config.verticalCycles
        ).setDefaultValue(1.0F)
         .setMin(0.1F).setMax(10.0F)
         .setTooltip(Component.translatable("shakee_blocki.config.placement.vertical_cycles.tooltip"))
         .setSaveConsumer(val -> config.verticalCycles = val)
         .build());

        placementCategory.addEntry(entryBuilder.startEnumSelector(
                Component.translatable("shakee_blocki.config.placement.easing"),
                Easing.class,
                config.easing != null ? config.easing : Easing.EASE_IN_OUT
        ).setDefaultValue(Easing.EASE_IN_OUT)
         .setTooltip(Component.translatable("shakee_blocki.config.placement.easing.tooltip"))
         .setSaveConsumer(val -> config.easing = val)
         .build());

        placementCategory.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("shakee_blocki.config.placement.randomize"),
                config.randomizeDirection
        ).setDefaultValue(true)
         .setTooltip(Component.translatable("shakee_blocki.config.placement.randomize.tooltip"))
         .setSaveConsumer(val -> config.randomizeDirection = val)
         .build());

        // --- Breaking Category ---
        ConfigCategory breakingCategory = builder.getOrCreateCategory(Component.translatable("shakee_blocki.config.category.breaking"));

        breakingCategory.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("shakee_blocki.config.breaking.enable"),
                config.enableBreakingAnimation
        ).setDefaultValue(true)
         .setTooltip(Component.translatable("shakee_blocki.config.breaking.enable.tooltip"))
         .setSaveConsumer(val -> config.enableBreakingAnimation = val)
         .build());

        breakingCategory.addEntry(entryBuilder.startEnumSelector(
                Component.translatable("shakee_blocki.config.breaking.mode"),
                BreakingMode.class,
                config.breakingMode != null ? config.breakingMode : BreakingMode.WOBBLE_AND_SHRINK
        ).setDefaultValue(BreakingMode.WOBBLE_AND_SHRINK)
         .setTooltip(Component.translatable("shakee_blocki.config.breaking.mode.tooltip"))
         .setSaveConsumer(val -> config.breakingMode = val)
         .build());

        breakingCategory.addEntry(entryBuilder.startFloatField(
                Component.translatable("shakee_blocki.config.breaking.min_scale"),
                config.breakingMinScale
        ).setDefaultValue(0.5F)
         .setMin(0.0F).setMax(0.9F)
         .setTooltip(Component.translatable("shakee_blocki.config.breaking.min_scale.tooltip"))
         .setSaveConsumer(val -> config.breakingMinScale = val)
         .build());

        breakingCategory.addEntry(entryBuilder.startIntSlider(
                Component.translatable("shakee_blocki.config.breaking.start_delay"),
                config.breakingStartDelayTicks,
                0, 20
        ).setDefaultValue(0)
         .setTooltip(Component.translatable("shakee_blocki.config.breaking.start_delay.tooltip"))
         .setSaveConsumer(val -> config.breakingStartDelayTicks = val)
         .build());

        breakingCategory.addEntry(entryBuilder.startIntSlider(
                Component.translatable("shakee_blocki.config.breaking.loop_ticks"),
                config.breakingLoopTicks,
                1, 40
        ).setDefaultValue(6)
         .setTooltip(Component.translatable("shakee_blocki.config.breaking.loop_ticks.tooltip"))
         .setSaveConsumer(val -> config.breakingLoopTicks = val)
         .build());

        breakingCategory.addEntry(entryBuilder.startFloatField(
                Component.translatable("shakee_blocki.config.breaking.horizontal_max_angle"),
                config.breakingHorizontalMaxAngle
        ).setDefaultValue(8.0F)
         .setMin(0.0F).setMax(45.0F)
         .setTooltip(Component.translatable("shakee_blocki.config.breaking.horizontal_max_angle.tooltip"))
         .setSaveConsumer(val -> config.breakingHorizontalMaxAngle = val)
         .build());

        breakingCategory.addEntry(entryBuilder.startFloatField(
                Component.translatable("shakee_blocki.config.breaking.vertical_max_angle"),
                config.breakingVerticalMaxAngle
        ).setDefaultValue(4.0F)
         .setMin(0.0F).setMax(45.0F)
         .setTooltip(Component.translatable("shakee_blocki.config.breaking.vertical_max_angle.tooltip"))
         .setSaveConsumer(val -> config.breakingVerticalMaxAngle = val)
         .build());

        breakingCategory.addEntry(entryBuilder.startFloatField(
                Component.translatable("shakee_blocki.config.breaking.horizontal_cycles"),
                config.breakingHorizontalCycles
        ).setDefaultValue(1.5F)
         .setMin(0.1F).setMax(10.0F)
         .setTooltip(Component.translatable("shakee_blocki.config.breaking.horizontal_cycles.tooltip"))
         .setSaveConsumer(val -> config.breakingHorizontalCycles = val)
         .build());

        breakingCategory.addEntry(entryBuilder.startFloatField(
                Component.translatable("shakee_blocki.config.breaking.vertical_cycles"),
                config.breakingVerticalCycles
        ).setDefaultValue(1.0F)
         .setMin(0.1F).setMax(10.0F)
         .setTooltip(Component.translatable("shakee_blocki.config.breaking.vertical_cycles.tooltip"))
         .setSaveConsumer(val -> config.breakingVerticalCycles = val)
         .build());

        breakingCategory.addEntry(entryBuilder.startEnumSelector(
                Component.translatable("shakee_blocki.config.breaking.easing"),
                Easing.class,
                config.breakingEasing != null ? config.breakingEasing : Easing.CONSTANT
        ).setDefaultValue(Easing.CONSTANT)
         .setTooltip(Component.translatable("shakee_blocki.config.breaking.easing.tooltip"))
         .setSaveConsumer(val -> config.breakingEasing = val)
         .build());

        breakingCategory.addEntry(entryBuilder.startIntSlider(
                Component.translatable("shakee_blocki.config.breaking.return_ticks"),
                config.breakingReturnTicks,
                1, 20
        ).setDefaultValue(4)
         .setTooltip(Component.translatable("shakee_blocki.config.breaking.return_ticks.tooltip"))
         .setSaveConsumer(val -> config.breakingReturnTicks = val)
         .build());

        breakingCategory.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("shakee_blocki.config.breaking.randomize"),
                config.breakingRandomizeDirection
        ).setDefaultValue(true)
         .setTooltip(Component.translatable("shakee_blocki.config.breaking.randomize.tooltip"))
         .setSaveConsumer(val -> config.breakingRandomizeDirection = val)
         .build());

        breakingCategory.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("shakee_blocki.config.breaking.animate_connected"),
                config.breakingAnimateConnectedBlocks
        ).setDefaultValue(true)
         .setTooltip(Component.translatable("shakee_blocki.config.breaking.animate_connected.tooltip"))
         .setSaveConsumer(val -> config.breakingAnimateConnectedBlocks = val)
         .build());

        // --- Physics & Interactions Category ---
        ConfigCategory physicsCategory = builder.getOrCreateCategory(Component.translatable("shakee_blocki.config.category.physics"));

        physicsCategory.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("shakee_blocki.config.physics.neighbor_ripple"),
                config.enableNeighborRipple
        ).setDefaultValue(true)
         .setTooltip(Component.translatable("shakee_blocki.config.physics.neighbor_ripple.tooltip"))
         .setSaveConsumer(val -> config.enableNeighborRipple = val)
         .build());

        physicsCategory.addEntry(entryBuilder.startFloatField(
                Component.translatable("shakee_blocki.config.physics.neighbor_ripple_intensity"),
                config.neighborRippleIntensity
        ).setDefaultValue(1.0F)
         .setMin(0.1F).setMax(3.0F)
         .setTooltip(Component.translatable("shakee_blocki.config.physics.neighbor_ripple_intensity.tooltip"))
         .setSaveConsumer(val -> config.neighborRippleIntensity = val)
         .build());

        physicsCategory.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("shakee_blocki.config.physics.velocity_bias"),
                config.enableVelocityBias
        ).setDefaultValue(true)
         .setTooltip(Component.translatable("shakee_blocki.config.physics.velocity_bias.tooltip"))
         .setSaveConsumer(val -> config.enableVelocityBias = val)
         .build());

        physicsCategory.addEntry(entryBuilder.startFloatField(
                Component.translatable("shakee_blocki.config.physics.velocity_bias_strength"),
                config.velocityBiasStrength
        ).setDefaultValue(1.0F)
         .setMin(0.1F).setMax(5.0F)
         .setTooltip(Component.translatable("shakee_blocki.config.physics.velocity_bias_strength.tooltip"))
         .setSaveConsumer(val -> config.velocityBiasStrength = val)
         .build());

        // --- Filters Category ---
        ConfigCategory filtersCategory = builder.getOrCreateCategory(Component.translatable("shakee_blocki.config.category.filters"));

        filtersCategory.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("shakee_blocki.config.filters.technical"),
                config.filterTechnicalBlocks
        ).setDefaultValue(true)
         .setTooltip(Component.translatable("shakee_blocki.config.filters.technical.tooltip"))
         .setSaveConsumer(val -> config.filterTechnicalBlocks = val)
         .build());

        filtersCategory.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("shakee_blocki.config.filters.foliage"),
                config.filterFoliageAndPlants
        ).setDefaultValue(false)
         .setTooltip(Component.translatable("shakee_blocki.config.filters.foliage.tooltip"))
         .setSaveConsumer(val -> config.filterFoliageAndPlants = val)
         .build());

        filtersCategory.addEntry(entryBuilder.startStrField(
                Component.translatable("shakee_blocki.config.filters.blacklist"),
                String.join(", ", config.customExcludedBlocks != null ? config.customExcludedBlocks : java.util.Collections.emptyList())
        ).setDefaultValue("")
         .setTooltip(Component.translatable("shakee_blocki.config.filters.blacklist.tooltip"))
         .setSaveConsumer(val -> {
             config.customExcludedBlocks = new java.util.ArrayList<>();
             if (val != null && !val.isBlank()) {
                 for (String part : val.split("[,;\\s]+")) {
                     String trimmed = part.trim();
                     if (!trimmed.isEmpty()) {
                         config.customExcludedBlocks.add(trimmed);
                     }
                 }
             }
         })
         .build());

        return builder.build();
    }
}
