package dev.steppedplayeranimations.mixin.figura;

import dev.steppedplayeranimations.compat.figura.FiguraSteppingContext;
import org.figuramc.figura.model.rendering.AvatarRenderer;
import org.figuramc.figura.model.rendering.ImmediateAvatarRenderer;
import org.figuramc.figura.model.PartCustomization;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(value = ImmediateAvatarRenderer.class, remap = false)
abstract class FiguraAvatarRendererMixin {
    @Inject(method = "commonRender", at = @At("HEAD"))
    private void steppedPlayerAnimations$beginFiguraRender(
            double verticalOffset,
            CallbackInfoReturnable<Integer> callback
    ) {
        FiguraSteppingContext.begin((AvatarRenderer) (Object) this);
    }

    @Inject(method = "commonRender", at = @At("RETURN"))
    private void steppedPlayerAnimations$endFiguraRender(
            double verticalOffset,
            CallbackInfoReturnable<Integer> callback
    ) {
        FiguraSteppingContext.end();
    }

    @ModifyArg(
            method = "renderPart",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/figuramc/figura/model/PartCustomization$PartCustomizationStack;push(Lorg/figuramc/figura/model/PartCustomization;)V",
                    ordinal = 0
            ),
            index = 0
    )
    private PartCustomization steppedPlayerAnimations$sampleFiguraPart(PartCustomization customization) {
        return FiguraSteppingContext.sampleForRender(customization);
    }
}
