package net.exylia.exyliaDisplays.hologram;

import net.exylia.exyliaDisplays.hologram.HologramFile;
import net.exylia.exyliaDisplays.common.store.Position;
import net.exylia.exyliaDisplays.hologram.StoredHologram;
import net.exylia.exyliaDisplays.config.DisplaysConfig;
import net.exylia.exyliaDisplays.config.DisplaysMessages;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.config.Configs;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The plugin's files, written and read back through ExyliaLib's config module. */
class ConfigFilesTest {

    @TempDir
    Path folder;

    private Plugin plugin() {
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "ExyliaHologramsTest";
                    case "getDataFolder" -> folder.toFile();
                    case "getLogger" -> Logger.getLogger("ExyliaHologramsTest");
                    case "getResource" -> ConfigFilesTest.class.getClassLoader().getResourceAsStream((String) args[0]);
                    case "isEnabled" -> true;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "ExyliaHologramsTest";
                    default -> null;
                });
    }

    @AfterEach
    void release() {
        Configs.releaseAll();
    }

    @Test
    @DisplayName("a hologram saved to holograms.yml reads back the same, nested sections included")
    void hologramsRoundTrip() throws IOException {
        Plugin plugin = plugin();
        ConfigFile<HologramFile> file = Configs.define(plugin, "holograms", HologramFile.class).load();
        StoredHologram shop = StoredHologram.at(new Position("world", 1.5, 70, -2.5),
                List.of("<size:2><shine>{primary}&lSHOP", "<item:DIAMOND>"), 32)
                .withPermission("vip.see");
        file.update(current -> new HologramFile(Map.of("shop", shop)));

        String written = Files.readString(folder.resolve("holograms.yml"));
        assertTrue(written.contains("view-distance: 32"), written);
        assertTrue(written.contains("hover-scale"), written);

        Configs.releaseAll();
        HologramFile read = Configs.define(plugin(), "holograms", HologramFile.class).load().get();
        assertEquals(shop, read.holograms().get("shop"));
    }

    @Test
    @DisplayName("a hand-written hologram with only some keys gets the defaults for the rest")
    void sparseHologram() throws IOException {
        Files.writeString(folder.resolve("holograms.yml"), """
                holograms:
                  welcome:
                    location:
                      world: lobby
                      x: 0.5
                      y: 100
                      z: 0.5
                    lines:
                      - 'Hi'
                    click:
                      commands:
                        - 'spawn'
                """);
        StoredHologram welcome = Configs.define(plugin(), "holograms", HologramFile.class).load().get()
                .holograms().get("welcome");
        assertEquals(List.of("Hi"), welcome.lines());
        assertEquals("lobby", welcome.location().world());
        assertTrue(welcome.enabled());
        assertEquals(48.0, welcome.viewDistance());
        assertEquals(List.of("spawn"), welcome.click().commands());
        assertEquals("CENTER", welcome.appearance().billboard());
    }

    @Test
    @DisplayName("config.yml generates with every module and the hologram presets")
    void config() {
        DisplaysConfig config = Configs.define(plugin(), "config", DisplaysConfig.class).load().get();
        assertEquals(new HologramsConfig().presets().keySet(), config.holograms().presets().keySet());
        assertTrue(config.enabled("holograms"));
        assertTrue(config.enabled("npcs"));
        assertTrue(config.enabled("a-module-the-file-does-not-name"));
    }

    @Test
    @DisplayName("every message file generates, and its Spanish file has every key")
    void messages() throws IOException {
        assertTranslated("messages", DisplaysMessages.class);
        assertTranslated("messages/holograms", HologramsMessages.class);
    }

    private <T extends Record> void assertTranslated(String name, Class<T> type) throws IOException {
        Configs.define(plugin(), name, type).load();
        String english = Files.readString(folder.resolve(name + ".yml"));
        var stream = ConfigFilesTest.class.getClassLoader().getResourceAsStream("lang/es/" + name + ".yml");
        assertTrue(stream != null, "no lang/es/" + name + ".yml");
        String spanish = new String(stream.readAllBytes());
        List<String> keys = english.lines()
                .filter(line -> line.matches("^ {0,2}[a-z-]+:.*") && !line.startsWith("config-version"))
                .map(line -> line.substring(0, line.indexOf(':')))
                .toList();
        for (String key : keys) {
            assertTrue(spanish.lines().anyMatch(line -> line.startsWith(key + ":")
                            || line.startsWith(key.replaceFirst("^( *)([a-z-]+)$", "$1'$2'") + ":")),
                    "lang/es/" + name + ".yml has no " + key.trim());
        }
    }
}
