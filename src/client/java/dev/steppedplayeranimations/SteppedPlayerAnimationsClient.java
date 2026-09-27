package dev.steppedplayeranimations;

import com.mojang.blaze3d.platform.InputConstants;
import dev.steppedplayeranimations.config.SteppedAnimationConfig;
import dev.steppedplayeranimations.config.SteppedAnimationsConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class SteppedPlayerAnimationsClient implements ClientModInitializer {
    public static final String MOD_ID = "stepped_player_animations";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final KeyMapping TOGGLE_KEY = new KeyMapping(
            "key.stepped_player_animations.toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            "key.category.stepped_player_animations"
    );
    private static final KeyMapping CYCLE_FRAME_RATE_KEY = new KeyMapping(
            "key.stepped_player_animations.cycle_frame_rate",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_BRACKET,
            "key.category.stepped_player_animations"
    );

    @Override
    public void onInitializeClient() {
        SteppedAnimationConfig.load();
        KeyBindingHelper.registerKeyBinding(TOGGLE_KEY);
        KeyBindingHelper.registerKeyBinding(CYCLE_FRAME_RATE_KEY);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.screen != null) {
                return;
            }
            while (TOGGLE_KEY.consumeClick()) {
                SteppedAnimationConfig.toggleEnabled();
                if (client.player != null) {
                    client.player.displayClientMessage(
                            Component.translatable(
                                    "message.stepped_player_animations.enabled",
                                    Component.translatable(
                                            SteppedAnimationConfig.isEnabled() ? "options.on" : "options.off"
                                    )
                            ),
                            true
                    );
                }
            }
            while (CYCLE_FRAME_RATE_KEY.consumeClick()) {
                SteppedAnimationConfig.cycleFrameRate();
                if (client.player != null) {
                    client.player.displayClientMessage(
                            Component.translatable(
                                    "message.stepped_player_animations.frame_rate",
                                    SteppedAnimationsConfigScreen.frameRateValue()
                            ),
                            true
                    );
                }
            }
        });

        List<String> missing = new ArrayList<>();
        requireMod(missing, "entity_model_features");
        requireMod(missing, "entity_texture_features");
        requireMod(missing, "emotecraft");
        requireMod(missing, "playeranimator");
        requireMod(missing, "bendy-lib");

        if (missing.isEmpty()) {
            LOGGER.info("Compatibility bridge enabled: Emotecraft-controlled parts override FA, free parts retain FA animation, and bend data is forwarded to visible EMF cubes.");
        } else {
            LOGGER.info("Compatibility bridge inactive; optional test-stack mods not loaded: {}", String.join(", ", missing));
        }
    }

    private static void requireMod(List<String> missing, String modId) {
        if (!FabricLoader.getInstance().isModLoaded(modId)) {
            missing.add(modId);
        }
    }
}
