package dev.steppedplayeranimations.mixin.emf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.steppedplayeranimations.SteppedPlayerAnimationsClient;
import io.github.kosmx.bendylib.impl.BendableCuboid;
import io.github.kosmx.bendylib.MutableCuboid;
import io.github.kosmx.bendylib.impl.IRepositionableVertex;
import io.github.kosmx.bendylib.impl.ICuboid;
import net.minecraft.util.Tuple;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

@Pseudo
@Mixin(targets = "traben.entity_model_features.models.parts.EMFModelPartCustom$EMFCube", remap = false)
abstract class EmfBendyCubeRenderMixin {
    @Unique
    private static boolean steppedPlayerAnimations$loggedBendyRender;
    @Unique
    private static boolean steppedPlayerAnimations$loggedUvRenderFailure;
    @Unique
    private static final Map<Object, List<steppedPlayerAnimations$FaceUv>>
            steppedPlayerAnimations$uvLayouts = new WeakHashMap<>();
    @Unique
    private static final Map<BendableCuboid, steppedPlayerAnimations$BendyLayout>
            steppedPlayerAnimations$bendyLayouts = new WeakHashMap<>();
    @Unique
    private static final ThreadLocal<steppedPlayerAnimations$RenderScratch>
            steppedPlayerAnimations$renderScratch =
            ThreadLocal.withInitial(steppedPlayerAnimations$RenderScratch::new);
    @Unique
    private static Field steppedPlayerAnimations$cubePolygons;
    @Unique
    private static Field steppedPlayerAnimations$polygonVertices;
    @Unique
    private static Field steppedPlayerAnimations$vertexPosition;
    @Unique
    private static Field steppedPlayerAnimations$vertexU;
    @Unique
    private static Field steppedPlayerAnimations$vertexV;
    @Unique
    private static Field steppedPlayerAnimations$bendySides;

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

        ICuboid activeCuboid = activeMutator.getB();
        if (activeCuboid instanceof BendableCuboid bendyCuboid) {
            try {
                steppedPlayerAnimations$renderWithOriginalUv(
                        this,
                        bendyCuboid,
                        pose,
                        vertices,
                        light,
                        overlay,
                        color
                );
                if (!steppedPlayerAnimations$loggedBendyRender) {
                    steppedPlayerAnimations$loggedBendyRender = true;
                    SteppedPlayerAnimationsClient.LOGGER.debug(
                            "Rendering BendyLib-deformed EMF geometry with its original UV layout."
                    );
                }
                callback.cancel();
                return;
            } catch (ReflectiveOperationException | RuntimeException exception) {
                // Returning to EMF's untouched cube is safer than drawing BendyLib's replacement
                // cuboid: the latter loses per-face EMF UVs and exposes unrelated skin pixels.
                mutableCuboid.getAndActivateMutator(null);
                if (!steppedPlayerAnimations$loggedUvRenderFailure) {
                    steppedPlayerAnimations$loggedUvRenderFailure = true;
                    SteppedPlayerAnimationsClient.LOGGER.error(
                            "Could not preserve EMF UVs while bending; drawing the original unbent cube.",
                            exception
                    );
                }
                return;
            }
        }

