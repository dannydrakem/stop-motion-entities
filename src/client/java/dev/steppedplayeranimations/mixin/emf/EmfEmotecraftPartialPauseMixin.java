package dev.steppedplayeranimations.mixin.emf;

import dev.steppedplayeranimations.compat.emf.EmfPartBlendState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "traben.entity_model_features.utils.EMFAnimationPauseHandler", remap = false)
abstract class EmfEmotecraftPartialPauseMixin {
    @Inject(method = "isPlayerEmoting_KosmX_mod", at = @At("HEAD"), cancellable = true)
    private static void steppedPlayerAnimations$replaceGlobalPauseWithPartPause(
            @Coerce Object emfEntity,
            CallbackInfoReturnable<Boolean> callback
    ) {
        if (emfEntity instanceof Entity entity && EmfPartBlendState.isManaged(entity.getUUID())) {
            callback.setReturnValue(false);
        }
    }
}
