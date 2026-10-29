package net.exylia.exyliaDisplays.npc;

import net.exylia.exyliaDisplays.ExyliaDisplays;
import net.exylia.exyliaDisplays.common.interaction.InteractionGate;
import net.exylia.exyliaDisplays.common.store.Position;
import net.exylia.exyliaDisplays.hologram.HologramModule;
import net.exylia.lib.hologram.Hologram;
import net.exylia.lib.hologram.HologramConfig;
import net.exylia.lib.hologram.Holograms;
import net.exylia.lib.interaction.Click;
import net.exylia.lib.interaction.Interactions;
import net.exylia.lib.npc.NpcSpec;
import net.exylia.lib.npc.Npcs;
import net.exylia.lib.npc.PlacedNpc;
import net.exylia.lib.npc.PluginNpcs;
import net.exylia.lib.placeholder.Placeholders;
import net.exylia.lib.task.TaskHandle;
import net.exylia.lib.text.Values;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * Keeps the NPCs in the world equal to the file, with what floats above them.
 *
 * <p>Told every change, it does the least that shows it: a new place moves the NPC, a new look is
 * handed to ExyliaLib (which only respawns it for a new skin, size or outline), and the label is
 * only drawn again when its own lines change. So nudging an NPC or changing what it holds never
 * makes it blink out for the players around it.
 *
 * <p>An NPC in a world that is not loaded waits for it and appears when it loads.
 */
public final class NpcRenderer implements Listener {

    /** Labels share the plugin's hologram ids with holograms.yml; the colon keeps them apart. */
    static final String LABEL = "npc-label:";

    private final ExyliaDisplays core;
    private final Plugin plugin;
    private final PluginNpcs npcs;
    /** Lays a live adjustment over what the file says; see {@link NpcAdjust#preview}. */
    private final BiFunction<String, StoredNpc, StoredNpc> overlay;
    /** What each NPC in the world was last drawn from. */
    private final Map<String, StoredNpc> shown = new ConcurrentHashMap<>();
    /** NPCs whose world is not loaded, drawn when it is. */
    private final Map<String, StoredNpc> waiting = new ConcurrentHashMap<>();
    /** Who may click each NPC and how often. */
    private final Map<String, InteractionGate> gates = new ConcurrentHashMap<>();
    /** Bumped on every turn to a clicker, so only the latest turn turns it back. */
    private final Map<String, AtomicInteger> turns = new ConcurrentHashMap<>();
    /** The account each placeholder skin named last; empty for nobody (the fallback). */
    private final Map<String, String> wearing = new ConcurrentHashMap<>();
    /** Placeholder skins not read yet, read on the next second rather than the next refresh. */
    private final Set<String> unread = ConcurrentHashMap.newKeySet();
    /** NPCs paused by a command or a screen; runtime only, kept here so a re-placed NPC stays paused. */
    private final Set<String> paused = ConcurrentHashMap.newKeySet();
    private final TaskHandle skins;
    private long nextSkinRead;

