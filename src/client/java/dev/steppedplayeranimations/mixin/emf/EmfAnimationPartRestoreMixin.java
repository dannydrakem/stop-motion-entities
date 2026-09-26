package dev.steppedplayeranimations.mixin.emf;

import dev.steppedplayeranimations.compat.emf.EmfPausedPartSnapshots;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "traben.entity_model_features.models.animation.EMFAnimationHandler", remap = false)
abstract class EmfAnimationPartRestoreMixin {
    @Inject(method = "animate", at = @At("HEAD"))
    private void steppedPlayerAnimations$capturePausedParts(ModelPart[] pausedParts, CallbackInfo callback) {
        EmfPausedPartSnapshots.capture(pausedParts);
    }

    @Inject(method = "animate", at = @At("RETURN"))
    private void steppedPlayerAnimations$restorePausedParts(ModelPart[] pausedParts, CallbackInfo callback) {
        EmfPausedPartSnapshots.restore();
    }
}
