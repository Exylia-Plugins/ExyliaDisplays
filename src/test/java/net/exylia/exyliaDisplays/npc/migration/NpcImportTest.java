package net.exylia.exyliaDisplays.npc.migration;

import net.exylia.exyliaDisplays.common.store.Place;
import net.exylia.exyliaDisplays.npc.StoredNpc;
import net.exylia.lib.npc.NpcLook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Citizens' and FancyNpcs' save files, read into NPCs.
 *
 * <p>The samples are written from what those plugins are known to save, not copied from a live
 * server: they pin down what this reader understands, not that it matches every version.
 */
class NpcImportTest {

    @TempDir
    Path folder;

    @Test
    @DisplayName("Citizens: string numbers, skin trait, look-close, equipment, commands; non-players skipped")
    void citizens() throws IOException {
        Path saves = folder.resolve("saves.yml");
        Files.writeString(saves, """
                npc:
                  '0':
                    name: '&6Guide'
                    traits:
                      type: PLAYER
                      location:
                        world: world
                        x: '10.5'
                        y: '64.0'
                        z: '-3.5'
                        yaw: '90.0'
                        pitch: '0.0'
                      skintrait:
                        skinName: Notch
                        fetchDefaultSkin: true
                      lookclose:
                        enabled: true
                        range: 6.0
                      equipment:
                        hand:
                          type: DIAMOND_SWORD
                        helmet: minecraft:golden_helmet
                      commandtrait:
                        commands:
                          '0':
                            command: /warp shop <p>
                            hand: RIGHT
                            player: true
                            cooldown: 3
                          '1':
                            command: say hi
                            hand: BOTH
                  '1':
                    name: Textured
                    traits:
                      location: {world: world, x: 0, y: 70, z: 0}
                      skintrait:
                        textureRaw: dGV4
                        signature: c2ln
                  '2':
                    name: Cow
                    traits:
                      type: COW
                      location: {world: world, x: 0, y: 70, z: 0}
                """);
        NpcImport.Result result = NpcImport.citizens(saves.toFile());
        assertEquals(1, result.skipped());
        StoredNpc guide = result.npcs().get("Guide");
        assertEquals(new Place("world", 10.5, 64, -3.5, 90, 0), guide.location());
        assertEquals(StoredNpc.Skin.player("Notch"), guide.skin());
        assertEquals(List.of("&6Guide"), guide.label().lines());
        assertEquals(new StoredNpc.Look(NpcLook.NEAREST, 6), guide.look());
        assertEquals(Map.of("hand", "DIAMOND_SWORD", "head", "GOLDEN_HELMET"), guide.equipment());
        assertEquals(List.of("right: warp shop %player_name%", "console: say hi"), guide.interactions().actions());
        assertEquals(3000, guide.interactions().cooldown());
        assertEquals(StoredNpc.Skin.texture("dGV4", "c2ln"), result.npcs().get("Textured").skin());
    }

    @Test
    @DisplayName("FancyNpcs: skin section, display name, glow colour, turn-to-player and actions")
    void fancy() throws IOException {
        Path file = folder.resolve("npcs.yml");
        Files.writeString(file, """
                npcs:
                  7c3a0d0e-0000-0000-0000-000000000001:
                    name: portal
                    displayName: '<aqua>Survival'
                    type: PLAYER
                    location: {world: lobby, x: 1.5, y: 80, z: 2.5, yaw: 180, pitch: 0}
                    skin:
                      identifier: jeb_
                      mirrorSkin: false
                    glowing: true
                    glowingColor: gold
                    turnToPlayer: true
                    turnToPlayerDistance: 10
                    scale: 1.5
                    visibility_distance: 30
                    interactionCooldown: 1.5
                    equipment:
                      mainhand: compass
                    actions:
                      RIGHT_CLICK:
                        '1': {action: message, value: 'Off you go, {player}'}
                        '2': {action: send_to_server, value: survival}
                      LEFT_CLICK:
                        '1': {action: unknown_thing, value: x}
                  7c3a0d0e-0000-0000-0000-000000000002:
                    name: me
                    displayName: <empty>
                    type: PLAYER
                    location: {world: lobby, x: 0, y: 80, z: 0}
                    skin: {mirrorSkin: true}
                  7c3a0d0e-0000-0000-0000-000000000003:
                    name: pig
                    type: PIG
                    location: {world: lobby, x: 0, y: 80, z: 0}
                """);
        NpcImport.Result result = NpcImport.fancy(file.toFile());
        assertEquals(1, result.skipped());
        StoredNpc portal = result.npcs().get("portal");
        assertEquals(new Place("lobby", 1.5, 80, 2.5, 180, 0), portal.location());
        assertEquals(StoredNpc.Skin.player("jeb_"), portal.skin());
        assertEquals(List.of("<aqua>Survival"), portal.label().lines());
        assertEquals("#ffaa00", portal.glow());
        assertEquals(new StoredNpc.Look(NpcLook.EACH_VIEWER, 10), portal.look());
        assertEquals(1.5, portal.scale());
        assertEquals(30, portal.viewDistance());
        assertEquals(Map.of("hand", "COMPASS"), portal.equipment());
        assertEquals(List.of("right: [MESSAGE] Off you go, %player_name%", "right: [SERVER] survival"),
                portal.interactions().actions());
        assertEquals(1500, portal.interactions().cooldown());
        StoredNpc me = result.npcs().get("me");
        assertEquals(StoredNpc.Skin.mirror(), me.skin());
        assertTrue(me.label().lines().isEmpty());
    }

    @Test
    @DisplayName("a missing file is nothing to import, not a failure")
    void missing() {
        assertTrue(NpcImport.citizens(folder.resolve("nope.yml").toFile()).npcs().isEmpty());
        assertTrue(NpcImport.fancy(folder.resolve("nope.yml").toFile()).npcs().isEmpty());
    }
}
