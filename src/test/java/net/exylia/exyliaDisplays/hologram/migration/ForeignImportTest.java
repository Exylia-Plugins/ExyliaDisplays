package net.exylia.exyliaDisplays.hologram.migration;

import net.exylia.exyliaDisplays.common.store.Position;
import net.exylia.exyliaDisplays.hologram.StoredHologram;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The other plugins' files, as they write them, read into holograms. */
class ForeignImportTest {

    @TempDir
    Path folder;

    @Test
    @DisplayName("DecentHolograms: first page, icons, hex colours, and lines standing on the place")
    void decent() throws IOException {
        Path holograms = Files.createDirectories(folder.resolve("holograms"));
        Files.writeString(holograms.resolve("Spawn.yml"), """
                location: world:10.5:70.0:-4.5
                enabled: true
                permission: ''
                display-range: 32
                update-interval: 40
                pages:
                - lines:
                  - content: '{#ff0000}Welcome'
                    height: 0.3
                  - content: '#ICON: minecraft:diamond'
                    height: 0.6
                  actions: {}
                """);
        ForeignImport.Result result = ForeignImport.decent(holograms.toFile());
        StoredHologram spawn = result.holograms().get("Spawn");
        assertEquals(List.of("<#ff0000>Welcome", "<item:DIAMOND>"), spawn.lines());
        assertEquals(new Position("world", 10.5, 69.5, -4.5), spawn.location());
        assertEquals(32, spawn.viewDistance());
        assertEquals(40, spawn.refresh().updateInterval());
    }

    @Test
    @DisplayName("HolographicDisplays: version 3 positions and version 2 strings")
    void holographic() throws IOException {
        Path database = folder.resolve("database.yml");
        Files.writeString(database, """
                shop:
                  lines:
                  - '&aShop'
                  - 'ICON: EMERALD'
                  position:
                    world: world
                    x: 1.0
                    y: 65.0
                    z: 2.0
                old:
                  location: 'world_nether, 5.0, 40.0, 6.0'
                  lines:
                  - 'Old one'
                broken:
                  lines: []
                """);
        ForeignImport.Result result = ForeignImport.holographic(database.toFile());
        assertEquals(List.of("&aShop", "<item:EMERALD>"), result.holograms().get("shop").lines());
        assertEquals(new Position("world_nether", 5, 39.75, 6), result.holograms().get("old").location());
        assertEquals(1, result.skipped());
    }

    @Test
    @DisplayName("FancyHolograms: text, item and block holograms with their look")
    void fancy() throws IOException {
        Path file = folder.resolve("holograms.yml");
        Files.writeString(file, """
                version: 2
                holograms:
                  welcome:
                    type: TEXT
                    location: {world: world, x: 0.5, y: 80.0, z: 0.5, yaw: -90.5, pitch: 0}
                    visibility_distance: 30
                    billboard: vertical
                    scale_x: 2
                    scale_y: 2
                    scale_z: 2
                    text: ['<rainbow>Welcome</rainbow>', 'Have fun']
                    text_shadow: true
                    background: '#55000000'
                  gem:
                    type: ITEM
                    location: {world: world, x: 3, y: 80, z: 3}
                    item: minecraft:emerald
                """);
        ForeignImport.Result result = ForeignImport.fancy(file.toFile());
        StoredHologram welcome = result.holograms().get("welcome");
        assertEquals(List.of("<rainbow>Welcome</rainbow>", "Have fun"), welcome.lines());
        assertEquals("VERTICAL", welcome.appearance().billboard());
        assertEquals(2.0, welcome.appearance().scaleY());
        assertEquals("#55000000", welcome.appearance().backgroundColor());
        assertEquals(30, welcome.viewDistance());
        assertEquals(-90.5f, welcome.location().yaw(), "it keeps facing the way it faced there");
        assertEquals(0.5, welcome.appearance().lineSpacing(), 1e-9, "scale 2 lines need twice the gap");
        assertTrue(welcome.toConfig().perPlayer() == false, "no placeholder, no per-viewer render");
        assertEquals(List.of("<item:EMERALD>"), result.holograms().get("gem").lines());
    }
}
