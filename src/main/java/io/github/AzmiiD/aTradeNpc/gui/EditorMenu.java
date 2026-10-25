package io.github.AzmiiD.aTradeNpc.gui;

import io.github.AzmiiD.aTradeNpc.ATradeNPC;
import io.github.AzmiiD.aTradeNpc.shop.Offer;
import io.github.AzmiiD.aTradeNpc.shop.OfferType;
import io.github.AzmiiD.aTradeNpc.shop.ShopType;
import io.github.AzmiiD.aTradeNpc.shop.TradeShop;
import io.github.AzmiiD.aTradeNpc.shop.TransactionService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Owner/admin editor: list of offers + shop settings. */
public class EditorMenu extends Menu {

    private static final int PER_PAGE = 45;
    private static final int PREV = 45, ADD_TRADE = 46, ADD_BUY = 47, ADD_SELL = 48, INFO = 49,
            RENAME = 50, APPEARANCE = 51, MOVE = 52, NEXT = 53;

    private final TradeShop shop;
    private int page;
    private final Map<Integer, Offer> slots = new HashMap<>();

    public EditorMenu(ATradeNPC plugin, Player player, TradeShop shop) {
        this(plugin, player, shop, 0);
    }

    public EditorMenu(ATradeNPC plugin, Player player, TradeShop shop, int page) {
        super(plugin, player);
        this.shop = shop;
        this.page = page;
    }

    @Override
    protected String title() {
        return plugin.getLang().format("gui.editor-title", "%name%", shop.getName());
    }

    @Override
    protected int size() {
        return 54;
    }

    private int pages() {
        return Math.max(1, (int) Math.ceil(shop.getOffers().size() / (double) PER_PAGE));
    }

    @Override
    protected void render() {
        slots.clear();
        List<Offer> offers = shop.getOffers();
        page = Math.max(0, Math.min(page, pages() - 1));
        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < offers.size(); i++) {
            Offer offer = offers.get(start + i);
            inventory.setItem(i, OfferView.display(plugin, shop, offer, true));
            slots.put(i, offer);
        }
        if (page > 0) {
            inventory.setItem(PREV, Items.button(Material.ARROW, "<#0BA6DF>Previous page"));
        }
        if (page < pages() - 1) {
            inventory.setItem(NEXT, Items.button(Material.ARROW, "<#0BA6DF>Next page"));
        }
        inventory.setItem(ADD_TRADE, Items.button(Material.EMERALD, "<#5AD1FF><bold>+ Trade offer",
                "<gray>Customer gives item(s)", "<gray>and receives an item.", "", "<dark_gray>Any item works, custom items too."));
        inventory.setItem(ADD_BUY, Items.button(Material.GOLD_INGOT, "<#0BA6DF><bold>+ Buy offer",
                "<gray>Customer pays money", "<gray>and receives an item.", "", "<dark_gray>Needs Vault."));
        inventory.setItem(ADD_SELL, Items.button(Material.HOPPER, "<green><bold>+ Sell offer",
                "<gray>Customer sells an item", "<gray>and receives money.", "", "<dark_gray>Needs Vault."));

