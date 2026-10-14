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
import java.util.Set;
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

    /** Ticks between checks that an attached hologram's entity is still there. */
    private static final long FOLLOW_TICKS = 10;
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
    /** {@link #wanted} the other way round, so an entity coming and going is one lookup, not a scan. */
    private final Map<UUID, Set<String>> riders = new ConcurrentHashMap<>();
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
            if (before.withPageLines(hologram.allPages()).equals(hologram)) {
                // Swapped in place, so each viewer stays on the page they were reading.
                live.get().pages(hologram.allPages());
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
        riders.clear();
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
        unwant(name);
        if (target == null) {
            return;
        }
        wanted.put(name, target);
        riders.compute(target, (key, names) -> {
            Set<String> riding = names == null ? ConcurrentHashMap.newKeySet() : names;
            riding.add(name);
            return riding;
        });
        Entity entity = Bukkit.getEntity(target);
        if (entity != null && present(entity)) {
            ride(name, live, entity, 1);
        }
    }

    /**
     * Mounts the hologram on the entity's own thread, then watches that the entity is still there.
     * ExyliaLib (1.298.1+) measures range from the ridden entity and mounts again for a viewer who
     * starts seeing it later, so nothing has to follow it here.
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
            if (pending.get()) {
                live.attachTo(entity);
                pending.set(false);
            }
        });
        mounts.put(name, new Mount(entity, follow, pending));
    }

    private void unwant(String name) {
        UUID target = wanted.remove(name);
        if (target != null) {
            riders.computeIfPresent(target, (key, names) -> names.remove(name) && names.isEmpty() ? null : names);
        }
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
        unwant(name);
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
        Set<String> names = riders.get(event.getEntity().getUniqueId());
        if (names == null) {
            return;
        }
        Entity entity = event.getEntity();
        for (String name : names) {
            Holograms.get(plugin, name).ifPresent(live -> ride(name, live, entity, MOUNT_DELAY));
        }
    }

    /** The entity left its world: its holograms hide until it is back, or drop an unsaved attachment for good. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemoved(EntityRemoveFromWorldEvent event) {
        UUID id = event.getEntity().getUniqueId();
        Set<String> names = riders.get(id);
        if (names == null) {
            return;
        }
        for (String name : names) {
            unmount(name);
            // An entity that is not saved never comes back once it leaves its world.
            if (id.equals(temporary.get(name))) {
                tasks.run(() -> forget(name));
            }
        }
    }
}
