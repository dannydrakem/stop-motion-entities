package dev.steppedplayeranimations.mixin.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.steppedplayeranimations.config.SteppedAnimationConfig;
import dev.steppedplayeranimations.timing.ExpiringStateCache;
import dev.steppedplayeranimations.timing.SteppedAnimationClock;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ItemEntityRenderer.class)
abstract class ItemEntityAnimationSamplerMixin {
    @Unique
    private final ExpiringStateCache<UUID, ItemAnimationState> steppedPlayerAnimations$itemStates =
            new ExpiringStateCache<>();
    @Unique
    private ItemAnimationState steppedPlayerAnimations$currentItemState;

    @Inject(
            method = "render(Lnet/minecraft/world/entity/item/ItemEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD")
    )
    private void steppedPlayerAnimations$sampleItemAnimation(
            ItemEntity item,
            float yaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int light,
            CallbackInfo callback
    ) {
        steppedPlayerAnimations$currentItemState = null;
        if (!SteppedAnimationConfig.isSteppingActive() || !SteppedAnimationConfig.isEntityEnabled(item)) {
            return;
        }
        ItemAnimationState state = steppedPlayerAnimations$itemStates.getOrCreate(
                item.getUUID(), ItemAnimationState::new
        );
        state.capture = state.gate.shouldCapture(
                SteppedAnimationClock.nowNanos(),
                SteppedAnimationConfig.sampleIntervalNanos(),
                SteppedAnimationConfig.revision()
        );
        steppedPlayerAnimations$currentItemState = state;
    }

    @ModifyArg(
            method = "render(Lnet/minecraft/world/entity/item/ItemEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;sin(F)F"),
            index = 0
    )
    private float steppedPlayerAnimations$holdBob(float phase) {
        ItemAnimationState state = steppedPlayerAnimations$currentItemState;
        if (state == null) {
            return phase;
        }
        if (state.capture) {
            state.bobPhase = phase;
        }
        return state.bobPhase;
    }

    @ModifyArg(
            method = "render(Lnet/minecraft/world/entity/item/ItemEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/math/Axis;rotation(F)Lorg/joml/Quaternionf;"),
            index = 0
    )
    private float steppedPlayerAnimations$holdSpin(float angle) {
        ItemAnimationState state = steppedPlayerAnimations$currentItemState;
        if (state == null) {
            return angle;
        }
        if (state.capture) {
            state.spin = angle;
        }
        return state.spin;
    }

    @Inject(
            method = "render(Lnet/minecraft/world/entity/item/ItemEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN")
    )
    private void steppedPlayerAnimations$endItemRender(
            ItemEntity item,
            float yaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int light,
            CallbackInfo callback
    ) {
        steppedPlayerAnimations$currentItemState = null;
    }

    @Unique
    private static final class ItemAnimationState {
        private final SteppedAnimationClock.Gate gate = new SteppedAnimationClock.Gate();
        private boolean capture;
        private float bobPhase;
        private float spin;
    }
}
