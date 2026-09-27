package dev.steppedplayeranimations.mixin.vanilla;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ModelPart.class)
public interface ModelPartChildrenAccessor {
    @Accessor("children")
    Map<String, ModelPart> steppedPlayerAnimations$children();
}
