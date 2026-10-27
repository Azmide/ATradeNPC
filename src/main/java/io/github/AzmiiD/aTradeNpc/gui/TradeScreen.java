package io.github.AzmiiD.aTradeNpc.gui;

import io.github.AzmiiD.aTradeNpc.ATradeNPC;
import io.github.AzmiiD.aTradeNpc.shop.Offer;
import io.github.AzmiiD.aTradeNpc.shop.OfferType;
import io.github.AzmiiD.aTradeNpc.shop.TradeShop;
import io.github.AzmiiD.aTradeNpc.shop.TransactionService;
import io.github.AzmiiD.aTradeNpc.util.ItemUtil;
import io.github.AzmiiD.aTradeNpc.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.TradeSelectEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The customer view: the vanilla villager trading screen.
 * <p>
 * The screen is only used for showing and selecting offers. Taking the result is always
 * cancelled and handled by {@link TransactionService}, so player-shop stock and Vault money
 * work too. Money is shown as a "coin" display item; in a Buy offer that coin is placed in the
 * first payment slot so the result appears, and it can never leave the screen.
 */
public class TradeScreen implements Listener {

    private static final int RESULT_SLOT = 2;

    private record Session(TradeShop shop, Merchant merchant, List<Offer> offers, Selection selection) {
    }

    private static final class Selection {
        private long revision;
    }

    private final ATradeNPC plugin;
    private final NamespacedKey displayKey;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Long> lastTrade = new HashMap<>();

    public TradeScreen(ATradeNPC plugin) {
        this.plugin = plugin;
        this.displayKey = new NamespacedKey(plugin, "display");
    }

    public void open(Player player, TradeShop shop) {
        if (shop.getOffers().isEmpty()) {
            plugin.getLang().send(player, "noOffers");
            return;
        }
        Merchant merchant = Bukkit.createMerchant();
        List<Offer> offers = new ArrayList<>(shop.getOffers());
        List<MerchantRecipe> recipes = new ArrayList<>();
        for (Offer offer : offers) {
            recipes.add(recipe(shop, offer));
        }
        merchant.setRecipes(recipes);
        var view = MenuType.MERCHANT.builder()
                .title(Text.parse(plugin.getLang().format("gui.trade-title", "%name%", shop.getName())))
                .merchant(merchant)
                .checkReachable(false)
                .build(player);
        player.openInventory(view);
        sessions.put(player.getUniqueId(), new Session(shop, merchant, offers, new Selection()));
        plugin.getSounds().play(player, "open-shop");
    }

    private MerchantRecipe recipe(TradeShop shop, Offer offer) {
        String price = plugin.getVault().format(offer.getPrice());
        ItemStack result;
        List<ItemStack> ingredients = new ArrayList<>();
        switch (offer.getType()) {
            case TRADE -> {
                result = offer.getItem().clone();
                ingredients.add(offer.getCost1().clone());
                if (!ItemUtil.isEmpty(offer.getCost2())) {
                    ingredients.add(offer.getCost2().clone());
                }
            }
            case BUY -> {
                result = offer.getItem().clone();
                ingredients.add(moneyItem("gui.money-pay", price));
            }
            default -> {
                result = moneyItem("gui.money-receive", price);
                ingredients.add(offer.getItem().clone());
            }
        }
        int stock = TransactionService.stock(shop, offer);
        MerchantRecipe recipe = new MerchantRecipe(result, 0, stock < 0 ? Integer.MAX_VALUE : stock, false, 0, 0f, true);
        recipe.setIngredients(ingredients);
        return recipe;
    }

