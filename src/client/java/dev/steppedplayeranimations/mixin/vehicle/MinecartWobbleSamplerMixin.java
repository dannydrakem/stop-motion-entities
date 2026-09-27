package dev.steppedplayeranimations.mixin.vehicle;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.steppedplayeranimations.SteppedPlayerAnimationsClient;
import dev.steppedplayeranimations.config.SteppedAnimationConfig;
import dev.steppedplayeranimations.timing.SteppedAnimationClock;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.MinecartRenderer;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mixin(MinecartRenderer.class)
abstract class MinecartWobbleSamplerMixin {
    @Unique
    private static final Map<UUID, WobbleState> steppedPlayerAnimations$WOBBLE_STATES = new HashMap<>();
    @Unique
    private static boolean steppedPlayerAnimations$logged;
    @Unique
    private AbstractMinecart steppedPlayerAnimations$currentMinecart;

    @Inject(
            method = "render(Lnet/minecraft/world/entity/vehicle/AbstractMinecart;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD")
    )
    private void steppedPlayerAnimations$beginMinecartRender(
            AbstractMinecart minecart,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            CallbackInfo callback
    ) {
        steppedPlayerAnimations$currentMinecart = minecart;
    }

    @ModifyArg(
            method = "render(Lnet/minecraft/world/entity/vehicle/AbstractMinecart;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/math/Axis;rotationDegrees(F)Lorg/joml/Quaternionf;"
            ),
            slice = @Slice(
                    from = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/entity/vehicle/AbstractMinecart;getHurtTime()I"
                    ),
                    to = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/entity/vehicle/AbstractMinecart;getDisplayOffset()I"
                    )
            ),
            index = 0
    )
    private float steppedPlayerAnimations$sampleHitWobble(float angle) {
        if (!SteppedAnimationConfig.isSteppingActive()) {
            return angle;
        }
        AbstractMinecart minecart = steppedPlayerAnimations$currentMinecart;
        if (minecart == null || !SteppedAnimationConfig.isEntityEnabled(minecart)) {
            return angle;
        }

        long now = SteppedAnimationClock.nowNanos();
        WobbleState state = steppedPlayerAnimations$WOBBLE_STATES.computeIfAbsent(
                minecart.getUUID(),
                ignored -> new WobbleState()
        );
        if (state.gate.shouldCapture(
                now,
                SteppedAnimationConfig.sampleIntervalNanos(),
                SteppedAnimationConfig.revision()
        )) {
            state.angle = angle;
            state.initialized = true;
        } else if (!steppedPlayerAnimations$logged && Math.abs(angle) >= 0.0001F) {
            steppedPlayerAnimations$logged = true;
            SteppedPlayerAnimationsClient.LOGGER.info(
                    "Stepped minecart hit-wobble hold active at {} FPS; rail movement and orientation remain smooth.",
                    SteppedAnimationConfig.frameRate().framesPerSecond()
            );
        }
        return state.angle;
    }

    @Inject(
            method = "render(Lnet/minecraft/world/entity/vehicle/AbstractMinecart;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN")
    )
    private void steppedPlayerAnimations$endMinecartRender(
            AbstractMinecart minecart,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            CallbackInfo callback
    ) {
        steppedPlayerAnimations$currentMinecart = null;
    }

    @Unique
    private static final class WobbleState {
        private final SteppedAnimationClock.Gate gate = new SteppedAnimationClock.Gate();
        private boolean initialized;
        private float angle;
    }
}
