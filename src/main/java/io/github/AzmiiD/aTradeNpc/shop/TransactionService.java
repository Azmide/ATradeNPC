package io.github.AzmiiD.aTradeNpc.shop;

import io.github.AzmiiD.aTradeNpc.ATradeNPC;
import io.github.AzmiiD.aTradeNpc.config.Lang;
import io.github.AzmiiD.aTradeNpc.economy.VaultHook;
import io.github.AzmiiD.aTradeNpc.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Executes offers. Every single trade is first done on copies of the player's and the
 * storage container's contents; only if everything fits are the copies written back.
 */
public class TransactionService {

    private enum Failure {
        NOT_ENOUGH_ITEMS("notEnoughItems"),
        NOT_ENOUGH_MONEY("notEnoughMoney"),
        INVENTORY_FULL("inventoryFull"),
        OUT_OF_STOCK("outOfStock"),
        SHOP_FULL("shopFull"),
        OWNER_NO_MONEY("ownerNoMoney"),
        NO_ECONOMY("noEconomy");

        final String langKey;

        Failure(String langKey) {
            this.langKey = langKey;
        }
    }

    private final ATradeNPC plugin;

    public TransactionService(ATradeNPC plugin) {
        this.plugin = plugin;
    }

    /**
     * Runs the offer up to {@code times} times and sends one summary message.
     *
     * @return how many times the offer was executed
     */
    public int execute(Player player, TradeShop shop, Offer offer, int times) {
        return execute(player, shop, offer, times, null);
    }

    /**
     * @param inputs the two payment slots of the villager trade screen, used before the player's inventory (nullable)
     */
    public int execute(Player player, TradeShop shop, Offer offer, int times, Inventory inputs) {
        Lang lang = plugin.getLang();
        VaultHook eco = plugin.getVault();
        int done = 0;
        Failure failure = null;
        for (int i = 0; i < times; i++) {
            failure = once(player, shop, offer, inputs);
            if (failure != null) {
                break;
            }
            done++;
        }
        if (done == 0) {
            if (failure != null) {
                lang.send(player, failure.langKey, "%price%", eco.format(offer.getPrice()));
                plugin.getSounds().play(player, "trade-fail");
            }
            return 0;
        }
        String item = ItemUtil.displayName(offer.getItem());
        int amount = offer.getItem().getAmount() * done;
        String price = eco.format(offer.getPrice() * done);
        switch (offer.getType()) {
            case TRADE -> lang.send(player, "traded", "%times%", String.valueOf(done), "%item%", item);
            case BUY -> lang.send(player, "bought", "%amount%", String.valueOf(amount), "%item%", item, "%price%", price);
            case SELL -> lang.send(player, "sold", "%amount%", String.valueOf(amount), "%item%", item, "%price%", price);
        }
        notifyOwner(player, shop, offer, done, amount, item, price);
        plugin.getSounds().play(player, "trade-success");
        log(player, shop, offer, done, amount, price);
        return done;
    }

    private void log(Player player, TradeShop shop, Offer offer, int times, int amount, String price) {
        String item = plainName(offer.getItem());
        String what = switch (offer.getType()) {
            case TRADE -> {
                StringBuilder costs = new StringBuilder();
                for (ItemStack cost : new ItemStack[]{offer.getCost1(), offer.getCost2()}) {
                    if (!ItemUtil.isEmpty(cost)) {
                        costs.append(costs.isEmpty() ? "" : " + ").append(cost.getAmount() * times).append("x ").append(plainName(cost));
                    }
                }
                yield "TRADE " + costs + " -> " + amount + "x " + item;
            }
            case BUY -> "BUY " + amount + "x " + item + " for " + price;
            case SELL -> "SELL " + amount + "x " + item + " for " + price;
        };
        String owner = shop.getType() == ShopType.PLAYER ? " (owner " + shop.getOwnerName() + ")" : " (admin)";
        plugin.getTransactionLogger().log(player.getName() + " " + what + " at shop #" + shop.getId() + owner);
    }

    private static String plainName(ItemStack stack) {
        return io.github.AzmiiD.aTradeNpc.util.Text.plain(io.github.AzmiiD.aTradeNpc.util.Text.parse(ItemUtil.displayName(stack)));
    }

    private void notifyOwner(Player customer, TradeShop shop, Offer offer, int times, int amount, String item, String price) {
        if (shop.getType() != ShopType.PLAYER || shop.getOwner() == null) {
            return;
        }
        Player owner = Bukkit.getPlayer(shop.getOwner());
        if (owner == null) {
            return;
        }
        String key = switch (offer.getType()) {
            case TRADE -> "ownerNotifyTrade";
            case BUY -> "ownerNotifyBuy";
            case SELL -> "ownerNotifySell";
        };
        plugin.getLang().send(owner, key, "%id%", String.valueOf(shop.getId()), "%player%", customer.getName(),
                "%times%", String.valueOf(times), "%amount%", String.valueOf(amount), "%item%", item, "%price%", price);
    }