        String location = shop.getType() == ShopType.PLAYER && shop.getChestBlock() != null
                ? shop.getChestBlock().getX() + " " + shop.getChestBlock().getY() + " " + shop.getChestBlock().getZ() : "-";
        inventory.setItem(INFO, Items.button(Material.BOOK, "<#0BA6DF>Shop #" + shop.getId(),
                "<gray>Name: <reset>" + shop.getName(),
                "<gray>Type: <white>" + shop.getType(),
                "<gray>Owner: <white>" + (shop.getType() == ShopType.PLAYER ? shop.getOwnerName() : "Server"),
                "<gray>Storage chest: <white>" + location,
                "<gray>Offers: <white>" + offers.size() + "/" + plugin.getSettings().maxOffers,
                "",
                shop.getType() == ShopType.PLAYER ? "<#0BA6DF>Left-click <gray>» open storage chest" : "",
                "<red>Shift-right-click <gray>» delete shop"));
        inventory.setItem(RENAME, Items.button(Material.NAME_TAG, "<#0BA6DF>Rename", "<gray>Type the new name in chat."));
        inventory.setItem(APPEARANCE, Items.button(Material.VILLAGER_SPAWN_EGG, "<#0BA6DF>NPC appearance",
                "<gray>Current: <white>" + shop.getEntityType().name()));
        inventory.setItem(MOVE, Items.button(Material.COMPASS, "<#0BA6DF>Move / center NPC",
                "<#0BA6DF>Left-click <gray>» move the NPC to you",
                "<#0BA6DF>Right-click <gray>» center it on its block"));
        fill(45, 53, Items.filler(Material.LIGHT_BLUE_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (shop.isRemoved()) {
            closeLater();
            return;
        }
        if (!shop.canEdit(player)) {
            closeLater();
            return;
        }
        int slot = event.getRawSlot();
        switch (slot) {
            case PREV -> {
                if (page > 0) {
                    page--;
                    refresh();
                }
            }
            case NEXT -> {
                if (page < pages() - 1) {
                    page++;
                    refresh();
                }
            }
            case ADD_TRADE -> addOffer(OfferType.TRADE);
            case ADD_BUY -> addOffer(OfferType.BUY);
            case ADD_SELL -> addOffer(OfferType.SELL);
            case INFO -> {
                if (event.isShiftClick() && event.isRightClick()) {
                    switchTo(new ConfirmDeleteMenu(plugin, player, shop));
                } else if (event.isLeftClick() && shop.getType() == ShopType.PLAYER) {
                    openStorage();
                }
            }
            case RENAME -> plugin.getPrompt().ask(player, "promptName", text -> {
                String name = shop.isRemoved() ? null : plugin.getShopService().cleanName(player, text);
                if (name != null) {
                    plugin.getShopService().rename(shop, name);
                    plugin.getLang().send(player, "shopRenamed", "%name%", shop.getName());
                }
                new EditorMenu(plugin, player, shop, page).open();
            }, () -> new EditorMenu(plugin, player, shop, page).open());
            case APPEARANCE -> switchTo(new AppearanceMenu(plugin, player, shop));
            case MOVE -> {
                if (event.isRightClick()) {
                    plugin.getShopService().center(shop);
                    plugin.getLang().send(player, "shopCentered");
                } else if (plugin.getShopService().move(player, shop, player.getLocation())) {
                    plugin.getLang().send(player, "shopMoved");
                }
            }
            default -> clickOffer(event, slot);
        }
    }

    private void openStorage() {
        Inventory storage = TransactionService.storage(shop);
        if (storage == null) {
            plugin.getLang().send(player, "storageUnavailable");
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(storage));
    }

    private void addOffer(OfferType type) {
        if (shop.getOffers().size() >= plugin.getSettings().maxOffers) {
            plugin.getLang().send(player, "tooManyOffers", "%max%", String.valueOf(plugin.getSettings().maxOffers));
            return;
        }
        switchTo(new OfferEditorMenu(plugin, player, shop, null, new Offer(type), page));
    }

    private void clickOffer(InventoryClickEvent event, int slot) {
        Offer offer = slots.get(slot);
        if (offer == null) {
            return;
        }
        List<Offer> offers = shop.getOffers();
        int index = offers.indexOf(offer);
        if (index < 0) {
            refresh();
            return;
        }
        if (event.isShiftClick()) {
            int target = event.isLeftClick() ? index - 1 : index + 1;
            if (target >= 0 && target < offers.size()) {
                Collections.swap(offers, index, target);
                plugin.getShopManager().save();
                refresh();
            }
            return;
        }
        if (event.isRightClick()) {
            if (offers.size() >= plugin.getSettings().maxOffers) {
                plugin.getLang().send(player, "tooManyOffers", "%max%", String.valueOf(plugin.getSettings().maxOffers));
                return;
            }
            offers.add(index + 1, offer.copy());
            plugin.getShopManager().save();
            plugin.getLang().send(player, "offerDuplicated");
            plugin.getSounds().play(player, "click");
            refresh();
            return;
        }
        if (event.isLeftClick()) {
            switchTo(new OfferEditorMenu(plugin, player, shop, offer, offer.copy(), page));
        }
    }
}
