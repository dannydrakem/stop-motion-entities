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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

@Pseudo
@Mixin(targets = "traben.entity_model_features.models.parts.EMFModelPartRoot", remap = false)
abstract class EmfBendyBridgeMixin {
    private static final Pair<Float, Float> ZERO_BEND = new Pair<>(0.0F, 0.0F);
    private static final Vec3f CONTROL_PROBE = new Vec3f(923.25F, -811.5F, 677.75F);
    private static final List<String> BLEND_GROUPS = List.of(
            "head", "torso", "rightArm", "leftArm", "rightLeg", "leftLeg"
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
    private static boolean loggedJustExpressionsFace;
    private static boolean loggedPlayerHeadHierarchy;
    private static final Set<String> LOGGED_NON_ZERO_BEND_SOURCES = new LinkedHashSet<>();
    private static String lastLoggedBlendMask;

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
                    SteppedPlayerAnimationsClient.LOGGER.info(
                            "Forwarding non-zero bend to FA Player cubes: source={}, axis={}, angle={}",
                            mapping.getValue(), bend.getLeft(), bend.getRight()
                    );
                }
                Object[] visibleParts = (Object[]) getAllCustomChildren.invoke(vanillaPart);
                for (Object visiblePartObject : visibleParts) {
                    ModelPart visiblePart = (ModelPart) visiblePartObject;
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

    private static void steppedPlayerAnimations$updatePartialPause(
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

        Set<ModelPart> pausedParts = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> controlledGroups = new LinkedHashSet<>();
        if (!loggedPlayerHeadHierarchy) {
            loggedPlayerHeadHierarchy = true;
            steppedPlayerAnimations$dumpPlayerHeadHierarchy(vanillaParts);
        }
        for (String group : BLEND_GROUPS) {
            boolean controlled = ANIMATION_SOURCES.get(group).stream()
                    .anyMatch(source -> steppedPlayerAnimations$isControlled(animation, source));
            if (!controlled) {
                continue;
            }

            controlledGroups.add(group);
            for (String target : EMF_TARGETS.get(group)) {
                Object vanillaPart = vanillaParts.get(target);
                if (!(vanillaPart instanceof ModelPart modelPart)) {
                    continue;
                }

                pausedParts.add(modelPart);
                Object[] customChildren = (Object[]) getAllCustomChildren.invoke(vanillaPart);
                Set<ModelPart> liveFaceParts = Collections.newSetFromMap(new IdentityHashMap<>());
                if (group.equals("head")) {
                    for (Object customChild : customChildren) {
                        if (steppedPlayerAnimations$isJustExpressionsFace(customChild)) {
                            ((ModelPart) customChild).getAllParts().forEach(liveFaceParts::add);
                        }
                    }
                }
                for (Object customChild : customChildren) {
                    for (ModelPart descendant : ((ModelPart) customChild).getAllParts().toList()) {
                        if (!liveFaceParts.contains(descendant)) {
                            pausedParts.add(descendant);
                        }
                    }
                }
                if (!liveFaceParts.isEmpty() && !loggedJustExpressionsFace) {
                    loggedJustExpressionsFace = true;
                    SteppedPlayerAnimationsClient.LOGGER.info(
                            "Just Expressions face branch detected; keeping all {} player_face parts animated during Emotecraft.",
                            liveFaceParts.size()
                    );
                }
            }
        }

        EmfPartBlendState.setManaged(entityId, true);
        steppedPlayerAnimations$setPausedParts(
                entityId,
                pausedParts.isEmpty() ? null : new ArrayList<>(pausedParts).toArray(ModelPart[]::new)
        );

        String mask = String.join(", ", controlledGroups);
        if (!mask.equals(lastLoggedBlendMask)) {
            lastLoggedBlendMask = mask;
            SteppedPlayerAnimationsClient.LOGGER.info(
                    "Partial FA/Emotecraft blend active; Emotecraft controls: [{}]",
                    mask
            );
        }
    }

    private static boolean steppedPlayerAnimations$isJustExpressionsFace(Object customChild)
            throws IllegalAccessException {
        String id = steppedPlayerAnimations$normalizeEmfId(
                String.valueOf(customPartId.get(customChild))
        );
        if ("player_face".equals(id)) {
            return true;
        }

        // EMF may rename the imported JPM root when its ID collides with an existing child.
        // The actual Just Expressions root has a stable structural signature even in that case:
        // its direct children contain the eyes and brows groups (and normally extras as well).
        Map<String, ModelPart> children =
                ((ModelPartChildrenAccessor) customChild).steppedPlayerAnimations$children();
        Set<String> normalizedChildNames = new LinkedHashSet<>();
        for (String childName : children.keySet()) {
            normalizedChildNames.add(steppedPlayerAnimations$normalizeEmfId(childName));
        }
        return normalizedChildNames.contains("eyes") && normalizedChildNames.contains("brows");
    }

    private static String steppedPlayerAnimations$normalizeEmfId(String id) {
        String normalized = id;
        while (normalized.startsWith("EMF_")) {
            normalized = normalized.substring(4);
        }
        return normalized;
    }

    private static void steppedPlayerAnimations$dumpPlayerHeadHierarchy(Map<String, Object> vanillaParts)
            throws IllegalAccessException {
        StringBuilder dump = new StringBuilder(8192);
        dump.append("Runtime EMF player head hierarchy (diagnostic stage 22.1).\n")
                .append("Available vanilla-part keys: ")
                .append(vanillaParts.keySet())
                .append('\n');

        Set<ModelPart> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        int[] remaining = {512};
        for (String rootName : List.of("head", "hat")) {
            Object root = vanillaParts.get(rootName);
            if (root instanceof ModelPart modelPart) {
                steppedPlayerAnimations$appendPartHierarchy(
                        dump,
                        rootName,
                        modelPart,
                        0,
                        visited,
                        remaining
                );
            } else {
                dump.append(rootName).append(" = <missing>\n");
            }
        }
        if (remaining[0] == 0) {
            dump.append("<diagnostic truncated after 512 unique parts>\n");
        }

        SteppedPlayerAnimationsClient.LOGGER.info("{}", dump);
    }

    private static void steppedPlayerAnimations$appendPartHierarchy(
            StringBuilder dump,
            String path,
            ModelPart part,
            int depth,
            Set<ModelPart> visited,
            int[] remaining
    ) throws IllegalAccessException {
        if (remaining[0] <= 0 || depth > 24) {
            return;
        }

        String internalId = "-";
        if (customPartId.getDeclaringClass().isInstance(part)) {
            Object value = customPartId.get(part);
            internalId = String.valueOf(value);
        }
        dump.append("  ".repeat(depth))
                .append(path)
                .append(" | class=")
                .append(part.getClass().getName())
                .append(" | id=")
                .append(internalId)
                .append(" | identity=")
                .append(Integer.toHexString(System.identityHashCode(part)))
                .append('\n');

        if (!visited.add(part)) {
            dump.append("  ".repeat(depth + 1)).append("<already visited>\n");
            return;
        }
        remaining[0]--;

        Map<String, ModelPart> children =
                ((ModelPartChildrenAccessor) (Object) part).steppedPlayerAnimations$children();
        for (Map.Entry<String, ModelPart> child : children.entrySet()) {
            steppedPlayerAnimations$appendPartHierarchy(
                    dump,
                    path + "/" + child.getKey(),
                    child.getValue(),
                    depth + 1,
                    visited,
                    remaining
            );
        }
    }

    private static boolean steppedPlayerAnimations$isControlled(AnimationApplier animation, String partName) {
        for (TransformType transform : List.of(
                TransformType.POSITION,
                TransformType.ROTATION,
                TransformType.BEND,
                TransformType.SCALE
        )) {
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
