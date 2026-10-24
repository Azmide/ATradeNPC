package io.github.AzmiiD.aTradeNpc.shop;

public enum ShopType {
    /** Infinite stock, money comes from/goes to nowhere. */
    ADMIN,
    /** Owned by a player: stock is a linked container, money goes to/comes from the owner. */
    PLAYER
}