    public NpcRenderer(ExyliaDisplays core, BiFunction<String, StoredNpc, StoredNpc> overlay) {
        this.core = core;
        this.overlay = overlay;
        this.plugin = core.getPlugin();
        this.npcs = Npcs.of(plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.skins = core.getTasks().runTimer(20L, 20L, this::readSkins);
    }

    /**
     * Shows an NPC as it is now saved.
     *
     * @param name its name
     * @param saved what it is now, or {@code null} once it was removed
     */
    public void apply(String name, StoredNpc saved) {
        StoredNpc npc = overlay.apply(name, saved);
        waiting.remove(name);
        if (saved == null) paused.remove(name);
        if (npc == null || !npc.enabled()) {
            hide(name);
            return;
        }
        Location at = npc.location().toLocation();
        if (at == null) {
            hide(name);
            waiting.put(name, npc);
            return;
        }
        StoredNpc before = shown.get(name);
        if (!npc.skin().isPlaceholder()) {
            wearing.remove(name);
        } else if (before == null || !before.skin().equals(npc.skin())) {
            wearing.remove(name);
            unread.add(name);
        }
        Optional<PlacedNpc> live = npcs.placed(name).filter(placed -> !placed.removed());
        if (before != null && live.isPresent()) {
            if (!before.location().equals(npc.location())) live.get().moveTo(at);
            if (!npc.looksLike(before)) live.get().update(spec(name, npc));
            if (!before.permission().equals(npc.permission())) live.get().visibleIf(seenBy(npc));
        } else {
            PlacedNpc placed = npcs.place(name, spec(name, npc), at);
            placed.visibleIf(seenBy(npc));
            placed.onClick((player, click) -> clicked(name, player, click));
            if (paused.contains(name)) placed.pause();
        }
        if (before == null || !before.interactions().equals(npc.interactions())) {
            gates.put(name, gate(name, npc));
        }
        label(name, npc, before);
        followedBy(npc, npc != saved);
        shown.put(name, npc);
    }

    /** Shows exactly these NPCs: the ones no longer listed are taken down. */
    public void applyAll(Map<String, StoredNpc> all) {
        for (String name : shown.keySet()) {
            if (!all.containsKey(name)) hide(name);
        }
        waiting.keySet().retainAll(all.keySet());
        all.forEach(this::apply);
    }

    /** Takes everything down, for a shutdown. */
    public void clear() {
        skins.cancel();
        wearing.clear();
        unread.clear();
        npcs.allPlaced().forEach(PlacedNpc::remove);
        Holograms.all(plugin).stream()
                .filter(hologram -> hologram.id().startsWith(LABEL) || hologram.id().startsWith(NpcTokens.BUBBLE))
                .forEach(Hologram::remove);
        shown.clear();
        waiting.clear();
        paused.clear();
        gates.clear();
        turns.clear();
    }

    public boolean isWaiting(String name) {
        return waiting.containsKey(name);
    }

    /** The NPC as it is drawn now, if it is. */
    public Optional<StoredNpc> shown(String name) {
        return Optional.ofNullable(shown.get(name));
    }

    /** The NPC standing in the world, if it is. */
    public Optional<PlacedNpc> live(String name) {
        return npcs.placed(name).filter(placed -> !placed.removed());
    }

    /**
     * Pauses it or lets it act on its own again: no idle, greeting, click animation or head
     * turning while paused. Not saved; a restart resumes it.
     */
    public void pause(String name, boolean pause) {
        if (pause) paused.add(name);
        else paused.remove(name);
        live(name).ifPresent(placed -> {
            if (pause) placed.pause();
            else placed.resume();
        });
    }

    public boolean isPaused(String name) {
        return paused.contains(name);
    }

    /** Whether it plays a performance now, a continuous idle included. */
    public boolean isPerforming(String name) {
        return live(name).map(PlacedNpc::isPerforming).orElse(false);
    }

    /** How many players see it now. */
    public int viewers(String name) {
        return live(name).map(PlacedNpc::viewerCount).orElse(0);
    }

    // ------------------------------------------------------------------
    // Clicks
    // ------------------------------------------------------------------

    /** A click on the NPC or its label, on the clicking player's thread. */
    private void clicked(String name, Player player, Click click) {
        StoredNpc npc = shown.get(name);
        InteractionGate gate = gates.get(name);
        if (npc == null || gate == null) return;
        StoredNpc.Interactions rules = npc.interactions();
        InteractionGate.Verdict verdict = gate.click(player, click, Map.of("npc", name, "click", click.name().toLowerCase()));
        // With debug on in config.yml, every click says what became of it: the first thing to
        // look at when an NPC "does nothing".
        core.getDebug().debug("NPC " + name + ": " + click + " by " + player.getName() + " -> " + verdict
                + (verdict == InteractionGate.Verdict.RUN ? " " + gate.interaction().lines() : ""));
        switch (verdict) {
            case RUN -> {
                if (rules.faceClicker()) turnTo(name, player);
            }
            case DENIED -> core.getMessages().send(player,
                    rules.denyMessage().isBlank() ? NpcsMessages.get().interactionDenied() : rules.denyMessage(),
                    Values.of().putText("name", name));
            case COOLING_DOWN -> {
                long left = gate.remaining(player.getUniqueId(), System.currentTimeMillis());
                core.getMessages().send(player,
                        rules.cooldownMessage().isBlank() ? NpcsMessages.get().interactionCooldown() : rules.cooldownMessage(),
                        Values.of().putText("name", name).put("remaining", Math.max(0.1, Math.ceil(left / 100.0) / 10.0)));
            }
            case IGNORED -> {
            }
        }
    }

    private InteractionGate gate(String name, StoredNpc npc) {
        StoredNpc.Interactions rules = npc.interactions();
        return new InteractionGate(Interactions.compile(plugin, rules.actions(), "NPC \"" + name + "\""),
                rules.permission(), rules.cooldown());
    }

    /**
     * Turns the NPC to face a player for a moment, then back the way the file says it faces.
     *
     * <p>Not saved: an NPC that kept the last clicker's direction would wander a little with
     * every click.
     */
    public void turnTo(String name, Player player) {
        StoredNpc npc = shown.get(name);
        PlacedNpc live = live(name).orElse(null);
        if (npc == null || live == null) return;
        Location eyes = player.getLocation();
        Location facing = npc.location().facing(eyes.getX(), eyes.getZ()).toLocation();
        if (facing == null) return;
        live.moveTo(facing);
        int turn = turns.computeIfAbsent(name, key -> new AtomicInteger()).incrementAndGet();
        long ticks = Math.max(1, NpcsConfig.get().turnBack().toMillis() / 50);
        core.getTasks().runLater(ticks, () -> {
            if (turns.get(name) == null || turns.get(name).get() != turn) return;
            StoredNpc now = shown.get(name);
            Location home = now == null ? null : now.location().toLocation();
            if (home != null) live(name).ifPresent(placed -> placed.moveTo(home));
        });
    }

    // ------------------------------------------------------------------
    // What floats above it
    // ------------------------------------------------------------------

    /**
     * Draws the label, or moves it, or takes it down; clicking it does what clicking the NPC does,
     * since the label is the part players aim at.
     */
    private void label(String name, StoredNpc npc, StoredNpc before) {
        String id = LABEL + name;
        List<String> lines = npc.label().lines();
        if (lines.isEmpty()) {
            Holograms.remove(plugin, id);
            return;
        }
        Location at = npc.location().above(npc.labelHeight()).toLocation();
        Optional<Hologram> drawn = Holograms.get(plugin, id);
        if (before != null && drawn.isPresent() && before.label().lines().equals(lines)
                && before.permission().equals(npc.permission()) && before.viewDistance() == npc.viewDistance()) {
            if (before.labelHeight() != npc.labelHeight() || !before.location().equals(npc.location())) {
                drawn.get().moveTo(at);
            }
            return;
        }
        HologramConfig config = new HologramConfig(true, HologramConfig.Kind.TEXT, lines, "", "", false,
                npc.viewDistance(), 0, 0, 0, new HologramConfig.Properties(), new HologramConfig.Refresh(),
                new HologramConfig.Click(List.of(), 1.0, 0, 1.0));
        Hologram label = Holograms.show(plugin, id, at, config, seenBy(npc));
        label.onClick((player, click) -> clicked(name, player, click));
    }

    /**
     * Keeps the linked hologram over its head, by moving it in holograms.yml: the hologram module
     * stays the only one that draws it, and an edit there is still where it is edited.
     *
     * <p>While the NPC is being adjusted ({@code preview}) the hologram is only drawn there, not
     * saved, so holograms.yml is written once, when the adjustment is.
     */
    private void followedBy(StoredNpc npc, boolean preview) {
        HologramModule holograms = HologramModule.get();
        String linked = npc.linkedHologram();
        if (linked.isBlank() || holograms == null) return;
        Position above = npc.location().above(npc.topHeight());
        if (preview) {
            holograms.store().get(linked).ifPresent(hologram ->
                    holograms.renderer().apply(linked, hologram.withLocation(above)));
            return;
        }
        // Drawn again from the file even when the edit changed nothing: an undone adjustment
        // leaves the hologram where the preview put it.
        holograms.store().edit(linked, hologram -> hologram.withLocation(above))
                .ifPresent(hologram -> holograms.renderer().apply(linked, hologram));
    }

    private static Predicate<Player> seenBy(StoredNpc npc) {
        String permission = npc.permission();
        return permission.isBlank() ? player -> true : player -> player.hasPermission(permission);
    }

    private void hide(String name) {
        npcs.unplace(name);
        Holograms.remove(plugin, LABEL + name);
        shown.remove(name);
        gates.remove(name);
        turns.remove(name);
        wearing.remove(name);
        unread.remove(name);
    }

    // ------------------------------------------------------------------
    // Skins that follow a placeholder
    // ------------------------------------------------------------------

    /** The spec to draw, wearing whoever its skin's placeholder named last. */
    private NpcSpec spec(String name, StoredNpc npc) {
        String account = wearing.get(name);
        return npc.toSpec(account == null || account.isEmpty() ? null : account);
    }

    /**
     * Every second: reads the placeholder skins nobody read yet, and all of them once
     * {@code skin-refresh} has passed. An NPC is drawn again only when the account changed.
     *
     * <p>On the main thread, where PlaceholderAPI may be asked, and for any player online: a
     * leaderboard's top name is the same for everybody. With nobody online nobody sees it either.
     */
    private void readSkins() {
        long now = System.currentTimeMillis();
        boolean due = now >= nextSkinRead;
        if (!due && unread.isEmpty()) return;
        Player reader = plugin.getServer().getOnlinePlayers().stream().findFirst().orElse(null);
        if (reader == null) return;
        if (due) nextSkinRead = now + NpcsConfig.get().skinRefresh().toMillis();
        shown.forEach((name, npc) -> {
            if (!npc.skin().isPlaceholder() || !(due || unread.contains(name))) return;
            unread.remove(name);
            String account = StoredNpc.Skin.account(Placeholders.apply(npc.skin().player(), reader));
            String named = account == null ? "" : account;
            String before = wearing.put(name, named);
            if (named.equals(before == null ? "" : before)) return;
            core.getDebug().debug("NPC " + name + " now wears " + (account == null ? "its fallback" : account));
            live(name).ifPresent(placed -> placed.update(spec(name, npc)));
        });
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        String world = event.getWorld().getName();
        Map.copyOf(waiting).forEach((name, npc) -> {
            if (npc.location().world().equals(world)) apply(name, npc);
        });
    }
}
