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
        List<String> images = new ArrayList<>();
        for (Offer offer : visible) {
            buttons.add(label(plugin, offer));
            images.add(icon(plugin, offer));
        }
        int previous = buttons.size();
        buttons.add("Halaman sebelumnya");
        int next = buttons.size();
        buttons.add("Halaman berikutnya");
        buttons.add("Tutup toko");
        images.add("textures/ui/arrow_left");
        images.add("textures/ui/arrow_right");
        images.add("textures/ui/cancel");
        String heading = "§l" + plain(shop.getName()) + "§r\nHalaman " + (start / 20 + 1) + "/" + Math.max(1, (offers.size() + 19) / 20)
                + "\n\nBELI: kamu membayar dan menerima barang.\nJUAL: kamu memberikan barang dan menerima uang.\nTUKAR: barang ditukar dengan barang.\n\nPilih barang untuk melihat rincian. Belum ada pembayaran sebelum konfirmasi.";
        return send(plugin, player, plain(shop.getName()), heading, buttons, images, index -> {
            if (shop.isRemoved()) return;
            if (index >= 0 && index < visible.size()) {
                Offer offer = visible.get(index);
                if (!shop.getOffers().contains(offer)) {
                    plugin.getLang().send(player, "offerChanged");
                    open(plugin, player, shop, page);
                    return;
                }
                String details = "§lRincian transaksi§r\n\nBarang: " + offer.getItem().getAmount() + "x " + plain(ItemUtil.displayName(offer.getItem()));
                if (offer.getType() != OfferType.TRADE) details += "\n" + (offer.getType() == OfferType.BUY ? "Kamu membayar: " : "Kamu menerima: ") + plugin.getVault().format(offer.getPrice());
                int stock = io.github.AzmiiD.aTradeNpc.shop.TransactionService.stock(shop, offer);
                details += "\nStok: " + (stock < 0 ? "Tidak terbatas" : stock + " transaksi");
                if (offer.getType() == OfferType.TRADE) {
                    for (var cost : new org.bukkit.inventory.ItemStack[]{offer.getCost1(), offer.getCost2()}) {
                        if (!ItemUtil.isEmpty(cost)) details += "\nKamu memberikan: " + cost.getAmount() + "x " + plain(ItemUtil.displayName(cost));
                    }
                }
                details += "\n\nPeriksa barang dan jumlah di atas. Tekan konfirmasi untuk 1 transaksi.";
                String confirm = offer.getType() == OfferType.BUY ? "Konfirmasi beli" : offer.getType() == OfferType.SELL ? "Konfirmasi jual" : "Konfirmasi tukar";
                boolean sent = send(plugin, player, "Konfirmasi transaksi", details, List.of(confirm, "Kembali ke toko"), List.of(icon(plugin, offer), "textures/ui/arrow_left"), choice -> {
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
        String action = offer.getType() == OfferType.BUY ? "§aBELI" : offer.getType() == OfferType.SELL ? "§6JUAL" : "§bTUKAR";
        return action + " §r" + offer.getItem().getAmount() + "x " + plain(ItemUtil.displayName(offer.getItem()))
                + (offer.getType() == OfferType.TRADE ? "\nLihat barang yang dibutuhkan" : "\n" + (offer.getType() == OfferType.BUY ? "Bayar " : "Terima ") + plugin.getVault().format(offer.getPrice()));
    }

    @SuppressWarnings("deprecation")
    private static String icon(ATradeNPC plugin, Offer offer) {
        var item = offer.getItem();
        var meta = item.getItemMeta();
        if (plugin.getConfig().getBoolean("bedrock-forms.custom-icons-enabled", false) && meta != null && meta.hasCustomModelData()) {
            String custom = plugin.getConfig().getString("bedrock-forms.icons." + item.getType().name() + "-" + meta.getCustomModelData());
            if (custom != null && !custom.isBlank()) return custom;
        }
        return switch (item.getType()) {
            case COD -> "textures/items/fish_raw";
            case SALMON -> "textures/items/fish_salmon_raw";
            case TROPICAL_FISH -> "textures/items/fish_clownfish_raw";
            case PUFFERFISH -> "textures/items/fish_pufferfish_raw";
            case FISHING_ROD -> "textures/items/fishing_rod_uncast";
            default -> "textures/items/emerald";
        };
    }

    private static String plain(String text) { return Text.plain(Text.parse(text)); }

    private static boolean send(ATradeNPC plugin, Player player, String title, String content,
                                List<String> buttons, List<String> images, IntConsumer action) {
        try {
            Class<?> simple = Class.forName("org.geysermc.cumulus.form.SimpleForm");
            Object builder = simple.getMethod("builder").invoke(null);
            var type = Class.forName("org.geysermc.cumulus.form.SimpleForm$Builder");
            type.getMethod("title", String.class).invoke(builder, title);
            type.getMethod("content", String.class).invoke(builder, content);
            Class<?> imageType = Class.forName("org.geysermc.cumulus.util.FormImage$Type");
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object path = Enum.valueOf((Class) imageType, "PATH");
            for (int i = 0; i < buttons.size(); i++) {
                type.getMethod("button", String.class, imageType, String.class).invoke(builder, buttons.get(i), path, images.get(i));
            }
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
