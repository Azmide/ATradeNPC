package io.github.AzmiiD.aTradeNpc.hologram;

import io.github.AzmiiD.aTradeNpc.ATradeNPC;
import io.github.AzmiiD.aTradeNpc.shop.TradeShop;
import org.bukkit.Location;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.DoubleSupplier;

/** Links existing, externally edited holograms to stable shop ids. Never owns/deletes their text or data. */
public final class HologramService {
    private final Supplier<Collection<TradeShop>> shops;
    private final Runnable save;
    private final Consumer<TradeShop> refresh;
    private final Supplier<HologramBridge> provider;
    private final DoubleSupplier linkHeight;

    public HologramService(ATradeNPC plugin) {
        this(() -> plugin.getShopManager().getShops(), () -> plugin.getShopManager().save(),
                shop -> plugin.getNpcManager().refresh(shop), new Supplier<>() {
                    private boolean incompatible;
                    private HologramBridge bridge;

                    @Override
                    public HologramBridge get() {
                        if (incompatible || !plugin.getServer().getPluginManager().isPluginEnabled("FancyHolograms")) {
                            return null;
                        }
                        try {
                            if (bridge == null) {
                                bridge = new FancyHologramsBridge(plugin.getLogger());
                            }
                            return bridge;
                        } catch (RuntimeException | LinkageError error) {
                            incompatible = true;
                            plugin.getLogger().warning("FancyHolograms API is incompatible; hologram linking is disabled.");
                            return null;
                        }
                    }
                }, () -> plugin.getSettings().hologramLinkHeight);
    }

    HologramService(Supplier<Collection<TradeShop>> shops, Runnable save,
                    Consumer<TradeShop> refresh, Supplier<HologramBridge> provider, DoubleSupplier linkHeight) {
        this.shops = shops;
        this.save = save;
        this.refresh = refresh;
        this.provider = provider;
        this.linkHeight = linkHeight;
    }

    public List<String> names() {
        HologramBridge bridge = provider.get();
        return bridge == null ? List.of() : bridge.names();
    }

    /** Null on success, otherwise a lang.yml message key. */
    public String link(TradeShop shop, String name) {
        HologramBridge bridge = provider.get();
        if (bridge == null) {
            return "hologramUnavailable";
        }
        HologramBridge.Entry entry = bridge.find(name);
        if (entry == null) {
            return "hologramNotFound";
        }
        if (!entry.persistent() || entry.linkedToFancyNpc()) {
            return "hologramConflict";
        }
        for (TradeShop other : shops.get()) {
            if (other != shop && entry.name().equalsIgnoreCase(other.getHologramName())) {
                return "hologramAlreadyLinked";
            }
        }
        Location anchor = shop.getLocation();
        Location location = entry.location();
        if (anchor == null || location == null || !anchor.getWorld().equals(location.getWorld())) {
            return "hologramWrongWorld";
        }
        // A link must visibly attach the hologram, rather than keeping an arbitrary
        // distance from where it was originally created. Relinking also repairs old links.
        double height = linkHeight.getAsDouble();
        Location target = anchor.clone().add(0, height, 0);
        if (!bridge.move(entry.name(), target)) {
            return "hologramMoveFailed";
        }
        shop.linkHologram(entry.name(), 0, height, 0);
        save.run();
        refresh.accept(shop);
        return null;
    }

    public void unlink(TradeShop shop) {
        shop.unlinkHologram();
        save.run();
        refresh.accept(shop);
    }

    public boolean hasHologram(TradeShop shop) {
        if (shop.getHologramName() == null) {
            return false;
        }
        HologramBridge bridge = provider.get();
        if (bridge == null) {
            return false;
        }
        HologramBridge.Entry entry = bridge.find(shop.getHologramName());
        return entry != null && !entry.linkedToFancyNpc();
    }

    /** Called before the shop location changes; captures manual /hologram position edits as the new offset. */
    public void beforeMove(TradeShop shop) {
        if (shop.getHologramName() == null || shop.isHologramMovePending()) {
            return;
        }
        HologramBridge bridge = provider.get();
        HologramBridge.Entry entry = bridge == null ? null : bridge.find(shop.getHologramName());
        Location anchor = shop.getLocation();
        if (entry != null && anchor != null && entry.location() != null
                && anchor.getWorld().equals(entry.location().getWorld())) {
            captureOffset(shop, entry, anchor);
        }
    }

    /** Called after moving or centering, before saving the shop. Missing providers are retried later. */
    public void afterMove(TradeShop shop) {
        if (shop.getHologramName() == null) {
            return;
        }
        shop.setHologramMovePending(true);
        applyPendingMove(shop);
    }

    /** Polls provider reload/removal and updates name tags. Does not reset positions edited in FancyHolograms. */
    public void checkAll() {
        for (TradeShop shop : shops.get()) {
            if (shop.getHologramName() != null) {
                if (applyPendingMove(shop)) {
                    save.run();
                }
                refresh.accept(shop);
            }
        }
    }

    private boolean applyPendingMove(TradeShop shop) {
        if (!shop.isHologramMovePending()) {
            return false;
        }
        HologramBridge bridge = provider.get();
        Location target = shop.getHologramLocation();
        HologramBridge.Entry entry = bridge == null ? null : bridge.find(shop.getHologramName());
        if (entry == null || entry.linkedToFancyNpc() || target == null) {
            return false;
        }
        if (!bridge.move(entry.name(), target)) {
            return false;
        }
        shop.setHologramMovePending(false);
        return true;
    }

    private static void captureOffset(TradeShop shop, HologramBridge.Entry entry, Location anchor) {
        Location location = entry.location();
        shop.linkHologram(entry.name(), location.getX() - anchor.getX(),
                location.getY() - anchor.getY(), location.getZ() - anchor.getZ());
    }
}
