package io.github.AzmiiD.aTradeNpc.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TradeMenuModeTest {
    @Test
    void autoUsesChestForBedrockAndMerchantForJava() {
        assertTrue(TradeMenuMode.AUTO.useChest(true));
        assertFalse(TradeMenuMode.AUTO.useChest(false));
    }

    @Test
    void forcedChestWorksWithoutBackendBedrockDetection() {
        assertTrue(TradeMenuMode.CHEST.useChest(false));
        assertTrue(TradeMenuMode.CHEST.useChest(true));
    }

    @Test
    void forcedMerchantRemainsAnExplicitOverride() {
        assertFalse(TradeMenuMode.MERCHANT.useChest(false));
        assertFalse(TradeMenuMode.MERCHANT.useChest(true));
    }

    @Test
    void configValuesAreCaseInsensitiveAndInvalidValuesCanBeReported() {
        assertEquals(TradeMenuMode.AUTO, TradeMenuMode.parse("auto"));
        assertEquals(TradeMenuMode.CHEST, TradeMenuMode.parse(" CHEST "));
        assertThrows(IllegalArgumentException.class, () -> TradeMenuMode.parse("invalid"));
    }
}
