package io.github.AzmiiD.aTradeNpc.command;

import io.github.AzmiiD.aTradeNpc.ATradeNPC;
import io.github.AzmiiD.aTradeNpc.config.Lang;
import io.github.AzmiiD.aTradeNpc.config.Settings;
import io.github.AzmiiD.aTradeNpc.gui.EditorMenu;
import io.github.AzmiiD.aTradeNpc.shop.ShopType;
import io.github.AzmiiD.aTradeNpc.shop.TradeShop;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /atrade &lt;create|edit|remove|rename|movehere|list|tp|open|reload|help&gt; */
public class ATradeCommand implements BasicCommand {

    private static final List<String> SUBS = List.of("create", "edit", "remove", "rename", "movehere", "center", "list", "tp", "open", "reload", "help");

    private final ATradeNPC plugin;

    public ATradeCommand(ATradeNPC plugin) {
        this.plugin = plugin;
    }

    private Lang lang() {
        return plugin.getLang();
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = CommandUtil.actor(stack);
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> create(sender, args);
            case "edit" -> edit(sender, args);
            case "remove", "delete" -> remove(sender, args);
            case "rename" -> rename(sender, args);
            case "movehere" -> moveHere(sender, args);
            case "center" -> center(sender, args);
            case "list" -> list(sender, args);
            case "tp" -> tp(sender, args);
            case "open" -> open(sender, args);
            case "reload" -> {
                if (!sender.hasPermission("atradenpc.admin")) {
                    lang().send(sender, "noPermission");
                    return;
                }
                plugin.reload();
                lang().send(sender, "reloaded");
            }
            default -> lang().sendList(sender, "help");
        }
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player p) {
            return p;
        }
        lang().send(sender, "playersOnly");
        return null;
    }

    // /atrade create <admin|player> [type] [name...]
    private void create(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return;
        }
        boolean admin = args.length > 1 && args[1].equalsIgnoreCase("admin");
        if (!player.hasPermission(admin ? "atradenpc.create.admin" : "atradenpc.create.player")) {
            lang().send(player, "noPermission");
            return;
        }
        Settings settings = plugin.getSettings();
        EntityType type = settings.defaultEntityType;
        int nameStart = 2;
        if (args.length > 2) {
            EntityType parsed = Settings.parseEntityType(args[2]);
            if (parsed != null && settings.allowedEntityTypes.contains(parsed)) {
                type = parsed;
                nameStart = 3;
            } else if (parsed != null || isAllCaps(args[2])) {
                lang().send(player, "invalidEntityType", "%type%", args[2], "%types%", typeList());
                return;
            }
        }
        String name = args.length > nameStart ? String.join(" ", Arrays.copyOfRange(args, nameStart, args.length)) : null;
        TradeShop shop = admin ? plugin.getShopService().createAdmin(player, type, name)
                : plugin.getShopService().createPlayer(player, type, name);
        if (shop != null) {
            lang().send(player, "shopCreated", "%id%", String.valueOf(shop.getId()));
        }
    }

    private static boolean isAllCaps(String s) {
        return s.equals(s.toUpperCase(Locale.ROOT)) && s.chars().anyMatch(Character::isLetter);
    }

    private String typeList() {
        return String.join(", ", plugin.getSettings().allowedEntityTypes.stream().map(Enum::name).toList());
    }

    /** Shop by id argument, or the NPC the player is looking at. Sends an error when not found. */
    private TradeShop findShop(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            try {
                TradeShop shop = plugin.getShopManager().get(Integer.parseInt(args[index].replace("#", "")));
                if (shop != null) {
                    return shop;
                }
            } catch (NumberFormatException ignored) {
            }
            lang().send(sender, "shopNotFound", "%id%", args[index]);
            return null;
        }
        if (sender instanceof Player player) {
            Entity target = player.getTargetEntity(6);
            TradeShop shop = target == null ? null : plugin.getShopManager().getByEntity(target);
            if (shop != null) {
                return shop;
            }
        }
        lang().send(sender, "notLookingAtShop");
        return null;
    }

    private boolean canEdit(CommandSender sender, TradeShop shop) {
        boolean ok = sender instanceof Player p ? shop.canEdit(p) : sender.hasPermission("atradenpc.admin");
        if (!ok) {
            lang().send(sender, "notYourShop");
        }
        return ok;
    }

    private void edit(CommandSender sender, String[] args) {
        Player player = player(sender);
        TradeShop shop = player == null ? null : findShop(sender, args, 1);
        if (shop != null && canEdit(sender, shop)) {
            new EditorMenu(plugin, player, shop).open();
        }
    }

    private void remove(CommandSender sender, String[] args) {
        TradeShop shop = findShop(sender, args, 1);
        if (shop != null && canEdit(sender, shop)) {
            plugin.getShopService().delete(shop);
            lang().send(sender, "shopRemoved", "%id%", String.valueOf(shop.getId()));
        }
    }

    // /atrade rename <id> <name...>
    private void rename(CommandSender sender, String[] args) {
        if (args.length < 3) {
            lang().sendList(sender, "help");
            return;
        }
        TradeShop shop = findShop(sender, args, 1);
        if (shop != null && canEdit(sender, shop)) {
            plugin.getShopService().rename(shop, String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
            lang().send(sender, "shopRenamed", "%name%", shop.getName());
        }
    }

    private void moveHere(CommandSender sender, String[] args) {
        Player player = player(sender);
        TradeShop shop = player == null ? null : findShop(sender, args, 1);
        if (shop != null && canEdit(sender, shop) && plugin.getShopService().move(player, shop, player.getLocation())) {
            lang().send(player, "shopMoved");
        }
    }

    private void center(CommandSender sender, String[] args) {
        TradeShop shop = findShop(sender, args, 1);
        if (shop != null && canEdit(sender, shop)) {
            plugin.getShopService().center(shop);
            lang().send(sender, "shopCentered");
        }
    }

    // /atrade list [player|all]
    private void list(CommandSender sender, String[] args) {
        List<TradeShop> shops;
        String who;
        if (args.length > 1) {
            if (!sender.hasPermission("atradenpc.admin")) {
                lang().send(sender, "noPermission");
                return;
            }
            if (args[1].equalsIgnoreCase("all")) {
                shops = new ArrayList<>(plugin.getShopManager().getShops());
                who = "everyone";
            } else if (args[1].equalsIgnoreCase("admin")) {
                shops = plugin.getShopManager().getShops().stream().filter(s -> s.getType() == ShopType.ADMIN).toList();
                who = "Server";
            } else {
                OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
                if (target == null) {
                    lang().send(sender, "playerNotFound", "%player%", args[1]);
                    return;
                }
                shops = plugin.getShopManager().getOwnedBy(target.getUniqueId());
                who = args[1];
            }
        } else {
            Player player = player(sender);
            if (player == null) {
                return;
            }
            shops = plugin.getShopManager().getOwnedBy(player.getUniqueId());
            who = player.getName();
        }
        lang().send(sender, "list-header", "%player%", who, "%count%", String.valueOf(shops.size()));
        if (shops.isEmpty()) {
            lang().send(sender, "list-empty");
            return;
        }
        for (TradeShop shop : shops) {
            Location loc = shop.getLocation();
            String line = lang().format("list-entry", "%id%", String.valueOf(shop.getId()), "%name%", shop.getName(),
                    "%type%", shop.getType().name(), "%world%", shop.getWorldName(),
                    "%x%", loc == null ? "?" : String.valueOf(loc.getBlockX()),
                    "%y%", loc == null ? "?" : String.valueOf(loc.getBlockY()),
                    "%z%", loc == null ? "?" : String.valueOf(loc.getBlockZ()),
                    "%offers%", String.valueOf(shop.getOffers().size()));
            if (sender.hasPermission("atradenpc.admin")) {
                line = "<click:run_command:'/atrade tp " + shop.getId() + "'><hover:show_text:'Teleport'>" + line + "</hover></click>";
            }
            sender.sendMessage(io.github.AzmiiD.aTradeNpc.util.Text.parse(line));
        }
    }

    private void tp(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (!player.hasPermission("atradenpc.admin")) {
            lang().send(player, "noPermission");
            return;
        }
        if (args.length < 2) {
            lang().sendList(player, "help");
            return;
        }
        TradeShop shop = findShop(sender, args, 1);
        Location loc = shop == null ? null : shop.getLocation();
        if (loc != null) {
            player.teleportAsync(loc);
        }
    }

    // /atrade open <id> [player] - for console, NPC plugins, menus...
    private void open(CommandSender sender, String[] args) {
        if (!sender.hasPermission("atradenpc.open")) {
            lang().send(sender, "noPermission");
            return;
        }
        if (args.length < 2) {
            lang().sendList(sender, "help");
            return;
        }
        TradeShop shop = findShop(sender, args, 1);
        if (shop == null) {
            return;
        }
        Player target;
        if (args.length > 2) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                lang().send(sender, "playerNotFound", "%player%", args[2]);
                return;
            }
        } else {
            target = player(sender);
            if (target == null) {
                return;
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (target.isOnline() && !shop.isRemoved()) {
                plugin.getTradeScreen().open(target, shop);
            }
        });
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] rawArgs) {
        CommandSender sender = CommandUtil.actor(stack);
        String[] args = CommandUtil.completionArgs(rawArgs);
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            return CommandUtil.filter(SUBS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("create")) {
            if (args.length == 2) {
                if (sender.hasPermission("atradenpc.create.admin")) {
                    out.add("admin");
                }
                if (sender.hasPermission("atradenpc.create.player")) {
                    out.add("player");
                }
            } else if (args.length == 3) {
                plugin.getSettings().allowedEntityTypes.forEach(t -> out.add(t.name()));
            }
            return CommandUtil.filter(out, args[args.length - 1]);
        }
        if (args.length == 2 && List.of("edit", "remove", "rename", "movehere", "center", "tp", "open").contains(sub)) {
            UUID self = sender instanceof Player p ? p.getUniqueId() : null;
            boolean admin = sender.hasPermission("atradenpc.admin");
            for (TradeShop shop : plugin.getShopManager().getShops()) {
                if (admin || (self != null && self.equals(shop.getOwner()))) {
                    out.add(String.valueOf(shop.getId()));
                }
            }
            return CommandUtil.filter(out, args[1]);
        }
        if (args.length == 2 && sub.equals("list") && sender.hasPermission("atradenpc.admin")) {
            out.add("all");
            out.add("admin");
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            return CommandUtil.filter(out, args[1]);
        }
        if (args.length == 3 && sub.equals("open")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            return CommandUtil.filter(out, args[2]);
        }
        return out;
    }
}
