package com.shakeeblocki;

import com.shakeeblocki.animation.ShakeeAnimationManager;
import com.shakeeblocki.animation.ShakeeAnimationRenderer;
import com.shakeeblocki.config.ShakeeConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShakeeBlocki implements ClientModInitializer {
    public static final String MOD_ID = "shakee_blocki";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        ShakeeConfig.load();
        LOGGER.info("Shakee Blocki initialized!");

        LevelRenderEvents.COLLECT_SUBMITS.register(ShakeeAnimationRenderer::render);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ShakeeAnimationManager.tick();
            if (client.level == null) {
                ShakeeAnimationManager.clear();
            }
        });
    }
}
