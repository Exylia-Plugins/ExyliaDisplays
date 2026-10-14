package net.exylia.exyliaDisplays.npc;

import net.exylia.lib.npc.NpcAnimation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What /npc <name> animation and [ANIMATE] read. */
class NpcAnimationEditTest {

    @Test
    @DisplayName("a trigger changes alone, an unknown trigger is refused, and an empty text clears it")
    void triggers() {
        StoredNpc npc = new StoredNpc().withAnimation("left-click", "wave").withAnimation("greet", "bow");
        assertEquals("wave", npc.animations().leftClick());
        assertEquals("bow", npc.animations().greet());
        assertEquals("", npc.animations().rightClick());
        assertEquals("", npc.withAnimation("left-click", "").animations().leftClick());
        assertNull(npc.withAnimation("jump", "wave"));
    }

    @Test
    @DisplayName("unknown words are reported, presets and moves are not")
    void unknownWords() {
        assertTrue(NpcDialogs.unknownWords("wave, 250ms, hurt").isEmpty());
        assertEquals(List.of("dance"), NpcDialogs.unknownWords("swing, dance"));
    }

    @Test
    @DisplayName("[ANIMATE] keeps its old spellings: bare swings, hurt flinches, nonsense swings")
    void animateToken() {
        assertEquals(NpcAnimation.Move.SWING, NpcTokens.animation("").steps().getFirst().move());
        assertEquals(NpcAnimation.Move.HURT, NpcTokens.animation("hurt").steps().getFirst().move());
        assertEquals(NpcAnimation.Move.SWING, NpcTokens.animation("whatever").steps().getFirst().move());
        assertEquals(3, NpcTokens.animation("wave").steps().size());
    }

    @Test
    @DisplayName("sitting lowers the head the label stands on")
    void sittingHead() {
        StoredNpc npc = new StoredNpc();
        assertTrue(npc.withSitting(true).headHeight() < npc.headHeight());
    }
}
