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
        assertEquals(List.of("a", "b", "b", "c"), hologram.duplicateLine(1).lines());
        assertEquals(List.of("b", "c", "a"), hologram.moveLine(0, 2).lines());
        assertEquals(List.of("c", "a", "b"), hologram.moveLine(2, 0).lines());
        assertTrue(hologram.hasLine(2));
        assertFalse(hologram.hasLine(3));
    }

    @Test
    @DisplayName("lines is page 1, pages are edited one at a time and removing the first promotes the second")
    void pages() {
        StoredHologram hologram = lines("a").addPage().onPage(1, view -> view.addLine("b").addLine("c"));
        assertEquals(2, hologram.pageCount());
        assertEquals(List.of("a"), hologram.lines());
        assertEquals(List.of("b", "c"), hologram.pageView(1).lines());
        assertEquals(List.of(List.of("a"), List.of("c", "b")),
                hologram.onPage(1, view -> view.moveLine(0, 1)).allPages());
        assertEquals(hologram, hologram.onPage(5, view -> view.addLine("x")), "a page that is not there is left alone");

        StoredHologram clicked = hologram.withPages(List.of(new net.exylia.lib.hologram.HologramConfig.Page(
                List.of("b", "c"), List.of("[PAGE] 1"))));
        StoredHologram promoted = clicked.removePage(0);
        assertEquals(List.of("b", "c"), promoted.lines());
        assertEquals(List.of("[PAGE] 1"), promoted.click().commands());
        assertEquals(1, promoted.pageCount());
        assertEquals(List.of("a"), clicked.removePage(1).allPages().get(0));
        assertEquals(List.of("[PAGE] 1"), clicked.withPageLines(List.of(List.of("z"), List.of("y"))).pages().get(0).click(),
                "replacing the lines keeps each page's click actions");
        assertEquals(2, clicked.toConfig().allPages().size());
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

    @org.junit.jupiter.api.Test
    void turningACentreHologramMakesItFixed() {
        StoredHologram turned = new StoredHologram().withFacing(90f, -20f);
        org.junit.jupiter.api.Assertions.assertEquals(90f, turned.location().yaw());
        org.junit.jupiter.api.Assertions.assertEquals(-20f, turned.location().pitch());
        org.junit.jupiter.api.Assertions.assertEquals("FIXED", turned.appearance().billboard(), "CENTER would never show it");
        StoredHologram vertical = new StoredHologram().withAppearance(new net.exylia.lib.hologram.HologramConfig.Properties(
                "VERTICAL", "CENTER", 1, 1, 1, true, false, 200, 0.25, 255, false, "#00000000", -1, false)).withFacing(45f, 0f);
        org.junit.jupiter.api.Assertions.assertEquals("VERTICAL", vertical.appearance().billboard(), "only CENTER is changed");
    }

    @Test
    @DisplayName("show and hide edit the list the visibility mode reads, and clearing empties only that one")
    void visibility() {
        java.util.UUID ana = java.util.UUID.randomUUID();
        java.util.UUID bob = java.util.UUID.randomUUID();
        StoredHologram.Visibility all = new StoredHologram.Visibility();
        assertTrue(all.sees(ana));
        StoredHologram.Visibility hidden = all.hide(List.of(ana)).hide(List.of(ana));
        assertFalse(hidden.sees(ana));
        assertTrue(hidden.sees(bob));
        assertEquals(List.of(ana.toString()), hidden.hiddenFrom(), "hiding twice keeps one entry");
        assertTrue(hidden.show(List.of(ana)).sees(ana));

        StoredHologram.Visibility manual = hidden.withMode("MANUAL");
        assertFalse(manual.sees(bob), "manual shows it to nobody it was not shown to");
        StoredHologram.Visibility shown = manual.show(List.of(bob));
        assertTrue(shown.sees(bob));
        assertEquals(List.of(bob.toString()), shown.players());
        assertFalse(shown.hide(List.of(bob)).sees(bob));
        assertEquals(List.of(), shown.cleared().shownTo());
        assertEquals(List.of(ana.toString()), shown.cleared().hiddenFrom(), "the other mode's list is kept");
        assertEquals("all", new StoredHologram.Visibility("typo", null, null).mode());
    }

    @Test
    @DisplayName("an attachment reads its UUID, ignores a typo, and its offset raises the drawn hologram")
    void attachment() {
        java.util.UUID mob = java.util.UUID.randomUUID();
        StoredHologram.Attachment attach = new StoredHologram.Attachment().withEntity(mob).withOffset(1.25);
        assertEquals(mob, attach.target());
        assertEquals(null, new StoredHologram.Attachment("not-a-uuid", 0).target());
        assertEquals(null, attach.withEntity(null).target());
        StoredHologram hologram = lines("a").withAttach(attach);
        assertEquals(1.25, hologram.toConfig(hologram.attach().offset()).offsetY());
        assertEquals(0, hologram.toConfig().offsetY());
    }
}
