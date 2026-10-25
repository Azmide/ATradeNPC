package io.github.AzmiiD.aTradeNpc.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Adds options that are new in the bundled config.yml/lang.yml to the files on disk, so updating the
 * jar never requires deleting the config. Existing values and comments are kept untouched; only
 * missing keys are added (with their default comments). A backup is made before the file is changed.
 */
public final class ConfigUpdater {

    private static final DateTimeFormatter BACKUP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private ConfigUpdater() {
    }

    /**
     * @param file     the file in the data folder (must already exist)
     * @param defaults the bundled resource of the same name
     * @return the keys that were added
     */
    public static List<String> update(File file, InputStream defaults, Logger logger) {
        List<String> added = new ArrayList<>();
        if (defaults == null || !file.exists()) {
            return added;
        }
        YamlConfiguration def = new YamlConfiguration();
        YamlConfiguration user = new YamlConfiguration();
        try (InputStreamReader reader = new InputStreamReader(defaults, StandardCharsets.UTF_8)) {
            def.load(reader);
            user.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            // A broken user file is reported by Bukkit when it is loaded; never overwrite it here.
            logger.log(Level.WARNING, "Could not check " + file.getName() + " for new options: " + e.getMessage());
            return added;
        }

        // getKeys(true) lists parents before their children, so new sections exist before their keys are set.
        for (String path : def.getKeys(true)) {
            if (user.contains(path, true) || blockedByValue(user, path)) {
                continue;
            }
            if (def.isConfigurationSection(path)) {
                user.createSection(path);
            } else {
                user.set(path, def.get(path));
            }
            user.setComments(path, def.getComments(path));
            user.setInlineComments(path, def.getInlineComments(path));
            added.add(path);
        }
        if (added.isEmpty()) {
            return added;
        }

        try {
            backup(file);
            user.save(file);
            logger.info("Added " + added.size() + " new option(s) to " + file.getName() + ": " + String.join(", ", added));
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save new options to " + file.getName(), e);
            added.clear();
        }
        return added;
    }

    /** True when a parent of {@code path} is a plain value in the user's file (the user replaced the section). */
    private static boolean blockedByValue(ConfigurationSection user, String path) {
        int dot = path.lastIndexOf('.');
        while (dot > 0) {
            String parent = path.substring(0, dot);
            if (user.contains(parent, true) && !user.isConfigurationSection(parent)) {
                return true;
            }
            dot = parent.lastIndexOf('.');
        }
        return false;
    }

    private static void backup(File file) throws IOException {
        File dir = new File(file.getParentFile(), "backups");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Could not create " + dir);
        }
        String name = file.getName().replaceFirst("\\.yml$", "") + "-" + LocalDateTime.now().format(BACKUP_FORMAT) + ".yml";
        Files.copy(file.toPath(), new File(dir, name).toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
