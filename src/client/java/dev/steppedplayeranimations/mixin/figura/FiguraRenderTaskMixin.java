package dev.steppedplayeranimations.mixin.figura;

import dev.steppedplayeranimations.compat.figura.FiguraSteppingContext;
import org.figuramc.figura.model.PartCustomization;
import org.figuramc.figura.model.rendertasks.RenderTask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Keeps Figura render-task transforms on the same sampled frame as their parent model part.
 * Item, block, text and entity tasks own a separate customization object, so sampling only the
 * avatar's bones lets those attachments continue moving between stepped frames.
 */
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
