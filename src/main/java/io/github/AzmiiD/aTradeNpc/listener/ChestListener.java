package io.github.AzmiiD.aTradeNpc.listener;

import io.github.AzmiiD.aTradeNpc.ATradeNPC;
import io.github.AzmiiD.aTradeNpc.shop.TradeShop;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Protects the storage containers of player shops. */
public class ChestListener implements Listener {

    private final ATradeNPC plugin;

    public ChestListener(ATradeNPC plugin) {
        this.plugin = plugin;
    }

    private boolean enabled() {
        return plugin.getSettings().protectChest;
    }

    /**
     * Not filtered by allowed-containers: removing a type from the config must not leave
     * the storage of existing shops unprotected.
     */
    private TradeShop shopOf(Block block) {
        return plugin.getShopManager().getByContainer(block);
    }

    private static boolean bypass(Player player, TradeShop shop) {
        return shop.isOwner(player) || player.hasPermission("atradenpc.admin");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onOpen(PlayerInteractEvent event) {
        if (!enabled() || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        TradeShop shop = shopOf(event.getClickedBlock());
        if (shop != null && !bypass(event.getPlayer(), shop)) {
            event.setCancelled(true);
            plugin.getLang().send(event.getPlayer(), "chestProtected", "%owner%", shop.getOwnerName());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        TradeShop shop = shopOf(block);
        if (shop == null) {
            return;
        }
        Player player = event.getPlayer();
        // The owner may break the added half of a double chest; the linked block itself always stays.
        if (!shop.isChestAt(block) && bypass(player, shop)) {
            return;
        }
        // Even the owner can't break it: the shop would lose its stock.
        event.setCancelled(true);
        if (bypass(player, shop)) {
            plugin.getLang().send(player, "chestBreakOwner", "%id%", String.valueOf(shop.getId()));
        } else {
            plugin.getLang().send(player, "chestProtected", "%owner%", shop.getOwnerName());
        }
    }

    /** Other players can't attach a chest to a shop chest (it would become part of the storage). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!(event.getBlockPlaced().getBlockData() instanceof Chest chest) || chest.getType() == Chest.Type.SINGLE) {
            return;
        }
        TradeShop shop = shopOf(event.getBlockPlaced().getRelative(connectedFace(chest)));
        if (shop != null && !bypass(event.getPlayer(), shop)) {
            event.setCancelled(true);
            plugin.getLang().send(event.getPlayer(), "chestProtected", "%owner%", shop.getOwnerName());
        }
    }

    /** Direction of the other half of a double chest (same rule as vanilla ChestBlock#getConnectedDirection). */
    private static BlockFace connectedFace(Chest chest) {
        BlockFace clockwise = switch (chest.getFacing()) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            default -> BlockFace.NORTH;
        };
        return chest.getType() == Chest.Type.LEFT ? clockwise : clockwise.getOppositeFace();
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> shopOf(b) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> shopOf(b) != null);
    }

    /** Withers, endermen, ravagers... */
    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (shopOf(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    /** Hoppers/droppers can't pull items out of a shop's storage. */
    @EventHandler(ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent event) {
        if (enabled() && isShopInventory(event.getSource())) {
            event.setCancelled(true);
        }
    }

    private boolean isShopInventory(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder(false);
        if (holder instanceof DoubleChest dc) {
            return isShopHolder(dc.getLeftSide(false)) || isShopHolder(dc.getRightSide(false));
        }
        return isShopHolder(holder);
    }

    private boolean isShopHolder(InventoryHolder holder) {
        return holder instanceof BlockState state && shopOf(state.getBlock()) != null;
    }
}
