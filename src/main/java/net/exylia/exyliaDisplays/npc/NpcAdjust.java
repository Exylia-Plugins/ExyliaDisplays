package net.exylia.exyliaDisplays.npc;

import net.exylia.exyliaDisplays.ExyliaDisplays;
import net.exylia.exyliaDisplays.common.store.Place;
import net.exylia.lib.effect.Effects;
import net.exylia.lib.text.Values;
import net.exylia.lib.tuner.Tuner;
import net.exylia.lib.tuner.TunerAxis;
import net.exylia.lib.tuner.TunerEnd;
import net.exylia.lib.tuner.TunerSession;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fine adjustment: a player moves, turns and scales an NPC with the mouse wheel while looking at
 * it, through ExyliaLib's {@link Tuner}. The NPC twin of {@code HologramAdjust}.
 *
 * <p>Nothing is written while the wheel turns: the adjusted values live in a working copy the
 * renderer lays over what the file says ({@link #preview}), so the NPC, its label and its linked
 * hologram move on every notch while npcs.yml and holograms.yml stay untouched. The file is written
 * once, through the store, when the session ends keeping its values; a cancel just stops laying the
 * copy over.
 */
public final class NpcAdjust {

    private static final String KEY = "npc:";

    /** The working copy of each NPC being adjusted, by name. */
    private final Map<String, StoredNpc> working = new ConcurrentHashMap<>();

    /** What the renderer draws: the file's version, with the adjustment laid over it while there is one. */
    public StoredNpc preview(String name, StoredNpc saved) {
        StoredNpc work = saved == null ? null : working.get(name);
        return work == null ? saved : merge(saved, work);
    }

    /** {@code /npc <name> adjust}: starts, or saves when the player is already adjusting it. */
    public void toggle(Player player, String name) {
        Optional<TunerSession> mine = mine(player, name);
        if (mine.isPresent()) {
            mine.get().save();
        } else {
            start(player, name);
        }
    }

    /** Ends the player's adjustment of an NPC, keeping or undoing it. */
    public void finish(Player player, String name, boolean keep) {
        Optional<TunerSession> mine = mine(player, name);
        if (mine.isEmpty()) {
            send(player, messages().adjust().notAdjusting(), Values.of().putText("name", name));
        } else if (keep) {
            mine.get().save();
        } else {
            mine.get().cancel();
        }
    }

    /** An NPC was deleted or renamed: whoever adjusts it stops, with nothing to save. */
    public void removed(String name) {
        if (working.containsKey(name)) {
            byKey(name).ifPresent(TunerSession::abort);
        }
    }

    /** After a reload, stops the adjustments of NPCs the file no longer has. */
    public void prune() {
        List.copyOf(working.keySet()).stream()
                .filter(name -> !NpcModule.get().store().exists(name))
                .forEach(this::removed);
    }

    /** Ends every adjustment keeping its values, before the module stops. */
    public void saveAll() {
        List.copyOf(working.keySet()).forEach(name -> byKey(name).ifPresent(TunerSession::save));
    }

    private void start(Player player, String name) {
        NpcModule module = NpcModule.get();
        StoredNpc saved = module.store().get(name).orElse(null);
        if (saved == null) {
            send(player, messages().notFound(), Values.of().putText("name", name));
            return;
        }
        if (saved.location().toLocation() == null) {
            send(player, messages().worldMissing(), Values.of("world", saved.location().world()));
            return;
        }
        Optional<TunerSession> other = byKey(name);
        if (other.isPresent()) {
            send(player, messages().adjust().busy(), Values.of().putText("name", name)
                    .putText("player", other.get().player().getName()));
            return;
        }
        NpcsMessages.Adjust text = messages().adjust();
        StoredNpc[] work = {saved};
        Tuner tuner = Tuner.of(ExyliaDisplays.getInstance().getPlugin(), name)
                .key(KEY + name)
                .hud(text.hud())
                .hints(text.hints())
                .axis(TunerAxis.blocks(text.x(), () -> work[0].location().x(),
                        x -> work[0] = moved(work[0], x - work[0].location().x(), 0, 0)))
                .axis(TunerAxis.blocks(text.y(), () -> work[0].location().y(),
                        y -> work[0] = moved(work[0], 0, y - work[0].location().y(), 0))
                        .snap(y -> Math.round(y * 2) / 2.0))
                .axis(TunerAxis.blocks(text.z(), () -> work[0].location().z(),
                        z -> work[0] = moved(work[0], 0, 0, z - work[0].location().z())))
                .axis(TunerAxis.relative(text.forward(), distance -> {
                    // Along where the player looks now, level: turning between notches steers it.
                    double yaw = Math.toRadians(player.getLocation().getYaw());
                    work[0] = moved(work[0], -Math.sin(yaw) * distance, 0, Math.cos(yaw) * distance);
                }))
                .axis(TunerAxis.degrees(text.yaw(), () -> work[0].location().yaw(),
                        yaw -> work[0] = turned(work[0], yaw, work[0].location().pitch())))
                .axis(TunerAxis.degrees(text.pitch(), () -> work[0].location().pitch(),
                        pitch -> work[0] = turned(work[0], work[0].location().yaw(), pitch))
                        .range(-90, 90))
                .axis(TunerAxis.of(text.scale(), () -> work[0].scale(),
                                scale -> work[0] = work[0].withScale(scale))
                        .steps(0.05, 0.1, 0.25, 0.5).step(1).range(0.0625, 16).unit("x")
                        .snap(scale -> Math.max(0.25, Math.round(scale * 4) / 4.0)))
                .axis(TunerAxis.of(text.labelHeight(), () -> work[0].label().heightOffset(),
                                height -> work[0] = work[0].withLabel(new StoredNpc.Label(work[0].label().lines(), height)))
                        .steps(0.01, 0.05, 0.1, 0.25).step(1).range(-2, 5)
                        .snap(height -> Math.round(height * 20) / 20.0))
                .onChange(session -> {
                    working.put(name, work[0]);
                    StoredNpc current = module.store().get(name).orElse(null);
                    if (current == null) {
                        session.abort();
                        return;
                    }
                    module.renderer().apply(name, current);
                    spark(session.player(), work[0]);
                })
                .onEnd((session, end) -> ended(session.player(), name, end));
        working.put(name, saved);
        if (tuner.start(player).isEmpty()) {
            working.remove(name);
            return;
        }
        player.closeInventory();
        send(player, text.started(), Values.of().putText("name", name));
    }

    private void ended(Player player, String name, TunerEnd end) {
        StoredNpc work = working.remove(name);
        NpcModule module = NpcModule.get();
        if (work == null || module == null) return;
        if (end == TunerEnd.ABORTED) {
            if (player.isOnline()) send(player, messages().adjust().gone(), Values.of().putText("name", name));
            return;
        }
        if (end.kept()) {
            module.store().edit(name, current -> merge(current, work));
        } else {
            module.renderer().apply(name, module.store().get(name).orElse(null));
        }
        if (!player.isOnline() || end == TunerEnd.DISABLED) return;
        send(player, end.kept() ? messages().adjust().saved() : messages().adjust().cancelled(),
                Values.of().putText("name", name));
    }

    /** The saved NPC with what the adjustment changes taken from the working copy. */
    static StoredNpc merge(StoredNpc base, StoredNpc work) {
        return base.withLocation(work.location()).withScale(work.scale())
                .withLabel(new StoredNpc.Label(base.label().lines(), work.label().heightOffset()));
    }

    static StoredNpc moved(StoredNpc npc, double dx, double dy, double dz) {
        Place at = npc.location();
        return npc.withLocation(new Place(at.world(), at.x() + dx, at.y() + dy, at.z() + dz, at.yaw(), at.pitch()));
    }

    private static StoredNpc turned(StoredNpc npc, double yaw, double pitch) {
        Place at = npc.location();
        return npc.withLocation(new Place(at.world(), at.x(), at.y(), at.z(), yaw, pitch));
    }

    /** A pinpoint at its feet, for the adjusting player only, so the exact spot is visible. */
    private static void spark(Player player, StoredNpc npc) {
        Location at = npc.location().toLocation();
        if (at != null && at.getWorld() == player.getWorld()) {
            Effects.particle("END_ROD").at(at).count(1).show(player);
        }
    }

    private static Optional<TunerSession> byKey(String name) {
        return Tuner.byKey(ExyliaDisplays.getInstance().getPlugin(), KEY + name);
    }

    private static Optional<TunerSession> mine(Player player, String name) {
        return byKey(name).filter(session -> session.player().getUniqueId().equals(player.getUniqueId()));
    }

    private static NpcsMessages messages() {
        return NpcsMessages.get();
    }

    private static void send(Player player, String line, Values values) {
        ExyliaDisplays.getInstance().getMessages().send(player, line, values);
    }
}
