package io.github.AzmiiD.aTradeNpc.gui;

import io.github.AzmiiD.aTradeNpc.ATradeNPC;
import io.github.AzmiiD.aTradeNpc.npc.NpcManager;
import io.github.AzmiiD.aTradeNpc.shop.TradeShop;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Choose the NPC entity type (and villager profession). */
public class AppearanceMenu extends Menu {

    private static final int TYPES = 36, BACK = 53;
    private static final Map<String, Material> WORKSTATIONS = Map.ofEntries(
            Map.entry("armorer", Material.BLAST_FURNACE), Map.entry("butcher", Material.SMOKER),
            Map.entry("cartographer", Material.CARTOGRAPHY_TABLE), Map.entry("cleric", Material.BREWING_STAND),
            Map.entry("farmer", Material.COMPOSTER), Map.entry("fisherman", Material.BARREL),
            Map.entry("fletcher", Material.FLETCHING_TABLE), Map.entry("leatherworker", Material.CAULDRON),
            Map.entry("librarian", Material.LECTERN), Map.entry("mason", Material.STONECUTTER),
            Map.entry("nitwit", Material.DEAD_BUSH), Map.entry("shepherd", Material.LOOM),
            Map.entry("toolsmith", Material.SMITHING_TABLE), Map.entry("weaponsmith", Material.GRINDSTONE),
            Map.entry("none", Material.BARRIER));

    private final TradeShop shop;
    private final Map<Integer, EntityType> typeSlots = new HashMap<>();
    private final Map<Integer, Villager.Profession> professionSlots = new HashMap<>();

    public AppearanceMenu(ATradeNPC plugin, Player player, TradeShop shop) {
        super(plugin, player);
        this.shop = shop;
    }

    @Override
    protected String title() {
        return plugin.getLang().format("gui.appearance-title");
    }

    @Override
    protected int size() {
        return 54;
    }

    private boolean hasProfession() {
        return shop.getEntityType() == EntityType.VILLAGER || shop.getEntityType() == EntityType.ZOMBIE_VILLAGER;
    }

    @Override
    protected void render() {
        typeSlots.clear();
        professionSlots.clear();
        List<EntityType> types = plugin.getSettings().allowedEntityTypes;
        for (int i = 0; i < types.size() && i < TYPES; i++) {
            EntityType type = types.get(i);
            Material egg = Material.matchMaterial(type.name() + "_SPAWN_EGG");
            boolean current = type == shop.getEntityType();
            inventory.setItem(i, Items.button(egg == null ? Material.NAME_TAG : egg,
                    (current ? "<green>» " : "<#0BA6DF>") + capitalize(type.name().toLowerCase(java.util.Locale.ROOT)),
                    current ? "<green>Current" : "<gray>Click to use"));
            typeSlots.put(i, type);
        }
        if (hasProfession()) {
            Villager.Profession current = NpcManager.profession(shop.getProfession());
            int slot = 36;
            for (Villager.Profession p : Registry.VILLAGER_PROFESSION) {
                if (slot > 52) {
                    break;
                }
                String key = p.getKey().getKey();
                Material icon = WORKSTATIONS.getOrDefault(key, Material.PAPER);
                boolean sel = p.equals(current);
                inventory.setItem(slot, Items.button(icon, (sel ? "<green>» " : "<#5AD1FF>") + capitalize(key),
                        sel ? "<green>Current profession" : "<gray>Click to set profession"));
                professionSlots.put(slot, p);
                slot++;
            }
        }
        inventory.setItem(BACK, Items.button(Material.ARROW, "<#0BA6DF>Back"));
        fill(36, 53, Items.filler(Material.LIGHT_BLUE_STAINED_GLASS_PANE));
    }

    private static String capitalize(String s) {
        StringBuilder sb = new StringBuilder();
        for (String w : s.replace('_', ' ').split(" ")) {
            if (!w.isEmpty()) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
        }
        return sb.toString();
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (shop.isRemoved() || !shop.canEdit(player)) {
            closeLater();
            return;
        }
        int slot = event.getRawSlot();
        if (slot == BACK) {
            switchTo(new EditorMenu(plugin, player, shop));
            return;
        }
        EntityType type = typeSlots.get(slot);
        if (type != null && type != shop.getEntityType()) {
            plugin.getShopService().setEntityType(shop, type);
            refresh();
            return;
        }
        Villager.Profession profession = professionSlots.get(slot);
        if (profession != null) {
            shop.setProfession(profession.getKey().toString());
            plugin.getNpcManager().refresh(shop);
            plugin.getShopManager().save();
            refresh();
        }
    }
}
