package dev.steppedplayeranimations.mixin.emf;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import traben.entity_model_features.utils.EMFAnimationPauseHandler;

@Mixin(value = EMFAnimationPauseHandler.class, remap = false)
abstract class EmfEmotecraftPauseMixin {
    @ModifyExpressionValue(
            method = "shouldAnimationsPause",
            at = @At(
                    value = "INVOKE",
                    target = "Ltraben/entity_model_features/utils/EMFAnimationPauseHandler;isPlayerEmoting_KosmX_mod(Ltraben/entity_model_features/utils/EMFEntity;)Z"
            )
    )
    private static boolean steppedPlayerAnimations$keepEmfAnimationsRunning(boolean original) {
        return false;
    }
}
