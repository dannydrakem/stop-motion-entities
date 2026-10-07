package dev.steppedplayeranimations.mixin.vehicle;

import dev.steppedplayeranimations.SteppedPlayerAnimationsClient;
import dev.steppedplayeranimations.config.SteppedAnimationConfig;
import dev.steppedplayeranimations.timing.SteppedAnimationClock;
import dev.steppedplayeranimations.timing.ExpiringStateCache;
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

import java.util.UUID;

@Mixin(BoatModel.class)
abstract class BoatPaddleSamplerMixin {
    @Unique
    private static boolean steppedPlayerAnimations$logged;
    @Unique
    private final ExpiringStateCache<UUID, PaddleState> steppedPlayerAnimations$paddleStates =
            new ExpiringStateCache<>();

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
        if (!SteppedAnimationConfig.isSteppingActive() || !SteppedAnimationConfig.isEntityEnabled(boat)) {
            return;
        }
        long now = SteppedAnimationClock.nowNanos();
        PaddleState state = steppedPlayerAnimations$paddleStates.getOrCreate(
                boat.getUUID(),
                PaddleState::new
        );
        if (state.gate.shouldCapture(
                now,
                SteppedAnimationConfig.sampleIntervalNanos(),
                SteppedAnimationConfig.revision()
        )) {
            state.capture(leftPaddle, rightPaddle);
            state.initialized = true;
        } else {
            state.restore(leftPaddle, rightPaddle);
            if (!steppedPlayerAnimations$logged) {
                steppedPlayerAnimations$logged = true;
                SteppedPlayerAnimationsClient.LOGGER.debug(
                        "Stepped boat-paddle hold active at {} FPS; boat movement and hull rocking remain smooth.",
                        SteppedAnimationConfig.frameRate().framesPerSecond()
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
