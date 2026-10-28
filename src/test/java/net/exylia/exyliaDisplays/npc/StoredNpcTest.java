package net.exylia.exyliaDisplays.npc;

import net.exylia.exyliaDisplays.common.store.Place;
import net.exylia.lib.npc.NpcPose;
import net.exylia.lib.npc.NpcSkin;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The edits commands and screens share, and the geometry everything above an NPC stands on. */
class StoredNpcTest {

    @Test
    @DisplayName("a slot is filled, replaced under any of its spellings, and emptied")
    void equipment() {
        StoredNpc npc = new StoredNpc().withEquipment(Map.of("helmet", "IRON_HELMET"));
        StoredNpc crowned = npc.equip(EquipmentSlot.HEAD, "GOLDEN_HELMET");
        assertEquals(Map.of("head", "GOLDEN_HELMET"), crowned.equipment());
        assertEquals(Map.of(), crowned.equip(EquipmentSlot.HEAD, "").equipment());
        assertEquals(Map.of(), crowned.equip(EquipmentSlot.HEAD, "AIR").equipment());
        assertEquals(EquipmentSlot.OFF_HAND, StoredNpc.slot("offhand"));
        assertEquals(EquipmentSlot.HAND, StoredNpc.slot("MAINHAND"));
        assertNull(StoredNpc.slot("wings"));
    }

    @Test
    @DisplayName("skins are read from a command, and drawn as heads in the list")
    void skins() {
        assertEquals(StoredNpc.Skin.player("Notch"), StoredNpc.Skin.parse("Notch"));
        assertEquals(StoredNpc.Skin.mirror(), StoredNpc.Skin.parse("@MIRROR"));
        assertEquals(new StoredNpc.Skin(), StoredNpc.Skin.parse(""));
        assertEquals(new StoredNpc.Skin(), StoredNpc.Skin.parse("@none"));
        assertNull(StoredNpc.Skin.parse("not a name!"));
        assertEquals("playerhead-Notch", StoredNpc.Skin.player("Notch").head());
        assertEquals("basehead-abc", StoredNpc.Skin.texture("abc", "").head());
        assertEquals("PLAYER_HEAD", StoredNpc.Skin.mirror().head());
    }

    @Test
    @DisplayName("a placeholder skin wears whoever it names, the fallback while it names nobody")
    void placeholderSkins() {
        StoredNpc.Skin top = StoredNpc.Skin.parse("%top_kills_1_name%");
        assertTrue(top.isPlaceholder());
        assertFalse(StoredNpc.Skin.player("Notch").isPlaceholder());
        StoredNpc.Skin backed = top.withFallback("Notch");
        assertEquals(NpcSkin.player("Steve"), backed.wearing("Steve"));
        assertEquals(NpcSkin.player("Notch"), backed.wearing(null));
        assertEquals(NpcSkin.none(), top.wearing(null));
        assertEquals("playerhead-Notch", backed.head());
        assertEquals("PLAYER_HEAD", top.head());
        assertEquals("Steve", StoredNpc.Skin.account("Steve"));
        assertEquals("Steve", StoredNpc.Skin.account("<gold>Steve "));
        assertNull(StoredNpc.Skin.account("%top_kills_1_name%"));
        assertNull(StoredNpc.Skin.account("No one yet"));
        assertNull(StoredNpc.Skin.account(""));
    }

    @Test
    @DisplayName("glow colours are read with or without #, and anything else is none")
    void glow() {
        assertEquals(0x8a51c4, new StoredNpc().withGlow("#8a51c4").glowRgb());
        assertEquals(0x8a51c4, new StoredNpc().withGlow("8A51C4").glowRgb());
        assertEquals(-1, new StoredNpc().withGlow("").glowRgb());
        assertEquals(-1, new StoredNpc().withGlow("purple").glowRgb());
    }

    @Test
    @DisplayName("labels stand on its head, scaled and posed; bubbles and holograms above the label")
    void heights() {
        StoredNpc npc = new StoredNpc().withLabelLines(List.of("a", "b"));
        assertEquals(1.8, npc.headHeight(), 1e-9);
        assertEquals(2.05, npc.labelHeight(), 1e-9);
        assertEquals(2.65, npc.topHeight(), 1e-9);
        assertEquals(3.6, npc.withScale(2).headHeight(), 1e-9);
        assertEquals(0.6, npc.withPose(NpcPose.LYING).headHeight(), 1e-9);
    }

    @Test
    @DisplayName("actions are added and removed by index; nothing outside the list is")
    void actions() {
        StoredNpc npc = new StoredNpc().addAction("a").addAction("b");
        assertEquals(List.of("a", "b"), npc.interactions().actions());
        assertEquals(List.of("b"), npc.removeAction(0).interactions().actions());
        assertTrue(npc.hasAction(1));
        assertFalse(npc.hasAction(2));
        assertFalse(npc.hasAction(-1));
    }

    @Test
    @DisplayName("a place turns to face a point and back, folding its yaw into -180..180")
    void facing() {
        Place origin = new Place("world", 0, 64, 0, 0, 30);
        assertEquals(0, origin.facing(0, 5).yaw());     // south
        assertEquals(90, origin.facing(-5, 0).yaw());   // west
        assertEquals(-90, origin.facing(5, 0).yaw());   // east
        assertEquals(180, Math.abs(origin.facing(0, -5).yaw()));
        assertEquals(0, origin.facing(0, 5).pitch());
        assertEquals(-90, origin.facingBack(90).yaw());
        assertEquals(origin, origin.facing(0, 0));
        assertEquals(new Place("world", 3.5, 64, -1.5, 0, 30), new Place("world", 3.2, 64, -1.9, 0, 30).centred());
    }

    @Test
    @DisplayName("a [SAY] line names its time last, and a text with semicolons keeps them")
    void say() {
        assertEquals(new NpcTokens.Say("Hello", 5), NpcTokens.Say.parse("Hello;5s", 3));
        assertEquals(new NpcTokens.Say("Hello", 3), NpcTokens.Say.parse("Hello", 3));
        assertEquals(new NpcTokens.Say("a;b", 1.5), NpcTokens.Say.parse("a;b;1.5", 3));
        assertEquals(new NpcTokens.Say("a; b c", 3), NpcTokens.Say.parse("a; b c", 3));
    }

    @Test
    @DisplayName("a cooldown is typed as a duration or bare seconds")
    void cooldowns() {
        assertEquals(2000, NpcDialogs.cooldown("2s"));
        assertEquals(500, NpcDialogs.cooldown("500ms"));
        assertEquals(1500, NpcDialogs.cooldown("1.5"));
        assertEquals(0, NpcDialogs.cooldown("0"));
        assertEquals(-1, NpcDialogs.cooldown("soon"));
        assertEquals(List.of("a", "", "b"), NpcDialogs.lines("a\n\nb\n\n"));
    }
}
