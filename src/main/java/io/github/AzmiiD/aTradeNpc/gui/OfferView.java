package io.github.AzmiiD.aTradeNpc.gui;

import io.github.AzmiiD.aTradeNpc.ATradeNPC;
import io.github.AzmiiD.aTradeNpc.config.Lang;
import io.github.AzmiiD.aTradeNpc.shop.Offer;
import io.github.AzmiiD.aTradeNpc.shop.TradeShop;
import io.github.AzmiiD.aTradeNpc.shop.TransactionService;
import io.github.AzmiiD.aTradeNpc.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** How an offer is displayed in the editor. */
final class OfferView {

    private OfferView() {
    }

    static ItemStack display(ATradeNPC plugin, TradeShop shop, Offer offer, boolean editor) {
        Lang lang = plugin.getLang();
        String price = plugin.getVault().format(offer.getPrice());
        List<String> lore = new ArrayList<>();
        String key = switch (offer.getType()) {
            case TRADE -> "gui.lore-trade";
            case BUY -> "gui.lore-buy";
            case SELL -> "gui.lore-sell";
        };
        for (String line : lang.rawList(key)) {
            if (line.contains("%costs%")) {
                for (ItemStack cost : new ItemStack[]{offer.getCost1(), offer.getCost2()}) {
                    if (!ItemUtil.isEmpty(cost)) {
                        lore.add(costLine(lang, cost));
                    }
                }
            } else {
                lore.add(line.replace("%price%", price));
            }
        }
        int stock = TransactionService.stock(shop, offer);
        if (stock >= 0) {
            lore.add(lang.format("gui.lore-stock", "%stock%", String.valueOf(stock)));
        }
        if (editor) {
            lore.addAll(lang.rawList("gui.lore-editor"));
        }
        ItemStack item = offer.getItem();
        return Items.withLore(ItemUtil.isEmpty(item) ? new ItemStack(Material.BARRIER) : item, lore);
    }

    static String costLine(Lang lang, ItemStack cost) {
        return lang.format("gui.lore-cost-line", "%amount%", String.valueOf(cost.getAmount()),
                "%item%", ItemUtil.displayName(cost));
    }
}
