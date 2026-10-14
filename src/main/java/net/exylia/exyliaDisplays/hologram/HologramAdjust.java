package net.exylia.exyliaDisplays.hologram;

import net.exylia.exyliaDisplays.ExyliaDisplays;
import net.exylia.lib.effect.Effects;
import net.exylia.lib.hologram.HologramConfig;
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
 * Fine adjustment: a player moves, turns and scales a hologram with the mouse wheel while looking
 * at it, through ExyliaLib's {@link Tuner}.
 *
 * <p>Nothing is written while the wheel turns. The adjusted values live in a working copy that the
 * renderer lays over what the file says ({@link #preview}), so the hologram moves on every notch,
 * a hand edit or another admin's change to the same hologram still shows underneath, and the file
 * watcher has nothing to fight. The file is written once, through the store, when the session ends
 * keeping its values; a cancel just stops laying the copy over.
 */
public final class HologramAdjust {

    private static final String KEY = "hologram:";

    /** The working copy of each hologram being adjusted, by name. */
    private final Map<String, StoredHologram> working = new ConcurrentHashMap<>();

    /**
     * What the renderer draws for a hologram: the file's version, with the adjusted position and
     * size laid over it while somebody is adjusting it.
     */
    public StoredHologram preview(String name, StoredHologram saved) {
        StoredHologram work = saved == null ? null : working.get(name);
        return work == null ? saved : merge(saved, work);
    }

    /** {@code /holo <name> adjust}: starts, or saves when the player is already adjusting it. */
    public void toggle(Player player, String name) {
        Optional<TunerSession> mine = mine(player, name);
        if (mine.isPresent()) {
            mine.get().save();
        } else {
            start(player, name);
        }
    }

    /** Ends the player's adjustment of a hologram, keeping or undoing it. */
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

    /** A hologram was deleted or renamed: whoever adjusts it stops, with nothing to save. */
    public void removed(String name) {
        if (working.containsKey(name)) {
            byKey(name).ifPresent(TunerSession::abort);
        }
    }

    /** After a reload, stops the adjustments of holograms the file no longer has. */
    public void prune() {
        List.copyOf(working.keySet()).stream()
                .filter(name -> !HologramModule.get().store().exists(name))
                .forEach(this::removed);
    }

    /** Ends every adjustment keeping its values, before the module stops. */
    public void saveAll() {
        List.copyOf(working.keySet()).forEach(name -> byKey(name).ifPresent(TunerSession::save));
    }

    private void start(Player player, String name) {
        HologramModule module = HologramModule.get();
        StoredHologram saved = module.store().get(name).orElse(null);
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
        HologramsMessages.Adjust text = messages().adjust();
        StoredHologram[] work = {saved};
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
                        yaw -> work[0] = work[0].withFacing((float) yaw, work[0].location().pitch())))
                .axis(TunerAxis.degrees(text.pitch(), () -> work[0].location().pitch(),
                        pitch -> work[0] = work[0].withFacing(work[0].location().yaw(), (float) pitch))
                        .range(-90, 90))
                .axis(TunerAxis.of(text.scale(), () -> work[0].appearance().scaleY(),
                                scale -> work[0] = scaled(work[0], scale))
                        .steps(0.05, 0.1, 0.25, 0.5).step(1).range(0.1, 10).unit("x")
                        .snap(scale -> Math.max(0.25, Math.round(scale * 4) / 4.0)))
                .axis(TunerAxis.of(text.spacing(), () -> work[0].appearance().lineSpacing(),
                                spacing -> work[0] = spaced(work[0], spacing))
                        .steps(0.01, 0.05, 0.1).step(1).range(0, 3)
                        .snap(spacing -> Math.round(spacing * 20) / 20.0))
                .onChange(session -> {
                    working.put(name, work[0]);
                    StoredHologram current = module.store().get(name).orElse(null);
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
        StoredHologram work = working.remove(name);
        HologramModule module = HologramModule.get();
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

    /** The saved hologram with what the adjustment changes taken from the working copy. */
    static StoredHologram merge(StoredHologram base, StoredHologram work) {
        HologramConfig.Properties b = base.appearance();
        HologramConfig.Properties w = work.appearance();
        return base.withLocation(work.location()).withAppearance(new HologramConfig.Properties(
                w.billboard(), b.alignment(), w.scaleX(), w.scaleY(), w.scaleZ(), b.shadow(), b.seeThrough(),
                b.lineWidth(), w.lineSpacing(), b.textOpacity(), b.defaultBackground(), b.backgroundColor(),
                b.brightness(), b.glowing(), b.distanceScale()));
    }

    private static StoredHologram moved(StoredHologram hologram, double dx, double dy, double dz) {
        return hologram.withLocation(hologram.location().plus(dx, dy, dz));
    }

    /** Scales evenly, keeping the proportions a hologram scaled per axis already has. */
    static StoredHologram scaled(StoredHologram hologram, double scale) {
        HologramConfig.Properties p = hologram.appearance();
        double factor = scale / p.scaleY();
        return hologram.withAppearance(new HologramConfig.Properties(p.billboard(), p.alignment(),
                p.scaleX() * factor, scale, p.scaleZ() * factor, p.shadow(), p.seeThrough(), p.lineWidth(),
                p.lineSpacing(), p.textOpacity(), p.defaultBackground(), p.backgroundColor(), p.brightness(),
                p.glowing(), p.distanceScale()));
    }

    private static StoredHologram spaced(StoredHologram hologram, double spacing) {
        HologramConfig.Properties p = hologram.appearance();
        return hologram.withAppearance(new HologramConfig.Properties(p.billboard(), p.alignment(), p.scaleX(),
                p.scaleY(), p.scaleZ(), p.shadow(), p.seeThrough(), p.lineWidth(), spacing, p.textOpacity(),
                p.defaultBackground(), p.backgroundColor(), p.brightness(), p.glowing(), p.distanceScale()));
    }

    /** A pinpoint at the anchor, for the adjusting player only, so the exact spot is visible. */
    private static void spark(Player player, StoredHologram hologram) {
        Location at = hologram.location().toLocation();
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

    private static HologramsMessages messages() {
        return HologramsMessages.get();
    }

    private static void send(Player player, String line, Values values) {
        ExyliaDisplays.getInstance().getMessages().send(player, line, values);
    }
}
