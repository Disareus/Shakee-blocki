package com.shakeeblocki;

import com.shakeeblocki.animation.ShakeeAnimationManager;
import com.shakeeblocki.animation.ShakeeAnimationRenderer;
import com.shakeeblocki.config.ShakeeConfig;
import com.shakeeblocki.config.ShakeeConfigScreen;
import com.shakeeblocki.platform.ShakeePlatform;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = ShakeeBlocki.MOD_ID, dist = Dist.CLIENT)
public final class ShakeeBlocki {
    public static final String MOD_ID = "shakee_blocki";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public ShakeeBlocki(ModContainer container) {
        ShakeePlatform.init(FMLPaths.CONFIGDIR.get());
        ShakeeConfig.load();
        LOGGER.info("Shakee Blocki initialized!");

        NeoForge.EVENT_BUS.addListener(ShakeeBlocki::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(ShakeeBlocki::onClientTick);

        // Expose the Cloth Config screen on the NeoForge Mods list, mirroring the
        // ModMenu factory on Fabric: only register when Cloth Config is present so
        // the extension is not registered with missing classes.
        if (net.neoforged.fml.ModList.get().isLoaded("cloth_config")) {
            container.registerExtensionPoint(IConfigScreenFactory.class,
                    (java.util.function.Supplier<IConfigScreenFactory>) () -> (modContainer, parent) -> ShakeeConfigScreen.create(parent));
        }
    }

    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        ShakeeAnimationRenderer.render(event);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        ShakeeAnimationManager.tick();
        if (Minecraft.getInstance().level == null) {
            ShakeeAnimationRenderer.clearPool();
        }
    }
}
