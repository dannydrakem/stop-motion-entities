package dev.steppedplayeranimations.mixin.vanilla;

import dev.steppedplayeranimations.timing.SteppedAnimationClock;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class GameRendererFrameClockMixin {
    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("HEAD"))
    private void steppedPlayerAnimations$beginRenderFrame(
            DeltaTracker deltaTracker,
            boolean renderLevel,
            CallbackInfo callback
    ) {
        SteppedAnimationClock.beginRenderFrame();
    }

    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("RETURN"))
    private void steppedPlayerAnimations$endRenderFrame(
            DeltaTracker deltaTracker,
            boolean renderLevel,
            CallbackInfo callback
    ) {
        SteppedAnimationClock.endRenderFrame();
    }
}
