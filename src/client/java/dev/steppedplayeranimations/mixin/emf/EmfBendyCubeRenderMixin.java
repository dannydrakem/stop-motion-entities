package dev.steppedplayeranimations.mixin.emf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.steppedplayeranimations.SteppedPlayerAnimationsClient;
import io.github.kosmx.bendylib.MutableCuboid;
import io.github.kosmx.bendylib.impl.ICuboid;
import net.minecraft.util.Tuple;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "traben.entity_model_features.models.parts.EMFModelPartCustom$EMFCube", remap = false)
abstract class EmfBendyCubeRenderMixin {
    @Unique
    private static boolean steppedPlayerAnimations$loggedBendyRender;

    // EMF is named "compile" in Loom's development runtime and "method_32089" in a normal
    // intermediary-mapped Fabric installation. These optional injectors deliberately cover both.
    @Inject(method = "compile", at = @At("HEAD"), cancellable = true, require = 0)
    private void steppedPlayerAnimations$renderActiveBendyCuboidNamed(
            PoseStack.Pose pose,
            VertexConsumer vertices,
            int light,
            int overlay,
            int color,
            CallbackInfo callback
    ) {
        steppedPlayerAnimations$renderActiveBendyCuboid(pose, vertices, light, overlay, color, callback);
    }

    @Inject(method = "method_32089", at = @At("HEAD"), cancellable = true, require = 0)
    private void steppedPlayerAnimations$renderActiveBendyCuboidIntermediary(
            PoseStack.Pose pose,
            VertexConsumer vertices,
            int light,
            int overlay,
            int color,
            CallbackInfo callback
    ) {
        steppedPlayerAnimations$renderActiveBendyCuboid(pose, vertices, light, overlay, color, callback);
    }

    @Unique
    private void steppedPlayerAnimations$renderActiveBendyCuboid(
            PoseStack.Pose pose,
            VertexConsumer vertices,
            int light,
            int overlay,
            int color,
            CallbackInfo callback
    ) {
        MutableCuboid mutableCuboid = (MutableCuboid) (Object) this;
        Tuple<String, ICuboid> activeMutator = mutableCuboid.getActiveMutator();
        if (activeMutator == null) {
            return;
        }

        ICuboid bendyCuboid = activeMutator.getB();
        bendyCuboid.render(pose, vertices, light, overlay, color);
        if (!steppedPlayerAnimations$loggedBendyRender) {
            steppedPlayerAnimations$loggedBendyRender = true;
            SteppedPlayerAnimationsClient.LOGGER.info(
                    "Rendering active BendyLib geometry through the EMF cube renderer."
            );
        }
        if (bendyCuboid.disableAfterDraw()) {
            mutableCuboid.getAndActivateMutator(null);
        }
        callback.cancel();
    }
}
