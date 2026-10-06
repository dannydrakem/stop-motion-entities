package dev.steppedplayeranimations.mixin.figura;

import dev.steppedplayeranimations.compat.figura.FiguraSteppingContext;
import org.figuramc.figura.model.PartCustomization;
import org.figuramc.figura.model.rendertasks.RenderTask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// Render tasks have their own transforms and must use the same sample as the parent model part.
@Pseudo
@Mixin(value = RenderTask.class, remap = false)
abstract class FiguraRenderTaskMixin {
    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/figuramc/figura/model/PartCustomization$PartCustomizationStack;push(Lorg/figuramc/figura/model/PartCustomization;)V"
            ),
            index = 0
    )
    private PartCustomization steppedPlayerAnimations$sampleFiguraRenderTask(
            PartCustomization customization
    ) {
        return FiguraSteppingContext.sampleForRender(customization);
    }
}
