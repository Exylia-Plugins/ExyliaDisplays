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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    @DisplayName("FancyHolograms 2: holograms.yml, as its own read() reads it")
    void fancyYaml() throws IOException {
        Files.writeString(folder.resolve("holograms.yml"), """
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
                    visibility: PERMISSION_REQUIRED
                    update_text_interval: 1000
                    translation_y: 0.5
                    block_brightness: 12
                    sky_brightness: 4
                    linkedNpc: guide
                  plain:
                    type: TEXT
                    location: {world: world, x: 0, y: 70, z: 0}
                    text: ['Hi']
                  solid:
                    type: TEXT
                    location: {world: world, x: 0, y: 70, z: 0}
                    text: ['Hi']
                    background: '#ff0000'
                  clear:
                    type: TEXT
                    location: {world: world, x: 0, y: 70, z: 0}
                    text: ['Hi']
                    background: TRANSPARENT
                  gem:
                    type: ITEM
                    location: {world: world, x: 3, y: 80, z: 3, yaw: 45, pitch: 30}
                    billboard: fixed
                    item: {id: 'minecraft:emerald', count: 1}
                  stairs:
                    type: BLOCK
                    location: {world: world, x: 3, y: 80, z: 3}
                    block: oak_stairs
                """);
        ForeignImport.Result result = ForeignImport.fancy(folder.toFile());
        StoredHologram welcome = result.holograms().get("welcome");
        assertEquals(List.of("<rainbow>Welcome</rainbow>", "Have fun"), welcome.lines());
        assertEquals("VERTICAL", welcome.appearance().billboard());
        assertEquals(2.0, welcome.appearance().scaleY());
        assertEquals("#55000000", welcome.appearance().backgroundColor());
        assertEquals(30, welcome.viewDistance());
        assertEquals(-90.5f, welcome.location().yaw(), "it keeps facing the way it faced there");
        assertEquals(0.5, welcome.appearance().lineSpacing(), 1e-9, "scale 2 lines need twice the gap");
        assertEquals(1000, welcome.appearance().lineWidth(), "FancyHolograms never wraps a line");
        assertTrue(welcome.toConfig().perPlayer() == false, "no placeholder, no per-viewer render");
        assertEquals("fancyholograms.viewhologram.welcome", welcome.permission());
        assertEquals(20, welcome.refresh().updateInterval(), "1000 ms is 20 ticks");
        assertEquals(80.5, welcome.location().y(), 1e-9, "its translation moves the location");
        assertEquals(12, welcome.appearance().brightness());
        assertEquals(java.util.Map.of("welcome", "guide"), result.links());

        assertTrue(result.holograms().get("plain").appearance().defaultBackground(), "no background is the client's own");
        assertFalse(result.holograms().get("plain").appearance().shadow(), "FancyHolograms has no text shadow by default");
        assertEquals("#FFFF0000", result.holograms().get("solid").appearance().backgroundColor(), "#RRGGBB is solid there");
        assertEquals("#00000000", result.holograms().get("clear").appearance().backgroundColor());

        StoredHologram gem = result.holograms().get("gem");
        assertEquals(List.of("<item:EMERALD>"), gem.lines());
        assertEquals("FIXED", gem.appearance().billboard());
        assertEquals(45f, gem.location().yaw());
        assertEquals(30f, gem.location().pitch(), "a tilted item stays tilted");
        assertEquals(-1, gem.appearance().brightness());
        assertEquals(List.of("<block:OAK_STAIRS>"), result.holograms().get("stairs").lines());
    }

    @Test
    @DisplayName("FancyHolograms 3: data/holograms/*.json, as its JsonAdapter writes it")
    void fancyJson() throws IOException {
        Path data = Files.createDirectories(folder.resolve("data/holograms/migrated"));
        Files.writeString(data.resolve("spawn.json"), """
                [
                  {"hologram_data": {"name": "title", "type": "TEXT",
                     "location": {"world": "world", "x": 1.5, "y": 70.0, "z": 1.5, "yaw": 90.0, "pitch": -15.0},
                     "world_name": "world", "visibility_distance": -1, "visibility": "ALL",
                     "linked_npc_name": "guide", "traits": []},
                   "display_data": {"scale": {"x": 1.0, "y": 1.0, "z": 1.0}, "translation": {"x": 0.0, "y": 0.25, "z": 0.0},
                     "shadow_radius": 0.0, "shadow_strength": 1.0, "brightness": {"block_light": 15, "sky_light": 15},
                     "billboard": "FIXED"},
                   "text_data": {"text": ["Spawn", "<gray>Welcome"], "text_shadow": true, "see_through": false,
                     "text_alignment": "LEFT", "text_update_interval": 500, "background_color": "#0", "text_opacity": 200}},
                  {"hologram_data": {"name": "beacon", "type": "BLOCK",
                     "location": {"world": "world", "x": 0.0, "y": 64.0, "z": 0.0, "yaw": 0.0, "pitch": 0.0},
                     "world_name": "world", "visibility_distance": 20, "visibility": "PERMISSION_REQUIRED", "traits": []},
                   "display_data": {"scale": {"x": 0.5, "y": 0.5, "z": 0.5}, "translation": {"x": 0.0, "y": 0.0, "z": 0.0},
                     "billboard": "CENTER", "glowing_color": "GOLD"},
                   "block_data": {"block_material": "minecraft:oak_stairs[facing=north,half=bottom]"}}
                ]
                """);
        ForeignImport.Result result = ForeignImport.fancy(folder.toFile());
        StoredHologram title = result.holograms().get("title");
        assertEquals(List.of("Spawn", "<gray>Welcome"), title.lines());
        assertEquals("FIXED", title.appearance().billboard());
        assertEquals("LEFT", title.appearance().alignment());
        assertEquals(90f, title.location().yaw());
        assertEquals(-15f, title.location().pitch());
        assertEquals(70.25, title.location().y(), 1e-9);
        assertEquals("#00000000", title.appearance().backgroundColor(), "#0 is transparent there");
        assertFalse(title.appearance().defaultBackground());
        assertEquals(200, title.appearance().textOpacity());
        assertEquals(15, title.appearance().brightness());
        assertEquals(10, title.refresh().updateInterval());
        assertEquals(48, title.viewDistance());
        assertEquals(java.util.Map.of("title", "guide"), result.links());

        StoredHologram beacon = result.holograms().get("beacon");
        assertEquals(List.of("<block:OAK_STAIRS><glow:#FFAA00>"), beacon.lines());
        assertEquals(0.5, beacon.appearance().scaleX());
        assertEquals(20, beacon.viewDistance());
        assertEquals("fancyholograms.viewhologram.beacon", beacon.permission());
    }
}
