package dev.steppedplayeranimations.mixin.emf;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import traben.entity_model_features.mod_compat.PALCompat;

@Mixin(value = PALCompat.class, remap = false)
abstract class EmfPlayerAnimatorFallbackMixin {
    @Inject(method = "shouldPauseEntityAnim", at = @At("HEAD"), cancellable = true)
    private static void steppedPlayerAnimations$keepEmfModel(CallbackInfoReturnable<Boolean> callback) {
        callback.setReturnValue(false);
    }
}
