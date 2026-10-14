package net.exylia.exyliaDisplays.npc;

import net.exylia.exyliaDisplays.ExyliaDisplays;
import net.exylia.lib.effect.Ticks;
import net.exylia.lib.hologram.Hologram;
import net.exylia.lib.hologram.HologramConfig;
import net.exylia.lib.hologram.Holograms;
import net.exylia.lib.interaction.Interactions;
import net.exylia.lib.npc.NpcAnimation;
import net.exylia.lib.npc.NpcPerformance;
import net.exylia.lib.text.Values;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The interaction tokens only an NPC can answer: {@code [SAY]}, {@code [ANIMATE]} and
 * {@code [LOOK]}.
 *
 * <p>They act on the NPC that was clicked, which the renderer names in the interaction's data
 * under {@code npc}. A line with one of them run from anywhere else does nothing.
 */
final class NpcTokens {

    /** Bubble ids: one per NPC and listener, so two players talking to one NPC never cut each other off. */
    static final String BUBBLE = "npc-say:";

    private final ExyliaDisplays core;
    private final NpcRenderer renderer;

    NpcTokens(ExyliaDisplays core, NpcRenderer renderer) {
        this.core = core;
        this.renderer = renderer;
    }

    /** Registers the tokens; before any NPC's lines compile, or the lines naming them are skipped. */
    void register() {
        token("SAY", (player, npc, argument) -> say(player, npc, argument));
        token("ANIMATE", (player, npc, argument) -> renderer.live(npc).ifPresent(placed -> {
            String trimmed = argument == null ? "" : argument.trim();
            // A performance is skipped while another plays or nobody is near: nothing queues.
            if (NpcPerformance.isPerformance(trimmed)) placed.perform(NpcPerformance.parse(trimmed));
            else placed.play(animation(trimmed));
        }));
        token("LOOK", (player, npc, argument) -> renderer.turnTo(npc, player));
    }

    /**
     * What an {@code [ANIMATE]} line plays when it is no performance ({@code anim:}, {@code emote:},
     * {@code keys:}, which perform instead): a preset or moves ({@code wave}, {@code swing, 250ms, hurt}).
     * A bare token swings, and a line written before animations existed ({@code hurt}, anything
     * else) keeps its meaning: a flinch or a swing.
     */
    static NpcAnimation animation(String argument) {
        String trimmed = argument == null ? "" : argument.trim();
        NpcAnimation parsed = trimmed.isEmpty() ? NpcAnimation.none() : NpcAnimation.parse(trimmed);
        if (!parsed.isEmpty()) return parsed;
        return NpcAnimation.parse(trimmed.toLowerCase(Locale.ROOT).startsWith("hurt") ? "hurt" : "swing");
    }

    @FunctionalInterface
    private interface NpcToken {
        void run(Player player, String npc, String argument);
    }

    private void token(String name, NpcToken token) {
        try {
            Interactions.token(core.getPlugin(), name, (player, click, argument, data) -> {
                if (data.get("npc") instanceof String npc) token.run(player, npc, argument);
            });
        } catch (IllegalArgumentException taken) {
            core.getDebug().warn("[" + name + "] cannot be used in NPC interactions: " + taken.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // [SAY]
    // ------------------------------------------------------------------

    /**
     * What a {@code [SAY]} line asks for: {@code text} or {@code text;5s}.
     *
     * @param text    what the bubble reads
     * @param seconds how long it stays
     */
    record Say(String text, double seconds) {

        /** Reads a line's argument; a last part that is no length of time is part of the text. */
        static Say parse(String argument, double fallbackSeconds) {
            String trimmed = argument == null ? "" : argument.trim();
            int split = trimmed.lastIndexOf(';');
            if (split >= 0) {
                double seconds = Ticks.parseSeconds(trimmed.substring(split + 1).trim(), Double.NaN);
                if (!Double.isNaN(seconds) && seconds > 0) {
                    return new Say(trimmed.substring(0, split).trim(), seconds);
                }
            }
            return new Say(trimmed, fallbackSeconds);
        }
    }

    /**
     * A speech bubble over the NPC, seen only by whoever it answers.
     *
     * <p>A hologram rather than chat because it is where the player is already looking, and per
     * player so a crowd around a guide does not read every answer it gives everyone else. A new
     * bubble for the same player replaces the old one at once.
     */
    private void say(Player player, String name, String argument) {
        StoredNpc npc = renderer.shown(name).orElse(null);
        if (npc == null) return;
        Say say = Say.parse(argument, NpcsConfig.get().sayDuration().toMillis() / 1000.0);
        if (say.text().isEmpty()) return;
        Location at = npc.location().above(npc.topHeight() + 0.1).toLocation();
        if (at == null) return;
        UUID listener = player.getUniqueId();
        String id = BUBBLE + name + ":" + listener;
        String line = Values.of("text", say.text()).apply(NpcsMessages.get().sayBubble());
        HologramConfig config = new HologramConfig(true, HologramConfig.Kind.TEXT, List.of(line), "", "", false,
                npc.viewDistance(), 0, 0, 0, new HologramConfig.Properties(), new HologramConfig.Refresh(),
                new HologramConfig.Click());
        Hologram bubble = Holograms.show(core.getPlugin(), id, at, config,
                viewer -> viewer.getUniqueId().equals(listener));
        long ticks = Math.max(1, Math.round(say.seconds() * 20));
        // Only this bubble: a newer one under the same id lives out its own time.
        core.getTasks().runLater(ticks, () -> Holograms.get(core.getPlugin(), id)
                .filter(current -> current == bubble).ifPresent(Hologram::remove));
    }
}
