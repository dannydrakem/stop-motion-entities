package dev.steppedplayeranimations.mixin.vanilla;

import dev.steppedplayeranimations.render.SteppedRenderContext;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
abstract class InventoryEntityRenderContextMixin {
    @Inject(
            method = "renderEntityInInventory(Lnet/minecraft/client/gui/GuiGraphics;FFFLorg/joml/Vector3f;Lorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At("HEAD")
    )
    private static void steppedPlayerAnimations$enterInventoryRender(
            GuiGraphics graphics,
            float x,
            float y,
            float scale,
            Vector3f translation,
            Quaternionf pose,
            Quaternionf cameraOrientation,
            LivingEntity entity,
            CallbackInfo callback
    ) {
        SteppedRenderContext.enterInventory();
    }

    @Inject(
            method = "renderEntityInInventory(Lnet/minecraft/client/gui/GuiGraphics;FFFLorg/joml/Vector3f;Lorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At("RETURN")
    )
    private static void steppedPlayerAnimations$exitInventoryRender(
            GuiGraphics graphics,
            float x,
            float y,
            float scale,
            Vector3f translation,
            Quaternionf pose,
            Quaternionf cameraOrientation,
            LivingEntity entity,
            CallbackInfo callback
    ) {
        SteppedRenderContext.exitInventory();
    }
}
