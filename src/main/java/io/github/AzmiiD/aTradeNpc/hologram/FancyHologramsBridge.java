package io.github.AzmiiD.aTradeNpc.hologram;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import org.bukkit.Location;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

/** Loaded only when FancyHolograms is installed and enabled. */
public final class FancyHologramsBridge implements HologramBridge {
    private final Logger logger;
    private boolean warned;
    private final Supplier<HologramManager> managers;

    public FancyHologramsBridge(Logger logger) {
        this(logger, () -> FancyHologramsPlugin.get().getHologramManager());
    }

    FancyHologramsBridge(Logger logger, Supplier<HologramManager> managers) {
        this.logger = logger;
        this.managers = managers;
        // Resolve/check the API while the optional-provider factory can still catch linkage errors.
        manager().isLoaded();
    }

    private HologramManager manager() {
        return managers.get();
    }

    @Override
    public Entry find(String name) {
        return access(manager -> manager.getHologram(name).map(hologram -> {
            var data = hologram.getData();
            String npc = data.getLinkedNpcName();
            return new Entry(hologram.getName(), data.getLocation(), data.isPersistent(),
                    npc != null && !npc.isBlank());
        }).orElse(null), null);
    }

    @Override
    public List<String> names() {
        return access(manager -> manager.getPersistentHolograms().stream()
                .map(hologram -> hologram.getName()).sorted().toList(), List.of());
    }

    @Override
    public boolean move(String name, Location location) {
        return access(manager -> {
            var hologram = manager.getHologram(name).orElseThrow();
            // Preserve the orientation and all appearance/visibility properties edited in FancyHolograms.
            Location target = location.clone();
            Location previous = hologram.getData().getLocation();
            target.setYaw(previous.getYaw());
            target.setPitch(previous.getPitch());
            hologram.getData().setLocation(target);
            hologram.forceUpdate();
            // forceUpdate changes provider state; explicitly send the teleport to current viewers.
            hologram.refreshForViewersInWorld();
            hologram.queueUpdate();
            manager.saveHolograms();
            return true;
        }, false);
    }

    private <T> T access(Function<HologramManager, T> operation, T fallback) {
        try {
            HologramManager manager = manager();
            return manager.isLoaded() ? operation.apply(manager) : fallback;
        } catch (RuntimeException | LinkageError error) {
            if (!warned) {
                logger.warning("FancyHolograms API could not be accessed; using NPC name tags until it is available: " + error);
                warned = true;
            }
            return fallback;
        }
    }
}
