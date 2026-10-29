package io.github.AzmiiD.aTradeNpc.compat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BedrockPlayersTest {
    private static final UUID BEDROCK = UUID.randomUUID();
    private static final UUID JAVA = UUID.randomUUID();
    private final ClassLoader loader = getClass().getClassLoader();

    @BeforeEach
    void reset() {
        ApiFixture.ready = true;
        ApiFixture.fail = false;
    }

    @Test
    void floodgateUsesUuidLookupInsteadOfNamePrefixes() {
        assertTrue(query("isFloodgatePlayer", BEDROCK, false));
        assertFalse(query("isFloodgatePlayer", JAVA, false));
    }

    @Test
    void geyserUsesConnectionPresence() {
        assertTrue(query("connectionByUuid", BEDROCK, true));
        assertFalse(query("connectionByUuid", JAVA, true));
    }

    @Test
    void apiNotReadyCanBecomeReadyOnLaterOpen() {
        ApiFixture.ready = false;
        assertFalse(query("connectionByUuid", BEDROCK, true));
        ApiFixture.ready = true;
        assertTrue(query("connectionByUuid", BEDROCK, true));
    }

    @Test
    void missingOrIncompatibleApisDoNotBreakOpeningShops() {
        assertFalse(BedrockPlayers.query(loader, "no.such.ProviderApi", "api", "lookup", BEDROCK, true));
        assertFalse(query("missingMethod", BEDROCK, true));
        ApiFixture.fail = true;
        assertFalse(query("isFloodgatePlayer", BEDROCK, false));
    }

    private boolean query(String method, UUID uuid, boolean connection) {
        return BedrockPlayers.query(loader, ApiFixture.class.getName(), "getInstance", method, uuid, connection);
    }

    public static class ApiFixture {
        static boolean ready, fail;

        public static ApiFixture getInstance() {
            return ready ? new ApiFixture() : null;
        }

        public boolean isFloodgatePlayer(UUID uuid) {
            if (fail) throw new IllegalStateException("Provider unavailable");
            return BEDROCK.equals(uuid);
        }

        public Object connectionByUuid(UUID uuid) {
            return isFloodgatePlayer(uuid) ? new Object() : null;
        }
    }
}
