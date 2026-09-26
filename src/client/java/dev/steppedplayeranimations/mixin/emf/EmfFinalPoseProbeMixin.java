package dev.steppedplayeranimations.mixin.emf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.steppedplayeranimations.SteppedPlayerAnimationsClient;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

@Pseudo
@Mixin(targets = "traben.entity_model_features.models.parts.EMFModelPartWithState", remap = false)
abstract class EmfFinalPoseProbeMixin {
    private static final Map<Object, ProbeState> PROBES = new WeakHashMap<>();
    private static Method getRoot;
    private static Field modelName;
    private static Method getFileName;
    private static Method getEmfManager;
    private static Field entityRenderCount;
    private static boolean reflectionFailed;

    @Inject(
            method = "method_22699",
            at = @At(
                    value = "INVOKE",
                    target = "Ltraben/entity_model_features/models/parts/EMFModelPartRoot;animate()V",
                    shift = At.Shift.AFTER
            )
    )
    private void steppedPlayerAnimations$probeFinalPlayerPose(
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            int packedLight,
            int packedOverlay,
            int color,
            CallbackInfo callback
    ) {
        if (reflectionFailed) {
            return;
        }

        try {
            steppedPlayerAnimations$initializeReflection();
            Object root = getRoot.invoke(this);
            Object modelId = modelName.get(root);
            String emfModelName = (String) getFileName.invoke(modelId);
            if (emfModelName == null || !emfModelName.startsWith("player")) {
                return;
            }

            Object manager = getEmfManager.invoke(null);
            long renderSequence = entityRenderCount.getLong(manager);
            steppedPlayerAnimations$record(root, emfModelName, renderSequence);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            reflectionFailed = true;
            SteppedPlayerAnimationsClient.LOGGER.error(
                    "Could not inspect the final EMF player pose; disabling the stage 7 probe.",
                    exception
            );
        }
    }

    private static void steppedPlayerAnimations$record(Object root, String modelName, long renderSequence) {
        ProbeState state = PROBES.computeIfAbsent(root, ignored -> new ProbeState());
        if (state.lastRenderSequence == renderSequence) {
            return;
        }
        state.lastRenderSequence = renderSequence;

        ModelPart rootPart = (ModelPart) root;
        int partCount = (int) rootPart.getAllParts().count();
        long fingerprint = steppedPlayerAnimations$fingerprint(rootPart);
        if (!state.loggedHook) {
            state.loggedHook = true;
            state.previousFingerprint = fingerprint;
            SteppedPlayerAnimationsClient.LOGGER.info(
                    "Final player pose hook reached after EMF animation: model={}, parts={}, renderSequence={}, fingerprint={}.",
                    modelName, partCount, renderSequence, Long.toUnsignedString(fingerprint)
            );
            return;
        }

        if (!state.loggedChange && state.previousFingerprint != fingerprint) {
            state.loggedChange = true;
            SteppedPlayerAnimationsClient.LOGGER.info(
                    "Final player pose hook confirmed live: pose changed after EMF animation (model={}, parts={}, previous={}, current={}).",
                    modelName,
                    partCount,
                    Long.toUnsignedString(state.previousFingerprint),
                    Long.toUnsignedString(fingerprint)
            );
        }
        state.previousFingerprint = fingerprint;
    }

    private static void steppedPlayerAnimations$initializeReflection() throws ReflectiveOperationException {
        if (getRoot != null) {
            return;
        }

        Class<?> modelPartClass = Class.forName("traben.entity_model_features.models.parts.EMFModelPart");
        Class<?> rootClass = Class.forName("traben.entity_model_features.models.parts.EMFModelPartRoot");
        Class<?> modelIdClass = Class.forName("traben.entity_model_features.models.EMFModel_ID");
        Class<?> managerClass = Class.forName("traben.entity_model_features.EMFManager");
        getRoot = modelPartClass.getMethod("getRoot");
        modelName = rootClass.getField("modelName");
        getFileName = modelIdClass.getMethod("getfileName");
        getEmfManager = managerClass.getMethod("getInstance");
        entityRenderCount = managerClass.getField("entityRenderCount");
    }

    private static long steppedPlayerAnimations$fingerprint(ModelPart root) {
        long hash = 0xcbf29ce484222325L;
        for (ModelPart part : root.getAllParts().toList()) {
            hash = steppedPlayerAnimations$mix(hash, Float.floatToIntBits(part.x));
            hash = steppedPlayerAnimations$mix(hash, Float.floatToIntBits(part.y));
            hash = steppedPlayerAnimations$mix(hash, Float.floatToIntBits(part.z));
            hash = steppedPlayerAnimations$mix(hash, Float.floatToIntBits(part.xRot));
            hash = steppedPlayerAnimations$mix(hash, Float.floatToIntBits(part.yRot));
            hash = steppedPlayerAnimations$mix(hash, Float.floatToIntBits(part.zRot));
            hash = steppedPlayerAnimations$mix(hash, Float.floatToIntBits(part.xScale));
            hash = steppedPlayerAnimations$mix(hash, Float.floatToIntBits(part.yScale));
            hash = steppedPlayerAnimations$mix(hash, Float.floatToIntBits(part.zScale));
            hash = steppedPlayerAnimations$mix(hash, part.visible ? 1 : 0);
            hash = steppedPlayerAnimations$mix(hash, part.skipDraw ? 1 : 0);
        }
        return hash;
    }

    private static long steppedPlayerAnimations$mix(long hash, int value) {
        return (hash ^ Integer.toUnsignedLong(value)) * 0x100000001b3L;
    }

    private static final class ProbeState {
        private long lastRenderSequence = Long.MIN_VALUE;
        private long previousFingerprint;
        private boolean loggedHook;
        private boolean loggedChange;
    }
}
