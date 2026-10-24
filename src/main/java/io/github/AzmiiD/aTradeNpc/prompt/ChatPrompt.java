package io.github.AzmiiD.aTradeNpc.prompt;

import io.github.AzmiiD.aTradeNpc.ATradeNPC;
import io.github.AzmiiD.aTradeNpc.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Asks a player to type something in chat (price, shop name). The answer is handled on the main thread. */
public class ChatPrompt implements Listener {

    private record Pending(Consumer<String> onAnswer, Runnable onCancel, BukkitTask timeout) {
    }

    private static final long TIMEOUT_TICKS = 20L * 60;

    private final ATradeNPC plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ChatPrompt(ATradeNPC plugin) {
        this.plugin = plugin;
    }

    /**
     * @param onAnswer called with the typed text (main thread)
     * @param onCancel called when the player typed "cancel" or the prompt timed out (main thread)
     */
    public void ask(Player player, String langKey, Consumer<String> onAnswer, Runnable onCancel) {
        cancel(player.getUniqueId());
        UUID id = player.getUniqueId();
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Pending p = pending.remove(id);
            if (p != null) {
                Player online = Bukkit.getPlayer(id);
                if (online != null) {
                    plugin.getLang().send(online, "promptTimeout");
                    p.onCancel.run();
                }
            }
        }, TIMEOUT_TICKS);
        pending.put(id, new Pending(onAnswer, onCancel, timeout));
        player.closeInventory();
        plugin.getLang().send(player, langKey);
    }

    private void cancel(UUID id) {
        Pending p = pending.remove(id);
        if (p != null) {
            p.timeout.cancel();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Pending p = pending.remove(player.getUniqueId());
        if (p == null) {
            return;
        }
        event.setCancelled(true);
        String text = Text.plain(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            p.timeout.cancel();
            if (!player.isOnline()) {
                return;
            }
            if (text.equalsIgnoreCase("cancel")) {
                plugin.getLang().send(player, "promptCancelled");
                p.onCancel.run();
            } else {
                p.onAnswer.accept(text);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer().getUniqueId());
    }
}
