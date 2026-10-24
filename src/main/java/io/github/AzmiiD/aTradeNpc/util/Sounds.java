package io.github.AzmiiD.aTradeNpc.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Sounds from config.yml ("sounds.<name>: key [volume] [pitch]"), e.g. "entity.villager.yes 1 1".
 * Sounds are played by key, so any vanilla or resource-pack sound works. Empty = disabled.
 */
public class Sounds {

    private record Entry(String key, float volume, float pitch) {
    }

    private final Map<String, Entry> sounds = new HashMap<>();

    public void load(ConfigurationSection section) {
        sounds.clear();
        if (section == null) {
            return;
        }
        for (String name : section.getKeys(false)) {
            String value = section.getString(name, "").trim();
            if (value.isEmpty()) {
                continue;
            }
            String[] parts = value.split("\\s+");
            try {
                float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1f;
                float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1f;
                sounds.put(name, new Entry(parts[0].toLowerCase(Locale.ROOT), volume, pitch));
            } catch (NumberFormatException ignored) {
                sounds.put(name, new Entry(parts[0].toLowerCase(Locale.ROOT), 1f, 1f));
            }
        }
    }

    public void play(Player player, String name) {
        Entry entry = sounds.get(name);
        if (entry != null) {
            player.playSound(player.getLocation(), entry.key(), entry.volume(), entry.pitch());
        }
    }
}
