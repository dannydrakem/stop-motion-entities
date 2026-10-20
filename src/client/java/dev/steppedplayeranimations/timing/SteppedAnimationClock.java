package dev.steppedplayeranimations.timing;

import dev.steppedplayeranimations.SteppedPlayerAnimationsClient;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class SteppedAnimationClock {
    private static final ThreadLocal<RenderFrame> RENDER_FRAME =
            ThreadLocal.withInitial(RenderFrame::new);
    private static boolean initialized;
    private static Method flashbackVisualMillis;
    private static boolean flashbackClockFailed;

    private SteppedAnimationClock() {
    }

    public static long nowNanos() {
        RenderFrame frame = RENDER_FRAME.get();
        if (frame.depth > 0) {
            return frame.nowNanos;
        }
        return readNowNanos();
    }

    public static void beginRenderFrame() {
        RenderFrame frame = RENDER_FRAME.get();
        /*
         * Flashback may render several output frames from inside Minecraft's
         * normal render loop.  Refresh on every GameRenderer entry so each of
         * those frames gets its own sample.  This also makes the clock recover
         * automatically if another mod aborts a render before our RETURN hook.
         */
        frame.depth++;
        frame.nowNanos = readNowNanos();
    }

    public static void endRenderFrame() {
        RenderFrame frame = RENDER_FRAME.get();
        if (frame.depth > 0) {
            frame.depth--;
        }
    }

    private static long readNowNanos() {
        initialize();
        if (flashbackVisualMillis != null && !flashbackClockFailed) {
            try {
                long visualMillis = (long) flashbackVisualMillis.invoke(null);
                return visualMillis * 1_000_000L;
            } catch (IllegalAccessException | InvocationTargetException | ClassCastException exception) {
                flashbackClockFailed = true;
                SteppedPlayerAnimationsClient.LOGGER.error(
                        "Could not read the Flashback visual timeline; falling back to the system animation clock.",
                        exception
                );
            }
        }
        return System.nanoTime();
    }

    private static final class RenderFrame {
        private int depth;
        private long nowNanos;
    }

    private static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        if (!FabricLoader.getInstance().isModLoaded("flashback")) {
            return;
        }

        try {
            Class<?> flashback = Class.forName("com.moulberry.flashback.Flashback");
            flashbackVisualMillis = flashback.getMethod("getVisualMillis");
            SteppedPlayerAnimationsClient.LOGGER.info(
                    "Flashback visual timeline detected; stepped-animation samples will follow replay and export time."
            );
        } catch (ReflectiveOperationException exception) {
            flashbackClockFailed = true;
            SteppedPlayerAnimationsClient.LOGGER.warn(
                    "Flashback is installed but its visual timeline API is unavailable; using the system animation clock.",
                    exception
            );
        }
    }

    public static final class Gate {
        private long lastObservedNanos = Long.MIN_VALUE;
        private long sampleBucket = Long.MIN_VALUE;
        private long lastIntervalNanos = Long.MIN_VALUE;
        private long lastConfigRevision = Long.MIN_VALUE;

        public boolean shouldCapture(long nowNanos, long intervalNanos, long configRevision) {
            if (intervalNanos <= 0L) {
                throw new IllegalArgumentException("The sample interval must be positive.");
            }
            long newBucket = Math.floorDiv(nowNanos, intervalNanos);
            boolean firstSample = lastObservedNanos == Long.MIN_VALUE;
            boolean timelineMovedBackward = !firstSample && nowNanos < lastObservedNanos;
            boolean enteredNewBucket = newBucket != sampleBucket;
            boolean settingsChanged = intervalNanos != lastIntervalNanos || configRevision != lastConfigRevision;
            lastObservedNanos = nowNanos;
            lastIntervalNanos = intervalNanos;
            lastConfigRevision = configRevision;
            if (firstSample || timelineMovedBackward || enteredNewBucket || settingsChanged) {
                sampleBucket = newBucket;
                return true;
            }
            return false;
        }
    }
}
