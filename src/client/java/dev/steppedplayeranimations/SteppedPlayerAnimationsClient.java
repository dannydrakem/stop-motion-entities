package dev.steppedplayeranimations;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public final class SteppedPlayerAnimationsClient implements ClientModInitializer {
    public static final String MOD_ID = "stepped_player_animations";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        List<String> missing = new ArrayList<>();
        requireMod(missing, "entity_model_features");
        requireMod(missing, "entity_texture_features");
        requireMod(missing, "emotecraft");
        requireMod(missing, "playeranimator");

        if (missing.isEmpty()) {
            LOGGER.info("Stage 3 compatibility bridge enabled: EMF custom player models will remain available while Emotecraft is active.");
        } else {
            LOGGER.info("Stage 3 compatibility bridge inactive; optional test-stack mods not loaded: {}", String.join(", ", missing));
        }
    }

    private static void requireMod(List<String> missing, String modId) {
        if (!FabricLoader.getInstance().isModLoaded(modId)) {
            missing.add(modId);
        }
    }
}
