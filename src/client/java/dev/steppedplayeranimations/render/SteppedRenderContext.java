package dev.steppedplayeranimations.render;

public final class SteppedRenderContext {
    private static final ThreadLocal<Integer> INVENTORY_DEPTH = ThreadLocal.withInitial(() -> 0);

    private SteppedRenderContext() {
    }

    public static void enterInventory() {
        INVENTORY_DEPTH.set(INVENTORY_DEPTH.get() + 1);
    }

    public static void exitInventory() {
        int depth = INVENTORY_DEPTH.get() - 1;
        if (depth <= 0) {
            INVENTORY_DEPTH.remove();
        } else {
            INVENTORY_DEPTH.set(depth);
        }
    }

    public static boolean isInventory() {
        return INVENTORY_DEPTH.get() > 0;
    }
}
