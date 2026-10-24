package io.github.AzmiiD.aTradeNpc;

import io.github.AzmiiD.aTradeNpc.command.ATradeCommand;
import io.github.AzmiiD.aTradeNpc.config.Lang;
import io.github.AzmiiD.aTradeNpc.config.Settings;
import io.github.AzmiiD.aTradeNpc.economy.VaultHook;
import io.github.AzmiiD.aTradeNpc.gui.Menu;
import io.github.AzmiiD.aTradeNpc.gui.MenuListener;
import io.github.AzmiiD.aTradeNpc.gui.TradeScreen;
import io.github.AzmiiD.aTradeNpc.listener.ChestListener;
import io.github.AzmiiD.aTradeNpc.listener.NpcListener;
import io.github.AzmiiD.aTradeNpc.npc.NpcManager;
import io.github.AzmiiD.aTradeNpc.prompt.ChatPrompt;
import io.github.AzmiiD.aTradeNpc.shop.ShopManager;
import io.github.AzmiiD.aTradeNpc.shop.ShopService;
import io.github.AzmiiD.aTradeNpc.shop.TransactionService;
import io.github.AzmiiD.aTradeNpc.util.Sounds;
import io.github.AzmiiD.aTradeNpc.util.TransactionLogger;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Map;

/** Shopkeeper NPCs that trade any item and buy/sell items for Vault money. */
public final class ATradeNPC extends JavaPlugin {

    private static ATradeNPC instance;

    private Settings settings;
    private final Lang lang = new Lang();
    private final VaultHook vault = new VaultHook();
    private final Sounds sounds = new Sounds();
    private TransactionLogger transactionLogger;
    private NamespacedKey shopKey;
    private ShopManager shopManager;
    private NpcManager npcManager;
    private ShopService shopService;
    private TransactionService transactions;
    private ChatPrompt prompt;
    private TradeScreen tradeScreen;

    @Override
    public void onEnable() {
        instance = this;
        shopKey = new NamespacedKey(this, "shop");
        transactionLogger = new TransactionLogger(getLogger(), getDataFolder());
        shopManager = new ShopManager(this);
        npcManager = new NpcManager(this);
        shopService = new ShopService(this);
        transactions = new TransactionService(this);
        prompt = new ChatPrompt(this);
        tradeScreen = new TradeScreen(this);

        registerPermissions();
        loadConfigs();
        shopManager.load();

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new MenuListener(this), this);
        pm.registerEvents(new NpcListener(this), this);
        pm.registerEvents(new ChestListener(this), this);
        pm.registerEvents(prompt, this);
        pm.registerEvents(tradeScreen, this);
        registerCommands();

        npcManager.spawnAllLoaded();
        npcManager.start(settings.npcCheckInterval);
        getLogger().info("Loaded " + shopManager.getShops().size() + " shop(s)");

        // Economy plugins may register with Vault after us, so check once the server is running.
        Bukkit.getScheduler().runTask(this, () -> {
            if (!vault.isAvailable()) {
                getLogger().warning("No Vault economy found: Buy/Sell (money) offers are disabled, item trades still work.");
            }
        });
    }

    @Override
    public void onDisable() {
        if (tradeScreen != null) {
            tradeScreen.closeAll();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu) {
                player.closeInventory();
            }
        }
        if (npcManager != null) {
            npcManager.stop();
            npcManager.despawnAll();
        }
        if (shopManager != null) {
            shopManager.saveNow();
        }
        if (transactionLogger != null) {
            transactionLogger.shutdown();
        }
        instance = null;
    }

    /** Paper plugins have no commands section: commands are registered through the lifecycle API. */
    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register("atrade", "ATradeNPC shopkeepers", List.of("atradenpc", "tradenpc"), new ATradeCommand(this)));
    }

    /** Paper plugins can't declare permissions in YAML, so they are registered here. */
    private void registerPermissions() {
        PluginManager pm = getServer().getPluginManager();
        Object[][] nodes = {
                {"atradenpc.use", PermissionDefault.TRUE, "Trade with shop NPCs"},
                {"atradenpc.create.player", PermissionDefault.TRUE, "Create your own player shops"},
                {"atradenpc.create.admin", PermissionDefault.OP, "Create and edit admin shops (infinite stock)"},
                {"atradenpc.open", PermissionDefault.OP, "Use /atrade open"},
                {"atradenpc.limit.bypass", PermissionDefault.OP, "No limit on player shops"},
        };
        for (Object[] n : nodes) {
            addPermission(pm, new Permission((String) n[0], (String) n[2], (PermissionDefault) n[1]));
        }
        addPermission(pm, new Permission("atradenpc.admin", "Edit/remove every shop, bypass chest protection, reload",
                PermissionDefault.OP, Map.of("atradenpc.create.admin", true, "atradenpc.open", true, "atradenpc.limit.bypass", true)));
    }

    private static void addPermission(PluginManager pm, Permission permission) {
        if (pm.getPermission(permission.getName()) == null) {
            pm.addPermission(permission);
        }
    }

    private void loadConfigs() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "lang.yml").exists()) {
            saveResource("lang.yml", false);
        }
        reloadConfig();
        settings = new Settings(getConfig(), getLogger());
        lang.load(new File(getDataFolder(), "lang.yml"), getResource("lang.yml"));
        vault.setCurrencyFormat(settings.currencyFormat);
        sounds.load(getConfig().getConfigurationSection("sounds"));
        transactionLogger.setEnabled(getConfig().getBoolean("log-transactions", true));
    }

    /** Reloads config + messages and respawns every NPC (shops are kept in memory). */
    public void reload() {
        loadConfigs();
        npcManager.despawnAll();
        npcManager.spawnAllLoaded();
        npcManager.start(settings.npcCheckInterval);
    }

    public static ATradeNPC getInstance() {
        return instance;
    }

    public Settings getSettings() {
        return settings;
    }

    public Lang getLang() {
        return lang;
    }

    public VaultHook getVault() {
        return vault;
    }

    public Sounds getSounds() {
        return sounds;
    }

    public TransactionLogger getTransactionLogger() {
        return transactionLogger;
    }

    public NamespacedKey getShopKey() {
        return shopKey;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public NpcManager getNpcManager() {
        return npcManager;
    }

    public ShopService getShopService() {
        return shopService;
    }

    public TransactionService getTransactions() {
        return transactions;
    }

    public TradeScreen getTradeScreen() {
        return tradeScreen;
    }

    public ChatPrompt getPrompt() {
        return prompt;
    }
}
