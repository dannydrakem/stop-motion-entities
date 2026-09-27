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
    private static boolean steppedPlayerAnimations$loggedActiveBridge;

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
        if (animation == null || !animation.isActive()) {
            return;
        }

        // EMF replacement player models do not reliably propagate PlayerAnimator's supplier to
        // vanilla armor models. The regular copyPropertiesTo call above already copied the held
        // rotations; only the bend state and the torso render supplier must be restored here.
        SetableSupplier<AnimationProcessor> supplier = new SetableSupplier<>();
        supplier.set(animation);
        ((IMutableModel) armorModel).setEmoteSupplier(supplier);

        IBendHelper.INSTANCE.bend(armorModel.body, steppedPlayerAnimations$torsoBend(animation));
        IBendHelper.INSTANCE.bend(armorModel.rightArm, animation.getBend("rightArm"));
        IBendHelper.INSTANCE.bend(armorModel.leftArm, animation.getBend("leftArm"));
        IBendHelper.INSTANCE.bend(armorModel.rightLeg, animation.getBend("rightLeg"));
        IBendHelper.INSTANCE.bend(armorModel.leftLeg, animation.getBend("leftLeg"));

        if (!steppedPlayerAnimations$loggedActiveBridge) {
            steppedPlayerAnimations$loggedActiveBridge = true;
            SteppedPlayerAnimationsClient.LOGGER.info(
                    "Forwarding PlayerAnimator limb and torso bends to humanoid armor layers."
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
