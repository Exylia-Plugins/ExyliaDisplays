package net.exylia.exyliaDisplays.hologram.migration;

import net.exylia.exyliaDisplays.common.store.Position;
import net.exylia.exyliaDisplays.hologram.StoredHologram;
import net.exylia.lib.hologram.HologramLine;
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
        assertEquals(new Position("world", 10.5, 69.25, -4.5), spawn.location(), "the top line stays where it was");
        assertEquals(32, spawn.viewDistance());
        assertEquals(40, spawn.refresh().updateInterval());
    }

    @Test
    @DisplayName("DecentHolograms: a file as Hologram.save() writes it, every key read")
    void decentEverything() throws IOException {
        Path holograms = Files.createDirectories(folder.resolve("holograms"));
        // Hologram.save(): location in the server's locale, then the keys in this order; lines
        // from HologramLine.serializeToMap() and actions by ClickType from HologramPage.
        Files.writeString(holograms.resolve("shop.yml"), """
                location: world:10,500:70,000:-4,500
                enabled: false
                permission: vip.holo
                flags:
                - DISABLE_UPDATING
                display-range: 32
                update-range: 48
                update-interval: 40
                facing: 90.0
                down-origin: false
                pages:
                - lines:
                  - content: '{#ff0000}Welcome <#00ff00>to</#0000ff> &u[x]'
                    height: 0.3
                  - content: <#ANIM:typewriter>Typed</#ANIM>
                    height: 0.3
                  - content: ''
                    height: 0.6
                  - content: '#ICON: WOOL:14 !ENCHANTED'
                    height: 0.6
                    permission: see.icon
                    offsetX: 0.5
                  - content: '#HEAD: PLAYER_HEAD (Notch)'
                    height: 0.75
                  actions:
                    LEFT:
                    - MESSAGE:&aHi {player}
                    - CONNECT:lobby
                    RIGHT:
                    - PERMISSION:vip.use
                    - CONSOLE:give {player} diamond 1
                    SHIFT_RIGHT:
                    - TELEPORT:10:64:10
                    - SOUND:ENTITY_PLAYER_LEVELUP:1:2
                    - NEXT_PAGE
                - lines:
                  - content: Page {page} of {pages}
                    height: 0.3
                  actions:
                    LEFT:
                    - PREV_PAGE
                    RIGHT:
                    - PAGE:shop:1
                """);
        // Before pages: a numbered 'lines' section, and down-origin hangs the lines up from the place.
        Files.writeString(holograms.resolve("old.yml"), """
                location: world:0:100:0
                down-origin: true
                lines:
                  '1':
                    content: Old
                    height: 0.3
                """);
        ForeignImport.Result result = ForeignImport.decent(holograms.toFile());
        StoredHologram shop = result.holograms().get("shop");
        List<HologramLine> lines = shop.lines().stream().map(HologramLine::parse).toList();
        assertEquals("<#ff0000>Welcome <gradient:#00ff00:#0000ff>to</gradient> <rainbow>█", lines.get(0).text());
        assertEquals(HologramLine.DEFAULT_FLOW, lines.get(0).flow(), "&u cycles colours there");
        assertEquals("Typed", lines.get(1).text());
        assertEquals(HologramLine.DEFAULT_TYPEWRITER, lines.get(1).typewriter());
        assertEquals(2.0, lines.get(2).size(), 1e-9, "a 0.6 gap is two text lines high");
        assertEquals("WOOL", lines.get(3).item(), "14 is a data value, not a namespace");
        assertEquals("see.icon", lines.get(3).permission());
        assertEquals("Notch", lines.get(4).head());
        assertEquals(5, lines.size());
        assertEquals(List.of("Page 2 of 2"), shop.pages().get(0).lines());
        assertEquals(List.of("left: [PAGE] previous", "right: [PAGE] 1"), shop.pages().get(0).click());
        // 2.55 blocks of lines hang down from 70 there; 2 blocks stand up to 70 here.
        assertEquals(new Position("world", 10.5, 68, -4.5, 90, 0), shop.location());
        assertFalse(shop.enabled());
        assertEquals("vip.holo", shop.permission());
        assertFalse(shop.refresh().autoUpdate(), "DISABLE_UPDATING");
        assertEquals(List.of("left: [MESSAGE] &aHi %player_name%", "left: [SERVER] lobby",
                "shift_right: [TELEPORT] %player_world%;10;64;10", "shift_right: [SOUND] ENTITY_PLAYER_LEVELUP;1;2",
                "shift_right: [PAGE] next"),
                shop.click().commands(), "what a PERMISSION check guards is left out");

        assertEquals(new Position("world", 0, 100.05, 0), result.holograms().get("old").location());
    }

    @Test
    @DisplayName("DecentHolograms 2.10 displays: displays/*.yml as Configurate writes them")
    void decentDisplays() throws IOException {
        Path displays = Files.createDirectories(folder.resolve("displays"));
        Files.writeString(displays.resolve("board.yml"), """
                type: TEXT
                location:
                  world: world
                  x: 5.0
                  y: 70.0
                  z: 5.0
                  yaw: 45.0
                  pitch: 0.0
                settings:
                  enabled: false
                  display-range: 64.0
                  update-interval: 10
                attributes:
                  billboard:
                    value-type: billboard_constraints
                    value: VERTICAL
                  background-color:
                    value-type: rgba
                    value: {red: 255, green: 0, blue: 0, alpha: 128}
                  translation:
                    value-type: vector3f
                    value: {x: 0.0, y: 0.5, z: 0.0}
                  text-shadow:
                    value-type: boolean
                    value: true
                  line-width:
                    value-type: integer
                    value: 150
                pages:
                - lines:
                  - '{#00ff00}Board'
                  - Line 2
                """);
        Files.writeString(displays.resolve("gem.yml"), """
                type: ITEM
                location: {world: world, x: 0.0, y: 64.0, z: 0.0, yaw: 0.0, pitch: 0.0}
                attributes:
                  glow-color:
                    value-type: rgba
                    value: {red: 255, green: 170, blue: 0, alpha: 255}
                item: DIAMOND
                """);
        Files.createDirectories(folder.resolve("holograms"));
        ForeignImport.Result result = ForeignImport.decent(folder.resolve("holograms").toFile());
        StoredHologram board = result.holograms().get("board");
        assertEquals(List.of("<#00ff00>Board", "Line 2"), board.lines());
        assertEquals("VERTICAL", board.appearance().billboard());
        assertEquals("#80FF0000", board.appearance().backgroundColor());
        assertEquals(new Position("world", 5, 70.5, 5, 45, 0), board.location());
        assertTrue(board.appearance().shadow());
        assertEquals(150, board.appearance().lineWidth());
        assertFalse(board.enabled());
        assertEquals(64, board.viewDistance());
        assertEquals(10, board.refresh().updateInterval());

        StoredHologram gem = result.holograms().get("gem");
        assertEquals(List.of("<item:DIAMOND><glow:#FFAA00>"), gem.lines());
        assertEquals("FIXED", gem.appearance().billboard(), "an item display's own default");
    }

    @Test
    @DisplayName("DecentHolograms: every page with its page turns, and heads with their owner")
    void decentPages() throws IOException {
        Path holograms = Files.createDirectories(folder.resolve("holograms"));
        Files.writeString(holograms.resolve("Rules.yml"), """
                location: world:0.5:70.0:0.5
                pages:
                - lines:
                  - content: '#HEAD: PLAYER_HEAD (Notch)'
                  - content: 'Rules'
                  actions: {}
                - lines:
                  - content: '#SMALLHEAD: PLAYER_HEAD ({player})'
                  - content: 'No griefing'
                  actions:
                    LEFT:
                    - 'PREV_PAGE:Rules'
                    RIGHT:
                    - 'PAGE:Rules:1'
                    - 'MESSAGE:&aBack to the start'
                """);
        StoredHologram rules = ForeignImport.decent(holograms.toFile()).holograms().get("Rules");
        assertEquals(List.of("<head:Notch>", "Rules"), rules.lines());
        assertEquals(2, rules.pageCount());
        assertEquals(List.of("<item:PLAYER_HEAD><size:0.5>", "No griefing"), rules.pageView(1).lines());
        assertEquals(List.of("left: [PAGE] previous", "right: [PAGE] 1", "right: [MESSAGE] &aBack to the start"),
                rules.pages().get(0).click());
        assertEquals("<head:abc><size:0.5>", ForeignImport.decentLine("#SMALLHEAD: PLAYER_HEAD (abc)"));
        assertEquals("<item:GOLD_BLOCK>", ForeignImport.decentLine("#HEAD: GOLD_BLOCK"), "any other item is worn as it is");
        assertEquals("[PAGE] next", ForeignImport.decentAction("NEXT_PAGE:Rules"));
    }

    @Test
    @DisplayName("FancyHolograms: the multiple pages trait becomes pages that turn on their own")
    void fancyPages() throws IOException {
        Path data = Files.createDirectories(folder.resolve("data/holograms"));
        Files.writeString(data.resolve("holograms.json"), """
                [{"hologram_data": {"name": "news", "type": "TEXT", "world_name": "world",
                   "location": {"world": "world", "x": 0.0, "y": 64.0, "z": 0.0, "yaw": 0.0, "pitch": 0.0}},
                  "display_data": {}, "text_data": {"text": ["placeholder"]}}]
                """);
        Path traits = Files.createDirectories(folder.resolve("data/traits/multiple_pages_trait"));
        Files.writeString(traits.resolve("news.json"), """
                {"mode": "CYCLE", "cycleDelay": 2000, "pages": [{"lines": ["One"]}, {"lines": ["Two", "2b"]}]}
                """);
        StoredHologram news = ForeignImport.fancy(folder.toFile()).holograms().get("news");
        assertEquals(List.of(List.of("One"), List.of("Two", "2b")), news.allPages());
        assertEquals(40, news.pageInterval());
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
    @DisplayName("HolographicDisplays 3: placeholders, custom placeholders, animations and pinged servers")
    void holographicPlaceholders() throws IOException {
        Files.writeString(folder.resolve("database.yml"), """
                shop:
                  lines:
                  - '{animation: title.yml}'
                  - '&aShop {separator}'
                  - 'Online: {online} / {max_players}'
                  - 'Lobby: {online: lobby} {status: lobby}'
                  - 'Hi {player}, {papi: player_level}'
                  - '{empty}'
                  - '{rainbow}Rainbow'
                  - 'ICON: WOOL:14'
                  - '\\{literal\\}'
                  position:
                    world: world
                    x: 1.0
                    y: 65.0
                    z: 2.0
                """);
        Files.writeString(folder.resolve("config.yml"), """
                holograms-view-range: 64
                time:
                  format: HH:mm
                bungee:
                  pinger:
                    enable: true
                    servers:
                    - 'lobby: 127.0.0.1:25566'
                """);
        Files.writeString(folder.resolve("custom-placeholders.yml"), """
                placeholders:
                  '{separator}': '&8|'
                """);
        Files.createDirectories(folder.resolve("animations"));
        Files.writeString(folder.resolve("animations/title.yml"), """
                interval-seconds: 0.5
                animation-frames:
                - '&bFrame 1'
                - '&3Frame 2'
                """);
        StoredHologram shop = ForeignImport.holographic(folder.resolve("database.yml").toFile()).holograms().get("shop");
        assertEquals(List.of("&bFrame 1", "&aShop &8|", "Online: %server_online% / %server_max_players%",
                "Lobby: %bungee_lobby% %pinger_isonline_127.0.0.1:25566%", "Hi %player_name%, %player_level%", " ",
                HologramLine.builder().flow(HologramLine.DEFAULT_FLOW).text("<rainbow>Rainbow").build().raw(),
                "<item:WOOL>", "{literal}"), shop.lines());
        assertEquals(64, shop.viewDistance());
        assertEquals(new Position("world", 1, 62.5, 2), shop.location(), "eight text lines and an item hang from 65");
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
