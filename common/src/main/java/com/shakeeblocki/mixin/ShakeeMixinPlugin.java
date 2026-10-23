package com.shakeeblocki.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class ShakeeMixinPlugin implements IMixinConfigPlugin {
    private static final boolean HAS_SODIUM = FabricLoader.getInstance().isModLoaded("sodium");
    // Minecraft 26.2 changed the LevelRenderer submit API (renderHitOutline ->
    // submitBlockOutline, submitMovingBlock gained the outline color parameter), so
    // outline mixins are split into per-version compat packages and gated here.
    // Empty means the version could not be detected; in that case neither compat
    // group is applied (fail-safe: skip outline animation instead of crashing).
    private static final java.util.Optional<Boolean> MINECRAFT_26_2_OR_NEWER = detectMinecraft26_2OrNewer();

    private static java.util.Optional<Boolean> detectMinecraft26_2OrNewer() {
        try {
            net.fabricmc.loader.api.Version baseline;
            try {
                baseline = net.fabricmc.loader.api.Version.parse("26.2");
            } catch (Exception e) {
                return java.util.Optional.empty();
            }
            return FabricLoader.getInstance().getModContainer("minecraft").flatMap(container -> {
                try {
                    return java.util.Optional.of(net.fabricmc.loader.api.Version.parse(
                            container.getMetadata().getVersion().getFriendlyString()));
                } catch (Exception e) {
                    return java.util.Optional.<net.fabricmc.loader.api.Version>empty();
                }
            }).map(version -> version.compareTo(baseline) >= 0);
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains(".sodium.")) {
            return HAS_SODIUM;
        }
        if (mixinClassName.endsWith(".WorldRendererOutlineMixin261")) {
            // Applied only on Minecraft 26.1.x.
            return MINECRAFT_26_2_OR_NEWER.isEmpty() ? false : !MINECRAFT_26_2_OR_NEWER.get();
        }
        if (mixinClassName.endsWith(".WorldRendererOutlineMixin262")) {
            // Applied on Minecraft 26.2 and newer.
            return !MINECRAFT_26_2_OR_NEWER.isEmpty() && MINECRAFT_26_2_OR_NEWER.get();
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
