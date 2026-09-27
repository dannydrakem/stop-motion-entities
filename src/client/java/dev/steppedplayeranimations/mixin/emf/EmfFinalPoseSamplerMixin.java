package dev.steppedplayeranimations.mixin.emf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.steppedplayeranimations.SteppedPlayerAnimationsClient;
import dev.steppedplayeranimations.config.SteppedAnimationConfig;
import dev.steppedplayeranimations.render.SteppedRenderContext;
import dev.steppedplayeranimations.timing.SteppedAnimationClock;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

@Pseudo
@Mixin(targets = "traben.entity_model_features.models.parts.EMFModelPartWithState", remap = false)
abstract class EmfFinalPoseSamplerMixin {
    private static final Map<Object, Map<SampleKey, SampleState>> SAMPLES = new WeakHashMap<>();
    private static Method getRoot;
    private static Field modelName;
    private static Method getFileName;
    private static Method getEmfManager;
    private static Field entityRenderCount;
    private static Method getCurrentEntity;
    private static Field isInHand;
    private static Field isLayerPhase;
    private static boolean reflectionFailed;
    private static final Set<String> LOGGED_CAPTURE_MODELS = new LinkedHashSet<>();
    private static final Set<String> LOGGED_HOLD_MODELS = new LinkedHashSet<>();

    private PoseSnapshot steppedPlayerAnimations$poseBeforeRender;

    @Inject(method = "method_22699", at = @At("HEAD"))
    private void steppedPlayerAnimations$rememberPoseBeforeRender(
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            int packedLight,
            int packedOverlay,
            int color,
            CallbackInfo callback
    ) {
        steppedPlayerAnimations$poseBeforeRender = null;
        if (reflectionFailed || !SteppedAnimationConfig.isSteppingActive()) {
            return;
        }

        try {
            steppedPlayerAnimations$initializeReflection();
            Object currentEntity = getCurrentEntity.invoke(null);
            if (currentEntity instanceof Entity entity && !SteppedAnimationConfig.isEntityEnabled(entity)) {
                return;
            }
            Object root = getRoot.invoke(this);
            boolean isolatedRenderPass = isInHand.getBoolean(null) || isLayerPhase.getBoolean(null);
            if (root == this && isolatedRenderPass) {
                steppedPlayerAnimations$poseBeforeRender = PoseSnapshot.capture(
                        ((ModelPart) root).getAllParts().toList()
                );
            }
        } catch (ReflectiveOperationException | ClassCastException exception) {
            steppedPlayerAnimations$disableSampler(exception);
        }
    }

    @Inject(
            method = "method_22699",
            at = @At(
                    value = "INVOKE",
                    target = "Ltraben/entity_model_features/models/parts/EMFModelPartRoot;animate()V",
                    shift = At.Shift.AFTER
            )
    )
    private void steppedPlayerAnimations$sampleFinalPlayerPose(
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            int packedLight,
            int packedOverlay,
            int color,
            CallbackInfo callback
    ) {
        if (reflectionFailed || !SteppedAnimationConfig.isSteppingActive()) {
            return;
        }

        try {
            steppedPlayerAnimations$initializeReflection();
            Object currentEntity = getCurrentEntity.invoke(null);
            if (!(currentEntity instanceof Entity entity)) {
                return;
            }
            if (!SteppedAnimationConfig.isEntityEnabled(entity)) {
                return;
            }

            Object root = getRoot.invoke(this);
            Object modelId = modelName.get(root);
            String emfModelName = (String) getFileName.invoke(modelId);
            if (emfModelName == null) {
                return;
            }

            Object manager = getEmfManager.invoke(null);
            long renderSequence = entityRenderCount.getLong(manager);
            steppedPlayerAnimations$sample(
                    root,
                    (ModelPart) root,
                    entity.getUUID(),
                    isInHand.getBoolean(null),
                    emfModelName,
                    renderSequence,
                    SteppedAnimationClock.nowNanos()
            );
        } catch (ReflectiveOperationException | ClassCastException exception) {
            steppedPlayerAnimations$disableSampler(exception);
        }
    }

    @Inject(method = "method_22699", at = @At("RETURN"))
    private void steppedPlayerAnimations$restorePoseAfterRender(
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            int packedLight,
            int packedOverlay,
            int color,
            CallbackInfo callback
    ) {
        if (steppedPlayerAnimations$poseBeforeRender != null) {
            steppedPlayerAnimations$poseBeforeRender.restore();
            steppedPlayerAnimations$poseBeforeRender = null;
        }
    }

