package io.github.AzmiiD.aTradeNpc.gui;

import io.github.AzmiiD.aTradeNpc.ATradeNPC;
import io.github.AzmiiD.aTradeNpc.shop.Offer;
import io.github.AzmiiD.aTradeNpc.shop.OfferType;
import io.github.AzmiiD.aTradeNpc.shop.TradeShop;
import io.github.AzmiiD.aTradeNpc.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Creates/edits one offer. Items are copied from the player's inventory (clicking an item
 * there puts a copy into the selected slot), so nothing is taken from the editor.
 */
public class OfferEditorMenu extends Menu {

    private static final int COST1 = 10, COST2 = 11, ARROW = 13, RESULT = 15;
    private static final int ITEM = 11, PRICE = 15;
    private static final int HELP = 4, BACK = 18, SAVE = 22, DELETE = 26;

    private final TradeShop shop;
    /** The offer in the shop being edited, null for a new offer. */
    private final Offer original;
    private final Offer offer;
    private final int editorPage;
    private int selected;

    public OfferEditorMenu(ATradeNPC plugin, Player player, TradeShop shop, Offer original, Offer offer, int editorPage) {
        super(plugin, player);
        this.shop = shop;
        this.original = original;
        this.offer = offer;
        this.editorPage = editorPage;
        this.selected = offer.getType() == OfferType.TRADE ? RESULT : ITEM;
    }

    private boolean trade() {
        return offer.getType() == OfferType.TRADE;
    }

    @Override
    protected String title() {
        return plugin.getLang().format("gui.offer-title", "%type%", typeName());
    }

