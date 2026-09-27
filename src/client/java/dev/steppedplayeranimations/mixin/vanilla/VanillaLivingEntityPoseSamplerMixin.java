package dev.steppedplayeranimations.mixin.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.steppedplayeranimations.SteppedPlayerAnimationsClient;
import dev.steppedplayeranimations.config.SteppedAnimationConfig;
import dev.steppedplayeranimations.render.SteppedRenderContext;
import dev.steppedplayeranimations.timing.SteppedAnimationClock;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.ListModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

@Mixin(LivingEntityRenderer.class)
abstract class VanillaLivingEntityPoseSamplerMixin {
    @Unique
    private static final Map<EntityModel<?>, Map<SampleKey, SampleState>> steppedPlayerAnimations$SAMPLES = new WeakHashMap<>();
    @Unique
    private static final Set<String> steppedPlayerAnimations$LOGGED_CAPTURE_MODELS = new LinkedHashSet<>();
    @Unique
    private static final Set<String> steppedPlayerAnimations$LOGGED_HOLD_MODELS = new LinkedHashSet<>();
    @Unique
    private static final Set<String> steppedPlayerAnimations$LOGGED_UNSUPPORTED_MODELS = new LinkedHashSet<>();

    @Unique
    private PoseSnapshot steppedPlayerAnimations$poseBeforeRender;

    @Shadow
    protected EntityModel<?> model;

    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD")
    )
    private void steppedPlayerAnimations$rememberPoseBeforeRender(
            LivingEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            CallbackInfo callback
    ) {
        steppedPlayerAnimations$poseBeforeRender = null;
        if (!SteppedAnimationConfig.isSteppingActive() || !SteppedAnimationConfig.isEntityEnabled(entity)) {
            return;
        }

        List<ModelPart> parts = steppedPlayerAnimations$collectParts(model);
        if (!parts.isEmpty() && !steppedPlayerAnimations$isEmfBacked(parts)) {
            steppedPlayerAnimations$poseBeforeRender = PoseSnapshot.capture(parts);
        }
    }

    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V",
                    shift = At.Shift.AFTER
            )
    )
    private void steppedPlayerAnimations$sampleVanillaPose(
            LivingEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            CallbackInfo callback
    ) {
        if (!SteppedAnimationConfig.isSteppingActive() || !SteppedAnimationConfig.isEntityEnabled(entity)) {
            return;
        }
        List<ModelPart> parts = steppedPlayerAnimations$collectParts(model);
        if (parts.isEmpty() || steppedPlayerAnimations$isEmfBacked(parts)) {
            return;
        }

        String modelName = model.getClass().getSimpleName();
        Map<SampleKey, SampleState> byContext = steppedPlayerAnimations$SAMPLES.computeIfAbsent(
                model,
                ignored -> new HashMap<>()
        );
        SampleState state = byContext.computeIfAbsent(
                new SampleKey(entity.getUUID(), SteppedRenderContext.isInventory()),
                ignored -> new SampleState()
        );
        long now = SteppedAnimationClock.nowNanos();
        boolean topologyChanged = state.snapshot == null || !state.snapshot.matches(parts);
        long intervalNanos = SteppedAnimationConfig.sampleIntervalNanos();
        boolean sampleDue = state.gate.shouldCapture(now, intervalNanos, SteppedAnimationConfig.revision());
        if (topologyChanged || sampleDue) {
            state.snapshot = PoseSnapshot.capture(parts);
            if (steppedPlayerAnimations$LOGGED_CAPTURE_MODELS.add(modelName)) {
                SteppedPlayerAnimationsClient.LOGGER.info(
                        "Stepped vanilla-pose sampling active: fps={}, model={}, entity={}, parts={}, interval={} ns.",
                        SteppedAnimationConfig.frameRate().framesPerSecond(),
                        modelName,
                        entity.getUUID(),
                        parts.size(),
                        intervalNanos
                );
            }
        } else {
            state.snapshot.restore();
            if (steppedPlayerAnimations$LOGGED_HOLD_MODELS.add(modelName)) {
                SteppedPlayerAnimationsClient.LOGGER.info(
                        "Stepped vanilla-pose hold confirmed: model={}, entity={}, parts={}.",
                        modelName, entity.getUUID(), parts.size()
                );
            }
        }
    }

    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN")
    )
    private void steppedPlayerAnimations$restorePoseAfterRender(
            LivingEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            CallbackInfo callback
    ) {
        if (steppedPlayerAnimations$poseBeforeRender != null) {
            steppedPlayerAnimations$poseBeforeRender.restore();
            steppedPlayerAnimations$poseBeforeRender = null;
        }
    }

    @Unique
    private static List<ModelPart> steppedPlayerAnimations$collectParts(EntityModel<?> entityModel) {
        Set<ModelPart> uniqueParts = Collections.newSetFromMap(new IdentityHashMap<>());
        if (entityModel instanceof HierarchicalModel<?> hierarchicalModel) {
            hierarchicalModel.root().getAllParts().forEach(uniqueParts::add);
        } else if (entityModel instanceof AgeableListModel<?> ageableListModel) {
            AgeableListModelAccessor accessor = (AgeableListModelAccessor) ageableListModel;
            accessor.steppedPlayerAnimations$headParts().forEach(
                    root -> root.getAllParts().forEach(uniqueParts::add)
            );
            accessor.steppedPlayerAnimations$bodyParts().forEach(
                    root -> root.getAllParts().forEach(uniqueParts::add)
            );
        } else if (entityModel instanceof ListModel<?> listModel) {
            for (ModelPart root : listModel.parts()) {
                root.getAllParts().forEach(uniqueParts::add);
            }
        } else {
            String modelName = entityModel.getClass().getName();
            if (steppedPlayerAnimations$LOGGED_UNSUPPORTED_MODELS.add(modelName)) {
                SteppedPlayerAnimationsClient.LOGGER.warn(
                        "The vanilla stepped-animation sampler cannot enumerate model parts for {}; leaving this model smooth.",
                        modelName
                );
            }
        }
        return new ArrayList<>(uniqueParts);
    }

    @Unique
    private static boolean steppedPlayerAnimations$isEmfBacked(List<ModelPart> parts) {
        for (ModelPart part : parts) {
            if (part.getClass().getName().startsWith("traben.entity_model_features.")) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private static final class SampleState {
        private final SteppedAnimationClock.Gate gate = new SteppedAnimationClock.Gate();
        private PoseSnapshot snapshot;
    }

    private record SampleKey(UUID entityId, boolean inventoryRender) {
    }

    @Unique
    private record PoseSnapshot(List<PartSnapshot> parts) {
        private static PoseSnapshot capture(List<ModelPart> modelParts) {
            return new PoseSnapshot(modelParts.stream().map(PartSnapshot::new).toList());
        }

        private boolean matches(List<ModelPart> modelParts) {
            if (parts.size() != modelParts.size()) {
                return false;
            }
            Set<ModelPart> expected = Collections.newSetFromMap(new IdentityHashMap<>());
            parts.forEach(snapshot -> expected.add(snapshot.part));
            return modelParts.stream().allMatch(expected::contains);
        }

        private void restore() {
            parts.forEach(PartSnapshot::restore);
        }
    }

    @Unique
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
