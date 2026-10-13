package net.exylia.exyliaDisplays.common.interaction;

import net.exylia.lib.interaction.Click;
import net.exylia.lib.interaction.Interaction;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * What stands between a click and an {@link Interaction}: who may use it and how often.
 *
 * <p>ExyliaLib runs the lines; this decides whether they run, so every module that puts something
 * clickable in the world gates it the same way. It answers with a {@link Verdict} rather than
 * telling the player itself, because each module words its refusals in its own messages file.
 *
 * <p>A click no line answers is {@link Verdict#IGNORED} and spends no cooldown: a left click on
 * something that only does things on right click must not make the right click wait.
 *
 * <p>Cooldowns are per player and in memory: they are a guard against spam, not a schedule, so a
 * restart forgetting them costs nothing.
 */
public final class InteractionGate {

    /** What a click came to. */
    public enum Verdict {
        /** The lines ran. */
        RUN,
        /** Nothing answers this click. */
        IGNORED,
        /** The player lacks the permission. */
        DENIED,
        /** The player used it too recently. */
        COOLING_DOWN
    }

    private final Interaction interaction;
    private final String permission;
    private final long cooldownMillis;
    /** When each player may use it again, in epoch milliseconds. */
    private final Map<UUID, Long> readyAt = new ConcurrentHashMap<>();

    /**
     * @param interaction    what runs
     * @param permission     what a player needs to use it; empty for everyone
     * @param cooldownMillis how long a player waits between two uses; 0 for no wait
     */
    public InteractionGate(Interaction interaction, String permission, long cooldownMillis) {
        this.interaction = interaction == null ? Interaction.empty() : interaction;
        this.permission = permission == null ? "" : permission.trim();
        this.cooldownMillis = Math.max(0, cooldownMillis);
    }

    /** A gate that never runs anything. */
    public static InteractionGate none() {
        return new InteractionGate(Interaction.empty(), "", 0);
    }

    public Interaction interaction() {
        return interaction;
    }

    /** Whether any click does something. */
    public boolean isEmpty() {
        return interaction.isEmpty();
    }

    /**
     * Gates a click and runs the lines when it passes.
     *
     * @param data the values the lines' {@code %name%} placeholders read
     */
    public Verdict click(Player player, Click click, Map<String, Object> data) {
        if (!interaction.handles(click)) {
            return Verdict.IGNORED;
        }
        Verdict verdict = check(player.getUniqueId(), player::hasPermission, System.currentTimeMillis());
        if (verdict == Verdict.RUN) {
            interaction.run(player, click, data);
        }
        return verdict;
    }

    /**
     * Whether a player may use it now, spending their cooldown when they may.
     *
     * <p>Kept free of {@link Player} so the rules can be checked without a server.
     *
     * @param player        who
     * @param hasPermission whether they hold a permission
     * @param now           the time, in epoch milliseconds
     */
    public Verdict check(UUID player, Predicate<String> hasPermission, long now) {
        if (!permission.isEmpty() && !hasPermission.test(permission)) {
            return Verdict.DENIED;
        }
        Long ready = readyAt.get(player);
        if (ready != null && now < ready) {
            return Verdict.COOLING_DOWN;
        }
        if (cooldownMillis > 0) {
            readyAt.put(player, now + cooldownMillis);
        } else if (ready != null) {
            readyAt.remove(player);
        }
        return Verdict.RUN;
    }

    /** How long a player still waits, in milliseconds; 0 when they need not. */
    public long remaining(UUID player, long now) {
        Long ready = readyAt.get(player);
        return ready == null ? 0 : Math.max(0, ready - now);
    }
}
