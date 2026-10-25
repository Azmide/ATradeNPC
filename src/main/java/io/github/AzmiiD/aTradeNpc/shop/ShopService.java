package io.github.AzmiiD.aTradeNpc.shop;

import io.github.AzmiiD.aTradeNpc.ATradeNPC;
import io.github.AzmiiD.aTradeNpc.config.Lang;
import io.github.AzmiiD.aTradeNpc.config.Settings;
import io.github.AzmiiD.aTradeNpc.gui.EditorMenu;
import io.github.AzmiiD.aTradeNpc.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.permissions.PermissionAttachmentInfo;

/** High level shop actions shared by commands, menus and listeners. */
public class ShopService {

    private static final String LIMIT_PREFIX = "atradenpc.limit.";
    /** Max visible characters / max raw length (with tags) of a shop name typed by a non-admin. */
    private static final int MAX_NAME_LENGTH = 48, MAX_NAME_RAW = 256;

    private final ATradeNPC plugin;

    public ShopService(ATradeNPC plugin) {
        this.plugin = plugin;
    }

    private Lang lang() {
        return plugin.getLang();
    }

    public TradeShop createAdmin(Player player, EntityType type, String name) {
        ShopManager manager = plugin.getShopManager();
        TradeShop shop = manager.create(ShopType.ADMIN);
        shop.setName(name == null ? "<gold>Shop" : name);
        shop.setEntityType(type);
        shop.setLocation(place(player.getLocation()));
        manager.add(shop);
        plugin.getNpcManager().spawn(shop);
        return shop;
    }

    /** Creates a player shop using the container the player looks at. Sends the error message on failure. */
    public TradeShop createPlayer(Player player, EntityType type, String name) {
        Settings settings = plugin.getSettings();
        if (!settings.playerShopsEnabled) {
            lang().send(player, "playerShopsDisabled");
            return null;
        }
        Block block = player.getTargetBlockExact(5);
        if (block == null || !settings.allowedContainers.contains(block.getType())
                || !(block.getState(false) instanceof Container)) {
            lang().send(player, "notLookingAtContainer");
            return null;
        }
        TradeShop used = plugin.getShopManager().getByContainer(block);
        if (used != null) {
            lang().send(player, "containerAlreadyUsed", "%id%", String.valueOf(used.getId()));
            return null;
        }
        if (!player.hasPermission("atradenpc.admin") && !canAccess(player, block)) {
            lang().send(player, "containerNoAccess");
            return null;
        }
        int limit = getLimit(player);
        if (limit >= 0 && plugin.getShopManager().getOwnedBy(player.getUniqueId()).size() >= limit) {
            lang().send(player, "shopLimitReached", "%limit%", String.valueOf(limit));
            return null;
        }
        Location loc = player.getLocation();
        if (!nearChest(block, loc)) {
            lang().send(player, "chestTooFar", "%distance%", String.valueOf((int) settings.maxChestDistance));
            return null;
        }
        ShopManager manager = plugin.getShopManager();
        TradeShop shop = manager.create(ShopType.PLAYER);
        shop.setOwner(player.getUniqueId(), player.getName());
        shop.setName(name == null ? "<yellow>" + player.getName() + "'s Shop" : name);
        shop.setEntityType(type);
        shop.setLocation(place(loc));
        shop.setChest(block);
        manager.add(shop);
        plugin.getNpcManager().spawn(shop);
        return shop;
    }

    /**
     * Lets protection/claim plugins decide whether the player may use the container,
     * by firing a fake right-click on it.
     */
    private boolean canAccess(Player player, Block block) {
        if (!plugin.getSettings().checkChestAccess) {
            return true;
        }
        PlayerInteractEvent test = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                player.getInventory().getItemInMainHand(), block, BlockFace.UP, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(test);
        return test.useInteractedBlock() != Event.Result.DENY;
    }

