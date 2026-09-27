package dev.steppedplayeranimations.mixin.vehicle;

import dev.steppedplayeranimations.SteppedPlayerAnimationsClient;
import dev.steppedplayeranimations.timing.SteppedAnimationClock;
import net.minecraft.client.model.BoatModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mixin(BoatModel.class)
abstract class BoatPaddleSamplerMixin {
    @Unique
    private static boolean steppedPlayerAnimations$logged;
    @Unique
    private final Map<UUID, PaddleState> steppedPlayerAnimations$paddleStates = new HashMap<>();

    @Shadow
    @Final
    private ModelPart leftPaddle;
    @Shadow
    @Final
    private ModelPart rightPaddle;

    @Inject(
            method = "setupAnim(Lnet/minecraft/world/entity/vehicle/Boat;FFFFF)V",
            at = @At("RETURN")
    )
    private void steppedPlayerAnimations$samplePaddles(
            Boat boat,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo callback
    ) {
        long now = SteppedAnimationClock.nowNanos();
        PaddleState state = steppedPlayerAnimations$paddleStates.computeIfAbsent(
                boat.getUUID(),
                ignored -> new PaddleState()
        );
        if (state.gate.shouldCapture(now)) {
            state.capture(leftPaddle, rightPaddle);
            state.initialized = true;
        } else {
            state.restore(leftPaddle, rightPaddle);
            if (!steppedPlayerAnimations$logged) {
                steppedPlayerAnimations$logged = true;
                SteppedPlayerAnimationsClient.LOGGER.info(
                        "12 FPS boat-paddle hold active; boat movement and hull rocking remain smooth."
                );
            }
        }
    }

    @Unique
    private static final class PaddleState {
        private final SteppedAnimationClock.Gate gate = new SteppedAnimationClock.Gate();
        private boolean initialized;
        private float leftXRot;
        private float leftYRot;
        private float rightXRot;
        private float rightYRot;

        private void capture(ModelPart left, ModelPart right) {
            leftXRot = left.xRot;
            leftYRot = left.yRot;
            rightXRot = right.xRot;
            rightYRot = right.yRot;
        }

        private void restore(ModelPart left, ModelPart right) {
            left.xRot = leftXRot;
            left.yRot = leftYRot;
            right.xRot = rightXRot;
            right.yRot = rightYRot;
        }
    }
}