    /** Coin icon that shows a money amount. Tagged so it can never be taken out of the screen. */
    private ItemStack moneyItem(String langKey, String price) {
        Material material = plugin.getSettings().moneyItem;
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Text.item(plugin.getLang().format(langKey + "-name", "%price%", price)));
        List<String> lore = new ArrayList<>();
        for (String line : plugin.getLang().rawList(langKey + "-lore")) {
            lore.add(line.replace("%price%", price));
        }
        meta.lore(Text.itemLore(lore));
        meta.getPersistentDataContainer().set(displayKey, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    public boolean isDisplayItem(ItemStack stack) {
        return !ItemUtil.isEmpty(stack) && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(displayKey, PersistentDataType.BYTE);
    }

    private Session session(Player player, Inventory top) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || !(top instanceof MerchantInventory mi) || mi.getMerchant() != session.merchant()) {
            return null;
        }
        return session;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Session session = session(player, event.getView().getTopInventory());
        // Protect leaked icons even after the merchant has been closed. SELL's result
        // icon is handled below as a money transaction, never as an item transfer.
        boolean result = session != null && event.getRawSlot() == RESULT_SLOT;
        if (isDisplayItem(event.getCursor()) || (!result && isDisplayItem(event.getCurrentItem()))
                || (event.getHotbarButton() >= 0
                && isDisplayItem(player.getInventory().getItem(event.getHotbarButton())))) {
            event.setCancelled(true);
            syncLater(player);
            return;
        }
        if (session == null) {
            return;
        }
        MerchantInventory inv = (MerchantInventory) event.getView().getTopInventory();
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            syncLater(player);
            return;
        }
        boolean top = event.getClickedInventory() == inv;
        if (top && event.getSlot() != RESULT_SLOT && isDisplayItem(inv.getItem(event.getSlot()))) {
            event.setCancelled(true);
            return;
        }
        if (event.getClick().isKeyboardClick() && isDisplayItem(event.getCurrentItem())) {
            event.setCancelled(true);
            return;
        }
        if (!top || event.getSlot() != RESULT_SLOT) {
            return;
        }
        // Result slot: never let vanilla do the trade.
        event.setCancelled(true);
        if (ItemUtil.isEmpty(inv.getItem(RESULT_SLOT)) || onCooldown(player)) {
            return;
        }
        int index = inv.getSelectedRecipeIndex();
        if (index < 0 || index >= session.offers().size()) {
            return;
        }
        Offer offer = session.offers().get(index);
        TradeShop shop = session.shop();
        if (shop.isRemoved() || !shop.getOffers().contains(offer)) {
            plugin.getLang().send(player, "offerChanged");
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.closeInventory();
                if (!shop.isRemoved()) {
                    open(player, shop);
                }
            });
            return;
        }
        int times = 1;
        if (event.isShiftClick()) {
            ItemStack item = offer.getItem();
            int per = Math.max(1, item.getAmount());
            if (offer.getType() == OfferType.SELL) {
                int have = ItemUtil.count(player.getInventory().getStorageContents(), item);
                for (int i = 0; i < 2; i++) {
                    ItemStack in = inv.getItem(i);
                    if (!ItemUtil.isEmpty(in) && in.isSimilar(item)) {
                        have += in.getAmount();
                    }
                }
                times = Math.max(1, have / per);
            } else {
                times = Math.max(1, item.getMaxStackSize() / per);
            }
        }
        plugin.getTransactions().execute(player, shop, offer, times, inv);
        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
    }

    /** Every trade click runs whole transactions (shift = up to a full inventory), so spam clicks are limited. */
    private boolean onCooldown(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastTrade.get(player.getUniqueId());
        if (last != null && now - last < plugin.getSettings().clickCooldown) {
            return true;
        }
        lastTrade.put(player.getUniqueId(), now);
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && (isDisplayItem(event.getOldCursor())
                || (session(player, event.getView().getTopInventory()) != null
                && (event.getRawSlots().contains(RESULT_SLOT)
                || event.getRawSlots().stream().anyMatch(slot -> slot < 2
                && isDisplayItem(event.getView().getItem(slot))))))) {
            event.setCancelled(true);
            syncLater(player);
        }
    }

    /** Fires before vanilla moves the payment items back: take our coin out first. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSelect(TradeSelectEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Session session = session(player, event.getInventory());
        if (session == null) {
            return;
        }
        MerchantInventory inv = event.getInventory();
        long revision = ++session.selection().revision;
        clearDisplay(inv);
        purge(player);
        int index = event.getIndex();
        // Vanilla also predicts returning inputs on the client. Always send the
        // complete server contents afterwards, including BUY -> SELL/TRADE.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (session(player, player.getOpenInventory().getTopInventory()) != session
                    || session.selection().revision != revision) {
                return;
            }
            purge(player);
            if (index >= 0 && index < session.offers().size()
                    && session.offers().get(index).getType() == OfferType.BUY
                    && inv.getSelectedRecipeIndex() == index
                    && ItemUtil.isEmpty(inv.getItem(0)) && ItemUtil.isEmpty(inv.getItem(1))) {
                MerchantRecipe recipe = session.merchant().getRecipe(index);
                inv.setItem(0, recipe.getIngredients().getFirst().clone());
            }
            player.updateInventory();
        });
    }

    /** Fires before vanilla returns the payment items to the player. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Session session = session(player, event.getInventory());
        if (session == null) {
            return;
        }
        sessions.remove(player.getUniqueId());
        lastTrade.remove(player.getUniqueId());
        clearDisplay((MerchantInventory) event.getInventory());
        purge(player);
        if (plugin.isEnabled()) {
            syncLater(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        purge(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isDisplayItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            syncLater(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (isDisplayItem(event.getItem().getItemStack())) {
            event.setCancelled(true);
            event.getItem().remove();
        }
    }

    private void syncLater(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                purge(player);
                player.updateInventory();
            }
        });
    }

    private void clearDisplay(MerchantInventory inv) {
        for (int i = 0; i < 2; i++) {
            if (isDisplayItem(inv.getItem(i))) {
                inv.setItem(i, null);
            }
        }
    }

    /** Safety net: a coin must never stay with a player. */
    private void purge(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isDisplayItem(contents[i])) {
                player.getInventory().setItem(i, null);
            }
        }
        if (isDisplayItem(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
    }

    public void closeAll() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.closeInventory();
            }
        }
        sessions.clear();
    }
}
