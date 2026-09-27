package dev.steppedplayeranimations.mixin.emf;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kosmx.playerAnim.core.impl.AnimationProcessor;
import dev.kosmx.playerAnim.core.util.Pair;
import dev.kosmx.playerAnim.core.util.SetableSupplier;
import dev.kosmx.playerAnim.impl.IAnimatedPlayer;
import dev.kosmx.playerAnim.impl.IMutableModel;
import dev.kosmx.playerAnim.impl.animation.AnimationApplier;
import dev.kosmx.playerAnim.impl.animation.IBendHelper;
import dev.steppedplayeranimations.SteppedPlayerAnimationsClient;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
abstract class PlayerAnimatorArmorBridgeMixin {
    private static final Pair<Float, Float> steppedPlayerAnimations$ZERO_BEND =
            new Pair<>(0.0F, 0.0F);
    private static boolean steppedPlayerAnimations$loggedActiveBridge;
    private static boolean steppedPlayerAnimations$loggedReset;

    @Inject(
            method = "renderArmorPiece",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;setPartVisibility(Lnet/minecraft/client/model/HumanoidModel;Lnet/minecraft/world/entity/EquipmentSlot;)V"
            )
    )
    private void steppedPlayerAnimations$forwardPlayerAnimatorBends(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            LivingEntity entity,
            EquipmentSlot slot,
            int packedLight,
            HumanoidModel<?> armorModel,
            CallbackInfo callback
    ) {
        if (!(entity instanceof IAnimatedPlayer animatedPlayer)) {
            return;
        }

        AnimationApplier animation = animatedPlayer.playerAnimator_getAnimation();
        boolean active = animation != null && animation.isActive();

        // EMF replacement player models do not reliably propagate PlayerAnimator's supplier to
        // vanilla armor models. The regular copyPropertiesTo call above already copied the held
        // rotations; only the bend state and the torso render supplier must be restored here.
        // The armor models are reused between frames, so an inactive animation must explicitly
        // clear both values instead of leaving the last emote bend stored in BendyLib.
        SetableSupplier<AnimationProcessor> supplier = new SetableSupplier<>();
        supplier.set(active ? animation : null);
        ((IMutableModel) armorModel).setEmoteSupplier(supplier);

        IBendHelper.INSTANCE.bend(
                armorModel.body,
                active ? steppedPlayerAnimations$torsoBend(animation) : steppedPlayerAnimations$ZERO_BEND
        );
        IBendHelper.INSTANCE.bend(
                armorModel.rightArm,
                active ? animation.getBend("rightArm") : steppedPlayerAnimations$ZERO_BEND
        );
        IBendHelper.INSTANCE.bend(
                armorModel.leftArm,
                active ? animation.getBend("leftArm") : steppedPlayerAnimations$ZERO_BEND
        );
        IBendHelper.INSTANCE.bend(
                armorModel.rightLeg,
                active ? animation.getBend("rightLeg") : steppedPlayerAnimations$ZERO_BEND
        );
        IBendHelper.INSTANCE.bend(
                armorModel.leftLeg,
                active ? animation.getBend("leftLeg") : steppedPlayerAnimations$ZERO_BEND
        );

        if (active && !steppedPlayerAnimations$loggedActiveBridge) {
            steppedPlayerAnimations$loggedActiveBridge = true;
            SteppedPlayerAnimationsClient.LOGGER.info(
                    "Forwarding PlayerAnimator limb and torso bends to humanoid armor layers."
            );
        } else if (!active && steppedPlayerAnimations$loggedActiveBridge && !steppedPlayerAnimations$loggedReset) {
            steppedPlayerAnimations$loggedReset = true;
            SteppedPlayerAnimationsClient.LOGGER.info(
                    "Cleared the retained PlayerAnimator bends from humanoid armor layers."
            );
        }
    }

    private static Pair<Float, Float> steppedPlayerAnimations$torsoBend(AnimationApplier animation) {
        Pair<Float, Float> torso = animation.getBend("torso");
        Pair<Float, Float> body = animation.getBend("body");
        return new Pair<>(
                torso.getLeft() + body.getLeft(),
                torso.getRight() + body.getRight()
        );
    }
}