        activeCuboid.render(pose, vertices, light, overlay, color);
        if (activeCuboid.disableAfterDraw()) {
            mutableCuboid.getAndActivateMutator(null);
        }
        callback.cancel();
    }

    @Unique
    private static void steppedPlayerAnimations$renderWithOriginalUv(
            Object emfCube,
            BendableCuboid bendyCuboid,
            PoseStack.Pose pose,
            VertexConsumer vertices,
            int light,
            int overlay,
            int color
    ) throws ReflectiveOperationException {
        List<steppedPlayerAnimations$FaceUv> sourceFaces =
                steppedPlayerAnimations$uvLayouts.get(emfCube);
        if (sourceFaces == null) {
            sourceFaces = steppedPlayerAnimations$captureUvLayout(emfCube);
            steppedPlayerAnimations$uvLayouts.put(emfCube, sourceFaces);
        }

        steppedPlayerAnimations$BendyLayout bendyLayout =
                steppedPlayerAnimations$bendyLayouts.get(bendyCuboid);
        if (bendyLayout == null) {
            bendyLayout = steppedPlayerAnimations$buildBendyLayout(bendyCuboid, sourceFaces);
            steppedPlayerAnimations$bendyLayouts.put(bendyCuboid, bendyLayout);
        }

        Matrix4f positionMatrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();
        steppedPlayerAnimations$RenderScratch scratch = steppedPlayerAnimations$renderScratch.get();
        for (steppedPlayerAnimations$QuadRenderData side : bendyLayout.sides()) {
            BendableCuboid.Quad bendySide = side.quad();
            Vector3f first = bendySide.vertices[0].getPos();
            Vector3f second = bendySide.vertices[1].getPos();
            Vector3f third = bendySide.vertices[2].getPos();
            Vector3f fourth = bendySide.vertices[3].getPos();
            scratch.firstEdge.set(second).sub(fourth);
            scratch.normal.set(first).sub(third).cross(scratch.firstEdge).normalize();
            if (!scratch.normal.isFinite()) {
                scratch.normal.set(0.0F, 1.0F, 0.0F);
            }
            scratch.normal.mul(normalMatrix);
            for (int index = 0; index < 4; index++) {
                Vector3f current = bendySide.vertices[index].getPos();
                positionMatrix.transformPosition(
                        current.x() / 16.0F,
                        current.y() / 16.0F,
                        current.z() / 16.0F,
                        scratch.transformedPosition
                );
                vertices.addVertex(
                        scratch.transformedPosition.x(),
                        scratch.transformedPosition.y(),
                        scratch.transformedPosition.z(),
                        color,
                        side.u()[index],
                        side.v()[index],
                        overlay,
                        light,
                        scratch.normal.x(),
                        scratch.normal.y(),
                        scratch.normal.z()
                );
            }
        }
    }

    @Unique
    private static steppedPlayerAnimations$BendyLayout steppedPlayerAnimations$buildBendyLayout(
            BendableCuboid bendyCuboid,
            List<steppedPlayerAnimations$FaceUv> sourceFaces
    ) throws ReflectiveOperationException {
        if (steppedPlayerAnimations$bendySides == null) {
            steppedPlayerAnimations$bendySides = BendableCuboid.class.getDeclaredField("sides");
            steppedPlayerAnimations$bendySides.setAccessible(true);
        }
        BendableCuboid.Quad[] bendySides =
                (BendableCuboid.Quad[]) steppedPlayerAnimations$bendySides.get(bendyCuboid);
        steppedPlayerAnimations$QuadRenderData[] renderData =
                new steppedPlayerAnimations$QuadRenderData[bendySides.length];
        Vector3f[] originalPositions = new Vector3f[4];
        for (int sideIndex = 0; sideIndex < bendySides.length; sideIndex++) {
            BendableCuboid.Quad side = bendySides[sideIndex];
            for (int vertexIndex = 0; vertexIndex < 4; vertexIndex++) {
                IRepositionableVertex vertex = (IRepositionableVertex) side.vertices[vertexIndex];
                originalPositions[vertexIndex] = vertex.getPosObject().getOriginalPos();
            }
            steppedPlayerAnimations$FaceUv sourceFace =
                    steppedPlayerAnimations$findFace(sourceFaces, originalPositions);
            float[] u = new float[4];
            float[] v = new float[4];
            for (int vertexIndex = 0; vertexIndex < 4; vertexIndex++) {
                steppedPlayerAnimations$Uv uv = sourceFace.uvAt(originalPositions[vertexIndex]);
                u[vertexIndex] = uv.u();
                v[vertexIndex] = uv.v();
            }
            renderData[sideIndex] = new steppedPlayerAnimations$QuadRenderData(side, u, v);
        }
        return new steppedPlayerAnimations$BendyLayout(renderData);
    }

    @Unique
    private static List<steppedPlayerAnimations$FaceUv> steppedPlayerAnimations$captureUvLayout(
            Object emfCube
    ) throws ReflectiveOperationException {
        if (steppedPlayerAnimations$cubePolygons == null) {
            steppedPlayerAnimations$cubePolygons = steppedPlayerAnimations$findField(
                    ModelPart.Cube.class,
                    field -> field.getType().isArray(),
                    "polygons",
                    "field_3649"
            );
        }
        Object[] polygons = (Object[]) steppedPlayerAnimations$cubePolygons.get(emfCube);
        if (polygons.length == 0) {
            throw new IllegalStateException("EMF cube has no source polygons");
        }

        if (steppedPlayerAnimations$polygonVertices == null) {
            Class<?> polygonClass = polygons[0].getClass();
            steppedPlayerAnimations$polygonVertices = steppedPlayerAnimations$findField(
                    polygonClass,
                    field -> field.getType().isArray(),
                    "vertices",
                    "field_3502"
            );
        }

        List<steppedPlayerAnimations$FaceUv> faces = new ArrayList<>(polygons.length);
        for (Object polygon : polygons) {
            Object[] sourceVertices = (Object[]) steppedPlayerAnimations$polygonVertices.get(polygon);
            if (sourceVertices.length != 4) {
                continue;
            }
            steppedPlayerAnimations$initializeVertexFields(sourceVertices[0].getClass());
            steppedPlayerAnimations$SourceVertex[] faceVertices =
                    new steppedPlayerAnimations$SourceVertex[4];
            for (int index = 0; index < 4; index++) {
                Object sourceVertex = sourceVertices[index];
                faceVertices[index] = new steppedPlayerAnimations$SourceVertex(
                        new Vector3f((Vector3f) steppedPlayerAnimations$vertexPosition.get(sourceVertex)),
                        steppedPlayerAnimations$vertexU.getFloat(sourceVertex),
                        steppedPlayerAnimations$vertexV.getFloat(sourceVertex)
                );
            }
            faces.add(new steppedPlayerAnimations$FaceUv(faceVertices));
        }
        if (faces.isEmpty()) {
            throw new IllegalStateException("EMF cube has no usable source faces");
        }
        return List.copyOf(faces);
    }

    @Unique
    private static void steppedPlayerAnimations$initializeVertexFields(Class<?> vertexClass)
            throws NoSuchFieldException {
        if (steppedPlayerAnimations$vertexPosition != null) {
            return;
        }
        steppedPlayerAnimations$vertexPosition = steppedPlayerAnimations$findField(
                vertexClass,
                field -> field.getType() == Vector3f.class,
                "pos",
                "field_3605"
        );
        steppedPlayerAnimations$vertexU = steppedPlayerAnimations$findField(
                vertexClass,
                field -> field.getType() == float.class,
                "u",
                "field_3604"
        );
        steppedPlayerAnimations$vertexV = steppedPlayerAnimations$findField(
                vertexClass,
                field -> field.getType() == float.class
                        && field != steppedPlayerAnimations$vertexU,
                "v",
                "field_3603"
        );
    }

    @Unique
    private static Field steppedPlayerAnimations$findField(
            Class<?> owner,
            java.util.function.Predicate<Field> fallback,
            String... preferredNames
    ) throws NoSuchFieldException {
        for (String name : preferredNames) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        for (Field field : owner.getDeclaredFields()) {
            if (fallback.test(field)) {
                field.setAccessible(true);
                return field;
            }
        }
        throw new NoSuchFieldException(owner.getName());
    }

    @Unique
    private static steppedPlayerAnimations$FaceUv steppedPlayerAnimations$findFace(
            List<steppedPlayerAnimations$FaceUv> faces,
            Vector3f[] originalPositions
    ) {
        steppedPlayerAnimations$FaceUv best = null;
        float bestDistance = Float.POSITIVE_INFINITY;
        for (steppedPlayerAnimations$FaceUv face : faces) {
            float distance = 0.0F;
            for (Vector3f position : originalPositions) {
                distance += Math.abs(face.coordinate(position, face.constantAxis) - face.constantValue);
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                best = face;
            }
        }
        if (best == null) {
            throw new IllegalStateException("No matching EMF source face");
        }
        return best;
    }

    @Unique
    private record steppedPlayerAnimations$BendyLayout(
            steppedPlayerAnimations$QuadRenderData[] sides
    ) {
    }

    @Unique
    private record steppedPlayerAnimations$QuadRenderData(
            BendableCuboid.Quad quad,
            float[] u,
            float[] v
    ) {
    }

    @Unique
    private static final class steppedPlayerAnimations$RenderScratch {
        private final Vector3f firstEdge = new Vector3f();
        private final Vector3f normal = new Vector3f();
        private final Vector3f transformedPosition = new Vector3f();
    }

    @Unique
    private record steppedPlayerAnimations$SourceVertex(Vector3f position, float u, float v) {
    }

    @Unique
    private record steppedPlayerAnimations$Uv(float u, float v) {
    }

    @Unique
    private static final class steppedPlayerAnimations$FaceUv {
        private final int constantAxis;
        private final int firstAxis;
        private final int secondAxis;
        private final float constantValue;
        private final float firstMin;
        private final float firstMax;
        private final float secondMin;
        private final float secondMax;
        private final steppedPlayerAnimations$Uv[][] corners = new steppedPlayerAnimations$Uv[2][2];

        private steppedPlayerAnimations$FaceUv(steppedPlayerAnimations$SourceVertex[] vertices) {
            float[] min = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
            float[] max = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
            for (steppedPlayerAnimations$SourceVertex vertex : vertices) {
                for (int axis = 0; axis < 3; axis++) {
                    float value = coordinate(vertex.position(), axis);
                    min[axis] = Math.min(min[axis], value);
                    max[axis] = Math.max(max[axis], value);
                }
            }

            int constant = 0;
            if (max[1] - min[1] < max[constant] - min[constant]) {
                constant = 1;
            }
            if (max[2] - min[2] < max[constant] - min[constant]) {
                constant = 2;
            }
            this.constantAxis = constant;
            this.firstAxis = constant == 0 ? 1 : 0;
            this.secondAxis = constant == 2 ? 1 : 2;
            this.constantValue = (min[constant] + max[constant]) * 0.5F;
            this.firstMin = min[firstAxis];
            this.firstMax = max[firstAxis];
            this.secondMin = min[secondAxis];
            this.secondMax = max[secondAxis];

            for (steppedPlayerAnimations$SourceVertex vertex : vertices) {
                int firstCorner = Math.abs(coordinate(vertex.position(), firstAxis) - firstMax)
                        < Math.abs(coordinate(vertex.position(), firstAxis) - firstMin) ? 1 : 0;
                int secondCorner = Math.abs(coordinate(vertex.position(), secondAxis) - secondMax)
                        < Math.abs(coordinate(vertex.position(), secondAxis) - secondMin) ? 1 : 0;
                corners[firstCorner][secondCorner] =
                        new steppedPlayerAnimations$Uv(vertex.u(), vertex.v());
            }
            for (int first = 0; first < 2; first++) {
                for (int second = 0; second < 2; second++) {
                    if (corners[first][second] == null) {
                        throw new IllegalStateException("Incomplete EMF face UV layout");
                    }
                }
            }
        }

        private steppedPlayerAnimations$Uv uvAt(Vector3f position) {
            float first = steppedPlayerAnimations$ratio(
                    coordinate(position, firstAxis),
                    firstMin,
                    firstMax
            );
            float second = steppedPlayerAnimations$ratio(
                    coordinate(position, secondAxis),
                    secondMin,
                    secondMax
            );
            steppedPlayerAnimations$Uv low = steppedPlayerAnimations$lerp(
                    corners[0][0],
                    corners[1][0],
                    first
            );
            steppedPlayerAnimations$Uv high = steppedPlayerAnimations$lerp(
                    corners[0][1],
                    corners[1][1],
                    first
            );
            return steppedPlayerAnimations$lerp(low, high, second);
        }

        private float coordinate(Vector3f position, int axis) {
            return switch (axis) {
                case 0 -> position.x();
                case 1 -> position.y();
                default -> position.z();
            };
        }
    }

    @Unique
    private static float steppedPlayerAnimations$ratio(float value, float min, float max) {
        float span = max - min;
        return Math.abs(span) < 0.00001F ? 0.0F : (value - min) / span;
    }

    @Unique
    private static steppedPlayerAnimations$Uv steppedPlayerAnimations$lerp(
            steppedPlayerAnimations$Uv first,
            steppedPlayerAnimations$Uv second,
            float amount
    ) {
        return new steppedPlayerAnimations$Uv(
                first.u() + (second.u() - first.u()) * amount,
                first.v() + (second.v() - first.v()) * amount
        );
    }
}
