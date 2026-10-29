package io.github.AzmiiD.aTradeNpc.gui;

import java.util.Locale;

/** AUTO preserves Java's merchant screen and routes detected Bedrock players to a chest menu. */
public enum TradeMenuMode {
    AUTO, CHEST, MERCHANT;

    public boolean useChest(boolean bedrock) {
        return this == CHEST || (this == AUTO && bedrock);
    }

    public static TradeMenuMode parse(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
