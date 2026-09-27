package dev.steppedplayeranimations.timing;

import dev.steppedplayeranimations.SteppedPlayerAnimationsClient;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class SteppedAnimationClock {
    public static final long SAMPLE_INTERVAL_NANOS = 1_000_000_000L / 12L;

    private static boolean initialized;
    private static Method flashbackVisualMillis;
    private static boolean flashbackClockFailed;

    private SteppedAnimationClock() {
    }

    public static long nowNanos() {
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
                    "Flashback visual timeline detected; 12 FPS samples will follow replay and export time."
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

        public boolean shouldCapture(long nowNanos) {
            long newBucket = Math.floorDiv(nowNanos, SAMPLE_INTERVAL_NANOS);
            boolean firstSample = lastObservedNanos == Long.MIN_VALUE;
            boolean timelineMovedBackward = !firstSample && nowNanos < lastObservedNanos;
            boolean enteredNewBucket = newBucket != sampleBucket;
            lastObservedNanos = nowNanos;
            if (firstSample || timelineMovedBackward || enteredNewBucket) {
                sampleBucket = newBucket;
                return true;
            }
            return false;
        }
    }
}
