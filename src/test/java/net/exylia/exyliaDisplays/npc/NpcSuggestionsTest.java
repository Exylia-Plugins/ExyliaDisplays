package net.exylia.exyliaDisplays.npc;

import net.exylia.exyliaDisplays.common.Completion;
import net.exylia.lib.npc.NpcAnimations;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What /npc completes, read from the NPC and from what is typed. */
class NpcSuggestionsTest {

    private static final StoredNpc NPC = new StoredNpc()
            .withLabelLines(List.of("{primary}&lGUIDE", "{letters}Right-click"))
            .withScale(1.5)
            .withAnimations(new NpcAnimations("", "swing, 250ms, hurt", "", Duration.ofSeconds(20), "", 6, Duration.ofSeconds(30)));

    private static List<String> typed(String input) {
        List<String> words = Completion.words(input);
        return Completion.matching(words, NpcSuggestions.suggest(NPC, words, null, List.of("Steve")));
    }

    @Test
    @DisplayName("label lines, settings and animations offer what the NPC has now")
    void currentValues() {
        assertEquals(List.of("1", "2"), typed("/npc guide label remove "));
        assertEquals(List.of("{letters}Right-click"), typed("/npc guide label set {l"));
        assertTrue(typed("/npc guide scale ").contains("1.5"));
        assertEquals("swing, 250ms, hurt", typed("/npc guide animation right-click ").get(0));
        assertEquals(List.of("swing, 250ms, hurt"), typed("/npc guide animation right-click swing, 2"));
        assertEquals(List.of("20s"), typed("/npc guide animation idle-every "));
        assertTrue(typed("/npc guide skin ").contains("Steve"));
        assertEquals(List.of(), typed("/npc guide label add "));
    }
}
