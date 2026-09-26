package dev.steppedplayeranimations.mixin.emf;

import dev.steppedplayeranimations.SteppedPlayerAnimationsClient;
import dev.kosmx.playerAnim.core.util.Pair;
import dev.kosmx.playerAnim.impl.IAnimatedPlayer;
import dev.kosmx.playerAnim.impl.animation.AnimationApplier;
import dev.kosmx.playerAnim.impl.animation.IBendHelper;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

@Pseudo
@Mixin(targets = "traben.entity_model_features.models.parts.EMFModelPartRoot", remap = false)
abstract class EmfBendyBridgeMixin {
    private static final Pair<Float, Float> ZERO_BEND = new Pair<>(0.0F, 0.0F);
    private static final Set<ModelPart> INITIALIZED_PARTS =
            Collections.newSetFromMap(new WeakHashMap<>());

    private static final Map<String, String> BEND_SOURCES = Map.of(
            "right_arm", "rightArm",
            "right_sleeve", "rightArm",
            "left_arm", "leftArm",
            "left_sleeve", "leftArm",
            "right_leg", "rightLeg",
            "right_pants", "rightLeg",
            "left_leg", "leftLeg",
            "left_pants", "leftLeg"
    );

    private static Method getCurrentEntity;
    private static Method getAllVanillaParts;
    private static Method getAllCustomChildren;
    private static boolean reflectionFailed;
    private static boolean loggedNonZeroBend;

    @Inject(method = "animate", at = @At("HEAD"))
    private void steppedPlayerAnimations$forwardBendsToEmfCubes(CallbackInfo callback) {
        if (reflectionFailed) {
            return;
        }

        try {
            initializeReflection();
            Object entity = getCurrentEntity.invoke(null);
            if (!(entity instanceof IAnimatedPlayer animatedPlayer)) {
                return;
            }

            AnimationApplier animation = animatedPlayer.playerAnimator_getAnimation();
            boolean active = animation != null && animation.isActive();
            @SuppressWarnings("unchecked")
            Map<String, Object> vanillaParts = (Map<String, Object>) getAllVanillaParts.invoke(this);

            for (Map.Entry<String, String> mapping : BEND_SOURCES.entrySet()) {
                Object vanillaPart = vanillaParts.get(mapping.getKey());
                if (vanillaPart == null) {
                    continue;
                }

                Pair<Float, Float> bend = active ? animation.getBend(mapping.getValue()) : ZERO_BEND;
                if (active && !loggedNonZeroBend && Math.abs(bend.getRight()) >= 0.0001F) {
                    loggedNonZeroBend = true;
                    SteppedPlayerAnimationsClient.LOGGER.info(
                            "Forwarding non-zero bend to FA Player cubes: source={}, axis={}, angle={}",
                            mapping.getValue(), bend.getLeft(), bend.getRight()
                    );
                }
                Object[] visibleParts = (Object[]) getAllCustomChildren.invoke(vanillaPart);
                for (Object visiblePartObject : visibleParts) {
                    ModelPart visiblePart = (ModelPart) visiblePartObject;
                    if (INITIALIZED_PARTS.add(visiblePart)) {
                        // PlayerAnimator initializes vanilla arms, sleeves, legs, and pants from
                        // their upper end. Using DOWN reverses the bend and turns elbows/knees
                        // outward on EMF cubes.
                        IBendHelper.INSTANCE.initBend(visiblePart, Direction.UP);
                    }
                    IBendHelper.INSTANCE.bend(visiblePart, bend);
                }
            }
        } catch (ReflectiveOperationException | ClassCastException exception) {
            reflectionFailed = true;
            SteppedPlayerAnimationsClient.LOGGER.error("Could not forward PlayerAnimator bends to EMF model cubes; disabling the bend bridge.", exception);
        }
    }

    private static void initializeReflection() throws ReflectiveOperationException {
        if (getCurrentEntity != null) {
            return;
        }

        Class<?> animationApi = Class.forName("traben.entity_model_features.EMFAnimationApi");
        Class<?> rootClass = Class.forName("traben.entity_model_features.models.parts.EMFModelPartRoot");
        Class<?> vanillaPartClass = Class.forName("traben.entity_model_features.models.parts.EMFModelPartVanilla");
        getCurrentEntity = animationApi.getMethod("getCurrentEntity");
        getAllVanillaParts = rootClass.getMethod("getAllVanillaPartsByNameEMF");
        getAllCustomChildren = vanillaPartClass.getMethod("getAllEMFCustomChildren");
    }
}
