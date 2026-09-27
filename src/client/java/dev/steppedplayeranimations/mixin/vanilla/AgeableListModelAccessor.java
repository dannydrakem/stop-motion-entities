package dev.steppedplayeranimations.mixin.vanilla;

import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AgeableListModel.class)
public interface AgeableListModelAccessor {
    @Invoker("headParts")
    Iterable<ModelPart> steppedPlayerAnimations$headParts();

    @Invoker("bodyParts")
    Iterable<ModelPart> steppedPlayerAnimations$bodyParts();
}