    private Failure once(Player player, TradeShop shop, Offer offer, Inventory inputs) {
        boolean playerShop = shop.getType() == ShopType.PLAYER;
        VaultHook eco = plugin.getVault();
        if (offer.getType() != OfferType.TRADE && !eco.isAvailable()) {
            return Failure.NO_ECONOMY;
        }

        Inventory storage = null;
        ItemStack[] chest = null;
        if (playerShop) {
            storage = storage(shop);
            if (storage == null) {
                return Failure.OUT_OF_STOCK;
            }
            chest = ItemUtil.copy(storage.getContents());
        }
        // inv = [payment slot 0, payment slot 1, player storage...]; display items (money icons) count as empty.
        int start = inputs == null ? 0 : 2;
        ItemStack[] storageContents = player.getInventory().getStorageContents();
        ItemStack[] inv = new ItemStack[start + storageContents.length];
        for (int i = 0; i < start; i++) {
            ItemStack in = inputs.getItem(i);
            inv[i] = ItemUtil.isEmpty(in) || plugin.getTradeScreen().isDisplayItem(in) ? null : in.clone();
        }
        ItemStack[] storageCopy = ItemUtil.copy(storageContents);
        System.arraycopy(storageCopy, 0, inv, start, storageCopy.length);
        ItemStack item = offer.getItem();
        double price = offer.getPrice();
        OfflinePlayer owner = playerShop ? Bukkit.getOfflinePlayer(shop.getOwner()) : null;

        switch (offer.getType()) {
            case TRADE -> {
                // Player pays the costs...
                for (ItemStack cost : new ItemStack[]{offer.getCost1(), offer.getCost2()}) {
                    if (!ItemUtil.isEmpty(cost) && !ItemUtil.remove(inv, cost, cost.getAmount())) {
                        return Failure.NOT_ENOUGH_ITEMS;
                    }
                }
                // ...the shop hands out the result...
                if (playerShop && !ItemUtil.remove(chest, item, item.getAmount())) {
                    return Failure.OUT_OF_STOCK;
                }
                if (ItemUtil.add(inv, start, item, item.getAmount()) > 0) {
                    return Failure.INVENTORY_FULL;
                }
                // ...and stores the costs.
                if (playerShop) {
                    for (ItemStack cost : new ItemStack[]{offer.getCost1(), offer.getCost2()}) {
                        if (!ItemUtil.isEmpty(cost) && ItemUtil.add(chest, cost, cost.getAmount()) > 0) {
                            return Failure.SHOP_FULL;
                        }
                    }
                }
            }
            case BUY -> {
                if (!eco.has(player, price)) {
                    return Failure.NOT_ENOUGH_MONEY;
                }
                if (playerShop && !ItemUtil.remove(chest, item, item.getAmount())) {
                    return Failure.OUT_OF_STOCK;
                }
                if (ItemUtil.add(inv, start, item, item.getAmount()) > 0) {
                    return Failure.INVENTORY_FULL;
                }
                if (!eco.withdraw(player, price)) {
                    return Failure.NOT_ENOUGH_MONEY;
                }
                if (playerShop) {
                    double tax = price * plugin.getSettings().taxPercent / 100.0;
                    eco.deposit(owner, price - tax);
                }
            }
            case SELL -> {
                if (!ItemUtil.remove(inv, item, item.getAmount())) {
                    return Failure.NOT_ENOUGH_ITEMS;
                }
                if (playerShop) {
                    if (ItemUtil.add(chest, item, item.getAmount()) > 0) {
                        return Failure.SHOP_FULL;
                    }
                    if (!eco.has(owner, price) || !eco.withdraw(owner, price)) {
                        return Failure.OWNER_NO_MONEY;
                    }
                }
                if (!eco.deposit(player, price)) {
                    if (playerShop) {
                        eco.deposit(owner, price); // refund
                    }
                    return Failure.NO_ECONOMY;
                }
            }
        }

        // Everything succeeded: apply the simulated contents.
        player.getInventory().setStorageContents(java.util.Arrays.copyOfRange(inv, start, inv.length));
        for (int i = 0; i < start; i++) {
            if (!plugin.getTradeScreen().isDisplayItem(inputs.getItem(i))) {
                inputs.setItem(i, inv[i]);
            }
        }
        if (storage != null) {
            storage.setContents(chest);
        }
        return null;
    }

    /** Inventory of the storage container of a player shop (whole double chest), or null if missing. */
    public static Inventory storage(TradeShop shop) {
        Block block = shop.getChestBlock();
        if (block == null || !block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
            return null;
        }
        return block.getState(false) instanceof Container container ? container.getInventory() : null;
    }

    /** How many times the offer can be done with the current stock (player shops), -1 = infinite. */
    public static int stock(TradeShop shop, Offer offer) {
        if (shop.getType() != ShopType.PLAYER || offer.getType() == OfferType.SELL) {
            return -1;
        }
        Inventory storage = storage(shop);
        if (storage == null) {
            return 0;
        }
        return ItemUtil.count(storage.getContents(), offer.getItem()) / Math.max(1, offer.getItem().getAmount());
    }
}
