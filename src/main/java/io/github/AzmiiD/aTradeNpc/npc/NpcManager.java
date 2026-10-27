package io.github.AzmiiD.aTradeNpc.npc;

import io.github.AzmiiD.aTradeNpc.ATradeNPC;
import io.github.AzmiiD.aTradeNpc.shop.TradeShop;
import io.github.AzmiiD.aTradeNpc.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import io.papermc.paper.entity.LookAnchor;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Spawns shop NPCs. NPC entities are not persistent: they are never saved with the chunk,
 * and are spawned again whenever the chunk of their shop is loaded.
 */
public class NpcManager {

    private final ATradeNPC plugin;
    private BukkitTask checkTask;
    private BukkitTask lookTask;
    /** True while we spawn an NPC, so the spawn listener can un-cancel it (mob-spawn protection plugins). */
    private boolean spawning;

    public NpcManager(ATradeNPC plugin) {
        this.plugin = plugin;
    }

    public boolean isSpawning() {
        return spawning;
    }

    public void start(int intervalSeconds) {
        stop();
        long ticks = intervalSeconds * 20L;
        checkTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkAll, ticks, ticks);
        if (plugin.getSettings().lookAtPlayers) {
            lookTask = Bukkit.getScheduler().runTaskTimer(plugin, this::lookAtPlayers, 4L, 4L);
        }
    }

    public void stop() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
        if (lookTask != null) {
            lookTask.cancel();
            lookTask = null;
        }
    }

    public void spawnAllLoaded() {
        for (TradeShop shop : plugin.getShopManager().getShops()) {
            if (isChunkLoaded(shop)) {
                spawn(shop);
            }
        }
    }

    public void despawnAll() {
        for (TradeShop shop : plugin.getShopManager().getShops()) {
            despawn(shop);
        }
    }

    /** Respawns dead NPCs and puts moved NPCs back in place. */
    private void checkAll() {
        for (TradeShop shop : plugin.getShopManager().getShops()) {
            if (!isChunkLoaded(shop)) {
                continue;
            }
            Entity entity = shop.getEntity();
            if (entity == null || !entity.isValid()) {
                spawn(shop);
                continue;
            }
            Location loc = shop.getLocation();
            if (loc != null && (entity.getWorld() != loc.getWorld() || entity.getLocation().distanceSquared(loc) > 0.25)) {
                entity.teleportAsync(loc);
            }
        }
    }

    /** Turns every NPC towards the nearest player, or back to its own direction when nobody is close. */
    private void lookAtPlayers() {
        double range = plugin.getSettings().lookRange;
        for (TradeShop shop : plugin.getShopManager().getShops()) {
            Entity entity = shop.getEntity();
            if (entity == null || !entity.isValid()) {
                continue;
            }
            Location loc = shop.getLocation();
            if (loc == null) {
                continue;
            }
            Player nearest = null;
            double best = range * range;
            for (Player player : entity.getWorld().getNearbyPlayers(entity.getLocation(), range)) {
                if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR || !player.canSee(entity)) {
                    continue;
                }
                double d = player.getLocation().distanceSquared(entity.getLocation());
                if (d <= best) {
                    best = d;
                    nearest = player;
                }
            }
            if (nearest != null) {
                Location eye = nearest.getEyeLocation();
                entity.lookAt(eye.getX(), eye.getY(), eye.getZ(), LookAnchor.EYES);
                if (entity instanceof LivingEntity living) {
                    living.setBodyYaw(entity.getYaw());
                }
            } else if (Math.abs(entity.getYaw() - loc.getYaw()) > 0.5f || Math.abs(entity.getPitch() - loc.getPitch()) > 0.5f) {
                entity.setRotation(loc.getYaw(), loc.getPitch());
                if (entity instanceof LivingEntity living) {
                    living.setBodyYaw(loc.getYaw());
                }
            }
        }
    }

    private static boolean isChunkLoaded(TradeShop shop) {
        Location loc = shop.getLocation();
        return loc != null && loc.getWorld().isChunkLoaded(shop.getChunkX(), shop.getChunkZ());
    }

    public void spawn(TradeShop shop) {
        if (shop.isRemoved() || (shop.getEntity() != null && shop.getEntity().isValid())) {
            return;
        }
        Location loc = shop.getLocation();
        if (loc == null) {
            return;
        }
        World world = loc.getWorld();
        Class<? extends Entity> cls = shop.getEntityType().getEntityClass();
        if (cls == null) {
            return;
        }
        spawning = true;
        try {
            Entity entity = world.spawn(loc, cls, CreatureSpawnEvent.SpawnReason.CUSTOM, e -> setup(shop, e));
            shop.setEntity(entity.isValid() ? entity : null);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not spawn NPC of shop #" + shop.getId() + ": " + e.getMessage());
        } finally {
            spawning = false;
        }
    }

    public void despawn(TradeShop shop) {
        Entity entity = shop.getEntity();
        if (entity != null) {
            entity.remove();
            shop.setEntity(null);
        }
    }

    /** Respawns the NPC, e.g. after the entity type changed. */
    public void respawn(TradeShop shop) {
        despawn(shop);
        if (isChunkLoaded(shop)) {
            spawn(shop);
        }
    }

    /** Updates name/profession of the living NPC without respawning it. */
    public void refresh(TradeShop shop) {
        Entity entity = shop.getEntity();
        if (entity != null && entity.isValid()) {
            applyLook(shop, entity);
        }
    }

    private void setup(TradeShop shop, Entity entity) {
        entity.getPersistentDataContainer().set(plugin.getShopKey(), PersistentDataType.INTEGER, shop.getId());
        entity.setPersistent(false);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        if (entity instanceof LivingEntity living) {
            living.setAI(false);
            living.setRemoveWhenFarAway(false);
            living.setCollidable(false);
            living.setCanPickupItems(false);
        }
        if (entity instanceof Mob mob) {
            mob.getEquipment().clear();
        }
        if (entity instanceof Ageable ageable) {
            ageable.setAdult();
        }
        applyLook(shop, entity);
    }

    private void applyLook(TradeShop shop, Entity entity) {
        entity.customName(Text.parse(shop.getName()));
        entity.setCustomNameVisible(!plugin.getSettings().hideHologramNpcName
                || !plugin.getHolograms().hasHologram(shop));
        Villager.Profession profession = profession(shop.getProfession());
        if (entity instanceof Villager villager) {
            villager.setProfession(profession);
            villager.setVillagerLevel(5);
        } else if (entity instanceof ZombieVillager zv) {
            zv.setVillagerProfession(profession);
        }
    }

    public static Villager.Profession profession(String key) {
        if (key != null) {
            NamespacedKey nk = NamespacedKey.fromString(key.toLowerCase(java.util.Locale.ROOT));
            Villager.Profession p = nk == null ? null : Registry.VILLAGER_PROFESSION.get(nk);
            if (p != null) {
                return p;
            }
        }
        return Villager.Profession.NONE;
    }
}
