package net.exylia.exyliaDisplays.npc;

import net.exylia.exyliaDisplays.common.store.Place;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** What a fine adjustment writes, and what it leaves to whoever else edited the NPC. */
class NpcAdjustTest {

    @Test
    @DisplayName("saving keeps the place, facing, scale and label height it adjusted, and everything else as saved now")
    void mergeTakesOnlyWhatItAdjusts() {
        StoredNpc original = new StoredNpc().withLabelLines(List.of("a"));
        StoredNpc work = NpcAdjust.moved(original, 1, 2, 3).withScale(2)
                .withLabel(new StoredNpc.Label(List.of("a"), 0.75));
        StoredNpc editedMeanwhile = original.withLabelLines(List.of("a", "b")).withPermission("vip");

        StoredNpc saved = NpcAdjust.merge(editedMeanwhile, work);

        assertEquals(List.of("a", "b"), saved.label().lines());
        assertEquals("vip", saved.permission());
        assertEquals(work.location(), saved.location());
        assertEquals(2, saved.scale());
        assertEquals(0.75, saved.label().heightOffset());
    }

    @Test
    @DisplayName("moving keeps the world and the facing")
    void moveKeepsFacing() {
        StoredNpc npc = new StoredNpc().withLocation(new Place("nether", 0.5, 64, 0.5, 90, 10));

        Place moved = NpcAdjust.moved(npc, 1, -1, 0.25).location();

        assertEquals(new Place("nether", 1.5, 63, 0.75, 90, 10), moved);
    }
}
