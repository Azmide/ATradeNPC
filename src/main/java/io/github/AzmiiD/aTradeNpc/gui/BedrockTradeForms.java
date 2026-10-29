package io.github.AzmiiD.aTradeNpc.gui;

import io.github.AzmiiD.aTradeNpc.ATradeNPC;
import io.github.AzmiiD.aTradeNpc.shop.Offer;
import io.github.AzmiiD.aTradeNpc.shop.TradeShop;
import io.github.AzmiiD.aTradeNpc.shop.OfferType;
import io.github.AzmiiD.aTradeNpc.util.ItemUtil;
import io.github.AzmiiD.aTradeNpc.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;
import java.util.function.Consumer;

/** Optional Cumulus forms, using the provider's own classes rather than shading another API copy. */
public final class BedrockTradeForms {
    private BedrockTradeForms() { }

    public static boolean open(ATradeNPC plugin, Player player, TradeShop shop, int page) {
        List<Offer> offers = new ArrayList<>(shop.getOffers());
        int start = Math.max(0, Math.min(page, Math.max(0, (offers.size() - 1) / 20))) * 20;
        List<Offer> visible = offers.subList(start, Math.min(start + 20, offers.size()));
        List<String> buttons = new ArrayList<>();
        for (Offer offer : visible) buttons.add(label(plugin, offer));
        int previous = buttons.size();
        buttons.add("Previous page");
        int next = buttons.size();
        buttons.add("Next page");
        buttons.add("Close");
        return send(plugin, player, plain(shop.getName()), "Select an offer to view details and confirm.", buttons, index -> {
            if (shop.isRemoved()) return;
            if (index >= 0 && index < visible.size()) {
                Offer offer = visible.get(index);
                if (!shop.getOffers().contains(offer)) {
                    plugin.getLang().send(player, "offerChanged");
                    open(plugin, player, shop, page);
                    return;
                }
                String details = label(plugin, offer);
                if (offer.getType() == OfferType.TRADE) {
                    for (var cost : new org.bukkit.inventory.ItemStack[]{offer.getCost1(), offer.getCost2()}) {
                        if (!ItemUtil.isEmpty(cost)) details += "\nCost: " + cost.getAmount() + "x " + plain(ItemUtil.displayName(cost));
                    }
                }
                boolean sent = send(plugin, player, "Confirm trade", details, List.of("Trade once", "Back"), choice -> {
                    if (shop.isRemoved()) return;
                    if (choice == 0 && shop.getOffers().contains(offer)) {
                        plugin.getTransactions().execute(player, shop, offer, 1);
                    } else if (choice == 0) plugin.getLang().send(player, "offerChanged");
                    open(plugin, player, shop, page);
                });
                if (!sent) new CustomerMenu(plugin, player, shop).open();
            } else if (index == previous) open(plugin, player, shop, Math.max(0, page - 1));
            else if (index == next) open(plugin, player, shop, Math.min((offers.size() - 1) / 20, page + 1));
        });
    }

    private static String label(ATradeNPC plugin, Offer offer) {
        return offer.getType() + " " + offer.getItem().getAmount() + "x " + plain(ItemUtil.displayName(offer.getItem()))
                + (offer.getType() == OfferType.TRADE ? "" : "\n" + plugin.getVault().format(offer.getPrice()));
    }

    private static String plain(String text) { return Text.plain(Text.parse(text)); }

    private static boolean send(ATradeNPC plugin, Player player, String title, String content,
                                List<String> buttons, IntConsumer action) {
        try {
            Class<?> simple = Class.forName("org.geysermc.cumulus.form.SimpleForm");
            Object builder = simple.getMethod("builder").invoke(null);
            var type = Class.forName("org.geysermc.cumulus.form.SimpleForm$Builder");
            type.getMethod("title", String.class).invoke(builder, title);
            type.getMethod("content", String.class).invoke(builder, content);
            for (String button : buttons) type.getMethod("button", String.class).invoke(builder, button);
            AtomicBoolean used = new AtomicBoolean();
            Consumer<Object> callback = response -> {
                if (!used.compareAndSet(false, true) || !plugin.isEnabled()) return;
                try {
                    int index = (int) Class.forName("org.geysermc.cumulus.response.SimpleFormResponse")
                            .getMethod("clickedButtonId").invoke(response);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) action.accept(index);
                    });
                } catch (ReflectiveOperationException error) {
                    plugin.getLogger().warning("Could not read Bedrock form response: " + error.getMessage());
                }
            };
            type.getMethod("validResultHandler", Consumer.class).invoke(builder, callback);
            Object form = type.getMethod("build").invoke(builder);
            Class<?> formType = Class.forName("org.geysermc.cumulus.form.Form");
            for (String[] provider : new String[][]{
                    {"org.geysermc.floodgate.api.FloodgateApi", "getInstance"},
                    {"org.geysermc.geyser.api.GeyserApi", "api"}}) {
                try {
                    Class<?> api = Class.forName(provider[0]);
                    Object instance = api.getMethod(provider[1]).invoke(null);
                    if (instance != null && Boolean.TRUE.equals(api.getMethod("sendForm", UUID.class, formType)
                            .invoke(instance, player.getUniqueId(), form))) return true;
                } catch (ReflectiveOperationException | LinkageError ignored) { }
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
            plugin.getLogger().warning("Bedrock forms unavailable; using chest menu: " + unavailable.getMessage());
        }
        return false;
    }
}
