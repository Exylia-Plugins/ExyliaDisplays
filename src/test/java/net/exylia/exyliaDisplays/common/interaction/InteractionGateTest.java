package net.exylia.exyliaDisplays.common.interaction;

import net.exylia.lib.interaction.Interaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Who may use an interaction and how often, without a server. */
class InteractionGateTest {

    private static final UUID ALEX = UUID.randomUUID();
    private static final UUID SAM = UUID.randomUUID();

    @Test
    @DisplayName("a permission keeps out whoever lacks it, and spends nothing")
    void permission() {
        InteractionGate gate = new InteractionGate(Interaction.empty(), "shop.use", 1000);
        assertEquals(InteractionGate.Verdict.DENIED, gate.check(ALEX, Set.<String>of()::contains, 0));
        assertEquals(0, gate.remaining(ALEX, 0));
        assertEquals(InteractionGate.Verdict.RUN, gate.check(ALEX, Set.of("shop.use")::contains, 0));
    }

    @Test
    @DisplayName("a cooldown is per player and runs out")
    void cooldown() {
        InteractionGate gate = new InteractionGate(Interaction.empty(), "", 2000);
        assertEquals(InteractionGate.Verdict.RUN, gate.check(ALEX, node -> true, 1000));
        assertEquals(InteractionGate.Verdict.COOLING_DOWN, gate.check(ALEX, node -> true, 2500));
        assertEquals(500, gate.remaining(ALEX, 2500));
        assertEquals(InteractionGate.Verdict.RUN, gate.check(SAM, node -> true, 2500));
        assertEquals(InteractionGate.Verdict.RUN, gate.check(ALEX, node -> true, 3000));
    }

    @Test
    @DisplayName("without a cooldown every click runs")
    void noCooldown() {
        InteractionGate gate = new InteractionGate(Interaction.empty(), null, 0);
        assertEquals(InteractionGate.Verdict.RUN, gate.check(ALEX, node -> false, 0));
        assertEquals(InteractionGate.Verdict.RUN, gate.check(ALEX, node -> false, 0));
    }

    @Test
    @DisplayName("players whose cooldown ran out are forgotten, the ones still waiting are not")
    void prunesExpired() {
        InteractionGate gate = new InteractionGate(Interaction.empty(), "", 1000);
        for (int i = 0; i < 63; i++) {
            gate.check(UUID.randomUUID(), node -> true, 0);
        }
        assertEquals(63, gate.tracked());
        gate.check(ALEX, node -> true, 1500);
        assertEquals(1, gate.tracked());
        assertEquals(InteractionGate.Verdict.COOLING_DOWN, gate.check(ALEX, node -> true, 2000));
    }
}
