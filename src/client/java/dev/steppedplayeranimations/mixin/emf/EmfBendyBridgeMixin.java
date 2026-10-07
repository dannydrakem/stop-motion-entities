package dev.steppedplayeranimations.mixin.emf;

import dev.steppedplayeranimations.SteppedPlayerAnimationsClient;
import dev.steppedplayeranimations.compat.emf.EmfPartBlendState;
import dev.steppedplayeranimations.mixin.vanilla.ModelPartChildrenAccessor;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.core.util.Pair;
import dev.kosmx.playerAnim.impl.IAnimatedPlayer;
import dev.kosmx.playerAnim.impl.animation.AnimationApplier;
import dev.kosmx.playerAnim.impl.animation.IBendHelper;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.HashMap;

@Pseudo
@Mixin(targets = "traben.entity_model_features.models.parts.EMFModelPartRoot", remap = false)
abstract class EmfBendyBridgeMixin {
    private static final Pair<Float, Float> ZERO_BEND = new Pair<>(0.0F, 0.0F);
    private static final Vec3f CONTROL_PROBE = new Vec3f(923.25F, -811.5F, 677.75F);
    private static final List<String> BLEND_GROUPS = List.of(
            "head", "torso", "rightArm", "leftArm", "rightLeg", "leftLeg"
    );
    private static final List<TransformType> CONTROL_TRANSFORMS = List.of(
            TransformType.POSITION,
            TransformType.ROTATION,
            TransformType.BEND,
            TransformType.SCALE
    );
    private static final Map<String, List<String>> ANIMATION_SOURCES = Map.of(
            "head", List.of("head"),
            "torso", List.of("torso", "body"),
            "rightArm", List.of("rightArm"),
            "leftArm", List.of("leftArm"),
            "rightLeg", List.of("rightLeg"),
            "leftLeg", List.of("leftLeg")
    );
    private static final Map<String, List<String>> EMF_TARGETS = Map.of(
            "head", List.of("head", "hat"),
            "torso", List.of("body", "jacket"),
            "rightArm", List.of("right_arm", "right_sleeve"),
            "leftArm", List.of("left_arm", "left_sleeve"),
            "rightLeg", List.of("right_leg", "right_pants"),
            "leftLeg", List.of("left_leg", "left_pants")
    );
    private static final Set<ModelPart> INITIALIZED_PARTS =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<ModelPart, ModelPart[]> LIVE_HEAD_PARTS_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, ModelPart[]> VISIBLE_PARTS_CACHE = new WeakHashMap<>();

