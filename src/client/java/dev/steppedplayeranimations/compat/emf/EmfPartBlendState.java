package dev.steppedplayeranimations.compat.emf;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EmfPartBlendState {
    private static final Set<UUID> MANAGED_PLAYERS = ConcurrentHashMap.newKeySet();

    private EmfPartBlendState() {
    }

    public static void setManaged(UUID playerId, boolean managed) {
        if (managed) {
            MANAGED_PLAYERS.add(playerId);
        } else {
            MANAGED_PLAYERS.remove(playerId);
        }
    }

    public static boolean isManaged(UUID playerId) {
        return MANAGED_PLAYERS.contains(playerId);
    }
}
