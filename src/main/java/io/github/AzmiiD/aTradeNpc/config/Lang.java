package io.github.AzmiiD.aTradeNpc.config;

import io.github.AzmiiD.aTradeNpc.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Messages from lang.yml. Missing keys fall back to the bundled defaults. */
public class Lang {

    private FileConfiguration config;
    private FileConfiguration defaults;

    public void load(File file, InputStream defaultStream) {
        this.config = YamlConfiguration.loadConfiguration(file);
        if (defaultStream != null) {
            this.defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            config.setDefaults(defaults);
        }
    }

    public String raw(String key) {
        return config.getString(key, key);
    }

    public List<String> rawList(String key) {
        if (config.isList(key)) {
            return config.getStringList(key);
        }
        return List.of(raw(key));
    }

    /** Message with placeholders applied, without prefix. Empty when the message is disabled ("" in lang.yml). */
    public String format(String key, String... placeholders) {
        return Text.replace(raw(key), toMap(placeholders));
    }

    public void send(CommandSender sender, String key, String... placeholders) {
        String msg = format(key, placeholders);
        sendRaw(sender, msg);
    }

    /** Sends an already formatted message (adds prefix). Does nothing for empty messages. */
    public void sendRaw(CommandSender sender, String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        sender.sendMessage(Text.parse(raw("prefix") + message));
    }

    public void sendList(CommandSender sender, String key, String... placeholders) {
        Map<String, String> map = toMap(placeholders);
        for (String line : rawList(key)) {
            sender.sendMessage(Text.parse(Text.replace(line, map)));
        }
    }

    public static Map<String, String> toMap(String... placeholders) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            map.put(placeholders[i], placeholders[i + 1]);
        }
        return map;
    }
}
