package net.exylia.exyliaHolograms.store;

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
    @DisplayName("names are checked, and an imported one is made usable")
    void names() {
        assertTrue(HologramStore.validName("spawn_shop-2"));
        assertFalse(HologramStore.validName("Spawn Shop"));
        assertFalse(HologramStore.validName(""));
        assertEquals("spawn_shop", HologramStore.sanitize("Spawn Shop!"));
        assertEquals("hologram", HologramStore.sanitize("!!!"));
        assertEquals(32, HologramStore.sanitize("x".repeat(40)).length());
    }

    @Test
    @DisplayName("a position is kept to a thousandth and centres on its block")
    void positions() {
        Position at = new Position("world", 10.12345, 64, -3.7).plus(0, 0, 0);
        assertEquals(10.123, at.x());
        assertEquals(new Position("world", 10.5, 64, -3.5), at.centred());
    }
}
