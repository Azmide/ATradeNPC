package io.github.AzmiiD.aTradeNpc.gui;

import io.github.AzmiiD.aTradeNpc.ATradeNPC;
import io.github.AzmiiD.aTradeNpc.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for every ATradeNPC GUI. All clicks are cancelled by {@link MenuListener};
 * menus never hold real items, only copies, so nothing can be duplicated or lost.
 */
public abstract class Menu implements InventoryHolder {

    protected final ATradeNPC plugin;
    protected final Player player;
    protected Inventory inventory;

    protected Menu(ATradeNPC plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    protected abstract String title();

    protected abstract int size();

    /** Draws the contents into {@link #inventory}. */
    protected abstract void render();

    /** Click inside the top inventory (already cancelled). */
    public abstract void onClick(InventoryClickEvent event);

    /** Click inside the player's own inventory while this menu is open (already cancelled). */
    public void onBottomClick(InventoryClickEvent event) {
    }

    public void onClose(InventoryCloseEvent event) {
    }

    public void open() {
        int size = Math.max(9, Math.min(54, size() - size() % 9));
        inventory = Bukkit.createInventory(this, size, Text.parse(title()));
        render();
        player.openInventory(inventory);
    }

    /** Clears and draws again without reopening. */
    public void refresh() {
        inventory.clear();
        render();
    }

    /** Opens another menu on the next tick (safe to call from click handlers). */
    protected void switchTo(Menu menu) {
        Bukkit.getScheduler().runTask(plugin, menu::open);
    }

    protected void closeLater() {
        Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
    }

    protected void fill(int from, int to, ItemStack item) {
        for (int i = from; i <= to && i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, item);
            }
        }
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
