package dev.steppedplayeranimations.mixin.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.steppedplayeranimations.SteppedPlayerAnimationsClient;
import dev.steppedplayeranimations.config.SteppedAnimationConfig;
import dev.steppedplayeranimations.render.SteppedRenderContext;
import dev.steppedplayeranimations.timing.SteppedAnimationClock;
import dev.steppedplayeranimations.timing.ExpiringStateCache;
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
    private static final Map<EntityModel<?>, ContextSamples>
            steppedPlayerAnimations$SAMPLES = new WeakHashMap<>();
    @Unique
    private static final Map<EntityModel<?>, ModelTopology> steppedPlayerAnimations$TOPOLOGIES = new WeakHashMap<>();
    @Unique
    private static final Set<String> steppedPlayerAnimations$LOGGED_CAPTURE_MODELS = new LinkedHashSet<>();
    @Unique
    private static final Set<String> steppedPlayerAnimations$LOGGED_HOLD_MODELS = new LinkedHashSet<>();
    @Unique
    private static final Set<String> steppedPlayerAnimations$LOGGED_UNSUPPORTED_MODELS = new LinkedHashSet<>();

    @Unique
    private PoseSnapshot steppedPlayerAnimations$poseBeforeRender;
    @Unique
    private boolean steppedPlayerAnimations$restorePoseAfterRender;

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
        steppedPlayerAnimations$restorePoseAfterRender = false;
        if (!SteppedAnimationConfig.isSteppingActive() || !SteppedAnimationConfig.isEntityEnabled(entity)) {
            return;
        }

        List<ModelPart> parts = steppedPlayerAnimations$topology(model).parts();
        if (!parts.isEmpty()) {
            if (steppedPlayerAnimations$poseBeforeRender == null
                    || !steppedPlayerAnimations$poseBeforeRender.matches(parts)) {
                steppedPlayerAnimations$poseBeforeRender = new PoseSnapshot(parts);
            } else {
                steppedPlayerAnimations$poseBeforeRender.capture();
            }
            steppedPlayerAnimations$restorePoseAfterRender = true;
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
        ModelTopology topology = steppedPlayerAnimations$topology(model);
        List<ModelPart> parts = topology.parts();
        if (parts.isEmpty() || topology.emfBacked()) {
            return;
        }

        String modelName = model.getClass().getSimpleName();
        ContextSamples byContext = steppedPlayerAnimations$SAMPLES.computeIfAbsent(
                model,
                ignored -> new ContextSamples()
        );
        SampleState state = byContext.cache(SteppedRenderContext.isInventory()).getOrCreate(
                entity.getUUID(),
                SampleState::new
        );
        long now = SteppedAnimationClock.nowNanos();
        boolean topologyChanged = state.snapshot == null || !state.snapshot.matches(parts);
        long intervalNanos = SteppedAnimationConfig.sampleIntervalNanos();
        boolean sampleDue = state.gate.shouldCapture(now, intervalNanos, SteppedAnimationConfig.revision());
        if (topologyChanged || sampleDue) {
            if (topologyChanged) {
                state.snapshot = new PoseSnapshot(parts);
            } else {
                state.snapshot.capture();
            }
            if (steppedPlayerAnimations$LOGGED_CAPTURE_MODELS.add(modelName)) {
                SteppedPlayerAnimationsClient.LOGGER.debug(
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
                SteppedPlayerAnimationsClient.LOGGER.debug(
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
        if (steppedPlayerAnimations$restorePoseAfterRender) {
            steppedPlayerAnimations$poseBeforeRender.restore();
            steppedPlayerAnimations$restorePoseAfterRender = false;
        }
    }

    @Unique
    private static ModelTopology steppedPlayerAnimations$topology(EntityModel<?> entityModel) {
        return steppedPlayerAnimations$TOPOLOGIES.computeIfAbsent(
                entityModel,
                VanillaLivingEntityPoseSamplerMixin::steppedPlayerAnimations$collectTopology
        );
    }

    @Unique
    private static ModelTopology steppedPlayerAnimations$collectTopology(EntityModel<?> entityModel) {
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
        List<ModelPart> parts = List.copyOf(new ArrayList<>(uniqueParts));
        boolean emfBacked = parts.stream().anyMatch(
                part -> part.getClass().getName().startsWith("traben.entity_model_features.")
        );
        return new ModelTopology(parts, emfBacked);
    }

    @Unique
    private static final class SampleState {
        private final SteppedAnimationClock.Gate gate = new SteppedAnimationClock.Gate();
        private PoseSnapshot snapshot;
    }

    @Unique
    private static final class ContextSamples {
        private final ExpiringStateCache<UUID, SampleState> world = new ExpiringStateCache<>();
        private final ExpiringStateCache<UUID, SampleState> inventory = new ExpiringStateCache<>();

        private ExpiringStateCache<UUID, SampleState> cache(boolean inventoryRender) {
            return inventoryRender ? inventory : world;
        }
    }

    @Unique
    private record ModelTopology(List<ModelPart> parts, boolean emfBacked) {
    }

    @Unique
    private static final class PoseSnapshot {
        private final List<ModelPart> topology;
        private final PartSnapshot[] parts;

        private PoseSnapshot(List<ModelPart> modelParts) {
            topology = modelParts;
            parts = new PartSnapshot[modelParts.size()];
            for (int index = 0; index < modelParts.size(); index++) {
                parts[index] = new PartSnapshot(modelParts.get(index));
            }
        }

        private boolean matches(List<ModelPart> modelParts) {
            if (topology == modelParts) {
                return true;
            }
            if (parts.length != modelParts.size()) {
                return false;
            }
            for (int index = 0; index < parts.length; index++) {
                if (parts[index].part != modelParts.get(index)) {
                    return false;
                }
            }
            return true;
        }

        private void capture() {
            for (PartSnapshot part : parts) {
                part.capture();
            }
        }

        private void restore() {
            for (PartSnapshot part : parts) {
                part.restore();
            }
        }
    }

    @Unique
    private static final class PartSnapshot {
        private final ModelPart part;
        private float x;
        private float y;
        private float z;
        private float xRot;
        private float yRot;
        private float zRot;
        private float xScale;
        private float yScale;
        private float zScale;
        private boolean visible;
        private boolean skipDraw;

        private PartSnapshot(ModelPart part) {
            this.part = part;
            capture();
        }

        private void capture() {
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
