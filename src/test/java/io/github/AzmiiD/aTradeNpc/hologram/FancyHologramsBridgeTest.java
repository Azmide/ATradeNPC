package io.github.AzmiiD.aTradeNpc.hologram;

import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.HologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import de.oliver.fancyholograms.api.hologram.HologramType;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class FancyHologramsBridgeTest {
    @Test
    void moveUpdatesProviderAndSendsPositionToExistingViewersBeforeSaving() {
        var hologram = new RecordingHologram(new HologramData("rodshop", HologramType.TEXT,
                new Location(null, 500, 64, 300, 75, 12)));
        AtomicInteger saves = new AtomicInteger();
        HologramManager manager = (HologramManager) Proxy.newProxyInstance(HologramManager.class.getClassLoader(),
                new Class<?>[]{HologramManager.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "isLoaded" -> true;
                    case "getHologram" -> Optional.of(hologram);
                    case "saveHolograms" -> {
                        assertEquals(1, hologram.updates);
                        assertEquals(1, hologram.viewerRefreshes);
                        saves.incrementAndGet();
                        yield null;
                    }
                    default -> null;
                });
        var bridge = new FancyHologramsBridge(Logger.getLogger("FancyHologramsBridgeTest"), () -> manager);
        assertTrue(bridge.move("rodshop", new Location(null, 10, 66.5, 20, 0, 0)));
        Location actual = hologram.getData().getLocation();
        assertEquals(10, actual.getX());
        assertEquals(66.5, actual.getY());
        assertEquals(20, actual.getZ());
        assertEquals(75, actual.getYaw());
        assertEquals(12, actual.getPitch());
        assertEquals(1, saves.get());
        assertTrue(hologram.getData().hasChanges());
    }

    private static final class RecordingHologram extends Hologram {
        private int updates;
        private int viewerRefreshes;

        RecordingHologram(HologramData data) { super(data); }
        public int getEntityId() { return 1; }
        public Display getDisplayEntity() { return null; }
        protected void create() { }
        protected void delete() { }
        protected void update() { updates++; }
        protected boolean show(Player player) { return true; }
        protected boolean hide(Player player) { return true; }
        protected void refresh(Player player) { }
        public void refreshForViewersInWorld() { viewerRefreshes++; }
    }
}
