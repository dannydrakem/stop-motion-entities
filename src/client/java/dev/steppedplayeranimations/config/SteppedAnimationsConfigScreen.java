package dev.steppedplayeranimations.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SteppedAnimationsConfigScreen extends Screen {
    private final Screen parent;
    private Button enabledButton;
    private Button frameRateButton;

    public SteppedAnimationsConfigScreen(Screen parent) {
        super(Component.translatable("screen.stepped_player_animations.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = width / 2 - 100;
        int top = height / 2 - 36;

        enabledButton = addRenderableWidget(Button.builder(
                enabledMessage(),
                button -> {
                    SteppedAnimationConfig.toggleEnabled();
                    refreshMessages();
                }
        ).bounds(left, top, 200, 20).build());

        frameRateButton = addRenderableWidget(Button.builder(
                frameRateMessage(),
                button -> {
                    SteppedAnimationConfig.cycleFrameRate();
                    refreshMessages();
                }
        ).bounds(left, top + 24, 200, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                button -> onClose()
        ).bounds(left, top + 64, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 82, 0xFFFFFFFF);
        graphics.drawCenteredString(
                font,
                Component.translatable("screen.stepped_player_animations.description"),
                width / 2,
                height / 2 - 63,
                0xFFAAAAAA
        );
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private void refreshMessages() {
        enabledButton.setMessage(enabledMessage());
        frameRateButton.setMessage(frameRateMessage());
    }

    private static Component enabledMessage() {
        return Component.translatable(
                "option.stepped_player_animations.enabled",
                Component.translatable(SteppedAnimationConfig.isEnabled() ? "options.on" : "options.off")
        );
    }

    private static Component frameRateMessage() {
        return Component.translatable(
                "option.stepped_player_animations.frame_rate",
                frameRateValue()
        );
    }

    public static Component frameRateValue() {
        SteppedAnimationConfig.FrameRate frameRate = SteppedAnimationConfig.frameRate();
        if (frameRate == SteppedAnimationConfig.FrameRate.UNLIMITED) {
            return Component.translatable("option.stepped_player_animations.unlimited");
        }
        return Component.translatable(
                "option.stepped_player_animations.fps",
                frameRate.framesPerSecond()
        );
    }
}
