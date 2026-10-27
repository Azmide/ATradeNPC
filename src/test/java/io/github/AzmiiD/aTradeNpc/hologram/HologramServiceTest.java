package io.github.AzmiiD.aTradeNpc.hologram;

import io.github.AzmiiD.aTradeNpc.shop.ShopType;
import io.github.AzmiiD.aTradeNpc.shop.TradeShop;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class HologramServiceTest {
    private static final World WORLD = world("world");
    private static final World OTHER_WORLD = world("other");
    private final List<TradeShop> shops = new ArrayList<>();
    private final FakeBridge bridge = new FakeBridge();
    private final AtomicInteger saves = new AtomicInteger();
    private boolean available = true;
    private HologramService service;
    private TradeShop shop;

    @BeforeAll
    static void server() throws Exception {
        // Only world lookup is needed. No Minecraft server, item factory or provider plugin is mocked.
        Server server = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWorld" -> "other".equals(args[0]) ? OTHER_WORLD : WORLD;
                    case "getLogger" -> Logger.getLogger("HologramServiceTest");
                    case "getName", "getVersion", "getBukkitVersion" -> "test";
                    default -> null;
                });
        // Bukkit.setServer also loads a server-build ServiceLoader provider,
        // which is deliberately absent from this API-only unit test classpath.
        var field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        field.set(null, server);
    }

    @AfterAll
    static void clearServer() throws Exception {
        var field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        field.set(null, null);
    }

    @BeforeEach
    void setup() {
        shop = shop(1);
        shops.add(shop);
        bridge.entries.put("shop_holo", new HologramBridge.Entry("shop_holo", new Location(WORLD, 11, 67, 22), true, false));
        service = new HologramService(() -> shops, saves::incrementAndGet, ignored -> {}, () -> available ? bridge : null);
    }

    @Test
    void linksPersistAndOldShopsRemainCompatible() throws Exception {
        assertNull(service.link(shop, "shop_holo"));
        assertEquals(0, bridge.moves);
        YamlConfiguration yaml = new YamlConfiguration();
        shop.save(yaml);
        YamlConfiguration reloaded = new YamlConfiguration();
        reloaded.loadFromString(yaml.saveToString());
        TradeShop loaded = TradeShop.load(1, reloaded);
        assertEquals("shop_holo", loaded.getHologramName());
        assertPosition(loaded.getHologramLocation(), WORLD, 11, 67, 22);
        reloaded.set("hologram", null);
        assertNull(TradeShop.load(2, reloaded).getHologramName());
    }

    @Test
    void movingFollowsNpcAndPreservesManualPositionEdits() {
        assertNull(service.link(shop, "shop_holo"));
        bridge.entries.put("shop_holo", new HologramBridge.Entry("shop_holo", new Location(WORLD, 12, 69, 23), true, false));
        service.checkAll();
        assertEquals(0, bridge.moves, "Periodic checks must not overwrite /hologram position edits");
        service.beforeMove(shop);
        shop.setLocation(new Location(WORLD, 100, 70, 200));
        service.afterMove(shop);
        assertPosition(bridge.entries.get("shop_holo").location(), WORLD, 102, 75, 203);
        assertFalse(shop.isHologramMovePending());
    }

    @Test
    void movesAcrossWorlds() {
        assertNull(service.link(shop, "shop_holo"));
        service.beforeMove(shop);
        shop.setLocation(new Location(OTHER_WORLD, 0, 80, 0));
        service.afterMove(shop);
        assertPosition(bridge.entries.get("shop_holo").location(), OTHER_WORLD, 1, 83, 2);
    }

    @Test
    void unavailableProviderDefersMultipleMovesAndRecoversAfterRestart() throws Exception {
        assertNull(service.link(shop, "shop_holo"));
        available = false;
        service.beforeMove(shop);
        shop.setLocation(new Location(WORLD, 100, 70, 200));
        service.afterMove(shop);
        service.beforeMove(shop);
        shop.setLocation(new Location(WORLD, 200, 80, 300));
        service.afterMove(shop);
        assertTrue(shop.isHologramMovePending());
        YamlConfiguration yaml = new YamlConfiguration();
        shop.save(yaml);
        shops.set(0, TradeShop.load(1, yaml));
        available = true;
        service.checkAll();
        assertPosition(bridge.entries.get("shop_holo").location(), WORLD, 201, 83, 302);
        assertFalse(shops.getFirst().isHologramMovePending());
        assertEquals(1, bridge.moves);
    }

    @Test
    void unlinkWorksWithoutProviderAndKeepsOriginalHologram() {
        assertNull(service.link(shop, "shop_holo"));
        available = false;
        service.unlink(shop);
        assertNull(shop.getHologramName());
        assertNotNull(bridge.entries.get("shop_holo"));
        assertFalse(service.hasHologram(shop));
    }

    @Test
    void failedProviderMoveIsRetriedWithoutLosingOffset() {
        assertNull(service.link(shop, "shop_holo"));
        service.beforeMove(shop);
        shop.setLocation(new Location(WORLD, 100, 70, 200));
        bridge.failMove = true;
        service.afterMove(shop);
        assertTrue(shop.isHologramMovePending());
        bridge.failMove = false;
        service.checkAll();
        assertPosition(bridge.entries.get("shop_holo").location(), WORLD, 101, 73, 202);
        assertFalse(shop.isHologramMovePending());
    }

    @Test
    void rejectsMissingDuplicateWrongWorldAndFancyNpcLinks() {
        assertEquals("hologramNotFound", service.link(shop, "missing"));
        assertNull(service.link(shop, "shop_holo"));
        TradeShop other = shop(2);
        shops.add(other);
        assertEquals("hologramAlreadyLinked", service.link(other, "shop_holo"));
        bridge.entries.put("bad", new HologramBridge.Entry("bad", new Location(OTHER_WORLD, 0, 0, 0), true, false));
        assertEquals("hologramWrongWorld", service.link(other, "bad"));
        bridge.entries.put("bad", new HologramBridge.Entry("bad", new Location(WORLD, 0, 0, 0), true, true));
        assertEquals("hologramConflict", service.link(other, "bad"));
        bridge.entries.put("bad", new HologramBridge.Entry("bad", new Location(WORLD, 0, 0, 0), false, false));
        assertEquals("hologramConflict", service.link(other, "bad"));
        available = false;
        assertEquals("hologramUnavailable", service.link(other, "shop_holo"));
        assertTrue(service.names().isEmpty());
    }

    @Test
    void removedOrReloadingHologramsRestoreNameFallbackWithoutDroppingLink() {
        assertNull(service.link(shop, "shop_holo"));
        assertTrue(service.hasHologram(shop));
        var entry = bridge.entries.remove("shop_holo");
        service.checkAll();
        assertFalse(service.hasHologram(shop));
        assertEquals("shop_holo", shop.getHologramName());
        bridge.entries.put("shop_holo", entry);
        assertTrue(service.hasHologram(shop));
    }

    private static TradeShop shop(int id) {
        TradeShop shop = new TradeShop(id, ShopType.ADMIN);
        shop.setEntityType(EntityType.VILLAGER);
        shop.setName("Shop");
        shop.setLocation(new Location(WORLD, 10, 64, 20));
        return shop;
    }

    private static World world(String name) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName", "toString" -> name;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> null;
                });
    }

    private static void assertPosition(Location actual, World world, double x, double y, double z) {
        assertSame(world, actual.getWorld());
        assertEquals(x, actual.getX(), 0.00001);
        assertEquals(y, actual.getY(), 0.00001);
        assertEquals(z, actual.getZ(), 0.00001);
    }

    private static final class FakeBridge implements HologramBridge {
        private final Map<String, Entry> entries = new HashMap<>();
        private int moves;
        private boolean failMove;

        public Entry find(String name) { return entries.get(name); }
        public List<String> names() { return new ArrayList<>(entries.keySet()); }
        public boolean move(String name, Location location) {
            if (failMove) {
                return false;
            }
            Entry before = entries.get(name);
            entries.put(name, new Entry(name, location.clone(), before.persistent(), before.linkedToFancyNpc()));
            moves++;
            return true;
        }
    }
}
