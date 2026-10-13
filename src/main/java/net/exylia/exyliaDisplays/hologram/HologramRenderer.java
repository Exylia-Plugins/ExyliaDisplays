package net.exylia.exyliaDisplays.hologram;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import net.exylia.lib.hologram.Hologram;
import net.exylia.lib.hologram.Holograms;
import net.exylia.lib.task.TaskHandle;
import net.exylia.lib.task.TaskScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

/**
 * Keeps the holograms in the world equal to the file.
 *
 * <p>Told every change, it does the least that shows it: new lines are swapped into the hologram
 * already there, a new position moves it, a change to who sees it is read by the next visibility
 * check, and only a change to how it is drawn shows it again. So typing into a line, or nudging a
 * hologram, never makes it flicker for the players around it.
 *
 * <p>A hologram in a world that is not loaded waits for it and appears when it loads. A hologram
 * attached to an entity rides it while the entity is in the world and is hidden while it is not;
 * it mounts again whenever the entity is added to a world (a player joining, respawning or
 * changing worlds, a saved mob's chunk loading).
 */
public final class HologramRenderer implements Listener {

    /** Ticks between checks that an attached hologram's anchor is still near its entity. */
    private static final long FOLLOW_TICKS = 10;
    /** How far an entity may get from its hologram's anchor before the anchor follows (squared blocks). */
    private static final double FOLLOW_SLACK = 8 * 8;
    /** Ticks to wait after an entity enters a world, so viewers are tracking it before it is mounted. */
    private static final long MOUNT_DELAY = 5;

    private final Plugin plugin;
    private final TaskScheduler tasks;
    /** Lays a live adjustment over what the file says; see {@link HologramAdjust#preview}. */
    private final BiFunction<String, StoredHologram, StoredHologram> overlay;
    /** What each hologram in the world was last drawn from. */
    private final Map<String, StoredHologram> shown = new ConcurrentHashMap<>();
    /** Holograms whose world is not loaded, drawn when it is. */
    private final Map<String, StoredHologram> waiting = new ConcurrentHashMap<>();
    /** Attachments to entities that are not saved (not persistent): gone on restart or when it unloads. */
    private final Map<String, UUID> temporary = new ConcurrentHashMap<>();
    /** The entity each shown hologram should ride. */
    private final Map<String, UUID> wanted = new ConcurrentHashMap<>();
    /** The holograms riding, or about to ride, an entity. */
    private final Map<String, Mount> mounts = new ConcurrentHashMap<>();

    /** An attached hologram: what it rides, the task keeping its anchor near, and whether it is mounted yet. */
    private record Mount(Entity entity, TaskHandle follow, AtomicBoolean pending) {
    }