    /** -1 = unlimited. The highest atradenpc.limit.N permission wins over the config default. */
    public int getLimit(Player player) {
        if (player.hasPermission("atradenpc.limit.bypass") || player.hasPermission("atradenpc.admin")) {
            return -1;
        }
        int limit = plugin.getSettings().maxPlayerShops;
        int best = -2;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            String perm = info.getPermission();
            if (info.getValue() && perm.startsWith(LIMIT_PREFIX)) {
                try {
                    best = Math.max(best, Integer.parseInt(perm.substring(LIMIT_PREFIX.length())));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return best >= 0 ? best : limit;
    }

    private boolean nearChest(Block chest, Location loc) {
        double max = plugin.getSettings().maxChestDistance;
        return chest.getWorld().equals(loc.getWorld())
                && chest.getLocation().add(0.5, 0.5, 0.5).distanceSquared(loc) <= max * max;
    }

    /** Moves the NPC. Player shops must stay near their chest. Sends the error message on failure. */
    public boolean move(Player player, TradeShop shop, Location loc) {
        Block chest = shop.getChestBlock();
        if (shop.getType() == ShopType.PLAYER && chest != null && !nearChest(chest, loc)) {
            lang().send(player, "chestTooFar", "%distance%", String.valueOf((int) plugin.getSettings().maxChestDistance));
            return false;
        }
        ShopManager manager = plugin.getShopManager();
        manager.unindex(shop);
        shop.setLocation(place(loc));
        manager.reindex(shop);
        manager.save();
        plugin.getNpcManager().respawn(shop);
        return true;
    }

    /** Where an NPC is put: the middle of the block when center-on-block is on, looking straight ahead. */
    public Location place(Location loc) {
        Location out = loc.clone();
        if (plugin.getSettings().centerNpc) {
            out.setX(loc.getBlockX() + 0.5);
            out.setZ(loc.getBlockZ() + 0.5);
        }
        out.setPitch(0);
        return out;
    }

    /** Puts the NPC in the middle of its block and turns it to the nearest 45 degrees. */
    public void center(TradeShop shop) {
        Location loc = shop.getLocation();
        if (loc == null) {
            return;
        }
        ShopManager manager = plugin.getShopManager();
        manager.unindex(shop);
        loc.setX(loc.getBlockX() + 0.5);
        loc.setZ(loc.getBlockZ() + 0.5);
        loc.setYaw(Math.round(loc.getYaw() / 45f) * 45f);
        loc.setPitch(0);
        shop.setLocation(loc);
        manager.reindex(shop);
        manager.save();
        plugin.getNpcManager().respawn(shop);
    }

    /**
     * Shop names typed by non-admins may only use colors/styles: click/hover tags would run when an admin
     * clicks the name in /atrade list. Returns null (and sends the error) when the name is too long.
     */
    public String cleanName(CommandSender sender, String name) {
        if (name == null || sender.hasPermission("atradenpc.admin")) {
            return name;
        }
        String clean = Text.sanitize(name);
        if (clean.length() > MAX_NAME_RAW || Text.plain(Text.parse(clean)).length() > MAX_NAME_LENGTH) {
            lang().send(sender, "nameTooLong", "%max%", String.valueOf(MAX_NAME_LENGTH));
            return null;
        }
        return clean;
    }

    public void rename(TradeShop shop, String name) {
        shop.setName(name);
        plugin.getNpcManager().refresh(shop);
        plugin.getShopManager().save();
    }

    public void setEntityType(TradeShop shop, EntityType type) {
        shop.setEntityType(type);
        plugin.getShopManager().save();
        plugin.getNpcManager().respawn(shop);
    }

    public void delete(TradeShop shop) {
        plugin.getNpcManager().despawn(shop);
        // Open menus of this shop notice the removed flag on their next click and close.
        plugin.getShopManager().remove(shop);
    }

    /** Right-click on the NPC: editor for the owner/admin when sneaking, otherwise the trade GUI. */
    public void interact(Player player, TradeShop shop) {
        if (player.isSneaking() && shop.canEdit(player)) {
            new EditorMenu(plugin, player, shop).open();
            return;
        }
        if (!player.hasPermission("atradenpc.use")) {
            lang().send(player, "noPermission");
            return;
        }
        if (shop.isOwner(player)) {
            new EditorMenu(plugin, player, shop).open();
            return;
        }
        plugin.getTradeScreen().open(player, shop);
    }
}
