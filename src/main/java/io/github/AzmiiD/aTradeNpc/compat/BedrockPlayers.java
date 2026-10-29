package io.github.AzmiiD.aTradeNpc.compat;

import java.util.UUID;

/** Optional API calls. No name-prefix guesses and no bundled Geyser/Floodgate classes. */
public final class BedrockPlayers {
    private BedrockPlayers() {
    }

    public static boolean isBedrockPlayer(UUID uuid) {
        ClassLoader loader = BedrockPlayers.class.getClassLoader();
        return query(loader, "org.geysermc.floodgate.api.FloodgateApi", "getInstance", "isFloodgatePlayer", uuid, false)
                || query(loader, "org.geysermc.geyser.api.GeyserApi", "api", "connectionByUuid", uuid, true);
    }

    static boolean query(ClassLoader loader, String apiClass, String getter, String lookup, UUID uuid, boolean connection) {
        try {
            Class<?> api = Class.forName(apiClass, false, loader);
            Object instance = api.getMethod(getter).invoke(null);
            if (instance == null) {
                return false; // Geyser API is null before the provider enables.
            }
            Object result = api.getMethod(lookup, UUID.class).invoke(instance, uuid);
            return connection ? result != null : Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
            // Provider absent, disabled, or incompatible. CHEST mode remains available for proxy-only setups.
            return false;
        }
    }
}
