package io.github.AzmiiD.aTradeNpc.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/** Values from config.yml. */
public class Settings {

    public final long clickCooldown;
    public final int npcCheckInterval;
    public final int maxOffers;
    public final EntityType defaultEntityType;
    public final List<EntityType> allowedEntityTypes = new ArrayList<>();
    public final String currencyFormat;
    public final Material moneyItem;
    public final double hologramLinkHeight;

    public final boolean centerNpc;
    public final boolean lookAtPlayers;
    public final double lookRange;

    public final boolean playerShopsEnabled;
    public final int maxPlayerShops;
    public final double maxChestDistance;
    public final Set<Material> allowedContainers = EnumSet.noneOf(Material.class);
    public final boolean checkChestAccess;
    public final boolean protectChest;
    public final double taxPercent;

    public Settings(FileConfiguration c, Logger logger) {
        clickCooldown = Math.max(0, c.getLong("click-cooldown-ms", 100));
        double height = c.getDouble("holograms.link-height", 2.5);
        hologramLinkHeight = Double.isFinite(height) ? Math.max(0, height) : 2.5;
        npcCheckInterval = Math.max(1, c.getInt("npc-check-interval-seconds", 5));
        maxOffers = Math.max(1, c.getInt("max-offers", 135));
        currencyFormat = c.getString("economy.currency-format", "$%amount%");
        Material money = Material.matchMaterial(c.getString("economy.money-item", "GOLD_NUGGET"));
        moneyItem = money != null && money.isItem() && !money.isAir() ? money : Material.GOLD_NUGGET;

        centerNpc = c.getBoolean("npc.center-on-block", true);
        lookAtPlayers = c.getBoolean("npc.look-at-players", true);
        lookRange = Math.max(1, c.getDouble("npc.look-range", 8));

        for (String name : c.getStringList("allowed-entity-types")) {
            EntityType type = parseEntityType(name);
            if (type == null) {
                logger.warning("Unknown or unusable entity type in allowed-entity-types: " + name);
            } else if (!allowedEntityTypes.contains(type)) {
                allowedEntityTypes.add(type);
            }
        }
        if (allowedEntityTypes.isEmpty()) {
            allowedEntityTypes.add(EntityType.VILLAGER);
        }
        EntityType def = parseEntityType(c.getString("default-entity-type", "VILLAGER"));
        defaultEntityType = def != null ? def : allowedEntityTypes.getFirst();

        playerShopsEnabled = c.getBoolean("player-shops.enabled", true);
        maxPlayerShops = c.getInt("player-shops.max-shops", 5);
        maxChestDistance = c.getDouble("player-shops.max-chest-distance", 10);
        for (String name : c.getStringList("player-shops.allowed-containers")) {
            Material m = Material.matchMaterial(name);
            if (m != null) {
                allowedContainers.add(m);
            }
        }
        checkChestAccess = c.getBoolean("player-shops.check-chest-access", true);
        protectChest = c.getBoolean("player-shops.protect-chest", true);
        taxPercent = Math.max(0, Math.min(100, c.getDouble("player-shops.tax-percent", 0)));
    }

    /** Living, spawnable entity types only (no players). */
    public static EntityType parseEntityType(String name) {
        if (name == null) {
            return null;
        }
        try {
            EntityType type = EntityType.valueOf(name.trim().toUpperCase(Locale.ROOT));
            return type.isAlive() && type.isSpawnable() && type != EntityType.PLAYER ? type : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
