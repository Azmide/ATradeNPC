package io.github.AzmiiD.aTradeNpc.gui;

import io.github.AzmiiD.aTradeNpc.ATradeNPC;
import io.github.AzmiiD.aTradeNpc.shop.Offer;
import io.github.AzmiiD.aTradeNpc.shop.OfferType;
import io.github.AzmiiD.aTradeNpc.shop.TradeShop;
import io.github.AzmiiD.aTradeNpc.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/** Cross-platform customer menu: display copies only; transactions never depend on virtual ingredients. */
public final class CustomerMenu extends Menu {
    private static final int PER_PAGE = 45, PREVIOUS = 45, CLOSE = 49, NEXT = 53;
    private final TradeShop shop;
    private final Map<Integer, Offer> slots = new HashMap<>();
    private int page;
    private boolean pending;

    public CustomerMenu(ATradeNPC plugin, Player player, TradeShop shop) {
        super(plugin, player);
        this.shop = shop;
    }

    @Override
    protected String title() {
        return plugin.getLang().format("gui.trade-title", "%name%", shop.getName());
    }

    @Override
    protected int size() {
        return 54;
    }

    private int lastPage() {
        return Math.max(0, (shop.getOffers().size() - 1) / PER_PAGE);
    }

    @Override
    protected void render() {
        page = Math.max(0, Math.min(page, lastPage()));
        slots.clear();
        int start = page * PER_PAGE;
        for (int slot = 0; slot < PER_PAGE && start + slot < shop.getOffers().size(); slot++) {
            Offer offer = shop.getOffers().get(start + slot);
            ItemStack display = OfferView.display(plugin, shop, offer, false);
            inventory.setItem(slot, Items.withLore(display, plugin.getLang().rawList("gui.lore-customer")));
            slots.put(slot, offer);
        }
        if (page > 0) {
            inventory.setItem(PREVIOUS, Items.button(Material.ARROW, plugin.getLang().raw("gui.customer-previous")));
        }
        inventory.setItem(CLOSE, Items.button(Material.BARRIER, plugin.getLang().raw("gui.customer-close")));
        if (page < lastPage()) {
            inventory.setItem(NEXT, Items.button(Material.ARROW, plugin.getLang().raw("gui.customer-next")));
        }
        fill(45, 53, Items.filler(Material.LIGHT_BLUE_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (pending || (!event.isLeftClick() && !event.isRightClick())) {
            return;
        }
        if (shop.isRemoved()) {
            closeLater();
            return;
        }
        int slot = event.getRawSlot();
        if (slot == CLOSE) {
            closeLater();
            return;
        }
        if (slot == PREVIOUS || slot == NEXT) {
            int target = page + (slot == PREVIOUS ? -1 : 1);
            if (target >= 0 && target <= lastPage()) {
                page = target;
                refresh();
            }
            return;
        }
        Offer offer = slots.get(slot);
        if (offer == null) {
            return;
        }
        boolean bulk = event.isShiftClick();
        pending = true;
        // Apply inventory changes after the cancelled click has finished. A queued
        // click cannot buy after closing/replacing the screen or editing the offer.
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inventory) {
                    return;
                }
                if (shop.isRemoved()) {
                    player.closeInventory();
                    return;
                }
                if (!shop.getOffers().contains(offer)) {
                    plugin.getLang().send(player, "offerChanged");
                } else {
                    plugin.getTransactions().execute(player, shop, offer, bulk ? bulkCount(offer) : 1);
                }
                refresh();
                player.updateInventory();
            } finally {
                pending = false;
            }
        });
    }

    private int bulkCount(Offer offer) {
        ItemStack item = offer.getItem();
        int per = Math.max(1, item.getAmount());
        return offer.getType() == OfferType.SELL
                ? Math.max(1, ItemUtil.count(player.getInventory().getStorageContents(), item) / per)
                : Math.max(1, item.getMaxStackSize() / per);
    }
}
