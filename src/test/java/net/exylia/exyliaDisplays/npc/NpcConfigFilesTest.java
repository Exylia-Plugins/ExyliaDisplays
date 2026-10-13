package net.exylia.exyliaDisplays.npc;

import net.exylia.exyliaDisplays.common.store.Place;
import net.exylia.exyliaDisplays.config.DisplaysConfig;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.config.Configs;
import net.exylia.lib.npc.NpcLook;
import net.exylia.lib.npc.NpcPose;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** npcs.yml, the npcs section of config.yml and messages/npcs.yml, through ExyliaLib's config module. */
class NpcConfigFilesTest {

    @TempDir
    Path folder;

    private Plugin plugin() {
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "ExyliaDisplaysTest";
                    case "getDataFolder" -> folder.toFile();
                    case "getLogger" -> Logger.getLogger("ExyliaDisplaysTest");
                    case "getResource" -> NpcConfigFilesTest.class.getClassLoader().getResourceAsStream((String) args[0]);
                    case "isEnabled" -> true;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "ExyliaDisplaysTest";
                    default -> null;
                });
    }

    @AfterEach
    void release() {
        Configs.releaseAll();
    }

    @Test
    @DisplayName("an NPC saved to npcs.yml reads back the same, every section included")
    void npcsRoundTrip() throws IOException {
        ConfigFile<NpcFile> file = Configs.define(plugin(), "npcs", NpcFile.class).load();
        StoredNpc guide = new StoredNpc()
                .withLocation(new Place("lobby", 0.5, 64, -3.5, 180, 0))
                .withSkin(StoredNpc.Skin.texture("dGV4dHVyZQ==", "c2lnbmF0dXJl"))
                .withPose(NpcPose.SNEAKING)
                .withScale(1.5)
                .withGlow("#8a51c4")
                .withEquipment(Map.of("hand", "DIAMOND_SWORD"))
                .withLook(new StoredNpc.Look(NpcLook.NEAREST, 6))
                .withPermission("vip.see")
                .withLabelLines(List.of("<size:1.2>{primary}&lGUIDE"))
                .withLinkedHologram("spawn")
                .withInteractions(new StoredNpc.Interactions(List.of("right: [SAY] Hi;3s", "left: spawn"),
                        "guide.use", 2500, "{error}No.", "", false));
        file.update(current -> new NpcFile(Map.of("guide", guide)));

        String written = Files.readString(folder.resolve("npcs.yml"));
        assertTrue(written.contains("linked-hologram: spawn"), written);
        assertTrue(written.contains("face-clicker: false"), written);

        Configs.releaseAll();
        NpcFile read = Configs.define(plugin(), "npcs", NpcFile.class).load().get();
        assertEquals(guide, read.npcs().get("guide"));
    }

    @Test
    @DisplayName("a hand-written NPC with only a location gets the defaults, and durations read as written")
    void sparseNpc() throws IOException {
        Files.writeString(folder.resolve("npcs.yml"), """
                npcs:
                  bob:
                    location:
                      world: lobby
                      x: 1
                      y: 65
                      z: 2
                    skin:
                      type: PLAYER
                      player: Notch
                    interactions:
                      actions:
                        - 'right: spawn'
                      cooldown: 2s
                """);
        StoredNpc bob = Configs.define(plugin(), "npcs", NpcFile.class).load().get().npcs().get("bob");
        assertEquals("lobby", bob.location().world());
        assertEquals(0, bob.location().yaw());
        assertTrue(bob.enabled());
        assertEquals(NpcPose.STANDING, bob.pose());
        assertEquals(StoredNpc.Skin.player("Notch"), bob.skin());
        assertEquals(List.of("right: spawn"), bob.interactions().actions());
        assertEquals(2000, bob.interactions().cooldown());
        assertTrue(bob.interactions().faceClicker());
        assertTrue(bob.label().lines().isEmpty());
        assertEquals(48.0, bob.viewDistance());
    }

    @Test
    @DisplayName("config.yml generates the npcs section with its presets")
    void config() {
        DisplaysConfig config = Configs.define(plugin(), "config", DisplaysConfig.class).load().get();
        assertEquals(new NpcsConfig().presets().keySet(), config.npcs().presets().keySet());
        assertEquals(Duration.ofSeconds(5), config.npcs().sayDuration());
        assertEquals(NpcLook.EACH_VIEWER, config.npcs().defaultLook());
    }

    @Test
    @DisplayName("messages/npcs.yml generates, and its Spanish file has every key")
    void messages() throws IOException {
        Configs.define(plugin(), "messages/npcs", NpcsMessages.class).load();
        String english = Files.readString(folder.resolve("messages/npcs.yml"));
        var stream = NpcConfigFilesTest.class.getClassLoader().getResourceAsStream("lang/es/messages/npcs.yml");
        assertTrue(stream != null, "no lang/es/messages/npcs.yml");
        String spanish = new String(stream.readAllBytes());
        List<String> keys = english.lines()
                .filter(line -> line.matches("^ {0,2}[a-z-]+:.*") && !line.startsWith("config-version"))
                .map(line -> line.substring(0, line.indexOf(':')))
                .toList();
        for (String key : keys) {
            assertTrue(spanish.lines().anyMatch(line -> line.startsWith(key + ":")),
                    "lang/es/messages/npcs.yml has no " + key.trim());
        }
    }
}