    public HologramRenderer(Plugin plugin, TaskScheduler tasks, BiFunction<String, StoredHologram, StoredHologram> overlay) {
        this.plugin = plugin;
        this.tasks = tasks;
        this.overlay = overlay;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Shows a hologram as it is now saved.
     *
     * @param name      its name
     * @param hologram  what it is now, or {@code null} once it was removed
     */
    public void apply(String name, StoredHologram hologram) {
        hologram = overlay.apply(name, hologram);
        waiting.remove(name);
        if (hologram == null) {
            temporary.remove(name);
        }
        if (hologram == null || !hologram.enabled()) {
            hide(name);
            return;
        }
        Location at = hologram.location().toLocation();
        if (at == null) {
            hide(name);
            waiting.put(name, hologram);
            return;
        }
        StoredHologram before = shown.get(name);
        Optional<Hologram> live = Holograms.get(plugin, name);
        if (before != null && live.isPresent()) {
            if (before.withPermission(hologram.permission()).withVisibility(hologram.visibility()).equals(hologram)) {
                // Who sees it is read live by the filter, so the next visibility check applies it.
                shown.put(name, hologram);
                return;
            }
            if (before.withLines(hologram.lines()).equals(hologram)) {
                live.get().lines(hologram.lines());
                shown.put(name, hologram);
                return;
            }
            if (before.withLocation(hologram.location()).equals(hologram)
                    && before.location().world().equals(hologram.location().world())) {
                live.get().moveTo(at);
                shown.put(name, hologram);
                return;
            }
        }
        UUID target = target(name, hologram);
        shown.put(name, hologram);
        Hologram drawn = Holograms.show(plugin, name, at,
                hologram.toConfig(target == null ? 0 : hologram.attach().offset()), player -> sees(name, player));
        mount(name, target, drawn);
    }

    /** Shows exactly these holograms: the ones no longer listed are taken down. */
    public void applyAll(Map<String, StoredHologram> holograms) {
        for (String name : shown.keySet()) {
            if (!holograms.containsKey(name)) {
                hide(name);
            }
        }
        waiting.keySet().retainAll(holograms.keySet());
        temporary.keySet().retainAll(holograms.keySet());
        holograms.forEach(this::apply);
    }

    /** Takes everything down, for a shutdown. */
    public void clear() {
        mounts.values().forEach(mount -> mount.follow().cancel());
        mounts.clear();
        wanted.clear();
        temporary.clear();
        Holograms.removeAll(plugin);
        shown.clear();
        waiting.clear();
    }

    /** Whether a hologram is waiting for its world to load. */
    public boolean isWaiting(String name) {
        return waiting.containsKey(name);
    }

    // ------------------------------------------------------------------
    // Attached to an entity
    // ------------------------------------------------------------------

    /**
     * Rides an entity the file cannot name again after a restart (it is not saved with its
     * chunk), until it is gone or {@link #forget} is called. Nothing is written down.
     */
    public void attachUnsaved(String name, Entity entity) {
        temporary.put(name, entity.getUniqueId());
        redraw(name);
    }

    /** Drops an unsaved attachment, so the hologram stands at its location again. */
    public void forget(String name) {
        if (temporary.remove(name) != null) {
            redraw(name);
        }
    }

    /** The entity a hologram rides or waits for: an unsaved attachment first, then the file's. */
    public UUID target(String name, StoredHologram hologram) {
        UUID unsaved = temporary.get(name);
        return unsaved != null ? unsaved : hologram.attach().target();
    }

    /** The entity a hologram is riding right now, if any. */
    private Optional<Entity> riding(String name) {
        Mount mount = mounts.get(name);
        return mount == null || mount.pending().get() ? Optional.empty() : Optional.of(mount.entity());
    }

    private void redraw(String name) {
        StoredHologram hologram = shown.remove(name);
        if (hologram != null) {
            apply(name, hologram);
        }
    }

    private void mount(String name, UUID target, Hologram live) {
        unmount(name);
        if (target == null) {
            wanted.remove(name);
            return;
        }
        wanted.put(name, target);
        Entity entity = Bukkit.getEntity(target);
        if (entity != null && present(entity)) {
            ride(name, live, entity, 1);
        }
    }

    /**
     * Mounts the hologram on the entity's own thread, then keeps its anchor near the entity: the
     * client moves a passenger with its mount, but the server still decides who is in range from
     * the anchor.
     *
     * <p>Known limit, in ExyliaLib (1.296): a mounted hologram's displays all sit on the mount's
     * passenger point, so the lines stack on each other and the offset is not applied until the
     * library sends each display a translation; a viewer who starts tracking the entity after the
     * hologram was spawned for them misses the mount and sees the anchor follow instead.
     */
    private void ride(String name, Hologram live, Entity entity, long delay) {
        unmount(name);
        AtomicBoolean pending = new AtomicBoolean(true);
        TaskHandle follow = tasks.runAtEntityTimer(entity, delay, FOLLOW_TICKS, handle -> {
            if (live.removed() || !present(entity)) {
                handle.cancel();
                mounts.computeIfPresent(name, (key, mount) -> mount.pending() == pending ? null : mount);
                return;
            }
            Location at = entity.getLocation();
            if (pending.get()) {
                live.moveTo(at);
                live.attachTo(entity);
                pending.set(false);
                return;
            }
            Location anchor = live.location();
            if (anchor.getWorld() != at.getWorld() || anchor.distanceSquared(at) > FOLLOW_SLACK) {
                live.moveTo(at);
            }
        });
        mounts.put(name, new Mount(entity, follow, pending));
    }

    private void unmount(String name) {
        Mount mount = mounts.remove(name);
        if (mount != null) {
            mount.follow().cancel();
        }
    }

    private static boolean present(Entity entity) {
        return entity instanceof Player player ? player.isOnline() : entity.isValid();
    }

    /** Who sees it, read on every visibility check so a change needs no redraw. */
    private boolean sees(String name, Player player) {
        StoredHologram hologram = shown.get(name);
        if (hologram == null) {
            return false;
        }
        String permission = hologram.permission();
        if (!permission.isBlank() && !player.hasPermission(permission)) {
            return false;
        }
        if (!hologram.visibility().sees(player.getUniqueId())) {
            return false;
        }
        // An attached hologram is hidden while its entity is away, never left standing alone.
        return !wanted.containsKey(name) || riding(name).isPresent();
    }

    private void hide(String name) {
        unmount(name);
        wanted.remove(name);
        Holograms.remove(plugin, name);
        shown.remove(name);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        String world = event.getWorld().getName();
        Map.copyOf(waiting).forEach((name, hologram) -> {
            if (hologram.location().world().equals(world)) {
                apply(name, hologram);
            }
        });
    }

    /** A player joining, respawning or changing worlds, or a saved mob loading: its holograms ride it again. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityAdded(EntityAddToWorldEvent event) {
        UUID id = event.getEntity().getUniqueId();
        if (!wanted.containsValue(id)) {
            return;
        }
        Entity entity = event.getEntity();
        wanted.forEach((name, target) -> {
            if (target.equals(id)) {
                Holograms.get(plugin, name).ifPresent(live -> ride(name, live, entity, MOUNT_DELAY));
            }
        });
    }

    /** The entity left its world: its holograms hide until it is back, or drop an unsaved attachment for good. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemoved(EntityRemoveFromWorldEvent event) {
        UUID id = event.getEntity().getUniqueId();
        if (!wanted.containsValue(id)) {
            return;
        }
        wanted.forEach((name, target) -> {
            if (!target.equals(id)) {
                return;
            }
            unmount(name);
            // An entity that is not saved never comes back once it leaves its world.
            if (id.equals(temporary.get(name))) {
                tasks.run(() -> forget(name));
            }
        });
    }
}
