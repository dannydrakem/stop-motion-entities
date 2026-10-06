package dev.steppedplayeranimations.compat.figura;

import dev.steppedplayeranimations.SteppedPlayerAnimationsClient;
import dev.steppedplayeranimations.config.SteppedAnimationConfig;
import dev.steppedplayeranimations.render.SteppedRenderContext;
import dev.steppedplayeranimations.timing.SteppedAnimationClock;
import net.minecraft.world.entity.Entity;
import org.figuramc.figura.math.matrix.FiguraMat3;
import org.figuramc.figura.math.matrix.FiguraMat4;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.figuramc.figura.model.PartCustomization;
import org.figuramc.figura.model.rendering.AvatarRenderer;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.ArrayDeque;

public final class FiguraSteppingContext {
    private static final Map<AvatarRenderer, RendererSamples> SAMPLES = new WeakHashMap<>();
    private static final ThreadLocal<ArrayDeque<ActiveRender>> ACTIVE =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static boolean logged;

    private FiguraSteppingContext() {
    }

    public static void begin(AvatarRenderer renderer) {
        Entity entity = renderer.entity;
        if (!SteppedAnimationConfig.isSteppingActive()
                || entity == null
                || !SteppedAnimationConfig.isEntityEnabled(entity)) {
            ACTIVE.get().push(ActiveRender.DISABLED);
            return;
        }

        RendererSamples rendererSamples = SAMPLES.computeIfAbsent(renderer, ignored -> new RendererSamples());
        RenderKey key = new RenderKey(
                entity.getUUID(),
                renderer.currentFilterScheme,
                SteppedRenderContext.isInventory()
        );
        SampleState state = rendererSamples.contexts.computeIfAbsent(key, ignored -> new SampleState());
        boolean capture = state.gate.shouldCapture(
                SteppedAnimationClock.nowNanos(),
                SteppedAnimationConfig.sampleIntervalNanos(),
                SteppedAnimationConfig.revision()
        );
        ACTIVE.get().push(new ActiveRender(state, capture));

        if (!logged) {
            logged = true;
            SteppedPlayerAnimationsClient.LOGGER.info(
                    "Figura avatar stepped-animation sampling active at {} FPS.",
                    SteppedAnimationConfig.frameRate().framesPerSecond()
            );
        }
    }

    public static void end() {
        ArrayDeque<ActiveRender> renders = ACTIVE.get();
        if (!renders.isEmpty()) {
            renders.pop();
        }
        if (renders.isEmpty()) {
            ACTIVE.remove();
        }
    }

    public static PartCustomization sampleForRender(PartCustomization customization) {
        ActiveRender active = ACTIVE.get().peek();
        if (active == null || active.state == null) {
            return customization;
        }

        Snapshot held = active.state.parts.get(customization);
        if (active.capture || held == null) {
            active.state.parts.put(customization, Snapshot.capture(customization));
            return customization;
        }

        PartCustomization renderCopy = new PartCustomization();
        customization.copyTo(renderCopy);
        held.apply(renderCopy);
        return renderCopy;
    }

    private static final class RendererSamples {
        private final Map<RenderKey, SampleState> contexts = new HashMap<>();
    }

    private record RenderKey(UUID entityId, Object filterScheme, boolean inventory) {
    }

    private static final class SampleState {
        private final SteppedAnimationClock.Gate gate = new SteppedAnimationClock.Gate();
        private final Map<PartCustomization, Snapshot> parts = new IdentityHashMap<>();
    }

    private static final class ActiveRender {
        private static final ActiveRender DISABLED = new ActiveRender(null, false);
        private final SampleState state;
        private final boolean capture;

        private ActiveRender(SampleState state, boolean capture) {
            this.state = state;
            this.capture = capture;
        }
    }

    private record Snapshot(
            FiguraVec3 position,
            FiguraVec3 rotation,
            FiguraVec3 scale,
            FiguraVec3 pivot,
            FiguraVec3 offsetPivot,
            FiguraVec3 offsetPosition,
            FiguraVec3 offsetRotation,
            FiguraVec3 offsetScale,
            FiguraVec3 animationPosition,
            FiguraVec3 animationRotation,
            FiguraVec3 animationScale,
            FiguraMat4 positionMatrix,
            FiguraMat3 normalMatrix,
            FiguraMat3 uvMatrix,
            boolean needsMatrixRecalculation,
            boolean visible
    ) {
        private static Snapshot capture(PartCustomization customization) {
            return new Snapshot(
                    customization.getPos(),
                    customization.getRot(),
                    customization.getScale(),
                    customization.getPivot(),
                    customization.getOffsetPivot(),
                    customization.getOffsetPos(),
                    customization.getOffsetRot(),
                    customization.getOffsetScale(),
                    customization.getAnimPos(),
                    customization.getAnimRot(),
                    customization.getAnimScale(),
                    customization.getPositionMatrix(),
                    customization.getNormalMatrix(),
                    customization.uvMatrix.copy(),
                    customization.needsMatrixRecalculation,
                    customization.visible
            );
        }

        private void apply(PartCustomization customization) {
            customization.setPos(position);
            customization.setRot(rotation);
            customization.setScale(scale);
            customization.setPivot(pivot);
            customization.offsetPivot(offsetPivot);
            customization.offsetPos(offsetPosition);
            customization.offsetRot(offsetRotation);
            customization.offsetScale(offsetScale);
            customization.setAnimPos(animationPosition.x, animationPosition.y, animationPosition.z);
            customization.setAnimRot(animationRotation.x, animationRotation.y, animationRotation.z);
            customization.setAnimScale(animationScale.x, animationScale.y, animationScale.z);
            customization.positionMatrix.set(positionMatrix);
            customization.normalMatrix.set(normalMatrix);
            customization.uvMatrix.set(uvMatrix);
            customization.needsMatrixRecalculation = needsMatrixRecalculation;
            customization.visible = visible;
        }
    }
}