    private String typeName() {
        return plugin.getLang().raw("gui.type-" + offer.getType().name().toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    protected int size() {
        return 27;
    }

    @Override
    protected void render() {
        inventory.setItem(HELP, Items.button(Material.BOOK, "<#0BA6DF>How to edit",
                "<#0BA6DF>Click a slot <gray>» select it (green marker)",
                "<#0BA6DF>Click an item in your inventory",
                "<gray>  » put a copy into the selected slot",
                "<#0BA6DF>Left / Right-click selected slot <gray>» amount +1 / -1",
                "<#0BA6DF>Shift-left <gray>» amount +8",
                "<#0BA6DF>Shift-right <gray>» clear slot",
                "",
                "<dark_gray>Custom items (names, lore, enchants,",
                "<dark_gray>model data, plugin items) are matched exactly."));
        if (trade()) {
            slot(COST1, offer.getCost1(), "<gray>Cost item #1", "<dark_gray>What the customer pays");
            slot(COST2, offer.getCost2(), "<gray>Cost item #2 <dark_gray>(optional)", "<dark_gray>What the customer pays");
            inventory.setItem(ARROW, Items.button(Material.SPECTRAL_ARROW, "<gray>→"));
            slot(RESULT, offer.getItem(), "<gray>Result item", "<dark_gray>What the customer receives");
        } else {
            String what = offer.getType() == OfferType.BUY ? "<dark_gray>What the customer buys" : "<dark_gray>What the customer sells to you";
            slot(ITEM, offer.getItem(), "<gray>Item", what);
            inventory.setItem(PRICE, Items.button(Material.GOLD_NUGGET, "<#0BA6DF>Price: <white>" + plugin.getVault().format(offer.getPrice()),
                    "<#0BA6DF>Click <gray>» type a price in chat",
                    "<gray>Price is per " + (ItemUtil.isEmpty(offer.getItem()) ? "offer" : offer.getItem().getAmount() + " item(s)")));
        }
        inventory.setItem(BACK, Items.button(Material.ARROW, "<#0BA6DF>Back", "<gray>Discard changes"));
        inventory.setItem(SAVE, Items.button(Material.LIME_CONCRETE, "<green><bold>Save offer"));
        if (original != null) {
            inventory.setItem(DELETE, Items.button(Material.RED_CONCRETE, "<red><bold>Delete offer", "<gray>Shift-click to confirm"));
        }
        fill(0, 26, Items.filler(Material.BLACK_STAINED_GLASS_PANE));
    }

    /** Draws an item slot plus its marker (the pane above it). */
    private void slot(int slot, ItemStack item, String emptyName, String hint) {
        if (ItemUtil.isEmpty(item)) {
            inventory.setItem(slot, Items.button(Material.LIGHT_GRAY_STAINED_GLASS_PANE, emptyName, hint,
                    "", "<gray>Select this slot, then click", "<gray>an item in your inventory."));
        } else {
            inventory.setItem(slot, item.clone());
        }
        boolean sel = slot == selected;
        inventory.setItem(slot - 9, Items.button(sel ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
                sel ? "<green>Selected" : "<gray>Click below to select", hint));
    }

    private boolean isItemSlot(int slot) {
        return trade() ? slot == COST1 || slot == COST2 || slot == RESULT : slot == ITEM;
    }

    private ItemStack get(int slot) {
        if (!trade() || slot == RESULT) {
            return offer.getItem();
        }
        return slot == COST1 ? offer.getCost1() : offer.getCost2();
    }

    private void set(int slot, ItemStack item) {
        if (!trade() || slot == RESULT) {
            offer.setItem(item);
        } else if (slot == COST1) {
            offer.setCost1(item);
        } else if (slot == COST2) {
            offer.setCost2(item);
        }
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (shop.isRemoved() || !shop.canEdit(player)) {
            closeLater();
            return;
        }
        int slot = event.getRawSlot();
        if (isItemSlot(slot)) {
            if (slot != selected) {
                selected = slot;
            } else {
                adjust(slot, event);
            }
            refresh();
            return;
        }
        // Clicking the marker above a slot selects it too.
        if (isItemSlot(slot + 9)) {
            selected = slot + 9;
            refresh();
            return;
        }
        switch (slot) {
            case PRICE -> {
                if (!trade()) {
                    askPrice();
                }
            }
            case BACK -> switchTo(new EditorMenu(plugin, player, shop, editorPage));
            case SAVE -> save();
            case DELETE -> {
                if (original != null && event.isShiftClick()) {
                    shop.getOffers().remove(original);
                    plugin.getShopManager().save();
                    plugin.getLang().send(player, "offerDeleted");
                    switchTo(new EditorMenu(plugin, player, shop, editorPage));
                }
            }
            default -> {
            }
        }
    }

    private void adjust(int slot, InventoryClickEvent event) {
        ItemStack item = get(slot);
        if (ItemUtil.isEmpty(item)) {
            return;
        }
        if (event.isShiftClick() && event.isRightClick()) {
            set(slot, null);
            return;
        }
        int delta = event.isShiftClick() ? 8 : event.isLeftClick() ? 1 : event.isRightClick() ? -1 : 0;
        int amount = Math.max(1, Math.min(item.getMaxStackSize(), item.getAmount() + delta));
        ItemStack copy = item.clone();
        copy.setAmount(amount);
        set(slot, copy);
    }

    @Override
    public void onBottomClick(InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (ItemUtil.isEmpty(clicked) || !isItemSlot(selected)) {
            return;
        }
        ItemStack copy = clicked.clone();
        copy.setAmount(Math.min(copy.getAmount(), copy.getMaxStackSize()));
        set(selected, copy);
        refresh();
    }

    private void askPrice() {
        plugin.getPrompt().ask(player, "promptPrice", text -> {
            try {
                double price = Double.parseDouble(text.replace(",", "").replace("$", ""));
                if (!Double.isFinite(price) || price <= 0) {
                    throw new NumberFormatException();
                }
                offer.setPrice(Math.round(price * 100) / 100.0);
                plugin.getLang().send(player, "priceSet", "%price%", plugin.getVault().format(offer.getPrice()));
            } catch (NumberFormatException e) {
                plugin.getLang().send(player, "invalidNumber", "%input%", text);
            }
            reopen();
        }, this::reopen);
    }

    private void reopen() {
        if (!shop.isRemoved()) {
            open();
        }
    }

    private void save() {
        offer.normalize();
        String missing = offer.validate();
        if (missing != null) {
            plugin.getLang().send(player, "offerIncomplete", "%reason%", plugin.getLang().raw(missing));
            refresh();
            return;
        }
        List<Offer> offers = shop.getOffers();
        int index = original == null ? -1 : offers.indexOf(original);
        if (index >= 0) {
            offers.set(index, offer);
        } else {
            if (offers.size() >= plugin.getSettings().maxOffers) {
                plugin.getLang().send(player, "tooManyOffers", "%max%", String.valueOf(plugin.getSettings().maxOffers));
                return;
            }
            offers.add(offer);
        }
        plugin.getShopManager().save();
        plugin.getLang().send(player, "offerSaved");
        switchTo(new EditorMenu(plugin, player, shop, editorPage));
    }
}
