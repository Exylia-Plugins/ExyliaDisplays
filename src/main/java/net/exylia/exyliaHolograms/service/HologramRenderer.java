package net.exylia.exyliaHolograms.service;

import net.exylia.exyliaHolograms.store.StoredHologram;
import net.exylia.lib.hologram.Hologram;
import net.exylia.lib.hologram.Holograms;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the holograms in the world equal to the file.
 *
 * <p>Told every change, it does the least that shows it: new lines are swapped into the hologram
 * already there, a new position moves it, and only a change to how it is drawn shows it again. So
 * typing into a line, or nudging a hologram, never makes it flicker for the players around it.
 *
 * <p>A hologram in a world that is not loaded waits for it and appears when it loads.
 */
public final class HologramRenderer implements Listener {

    private final Plugin plugin;
    /** What each hologram in the world was last drawn from. */
    private final Map<String, StoredHologram> shown = new ConcurrentHashMap<>();
    /** Holograms whose world is not loaded, drawn when it is. */
    private final Map<String, StoredHologram> waiting = new ConcurrentHashMap<>();

    public HologramRenderer(Plugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Shows a hologram as it is now saved.
     *
     * @param name      its name
     * @param hologram  what it is now, or {@code null} once it was removed
     */
    public void apply(String name, StoredHologram hologram) {
        waiting.remove(name);
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
        String permission = hologram.permission();
        if (permission.isBlank()) {
            Holograms.show(plugin, name, at, hologram.toConfig());
        } else {
            Holograms.show(plugin, name, at, hologram.toConfig(), player -> player.hasPermission(permission));
        }
        shown.put(name, hologram);
    }

    /** Shows exactly these holograms: the ones no longer listed are taken down. */
    public void applyAll(Map<String, StoredHologram> holograms) {
        for (String name : shown.keySet()) {
            if (!holograms.containsKey(name)) {
                hide(name);
            }
        }
        waiting.keySet().retainAll(holograms.keySet());
        holograms.forEach(this::apply);
    }

    /** Takes everything down, for a shutdown. */
    public void clear() {
        Holograms.removeAll(plugin);
        shown.clear();
        waiting.clear();
    }

    /** Whether a hologram is waiting for its world to load. */
    public boolean isWaiting(String name) {
        return waiting.containsKey(name);
    }

    private void hide(String name) {
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
}
