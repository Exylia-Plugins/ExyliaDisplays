package net.exylia.exyliaDisplays.hologram;

import net.exylia.exyliaDisplays.common.store.Names;
import net.exylia.exyliaDisplays.common.store.Position;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The line edits commands and screens share. */
class StoredHologramTest {

    private static StoredHologram lines(String... lines) {
        return new StoredHologram().withLines(List.of(lines));
    }

    @Test
    @DisplayName("lines are added, inserted, replaced, removed and moved by index")
    void lineEdits() {
        StoredHologram hologram = lines("a", "b", "c");
        assertEquals(List.of("a", "b", "c", "d"), hologram.addLine("d").lines());
        assertEquals(List.of("x", "a", "b", "c"), hologram.insertLine(0, "x").lines());
        assertEquals(List.of("a", "b", "c", "x"), hologram.insertLine(99, "x").lines());
        assertEquals(List.of("a", "x", "c"), hologram.setLine(1, "x").lines());
        assertEquals(List.of("a", "c"), hologram.removeLine(1).lines());
        assertEquals(List.of("b", "c", "a"), hologram.moveLine(0, 2).lines());
        assertEquals(List.of("c", "a", "b"), hologram.moveLine(2, 0).lines());
        assertTrue(hologram.hasLine(2));
        assertFalse(hologram.hasLine(3));
    }

    @Test
    @DisplayName("a line with a placeholder is drawn for each viewer, so it can be about them")
    void placeholdersArePersonal() {
        assertTrue(lines("{primary}Welcome", "%player_name%").toConfig().perPlayer());
        assertTrue(lines("Online: %server_online%").toConfig().perPlayer());
        assertFalse(lines("50% off", "100%").toConfig().perPlayer());
        assertTrue(lines("plain").withPerPlayer(true).toConfig().perPlayer());
    }

    @Test
    @DisplayName("a hologram made where a player stands faces them")
    void facesItsCreator() {
        org.bukkit.Location looking = new org.bukkit.Location(null, 1, 64, 1, 180f, 30f);
        Position at = Position.facing(looking);
        assertEquals(0f, at.yaw(), "a player looking north reads a hologram facing south");
        assertEquals(0f, at.pitch());
        assertEquals(-90f, Position.facing(new org.bukkit.Location(null, 0, 0, 0, 90f, 0f)).yaw());
    }

    @Test
    @DisplayName("names are checked, and an imported one is made usable")
    void names() {
        assertTrue(Names.valid("spawn_shop-2"));
        assertFalse(Names.valid("Spawn Shop"));
        assertFalse(Names.valid(""));
        assertEquals("spawn_shop", Names.sanitize("Spawn Shop!"));
        assertEquals("unnamed", Names.sanitize("!!!"));
        assertEquals(32, Names.sanitize("x".repeat(40)).length());
    }

    @Test
    @DisplayName("a position is kept to a thousandth and centres on its block")
    void positions() {
        Position at = new Position("world", 10.12345, 64, -3.7).plus(0, 0, 0);
        assertEquals(10.123, at.x());
        assertEquals(new Position("world", 10.5, 64, -3.5), at.centred());
    }
}
