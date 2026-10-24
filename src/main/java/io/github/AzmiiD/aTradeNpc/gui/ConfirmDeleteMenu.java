package io.github.AzmiiD.aTradeNpc.gui;

import io.github.AzmiiD.aTradeNpc.ATradeNPC;
import io.github.AzmiiD.aTradeNpc.shop.TradeShop;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/** Asks for confirmation before a shop is deleted. */
public class ConfirmDeleteMenu extends Menu {

    private static final int YES = 11, NO = 15;

    private final TradeShop shop;

    public ConfirmDeleteMenu(ATradeNPC plugin, Player player, TradeShop shop) {
        super(plugin, player);
        this.shop = shop;
    }

    @Override
    protected String title() {
        return plugin.getLang().format("gui.confirm-title", "%id%", String.valueOf(shop.getId()));
    }

    @Override
    protected int size() {
        return 27;
    }

    @Override
    protected void render() {
        inventory.setItem(YES, Items.button(Material.RED_CONCRETE, "<red><bold>Delete shop",
                "<gray>The NPC and all offers are removed.", "<gray>Items in the storage chest stay there."));
        inventory.setItem(NO, Items.button(Material.LIME_CONCRETE, "<green><bold>Cancel"));
        fill(0, 26, Items.filler(Material.BLACK_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (shop.isRemoved() || !shop.canEdit(player)) {
            closeLater();
            return;
        }
        if (event.getRawSlot() == YES) {
            plugin.getShopService().delete(shop);
            plugin.getLang().send(player, "shopRemoved", "%id%", String.valueOf(shop.getId()));
            closeLater();
        } else if (event.getRawSlot() == NO) {
            switchTo(new EditorMenu(plugin, player, shop));
        }
    }
}
