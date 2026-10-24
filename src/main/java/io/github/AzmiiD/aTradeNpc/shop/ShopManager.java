package io.github.AzmiiD.aTradeNpc.shop;

import io.github.AzmiiD.aTradeNpc.ATradeNPC;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.data.type.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/** All shops, their storage file (data/shops.yml) and lookups by entity, chunk and container. */
public class ShopManager {

    private final ATradeNPC plugin;
    private final File file;
    private final Map<Integer, TradeShop> shops = new LinkedHashMap<>();
    /** world -> chunk key -> shops whose NPC stands in that chunk. */
    private final Map<String, Map<Long, List<TradeShop>>> byChunk = new HashMap<>();
    /** "world;x;y;z" of storage containers -> shop. */
    private final Map<String, TradeShop> byContainer = new HashMap<>();
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ATradeNPC-save");
        t.setDaemon(true);
        return t;
    });
    private int nextId = 1;

    public ShopManager(ATradeNPC plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/shops.yml");
    }

    public void load() {
        shops.clear();
        byChunk.clear();
        byContainer.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        nextId = Math.max(1, yaml.getInt("next-id", 1));
        ConfigurationSection section = yaml.getConfigurationSection("shops");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    int id = Integer.parseInt(key);
                    TradeShop shop = TradeShop.load(id, section.getConfigurationSection(key));
                    shops.put(id, shop);
                    index(shop);
                    indexContainer(shop);
                    nextId = Math.max(nextId, id + 1);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Could not load shop " + key, e);
                }
            }
        }
    }

    /** Saves asynchronously (the YAML is built on the calling thread). */
    public void save() {
        String data = serialize();
        saveExecutor.execute(() -> write(data));
    }

    /** Waits for pending async saves, then saves synchronously (plugin disable). */
    public void saveNow() {
        String data = serialize();
        saveExecutor.shutdown();
        try {
            saveExecutor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        write(data);
    }

    private String serialize() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("next-id", nextId);
        for (TradeShop shop : shops.values()) {
            shop.save(yaml.createSection("shops." + shop.getId()));
        }
        return yaml.saveToString();
    }

    private void write(String data) {
        try {
            File dir = file.getParentFile();
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("Could not create " + dir);
            }
            File tmp = new File(dir, file.getName() + ".tmp");
            Files.writeString(tmp.toPath(), data, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save shops", e);
        }
    }

    public TradeShop create(ShopType type) {
        TradeShop shop = new TradeShop(nextId++, type);
        return shop;
    }

    /** Registers a fully set up shop (location must be set). */
    public void add(TradeShop shop) {
        shops.put(shop.getId(), shop);
        index(shop);
        indexContainer(shop);
        save();
    }

    public void remove(TradeShop shop) {
        shop.setRemoved(true);
        shops.remove(shop.getId());
        unindex(shop);
        Block chest = shop.getChestBlock();
        if (chest != null) {
            byContainer.remove(key(chest));
        }
        save();
    }

    /** Call before changing a shop's location, then {@link #reindex(TradeShop)} after. */
    public void unindex(TradeShop shop) {
        Map<Long, List<TradeShop>> world = byChunk.get(shop.getWorldName());
        if (world == null) {
            return;
        }
        long key = Chunk.getChunkKey(shop.getChunkX(), shop.getChunkZ());
        List<TradeShop> list = world.get(key);
        if (list != null) {
            list.remove(shop);
            if (list.isEmpty()) {
                world.remove(key);
            }
        }
    }

    public void reindex(TradeShop shop) {
        index(shop);
    }

    private void index(TradeShop shop) {
        if (shop.getWorldName() == null) {
            return;
        }
        byChunk.computeIfAbsent(shop.getWorldName(), w -> new HashMap<>())
                .computeIfAbsent(Chunk.getChunkKey(shop.getChunkX(), shop.getChunkZ()), k -> new ArrayList<>())
                .add(shop);
    }

    private void indexContainer(TradeShop shop) {
        Block chest = shop.getType() == ShopType.PLAYER ? shop.getChestBlock() : null;
        if (chest != null) {
            byContainer.put(key(chest), shop);
        }
    }

    private static String key(Block block) {
        return block.getWorld().getName() + ';' + block.getX() + ';' + block.getY() + ';' + block.getZ();
    }

    public List<TradeShop> getShopsInChunk(Chunk chunk) {
        Map<Long, List<TradeShop>> world = byChunk.get(chunk.getWorld().getName());
        if (world == null) {
            return Collections.emptyList();
        }
        List<TradeShop> list = world.get(chunk.getChunkKey());
        return list == null ? Collections.emptyList() : new ArrayList<>(list);
    }

    public TradeShop get(int id) {
        return shops.get(id);
    }

    public Collection<TradeShop> getShops() {
        return Collections.unmodifiableCollection(shops.values());
    }

    public TradeShop getByEntity(Entity entity) {
        Integer id = entity.getPersistentDataContainer().get(plugin.getShopKey(), PersistentDataType.INTEGER);
        return id == null ? null : shops.get(id);
    }

    /** The shop using this container block (either half of a double chest). */
    public TradeShop getByContainer(Block block) {
        if (byContainer.isEmpty()) {
            return null;
        }
        TradeShop shop = byContainer.get(key(block));
        if (shop != null) {
            return shop;
        }
        if (block.getBlockData() instanceof Chest chest && chest.getType() != Chest.Type.SINGLE
                && block.getState(false) instanceof org.bukkit.block.Chest state
                && state.getInventory().getHolder(false) instanceof DoubleChest dc) {
            for (InventoryHolder side : new InventoryHolder[]{dc.getLeftSide(false), dc.getRightSide(false)}) {
                if (side instanceof org.bukkit.block.Chest c && !c.getBlock().equals(block)) {
                    return byContainer.get(key(c.getBlock()));
                }
            }
        }
        return null;
    }

    public List<TradeShop> getOwnedBy(UUID owner) {
        List<TradeShop> out = new ArrayList<>();
        for (TradeShop shop : shops.values()) {
            if (owner.equals(shop.getOwner())) {
                out.add(shop);
            }
        }
        return out;
    }
}
