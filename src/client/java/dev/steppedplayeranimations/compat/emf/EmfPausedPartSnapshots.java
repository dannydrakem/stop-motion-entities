package dev.steppedplayeranimations.compat.emf;

import dev.steppedplayeranimations.SteppedPlayerAnimationsClient;
import net.minecraft.client.model.geom.ModelPart;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public final class EmfPausedPartSnapshots {
    private static final ThreadLocal<Deque<List<Snapshot>>> SNAPSHOT_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static boolean loggedRestore;

    private EmfPausedPartSnapshots() {
    }

    public static void capture(ModelPart[] pausedParts) {
        if (pausedParts == null || pausedParts.length == 0) {
            SNAPSHOT_STACK.get().push(List.of());
            return;
        }

        Set<ModelPart> uniqueParts = Collections.newSetFromMap(new IdentityHashMap<>());
        Collections.addAll(uniqueParts, pausedParts);
        List<Snapshot> snapshots = new ArrayList<>(uniqueParts.size());
        for (ModelPart part : uniqueParts) {
            snapshots.add(new Snapshot(part));
        }
        SNAPSHOT_STACK.get().push(snapshots);
    }

    public static void restore() {
        Deque<List<Snapshot>> stack = SNAPSHOT_STACK.get();
        if (stack.isEmpty()) {
            return;
        }

        List<Snapshot> snapshots = stack.pop();
        for (Snapshot snapshot : snapshots) {
            snapshot.restore();
        }
        if (!loggedRestore && !snapshots.isEmpty()) {
            loggedRestore = true;
            SteppedPlayerAnimationsClient.LOGGER.debug(
                    "Restoring {} PlayerAnimator-controlled EMF parts after FA animation evaluation.",
                    snapshots.size()
            );
        }
        if (stack.isEmpty()) {
            SNAPSHOT_STACK.remove();
        }
    }

    private static final class Snapshot {
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

        private Snapshot(ModelPart part) {
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