    private static void steppedPlayerAnimations$sample(
            Object rootIdentity,
            ModelPart root,
            UUID entityId,
            boolean handRender,
            String modelName,
            long renderSequence,
            long now
    ) {
        Map<SampleKey, SampleState> byContext = SAMPLES.computeIfAbsent(rootIdentity, ignored -> new HashMap<>());
        SampleState state = byContext.computeIfAbsent(
                new SampleKey(entityId, handRender, SteppedRenderContext.isInventory()),
                ignored -> new SampleState()
        );
        if (state.lastRenderSequence == renderSequence) {
            return;
        }
        state.lastRenderSequence = renderSequence;

        List<ModelPart> parts = root.getAllParts().toList();
        boolean topologyChanged = state.snapshot == null || !state.snapshot.matches(parts);
        long intervalNanos = SteppedAnimationConfig.sampleIntervalNanos();
        boolean sampleDue = state.gate.shouldCapture(now, intervalNanos, SteppedAnimationConfig.revision());
        if (topologyChanged || sampleDue) {
            state.snapshot = PoseSnapshot.capture(parts);
            if (LOGGED_CAPTURE_MODELS.add(modelName)) {
                SteppedPlayerAnimationsClient.LOGGER.info(
                        "Stepped final-pose sampling active: fps={}, model={}, entity={}, parts={}, interval={} ns.",
                        SteppedAnimationConfig.frameRate().framesPerSecond(),
                        modelName,
                        entityId,
                        parts.size(),
                        intervalNanos
                );
            }
        } else {
            state.snapshot.restore();
            if (LOGGED_HOLD_MODELS.add(modelName)) {
                SteppedPlayerAnimationsClient.LOGGER.info(
                        "Stepped final-pose hold confirmed between samples: model={}, entity={}, parts={}.",
                        modelName, entityId, parts.size()
                );
            }
        }
    }

    private static void steppedPlayerAnimations$initializeReflection() throws ReflectiveOperationException {
        if (getRoot != null) {
            return;
        }

        Class<?> animationApiClass = Class.forName("traben.entity_model_features.EMFAnimationApi");
        Class<?> animationStateClass = Class.forName(
                "traben.entity_model_features.models.animation.state.EMFState"
        );
        Class<?> modelPartClass = Class.forName("traben.entity_model_features.models.parts.EMFModelPart");
        Class<?> rootClass = Class.forName("traben.entity_model_features.models.parts.EMFModelPartRoot");
        Class<?> modelIdClass = Class.forName("traben.entity_model_features.models.EMFModel_ID");
        Class<?> managerClass = Class.forName("traben.entity_model_features.EMFManager");
        getCurrentEntity = animationApiClass.getMethod("getCurrentEntity");
        isInHand = animationStateClass.getField("isInHand");
        isLayerPhase = animationStateClass.getField("isLayerPhase");
        getRoot = modelPartClass.getMethod("getRoot");
        modelName = rootClass.getField("modelName");
        getFileName = modelIdClass.getMethod("getfileName");
        getEmfManager = managerClass.getMethod("getInstance");
        entityRenderCount = managerClass.getField("entityRenderCount");
    }

    private static void steppedPlayerAnimations$disableSampler(Exception exception) {
        reflectionFailed = true;
        SteppedPlayerAnimationsClient.LOGGER.error(
                "Could not sample the final EMF entity pose; disabling the stepped-animation sampler.",
                exception
        );
    }

    private record SampleKey(UUID entityId, boolean handRender, boolean inventoryRender) {
    }

    private static final class SampleState {
        private long lastRenderSequence = Long.MIN_VALUE;
        private final SteppedAnimationClock.Gate gate = new SteppedAnimationClock.Gate();
        private PoseSnapshot snapshot;
    }

    private record PoseSnapshot(List<PartSnapshot> parts) {
        private static PoseSnapshot capture(List<ModelPart> modelParts) {
            return new PoseSnapshot(modelParts.stream().map(PartSnapshot::new).toList());
        }

        private boolean matches(List<ModelPart> modelParts) {
            if (parts.size() != modelParts.size()) {
                return false;
            }
            for (int index = 0; index < parts.size(); index++) {
                if (parts.get(index).part != modelParts.get(index)) {
                    return false;
                }
            }
            return true;
        }

        private void restore() {
            parts.forEach(PartSnapshot::restore);
        }
    }

    private static final class PartSnapshot {
        private final ModelPart part;
        private final float x;
        private final float y;
        private final float z;
        private final float xRot;
        private final float yRot;
        private final float zRot;
        private final float xScale;
        private final float yScale;
        private final float zScale;
        private final boolean visible;
        private final boolean skipDraw;

        private PartSnapshot(ModelPart part) {
            this.part = part;
            this.x = part.x;
            this.y = part.y;
            this.z = part.z;
            this.xRot = part.xRot;
            this.yRot = part.yRot;
            this.zRot = part.zRot;
            this.xScale = part.xScale;
            this.yScale = part.yScale;
            this.zScale = part.zScale;
            this.visible = part.visible;
            this.skipDraw = part.skipDraw;
        }

        private void restore() {
            part.x = x;
            part.y = y;
            part.z = z;
            part.xRot = xRot;
            part.yRot = yRot;
            part.zRot = zRot;
            part.xScale = xScale;
            part.yScale = yScale;
            part.zScale = zScale;
            part.visible = visible;
            part.skipDraw = skipDraw;
        }
    }
}
