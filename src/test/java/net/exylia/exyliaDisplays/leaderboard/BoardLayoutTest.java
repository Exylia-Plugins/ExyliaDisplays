package net.exylia.exyliaDisplays.leaderboard;

import net.exylia.lib.hologram.HologramLine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A leaderboard's pages, places and buttons. */
class BoardLayoutTest {

    private static BoardLayout layout(List<String> stats, List<String> spans) {
        BoardSection board = new BoardSection().withEnabled(true).withStats(stats).withSpans(spans).withRows(3);
        return BoardLayout.of(board, id -> id.toUpperCase(), id -> "<" + id + ">");
    }

    @Test
    @DisplayName("one page per stat and period, the stat's periods side by side")
    void pages() {
        BoardLayout layout = layout(List.of("kills", "deaths"), List.of("all-time", "weekly", "daily"));
        assertEquals(6, layout.pages().size());
        assertTrue(layout.pages().get(0).getFirst().contains("TOP KILLS"));
        assertTrue(layout.pages().get(4).getFirst().contains("TOP DEATHS"));
        assertTrue(String.join("\n", layout.pages().get(4)).contains("%exyliadisplays_lb_deaths:weekly:1:name%"));
    }

    @Test
    @DisplayName("every place reads the board, and swaps for its empty line when nobody holds it")
    void places() {
        List<String> page = layout(List.of("kills"), List.of("weekly")).pages().getFirst();
        HologramLine taken = page.stream().map(HologramLine::parse)
                .filter(line -> line.text().contains("kills:weekly:2:name")).findFirst().orElseThrow();
        assertEquals("%exyliadisplays_lb_kills:weekly:2:taken% == 1", taken.condition());
        assertTrue(page.stream().map(HologramLine::parse)
                .anyMatch(line -> "%exyliadisplays_lb_kills:weekly:2:taken% == 0".equals(line.condition())));
        assertTrue(page.stream().anyMatch(line -> line.contains("kills:weekly:me:rank% > 0")));
    }

    @Test
    @DisplayName("the buttons turn to the next and previous stat or period, wrapping")
    void buttons() {
        BoardLayout layout = layout(List.of("kills", "deaths"), List.of("all-time", "weekly"));
        // Page 2 (index 1) is kills, weekly: the next stat is deaths weekly (page 4), the period wraps to page 1.
        assertEquals(List.of("left: [PAGE] 4", "right: [PAGE] 4"), layout.buttons().get("lb1stat"));
        assertEquals(List.of("left: [PAGE] 1", "right: [PAGE] 1"), layout.buttons().get("lb1span"));
        // Page 3 (index 2) is deaths, all time: its period button stays on deaths.
        assertEquals(List.of("left: [PAGE] 4", "right: [PAGE] 4"), layout.buttons().get("lb2span"));
        assertEquals(List.of("left: [PAGE] 1", "right: [PAGE] 1"), layout.buttons().get("lb2stat"));
        assertTrue(layout.pages().get(1).stream().anyMatch(line -> line.startsWith("<button:lb1stat>")));
    }

    @Test
    @DisplayName("a board with one stat and one period has no buttons")
    void single() {
        BoardLayout layout = layout(List.of("kills"), List.of("all-time"));
        assertEquals(1, layout.pages().size());
        assertTrue(layout.buttons().isEmpty());
    }
}
