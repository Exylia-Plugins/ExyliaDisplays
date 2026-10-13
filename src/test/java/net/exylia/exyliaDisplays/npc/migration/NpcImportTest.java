package net.exylia.exyliaDisplays.npc.migration;

import net.exylia.exyliaDisplays.common.store.Place;
import net.exylia.exyliaDisplays.npc.StoredNpc;
import net.exylia.lib.npc.NpcLook;
import net.exylia.lib.npc.NpcPose;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertEquals(List.of("right: warp shop %player_name%", "[SAY] hi"), guide.interactions().actions(),
                "Citizens turns 'say' into the NPC speaking");
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
    @DisplayName("Citizens: spawned, mirror, glow colour, pose, scale, hologram lines, per-player look, guarded commands")
    void citizensEverything() throws IOException {
        Path saves = folder.resolve("saves.yml");
        // AbstractNPC.save(): name, uuid, metadata, traits by @TraitName with their @Persist keys.
        Files.writeString(saves, """
                npc:
                  '3':
                    name: Shopkeeper
                    uuid: 0f4a7e8e-0000-0000-0000-000000000003
                    traitnames: type,location,spawned,mirrortrait,scoreboardtrait,sneak,attributetrait,lookclose,hologramtrait,equipment,commandtrait
                    metadata:
                      glowing: true
                      nameplate-visible: false
                    traits:
                      spawned: false
                      type: PLAYER
                      location:
                        bodyYaw: 0.0
                        world: world
                        x: '0.0'
                        y: '64.0'
                        z: '0.0'
                        yaw: '0.0'
                        pitch: '0.0'
                      mirrortrait:
                        enabled: true
                        mirrorEquipment: false
                        mirrorName: false
                      scoreboardtrait:
                        color: GOLD
                        tags:
                        - CITIZENS_NPC
                      sneak:
                        sneaking: true
                      attributetrait:
                        attributes:
                          minecraft:scale: 2.0
                      lookclose:
                        enabled: true
                        perplayer: true
                        range: 5.0
                      hologramtrait:
                        lines:
                          '0':
                            text: Line above name
                            margin: {top: 0.0, bottom: 0.0}
                          '1':
                            text: Top line
                            margin: {top: 0.0, bottom: 0.0}
                      equipment:
                        chestplate:
                          type: diamond_chestplate
                          amount: 1
                      commandtrait:
                        executionMode: LINEAR
                        commands:
                          '0': {command: server survival, hand: RIGHT, player: true}
                          '1': {command: eco give <p> 100, hand: BOTH, n: 1}
                          '2': {command: broadcast <player> clicked <npc>, hand: LEFT, delay: 20}
                          '3':
                            command: vip
                            hand: BOTH
                            player: true
                            permissions: {'0': vip.use}
                  '4':
                    name: Gambler
                    traits:
                      location: {world: world, x: '0', y: '64', z: '0'}
                      commandtrait:
                        executionMode: RANDOM
                        commands:
                          '0': {command: give <p> diamond, hand: BOTH}
                """);
        NpcImport.Result result = NpcImport.citizens(saves.toFile());
        StoredNpc shop = result.npcs().get("Shopkeeper");
        assertFalse(shop.enabled(), "despawned there");
        assertEquals(StoredNpc.Skin.mirror(), shop.skin());
        assertEquals("#ffaa00", shop.glow());
        assertEquals(NpcPose.SNEAKING, shop.pose());
        assertEquals(2.0, shop.scale());
        assertEquals(new StoredNpc.Look(NpcLook.EACH_VIEWER, 5), shop.look());
        assertEquals(List.of("Top line", "Line above name"), shop.label().lines(), "top first, its nameplate hidden");
        assertEquals(Map.of("chest", "DIAMOND_CHESTPLATE"), shop.equipment());
        assertEquals(List.of("right: [SERVER] survival", "[DELAY] 20t",
                        "left: console: broadcast %player_name% clicked %npc%"), shop.interactions().actions(),
                "a limited or permission command is left out, and a delay waits from the click");
        assertTrue(result.npcs().get("Gambler").interactions().actions().isEmpty(),
                "one random command there would be every command here");
    }

    @Test
    @DisplayName("FancyNpcs: cached skin, permission visibility, pose, gated actions and the old keys")
    void fancyEverything() throws IOException {
        Path file = folder.resolve("npcs.yml");
        // NpcManagerImpl.saveNpcs(): every key it writes, in its order.
        Files.writeString(file, """
                npcs:
                  7c3a0d0e-0000-0000-0000-000000000004:
                    name: guard
                    creator: 7c3a0d0e-0000-0000-0000-00000000ffff
                    displayName: <red>Guard
                    type: PLAYER
                    location: {world: world, x: 0.0, y: 64.0, z: 0.0, yaw: 0.0, pitch: 0.0}
                    showInTab: false
                    spawnEntity: true
                    collidable: true
                    glowing: false
                    glowingColor: white
                    turnToPlayer: false
                    turnToPlayerDistance: -1
                    interactionCooldown: 0.0
                    scale: 1.0
                    visibility_distance: -1
                    visibility: PERMISSION_REQUIRED
                    skin:
                      identifier: 069a79f4-44e9-4726-a5be-fca90e38aaf5
                      variant: AUTO
                      mirrorSkin: false
                    attributes:
                      pose: sleeping
                      on_fire: 'true'
                    actions:
                      RIGHT_CLICK:
                        '1': {action: need_permission, value: guard.talk}
                        '2': {action: player_command, value: server pvp}
                        '3': {action: wait, value: '2'}
                        '4': {action: console_command, value: 'say {player}'}
                      LEFT_CLICK:
                        '1': {action: message, value: hi}
                        '2': {action: need_item, value: DIAMOND 1}
                        '3': {action: console_command, value: 'give {player} emerald'}
                  7c3a0d0e-0000-0000-0000-000000000005:
                    name: old
                    type: PLAYER
                    location: {world: world, x: 0.0, y: 64.0, z: 0.0}
                    message: Hello
                    serverCommands:
                    - say hi
                """);
        // SkinCacheFile: JDB documents at .data/skins/<Base64 of the identifier>.json.
        Path skins = Files.createDirectories(folder.resolve(".data/skins"));
        String key = java.util.Base64.getEncoder().encodeToString("069a79f4-44e9-4726-a5be-fca90e38aaf5".getBytes());
        Files.writeString(skins.resolve(key + ".json"), """
                {
                  "skinData": {"identifier": "069a79f4-44e9-4726-a5be-fca90e38aaf5", "variant": "AUTO",
                               "textureValue": "dGV4", "textureSignature": "c2ln"},
                  "lastUpdated": 0,
                  "timeToLive": 0
                }
                """);
        NpcImport.Result result = NpcImport.fancy(file.toFile());
        StoredNpc guard = result.npcs().get("guard");
        assertEquals(StoredNpc.Skin.texture("dGV4", "c2ln"), guard.skin(), "the texture it cached for the UUID");
        assertEquals("fancynpcs.npc.guard.see", guard.permission());
        assertEquals(NpcPose.LYING, guard.pose());
        assertEquals("guard.talk", guard.interactions().permission(), "a permission check first becomes who may use it");
        assertEquals(List.of("right: [SERVER] pvp", "right: [DELAY] 2s", "right: console: say %player_name%",
                "left: [MESSAGE] hi"), guard.interactions().actions(), "what need_item guards is left out");
        StoredNpc old = result.npcs().get("old");
        assertEquals(List.of("console: say hi", "[MESSAGE] Hello"), old.interactions().actions());
        assertTrue(old.label().lines().isEmpty(), "no displayName is FancyNpcs' <empty>");
    }

    @Test
    @DisplayName("a missing file is nothing to import, not a failure")
    void missing() {
        assertTrue(NpcImport.citizens(folder.resolve("nope.yml").toFile()).npcs().isEmpty());
        assertTrue(NpcImport.fancy(folder.resolve("nope.yml").toFile()).npcs().isEmpty());
    }
}
