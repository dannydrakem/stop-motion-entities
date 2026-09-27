package dev.steppedplayeranimations.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.steppedplayeranimations.SteppedPlayerAnimationsClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class SteppedAnimationConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("stepped_player_animations.json");

    private static boolean enabled = true;
    private static FrameRate frameRate = FrameRate.FPS_12;
    private static Set<String> disabledEntityTypes = new HashSet<>();
    private static long revision;

    private SteppedAnimationConfig() {
    }

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }

        try {
            ConfigData loaded = GSON.fromJson(Files.readString(CONFIG_PATH, StandardCharsets.UTF_8), ConfigData.class);
            if (loaded != null) {
                enabled = loaded.enabled;
                frameRate = loaded.frameRate == null ? FrameRate.FPS_12 : loaded.frameRate;
                disabledEntityTypes = loaded.disabledEntityTypes == null
                        ? new HashSet<>()
                        : new HashSet<>(loaded.disabledEntityTypes);
                disabledEntityTypes.removeIf(entityId -> entityId == null || entityId.isBlank());
                normalizeState();
            }
            revision++;
        } catch (IOException | RuntimeException exception) {
            SteppedPlayerAnimationsClient.LOGGER.error(
                    "Could not load {}; using default animation settings.",
                    CONFIG_PATH,
                    exception
            );
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean isSteppingActive() {
        return enabled && frameRate != FrameRate.UNLIMITED;
    }

    public static FrameRate frameRate() {
        return frameRate;
    }

    public static long sampleIntervalNanos() {
        return frameRate.intervalNanos();
    }

    public static long revision() {
        return revision;
    }

    public static boolean isEntityEnabled(Entity entity) {
        return entity != null && isEntityTypeEnabled(entity.getType());
    }

    public static boolean isEntityTypeEnabled(EntityType<?> entityType) {
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        return entityId == null || !disabledEntityTypes.contains(entityId.toString());
    }

    public static void setEntityTypeEnabled(EntityType<?> entityType, boolean entityEnabled) {
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        if (entityId == null) {
            return;
        }

        boolean changed = entityEnabled
                ? disabledEntityTypes.remove(entityId.toString())
                : disabledEntityTypes.add(entityId.toString());
        if (changed) {
            revision++;
            save();
        }
    }

    public static void setEntityTypesEnabled(Collection<EntityType<?>> entityTypes, boolean entityEnabled) {
        boolean changed = false;
        for (EntityType<?> entityType : entityTypes) {
            ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
            if (entityId == null) {
                continue;
            }
            changed |= entityEnabled
                    ? disabledEntityTypes.remove(entityId.toString())
                    : disabledEntityTypes.add(entityId.toString());
        }
        if (changed) {
            revision++;
            save();
        }
    }

    public static void toggleEnabled() {
        setEnabled(!enabled);
    }

    public static void setEnabled(boolean newEnabled) {
        FrameRate newFrameRate = newEnabled ? FrameRate.FPS_12 : FrameRate.UNLIMITED;
        if (enabled == newEnabled && frameRate == newFrameRate) {
            return;
        }
        enabled = newEnabled;
        frameRate = newFrameRate;
        revision++;
        save();
    }

    public static void cycleFrameRateForward() {
        setFrameRate(frameRate.next());
    }

    public static void cycleFrameRateBackward() {
        setFrameRate(frameRate.previous());
    }

    public static void setFrameRate(FrameRate newFrameRate) {
        if (newFrameRate == null) {
            return;
        }

        boolean newEnabled = newFrameRate != FrameRate.UNLIMITED;
        if (frameRate == newFrameRate && enabled == newEnabled) {
            return;
        }

        frameRate = newFrameRate;
        enabled = newEnabled;
        revision++;
        save();
    }

    private static void normalizeState() {
        if (!enabled || frameRate == FrameRate.UNLIMITED) {
            enabled = false;
            frameRate = FrameRate.UNLIMITED;
        }
    }

    public static void save() {
        ConfigData data = new ConfigData();
        data.enabled = enabled;
        data.frameRate = frameRate;
        data.disabledEntityTypes = new HashSet<>(disabledEntityTypes);
        Path temporaryPath = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(temporaryPath, GSON.toJson(data), StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporaryPath,
                        CONFIG_PATH,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporaryPath, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            SteppedPlayerAnimationsClient.LOGGER.error("Could not save {}.", CONFIG_PATH, exception);
        }
    }

    public enum FrameRate {
        FPS_8(8),
        FPS_12(12),
        FPS_24(24),
        FPS_25(25),
        UNLIMITED(0);

        private static final FrameRate[] VALUES = values();
        private final int framesPerSecond;

        FrameRate(int framesPerSecond) {
            this.framesPerSecond = framesPerSecond;
        }

        public int framesPerSecond() {
            return framesPerSecond;
        }

        public long intervalNanos() {
            return framesPerSecond == 0 ? 0L : 1_000_000_000L / framesPerSecond;
        }

        public FrameRate next() {
            return VALUES[(ordinal() + 1) % VALUES.length];
        }

        public FrameRate previous() {
            return VALUES[(ordinal() - 1 + VALUES.length) % VALUES.length];
        }
    }

    private static final class ConfigData {
        private boolean enabled = true;
        private FrameRate frameRate = FrameRate.FPS_12;
        private Set<String> disabledEntityTypes = new HashSet<>();
    }
}
