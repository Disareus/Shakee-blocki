package com.shakeeblocki.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

public class ShakeeModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // Cloth Config is mandatory for config screens on this target: ModMenu 17+ (1.21.11)
        // hides the config button entirely when this factory returns null, so without
        // Cloth Config the screen silently vanishes (fabric.mod.json depends on cloth-config).
        if (FabricLoader.getInstance().isModLoaded("cloth-config")) {
            return ShakeeConfigScreen::create;
        }
        return null;
    }
}
