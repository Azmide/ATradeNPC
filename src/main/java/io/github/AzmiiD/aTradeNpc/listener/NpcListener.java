package io.github.AzmiiD.aTradeNpc.listener;

import io.github.AzmiiD.aTradeNpc.ATradeNPC;
import io.github.AzmiiD.aTradeNpc.shop.TradeShop;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

/** Keeps shop NPCs alive, in place and clickable. */
public class NpcListener implements Listener {

    private final ATradeNPC plugin;

    public NpcListener(ATradeNPC plugin) {
        this.plugin = plugin;
    }

    private boolean isNpc(Entity entity) {
        return entity.getPersistentDataContainer().has(plugin.getShopKey(), PersistentDataType.INTEGER);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        TradeShop shop = plugin.getShopManager().getByEntity(event.getRightClicked());
        if (shop == null) {
            if (isNpc(event.getRightClicked())) {
                event.setCancelled(true); // leftover of a deleted shop
                event.getRightClicked().remove();
            }
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        plugin.getShopService().interact(player, shop);
    }

    /** Armor stands and some entities fire this one instead/as well. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        if (isNpc(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        if (isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCareerChange(VillagerCareerChangeEvent event) {
        if (isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        if (isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /** Mob-spawn protection (WorldGuard, claim plugins...) must not block our NPCs. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(EntitySpawnEvent event) {
        if (plugin.getNpcManager().isSpawning() && isNpc(event.getEntity())) {
            event.setCancelled(false);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        var shops = plugin.getShopManager().getShopsInChunk(event.getChunk());
        if (shops.isEmpty()) {
            return;
        }
        // Next tick: spawning entities while the chunk is still loading is not safe.
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (TradeShop shop : shops) {
                if (event.getChunk().isLoaded()) {
                    plugin.getNpcManager().spawn(shop);
                }
            }
        });
    }

    /** NPCs are not persistent, but remove any that were saved anyway (e.g. after a crash). */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (isNpc(entity)) {
                TradeShop shop = plugin.getShopManager().getByEntity(entity);
                if (shop == null || shop.getEntity() != entity) {
                    entity.remove();
                }
            }
        }
    }
}
