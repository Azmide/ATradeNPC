package io.github.AzmiiD.aTradeNpc.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class ConfigUpdaterTest {
    @TempDir Path directory;

    @Test
    void bundledYamlIsValidAndHasIntegrationDefaults() throws Exception {
        for (String resource : new String[]{"config.yml", "lang.yml", "paper-plugin.yml"}) {
            try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(input);
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.load(new InputStreamReader(input, StandardCharsets.UTF_8));
                if (resource.equals("lang.yml")) {
                    assertTrue(yaml.contains("hologramLinked"));
                }
                if (resource.equals("config.yml")) {
                    assertEquals(2.5, yaml.getDouble("holograms.link-height"));
                }
            }
        }
    }

    @Test
    void addsIntegrationOptionsWithBackupAndPreservesCustomValues() throws Exception {
        Path file = directory.resolve("config.yml");
        String original = "# Owner settings\nclick-cooldown-ms: 275\neconomy:\n  money-item: EMERALD\n";
        Files.writeString(file, original);
        var added = ConfigUpdater.update(file.toFile(), getClass().getClassLoader().getResourceAsStream("config.yml"),
                Logger.getLogger("ConfigUpdaterTest"));
        assertTrue(added.contains("holograms.link-height"));
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        assertEquals(275, yaml.getInt("click-cooldown-ms"));
        assertEquals("EMERALD", yaml.getString("economy.money-item"));
        assertEquals(2.5, yaml.getDouble("holograms.link-height"));
        try (var backups = Files.list(directory.resolve("backups"))) {
            assertEquals(original, Files.readString(backups.findFirst().orElseThrow()));
        }
        assertTrue(ConfigUpdater.update(file.toFile(), getClass().getClassLoader().getResourceAsStream("config.yml"),
                Logger.getLogger("ConfigUpdaterTest")).isEmpty());
    }

    @Test
    void preservesLegacyUserKeysWithoutOverwritingValues() throws Exception {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, "holograms:\n  hide-npc-name: false\n");
        ConfigUpdater.update(file.toFile(), new ByteArrayInputStream("holograms:\n  hide-npc-name: true\n".getBytes(StandardCharsets.UTF_8)),
                Logger.getLogger("ConfigUpdaterTest"));
        assertFalse(YamlConfiguration.loadConfiguration(file.toFile()).getBoolean("holograms.hide-npc-name"));
    }

    @Test
    void repairsShippedLegacyLanguageSyntaxWithoutLosingCustomMessages() throws Exception {
        Path file = directory.resolve("lang.yml");
        String original = "prefix: 'Custom prefix'\ninvalidEntityType:\"Custom entity error\"\n";
        Files.writeString(file, original);
        var added = ConfigUpdater.update(file.toFile(), getClass().getClassLoader().getResourceAsStream("lang.yml"),
                Logger.getLogger("ConfigUpdaterTest"));
        assertTrue(added.contains("hologramLinked"));
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        assertEquals("Custom prefix", yaml.getString("prefix"));
        assertEquals("Custom entity error", yaml.getString("invalidEntityType"));
        try (var backups = Files.list(directory.resolve("backups"))) {
            assertEquals(original, Files.readString(backups.findFirst().orElseThrow()));
        }
    }

    @Test
    void unrelatedMalformedYamlIsNeverOverwritten() throws Exception {
        Path file = directory.resolve("lang.yml");
        String original = "prefix: [broken\n";
        Files.writeString(file, original);
        assertTrue(ConfigUpdater.update(file.toFile(), getClass().getClassLoader().getResourceAsStream("lang.yml"),
                Logger.getLogger("ConfigUpdaterTest")).isEmpty());
        assertEquals(original, Files.readString(file));
        assertFalse(Files.exists(directory.resolve("backups")));
    }
}
