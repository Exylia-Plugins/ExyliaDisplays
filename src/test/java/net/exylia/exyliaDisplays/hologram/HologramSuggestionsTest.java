package net.exylia.exyliaDisplays.hologram;

import net.exylia.exyliaDisplays.common.Completion;
import net.exylia.exyliaDisplays.common.store.Position;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What /holo completes, read from the hologram and from what is typed. */
class HologramSuggestionsTest {

    private static final String STYLED = "<size:2><shine:highlight,speed=10>{primary}&lTHE ARENA";
    private static final StoredHologram HOLOGRAM = new StoredHologram()
            .withLines(List.of(STYLED, "second", "third"))
            .addPage().onPage(1, page -> page.withLines(List.of("other")));

    private static List<String> typed(String input) {
        List<String> words = Completion.words(input);
        return Completion.matching(words, HologramSuggestions.suggest(HOLOGRAM, words, null, Set.of("title")));
    }

    @Test
    @DisplayName("line numbers are the lines of the page being edited")
    void lineNumbers() {
        assertEquals(List.of("1", "2", "3"), typed("/holo arena setline "));
        assertEquals(List.of("1", "2", "3", "4"), typed("/holo arena insertline "));
        assertEquals(List.of("2"), typed("/holo arena moveline 1 2"));
        assertEquals(List.of("1"), typed("/holo arena page 2 removeline "));
        assertEquals(List.of("1", "2"), typed("/holo arena page "));
        assertEquals(List.of("1", "2"), typed("/holo arena page remove "));
        assertEquals(List.of(), typed("/holo arena addline "));
    }

    @Test
    @DisplayName("setline offers the line as it is, tags included, while it is typed over")
    void lineText() {
        assertEquals(List.of(STYLED), typed("/holo arena setline 1 "));
        assertEquals(List.of(STYLED), typed("/holo arena setline 1 <size:2><shine:highlight,speed=10>{primary}&lTHE AR"));
        assertEquals(List.of(), typed("/holo arena setline 1 nothing like it"));
        assertEquals(List.of("other"), typed("/holo arena page 2 setline 1 "));
        assertEquals(List.of(), typed("/holo arena setline 9 "));
    }

    @Test
    @DisplayName("a line tag offers its current value first, then what it takes, and reset when set")
    void tagValues() {
        List<String> shine = HologramSuggestions.tagValues(STYLED, "shine", Set.of(), Set.of());
        assertEquals("highlight,speed=10", shine.get(0));
        assertTrue(shine.contains("on") && shine.contains("reset"));
        assertEquals(List.of("left", "center", "right"), HologramSuggestions.tagValues("plain", "align", Set.of(), Set.of()));
        assertEquals(List.of("2", "0.5", "1", "1.5", "reset"), HologramSuggestions.tagValues(STYLED, "size", Set.of(), Set.of()));
        assertEquals(List.of("{primary}&lTHE ARENA"), HologramSuggestions.tagValues(STYLED, "text", Set.of(), Set.of()));
        assertEquals(List.of("vip.rank", "reset"), HologramSuggestions.tagValues("<perm:vip.rank>hi", "permission", Set.of(), Set.of()));
        assertEquals(List.of("title"), typed("/holo arena line 1 preset "));
    }

    @Test
    @DisplayName("settings offer their current value, and move offers where the player stands")
    void currentValues() {
        assertTrue(typed("/holo arena viewdistance ").contains(HologramViews.number(HOLOGRAM.viewDistance())));
        assertEquals(List.of("center", "vertical", "horizontal", "fixed"), typed("/holo arena billboard "));
        Position here = new Position("world", 10.04, 64, -3.26, 90f, 0f);
        List<String> x = Completion.matching(Completion.words("holo arena move "),
                HologramSuggestions.suggest(HOLOGRAM, Completion.words("holo arena move "), here, Set.of()));
        assertTrue(x.contains("10") && x.contains("10 64 -3.3"), x.toString());
        assertEquals(List.of("arena"), typed("/holo arena rename "));
    }
}
