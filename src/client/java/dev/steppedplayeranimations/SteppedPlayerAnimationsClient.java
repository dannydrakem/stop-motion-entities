package dev.steppedplayeranimations;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SteppedPlayerAnimationsClient implements ClientModInitializer {
    public static final String MOD_ID = "stepped_player_animations";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("Stepped Player Animations initialized (stage 2 base build).");
    }
}
