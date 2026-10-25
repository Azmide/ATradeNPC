package io.github.AzmiiD.aTradeNpc.shop;

import io.github.AzmiiD.aTradeNpc.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A shop NPC with its offers. Locations are kept as world name + coordinates so unloaded worlds are fine. */
public final class TradeShop {

    private final int id;
    private final ShopType type;
    private UUID owner;
    private String ownerName;
    private String name;
    private EntityType entityType;
    private String profession;

    private String world;
    private double x, y, z;
    private float yaw, pitch;

    // Storage container (player shops)
    private String chestWorld;
    private int chestX, chestY, chestZ;

    private final List<Offer> offers = new ArrayList<>();

    /** The currently spawned entity (not saved). */
    private transient Entity entity;
    private transient boolean removed;
    private transient int brokenOffers;

    public TradeShop(int id, ShopType type) {
        this.id = id;
        this.type = type;
    }

    public Location getLocation() {
        World w = world == null ? null : Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }

    public void setLocation(Location loc) {
        this.world = loc.getWorld().getName();
        this.x = loc.getX();
        this.y = loc.getY();
        this.z = loc.getZ();
        this.yaw = loc.getYaw();
        this.pitch = loc.getPitch();
    }

    public String getWorldName() {
        return world;
    }

    public int getChunkX() {
        return (int) Math.floor(x) >> 4;
    }

    public int getChunkZ() {
        return (int) Math.floor(z) >> 4;
    }

    public Block getChestBlock() {
        if (chestWorld == null) {
            return null;
        }
        World w = Bukkit.getWorld(chestWorld);
        return w == null ? null : w.getBlockAt(chestX, chestY, chestZ);
    }

    public void setChest(Block block) {
        this.chestWorld = block.getWorld().getName();
        this.chestX = block.getX();
        this.chestY = block.getY();
        this.chestZ = block.getZ();
    }

    /** "world;x;y;z" of the storage container, null when there is none. Works while the world is unloaded. */
    public String getChestKey() {
        return chestWorld == null ? null : chestWorld + ';' + chestX + ';' + chestY + ';' + chestZ;
    }

    public boolean isChestAt(Block block) {
        return chestWorld != null && block.getX() == chestX && block.getY() == chestY && block.getZ() == chestZ
                && block.getWorld().getName().equals(chestWorld);
    }

    public boolean isOwner(Player player) {
        return owner != null && owner.equals(player.getUniqueId());
    }

    /** Owner of a player shop, or anyone with the admin permission. */
    public boolean canEdit(Player player) {
        if (player.hasPermission("atradenpc.admin")) {
            return true;
        }
        return type == ShopType.PLAYER ? isOwner(player) : player.hasPermission("atradenpc.create.admin");
    }

    public void save(ConfigurationSection s) {
        s.set("type", type.name());
        s.set("owner", owner == null ? null : owner.toString());
        s.set("owner-name", ownerName);
        s.set("name", name);
        s.set("entity", entityType.name());
        s.set("profession", profession);
        s.set("location.world", world);
        s.set("location.x", x);
        s.set("location.y", y);
        s.set("location.z", z);
        s.set("location.yaw", yaw);
        s.set("location.pitch", pitch);
        if (chestWorld != null) {
            s.set("chest.world", chestWorld);
            s.set("chest.x", chestX);
            s.set("chest.y", chestY);
            s.set("chest.z", chestZ);
        }
        for (int i = 0; i < offers.size(); i++) {
            offers.get(i).save(s.createSection("offers." + i));
        }
    }

    public static TradeShop load(int id, ConfigurationSection s) {
        TradeShop shop = new TradeShop(id, ShopType.valueOf(s.getString("type", "ADMIN")));
        String owner = s.getString("owner");
        shop.owner = owner == null ? null : UUID.fromString(owner);
        shop.ownerName = s.getString("owner-name");
        shop.name = s.getString("name", "Shop");
        EntityType type = Settings.parseEntityType(s.getString("entity"));
        shop.entityType = type == null ? EntityType.VILLAGER : type;
        shop.profession = s.getString("profession");
        shop.world = s.getString("location.world");
        shop.x = s.getDouble("location.x");
        shop.y = s.getDouble("location.y");
        shop.z = s.getDouble("location.z");
        shop.yaw = (float) s.getDouble("location.yaw");
        shop.pitch = (float) s.getDouble("location.pitch");
        if (s.isConfigurationSection("chest")) {
            shop.chestWorld = s.getString("chest.world");
            shop.chestX = s.getInt("chest.x");
            shop.chestY = s.getInt("chest.y");
            shop.chestZ = s.getInt("chest.z");
        }
        ConfigurationSection offers = s.getConfigurationSection("offers");
        if (offers != null) {
            for (String key : offers.getKeys(false)) {
                ConfigurationSection o = offers.getConfigurationSection(key);
                if (o == null) {
                    continue;
                }
                // One broken offer (e.g. an item from a removed plugin) must not take the whole shop down.
                try {
                    Offer offer = Offer.load(o);
                    offer.normalize();
                    if (offer.validate() == null) {
                        shop.offers.add(offer);
                    } else {
                        shop.brokenOffers++;
                    }
                } catch (Exception e) {
                    shop.brokenOffers++;
                }
            }
        }
        return shop;
    }

    /** Offers that could not be loaded (not saved again). */
    public int getBrokenOffers() {
        return brokenOffers;
    }

    public int getId() {
        return id;
    }

    public ShopType getType() {
        return type;
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner, String ownerName) {
        this.owner = owner;
        this.ownerName = ownerName;
    }

    public String getOwnerName() {
        return ownerName == null ? "?" : ownerName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(EntityType entityType) {
        this.entityType = entityType;
    }

    public String getProfession() {
        return profession;
    }

    public void setProfession(String profession) {
        this.profession = profession;
    }

    public List<Offer> getOffers() {
        return offers;
    }

    public Entity getEntity() {
        return entity;
    }

    public void setEntity(Entity entity) {
        this.entity = entity;
    }

    public boolean isRemoved() {
        return removed;
    }

    public void setRemoved(boolean removed) {
        this.removed = removed;
    }
}