    private static final Map<String, String> BEND_SOURCES = Map.of(
            "body", "torso",
            "jacket", "torso",
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
    private static Field entitiesPausedParts;
    private static Field customPartId;
    private static boolean reflectionFailed;
    private static final Set<String> LOGGED_LIVE_HEAD_BRANCHES = new LinkedHashSet<>();
    private static final Set<String> LOGGED_EMBEDDED_HEAD_RIGS = new LinkedHashSet<>();
    private static final Set<String> LOGGED_NON_ZERO_BEND_SOURCES = new LinkedHashSet<>();
    private static String lastLoggedBlendMask;

    @Unique
    private final Map<Integer, ModelPart[]> steppedPlayerAnimations$pausedPartsCache = new HashMap<>();
    @Unique
    private boolean steppedPlayerAnimations$bendsWereApplied;

    @Inject(method = "animate", at = @At("HEAD"))
    private void steppedPlayerAnimations$forwardBendsToEmfCubes(CallbackInfo callback) {
        if (reflectionFailed) {
            return;
        }

        UUID entityId = null;
        try {
            initializeReflection();
            Object entity = getCurrentEntity.invoke(null);
            if (!(entity instanceof Entity minecraftEntity)) {
                return;
            }
            entityId = minecraftEntity.getUUID();
            if (!(entity instanceof IAnimatedPlayer animatedPlayer)) {
                EmfPartBlendState.setManaged(entityId, false);
                steppedPlayerAnimations$setPausedParts(entityId, null);
                return;
            }

            AnimationApplier animation = animatedPlayer.playerAnimator_getAnimation();
            boolean active = animation != null && animation.isActive();
            @SuppressWarnings("unchecked")
            Map<String, Object> vanillaParts = (Map<String, Object>) getAllVanillaParts.invoke(this);

            steppedPlayerAnimations$updatePartialPause(entityId, active, animation, vanillaParts);

            // A shared EMF model must be cleared once after rendering an animated player so the
            // next entity cannot inherit its bend. Repeating the same zero-bend traversal on every
            // later idle frame only performs reflection and map lookups without changing output.
            if (!active && !steppedPlayerAnimations$bendsWereApplied) {
                return;
            }

            for (Map.Entry<String, String> mapping : BEND_SOURCES.entrySet()) {
                Object vanillaPart = vanillaParts.get(mapping.getKey());
                if (vanillaPart == null) {
                    continue;
                }

                Pair<Float, Float> bend = active
                        ? steppedPlayerAnimations$getBend(animation, mapping.getValue())
                        : ZERO_BEND;
                if (active
                        && Math.abs(bend.getRight()) >= 0.0001F
                        && LOGGED_NON_ZERO_BEND_SOURCES.add(mapping.getValue())) {
                    SteppedPlayerAnimationsClient.LOGGER.debug(
                            "Forwarding non-zero bend to FA Player cubes: source={}, axis={}, angle={}",
                            mapping.getValue(), bend.getLeft(), bend.getRight()
                    );
                }
                ModelPart[] visibleParts = steppedPlayerAnimations$getVisibleParts(vanillaPart);
                for (ModelPart visiblePart : visibleParts) {
                    if (INITIALIZED_PARTS.add(visiblePart)) {
                        // Match PlayerAnimator's own pivot convention: the torso and jacket bend
                        // from their lower end, while limbs and their overlay layers bend from
                        // their upper end.
                        Direction bendDirection = mapping.getValue().equals("torso")
                                ? Direction.DOWN
                                : Direction.UP;
                        IBendHelper.INSTANCE.initBend(visiblePart, bendDirection);
                    }
                    IBendHelper.INSTANCE.bend(visiblePart, bend);
                }
            }
            steppedPlayerAnimations$bendsWereApplied = active;
        } catch (ReflectiveOperationException | ClassCastException exception) {
            if (entityId != null) {
                EmfPartBlendState.setManaged(entityId, false);
                try {
                    steppedPlayerAnimations$setPausedParts(entityId, null);
                } catch (IllegalAccessException ignored) {
                }
            }
            reflectionFailed = true;
            SteppedPlayerAnimationsClient.LOGGER.error("Could not forward PlayerAnimator bends to EMF model cubes; disabling the bend bridge.", exception);
        }
    }

    private static ModelPart[] steppedPlayerAnimations$getVisibleParts(Object vanillaPart)
            throws ReflectiveOperationException {
        ModelPart[] cached = VISIBLE_PARTS_CACHE.get(vanillaPart);
        if (cached != null) {
            return cached;
        }
        Object[] reflected = (Object[]) getAllCustomChildren.invoke(vanillaPart);
        ModelPart[] visibleParts = new ModelPart[reflected.length];
        for (int index = 0; index < reflected.length; index++) {
            visibleParts[index] = (ModelPart) reflected[index];
        }
        VISIBLE_PARTS_CACHE.put(vanillaPart, visibleParts);
        return visibleParts;
    }

    private static Pair<Float, Float> steppedPlayerAnimations$getBend(
            AnimationApplier animation,
            String source
    ) {
        Pair<Float, Float> bend = animation.getBend(source);
        if (!source.equals("torso")) {
            return bend;
        }

        // AnimationApplier.updatePart("torso", ...) combines these two legacy
        // channels. Some Emotecraft animations put the actual waist bend in
        // "body", so forwarding only "torso" silently produces a straight cube.
        Pair<Float, Float> legacyBodyBend = animation.getBend("body");
        return new Pair<>(
                bend.getLeft() + legacyBodyBend.getLeft(),
                bend.getRight() + legacyBodyBend.getRight()
        );
    }

    private void steppedPlayerAnimations$updatePartialPause(
            UUID entityId,
            boolean active,
            AnimationApplier animation,
            Map<String, Object> vanillaParts
    ) throws ReflectiveOperationException {
        if (!active) {
            EmfPartBlendState.setManaged(entityId, false);
            steppedPlayerAnimations$setPausedParts(entityId, null);
            return;
        }

        int controlMask = 0;
        for (int groupIndex = 0; groupIndex < BLEND_GROUPS.size(); groupIndex++) {
            String group = BLEND_GROUPS.get(groupIndex);
            for (String source : ANIMATION_SOURCES.get(group)) {
                if (steppedPlayerAnimations$isControlled(animation, source)) {
                    controlMask |= 1 << groupIndex;
                    break;
                }
            }
        }

        ModelPart[] pausedParts = steppedPlayerAnimations$pausedPartsCache.get(controlMask);
        if (pausedParts == null) {
            Set<ModelPart> collectedParts = Collections.newSetFromMap(new IdentityHashMap<>());
            for (int groupIndex = 0; groupIndex < BLEND_GROUPS.size(); groupIndex++) {
                if ((controlMask & (1 << groupIndex)) == 0) {
                    continue;
                }
                String group = BLEND_GROUPS.get(groupIndex);
                for (String target : EMF_TARGETS.get(group)) {
                    Object vanillaPart = vanillaParts.get(target);
                    if (!(vanillaPart instanceof ModelPart modelPart)) {
                        continue;
                    }

                    collectedParts.add(modelPart);
                    ModelPart[] customChildren = steppedPlayerAnimations$getVisibleParts(vanillaPart);
                    Set<ModelPart> liveHeadAnimationParts = Collections.newSetFromMap(new IdentityHashMap<>());
                    if (group.equals("head")) {
                        for (ModelPart customChild : customChildren) {
                            steppedPlayerAnimations$collectIndependentHeadAnimationParts(
                                    customChild,
                                    liveHeadAnimationParts
                            );
                        }
                    }
                    for (ModelPart customChild : customChildren) {
                        customChild.getAllParts().forEach(descendant -> {
                            if (!liveHeadAnimationParts.contains(descendant)) {
                                collectedParts.add(descendant);
                            }
                        });
                    }
                }
            }
            pausedParts = collectedParts.toArray(ModelPart[]::new);
            steppedPlayerAnimations$pausedPartsCache.put(controlMask, pausedParts);
        }

        EmfPartBlendState.setManaged(entityId, true);
        steppedPlayerAnimations$setPausedParts(
                entityId,
                pausedParts.length == 0 ? null : pausedParts
        );

        if (controlMask != steppedPlayerAnimations$lastLoggedControlMask) {
            steppedPlayerAnimations$lastLoggedControlMask = controlMask;
            List<String> controlledGroups = new ArrayList<>();
            for (int groupIndex = 0; groupIndex < BLEND_GROUPS.size(); groupIndex++) {
                if ((controlMask & (1 << groupIndex)) != 0) {
                    controlledGroups.add(BLEND_GROUPS.get(groupIndex));
                }
            }
            String mask = String.join(", ", controlledGroups);
            if (!mask.equals(lastLoggedBlendMask)) {
                lastLoggedBlendMask = mask;
                SteppedPlayerAnimationsClient.LOGGER.debug(
                        "Partial FA/Emotecraft blend active; Emotecraft controls: [{}]",
                        mask
                );
            }
        }
    }

    @Unique
    private int steppedPlayerAnimations$lastLoggedControlMask = Integer.MIN_VALUE;

    private static void steppedPlayerAnimations$collectIndependentHeadAnimationParts(
            Object customChild,
            Set<ModelPart> destination
    )
            throws IllegalAccessException {
        ModelPart root = (ModelPart) customChild;
        ModelPart[] cached = LIVE_HEAD_PARTS_CACHE.get(root);
        if (cached != null) {
            Collections.addAll(destination, cached);
            return;
        }

        Set<ModelPart> liveParts = Collections.newSetFromMap(new IdentityHashMap<>());
        String id = steppedPlayerAnimations$getNormalizedPartId(customChild);
        if (steppedPlayerAnimations$isFacialBranchName(id)) {
            root.getAllParts().forEach(liveParts::add);
            steppedPlayerAnimations$logLiveHeadBranch(id, liveParts.size());
        } else {
            // Expressive Fresh Moves combines ordinary head geometry and a 400-part facial
            // selector under one custom `head` root. Unlike a standalone player_face branch,
            // those nested parts depend on the pack's own root animation and skin-feature
            // switching. Letting them run while Emotecraft owns the root makes texture layers
            // drift and expose the wrong skin pixels. Detect the embedded rig for diagnostics,
            // but use the safe fallback: pause it together with the root during the emote.
            Set<ModelPart> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            Deque<ModelPart> remaining = new ArrayDeque<>();
            Set<String> embeddedFacialBranches = new LinkedHashSet<>();
            Map<String, ModelPart> rootChildren =
                    ((ModelPartChildrenAccessor) (Object) root).steppedPlayerAnimations$children();
            remaining.addAll(rootChildren.values());

            int inspected = 0;
            while (!remaining.isEmpty() && inspected < 1024) {
                ModelPart part = remaining.removeFirst();
                if (!visited.add(part)) {
                    continue;
                }
                inspected++;

                String partId = customPartId.getDeclaringClass().isInstance(part)
                        ? steppedPlayerAnimations$getNormalizedPartId(part)
                        : "";
                if (steppedPlayerAnimations$isFacialComponentName(partId)) {
                    embeddedFacialBranches.add(partId);
                    continue;
                }

                Map<String, ModelPart> children =
                        ((ModelPartChildrenAccessor) (Object) part).steppedPlayerAnimations$children();
                for (Map.Entry<String, ModelPart> child : children.entrySet()) {
                    String childName = steppedPlayerAnimations$normalizeEmfId(child.getKey());
                    if (steppedPlayerAnimations$isFacialComponentName(childName)) {
                        embeddedFacialBranches.add(childName);
                    } else {
                        remaining.addLast(child.getValue());
                    }
                }
            }
            if (!embeddedFacialBranches.isEmpty()) {
                if (LOGGED_EMBEDDED_HEAD_RIGS.add(id)) {
                    SteppedPlayerAnimationsClient.LOGGER.debug(
                            "Embedded EMF facial rig detected under '{}'; using safe emote pause for nested branches {}.",
                            id,
                            embeddedFacialBranches
                    );
                }
            }
        }

        ModelPart[] result = liveParts.toArray(ModelPart[]::new);
        LIVE_HEAD_PARTS_CACHE.put(root, result);
        Collections.addAll(destination, result);
    }

    private static void steppedPlayerAnimations$logLiveHeadBranch(String id, int partCount) {
        if (!id.isEmpty() && LOGGED_LIVE_HEAD_BRANCHES.add(id)) {
            SteppedPlayerAnimationsClient.LOGGER.debug(
                    "Independent EMF facial branch '{}' detected; keeping {} internal parts live during Emotecraft.",
                    id,
                    partCount
            );
        }
    }

    private static String steppedPlayerAnimations$getNormalizedPartId(Object part)
            throws IllegalAccessException {
        return steppedPlayerAnimations$normalizeEmfId(String.valueOf(customPartId.get(part)));
    }

    private static boolean steppedPlayerAnimations$isFacialBranchName(String name) {
        return name.equals("player_face")
                || name.equals("face")
                || name.equals("eyes")
                || name.equals("brows")
                || name.equals("eyelids")
                || name.equals("eyelashes")
                || name.equals("mouth");
    }

    private static boolean steppedPlayerAnimations$isFacialComponentName(String name) {
        return steppedPlayerAnimations$isFacialBranchName(name)
                || steppedPlayerAnimations$isEyeName(name)
                || steppedPlayerAnimations$isBrowName(name)
                || steppedPlayerAnimations$isMouthName(name);
    }

    private static boolean steppedPlayerAnimations$isEyeName(String name) {
        return name.equals("eye")
                || name.equals("eyes")
                || name.contains("_eye")
                || name.contains("eye_")
                || name.contains("eyelid")
                || name.contains("eyelash")
                || name.contains("pupil")
                || name.contains("blink");
    }

    private static boolean steppedPlayerAnimations$isBrowName(String name) {
        return name.contains("brow");
    }

    private static boolean steppedPlayerAnimations$isMouthName(String name) {
        return name.contains("mouth") || name.contains("lip");
    }

    private static String steppedPlayerAnimations$normalizeEmfId(String id) {
        String normalized = id.toLowerCase(Locale.ROOT);
        while (normalized.startsWith("emf_")) {
            normalized = normalized.substring(4).toLowerCase(Locale.ROOT);
        }
        return normalized;
    }

    private static boolean steppedPlayerAnimations$isControlled(AnimationApplier animation, String partName) {
        for (TransformType transform : CONTROL_TRANSFORMS) {
            Vec3f result = animation.get3DTransform(partName, transform, CONTROL_PROBE);
            if (Float.compare(result.getX(), CONTROL_PROBE.getX()) != 0
                    || Float.compare(result.getY(), CONTROL_PROBE.getY()) != 0
                    || Float.compare(result.getZ(), CONTROL_PROBE.getZ()) != 0) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static void steppedPlayerAnimations$setPausedParts(UUID entityId, ModelPart[] parts)
            throws IllegalAccessException {
        Map<UUID, Object> pausedPartsByEntity = (Map<UUID, Object>) entitiesPausedParts.get(null);
        if (parts == null || parts.length == 0) {
            pausedPartsByEntity.remove(entityId);
        } else {
            pausedPartsByEntity.put(entityId, parts);
        }
    }

    private static void initializeReflection() throws ReflectiveOperationException {
        if (getCurrentEntity != null) {
            return;
        }

        Class<?> animationApi = Class.forName("traben.entity_model_features.EMFAnimationApi");
        Class<?> rootClass = Class.forName("traben.entity_model_features.models.parts.EMFModelPartRoot");
        Class<?> vanillaPartClass = Class.forName("traben.entity_model_features.models.parts.EMFModelPartVanilla");
        Class<?> customPartClass = Class.forName("traben.entity_model_features.models.parts.EMFModelPartCustom");
        Class<?> pauseHandlerClass = Class.forName("traben.entity_model_features.utils.EMFAnimationPauseHandler");
        getCurrentEntity = animationApi.getMethod("getCurrentEntity");
        getAllVanillaParts = rootClass.getMethod("getAllVanillaPartsByNameEMF");
        getAllCustomChildren = vanillaPartClass.getMethod("getAllEMFCustomChildren");
        customPartId = customPartClass.getField("id");
        entitiesPausedParts = pauseHandlerClass.getField("entitiesPausedParts");
    }
}
