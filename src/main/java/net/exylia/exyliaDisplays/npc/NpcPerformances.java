package net.exylia.exyliaDisplays.npc;

import net.exylia.exyliaDisplays.ExyliaDisplays;
import net.exylia.exyliaDisplays.common.FileWatcher;
import net.exylia.lib.npc.NpcPerformance;
import net.exylia.lib.npc.Npcs;
import net.exylia.lib.ragdoll.RagdollAnimation;
import net.exylia.lib.text.Values;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Performances: whole-body animations an NPC plays in place of vanilla moves.
 *
 * <p>Knows what can be played by name: ExyliaEmotes' emotes ({@code emote:<id>}), read from its
 * {@code emotes.yml} (the file is the contract; its classes are never touched), and the animation
 * files in this plugin's {@code animations} folder ({@code anim:<name>}). Both are read into memory
 * when the module starts, on {@code /npcadmin reload}, when ExyliaEmotes is enabled and when its
 * file changes, so completion and the resolver only ever read a map.
 */
public final class NpcPerformances implements Listener {

    /** The plugin whose emotes NPCs can play. */
    public static final String EMOTES_PLUGIN = "ExyliaEmotes";

    /**
     * One emote of ExyliaEmotes.
     *
     * @param id          its key under {@code emotes}
     * @param name        what its menus call it
     * @param material    the item it is drawn with
     * @param performance what it plays
     */
    public record Emote(String id, String name, String material, NpcPerformance performance) {
    }

    /**
     * What there is to play, as one snapshot so a reader never sees half a reload.
     *
     * @param installed  whether ExyliaEmotes is enabled
     * @param emotes     its solo emotes by id, in its file's order
     * @param animations the animation files of this plugin's {@code animations} folder, by name
     */
    record Catalog(boolean installed, Map<String, Emote> emotes, List<String> animations) {
        static final Catalog EMPTY = new Catalog(false, Map.of(), List.of());
    }

    private static volatile Catalog catalog = Catalog.EMPTY;

    private final ExyliaDisplays core;
    private FileWatcher watcher;

    NpcPerformances(ExyliaDisplays core) {
        this.core = core;
    }

    // ------------------------------------------------------------------
    // Life
    // ------------------------------------------------------------------

    /** Reads what there is to play, hands emotes to the NPC library, and follows ExyliaEmotes. */
    void enable() {
        Npcs.of(core.getPlugin()).emotes(NpcPerformances::emote);
        core.getServer().getPluginManager().registerEvents(this, core.getPlugin());
        refresh();
    }

    void disable() {
        HandlerList.unregisterAll(this);
        if (watcher != null) watcher.stop();
        watcher = null;
        catalog = Catalog.EMPTY;
    }

    /** Reads emotes.yml and the animations folder again, and watches the emotes file while ExyliaEmotes runs. */
    void refresh() {
        Plugin emotes = core.getServer().getPluginManager().getPlugin(EMOTES_PLUGIN);
        boolean installed = emotes != null && emotes.isEnabled();
        File file = installed ? new File(emotes.getDataFolder(), "emotes.yml") : null;
        Map<String, Emote> read = file != null && file.isFile()
                ? read(YamlConfiguration.loadConfiguration(file)) : Map.of();
        catalog = new Catalog(installed, read, animations(new File(core.getPlugin().getDataFolder(), "animations")));
        if (watcher != null) watcher.stop();
        watcher = null;
        if (file != null && file.getParentFile().isDirectory()) {
            watcher = new FileWatcher(file.toPath(), core.getTasks(), this::changed, core.getPlugin().getLogger());
            watcher.start();
        }
        core.getDebug().log("Performances: " + read.size() + " emotes, " + catalog.animations().size() + " animation files");
    }

    private void changed() {
        refresh();
    }

    @EventHandler
    public void onEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equals(EMOTES_PLUGIN)) refresh();
    }

    @EventHandler
    public void onDisable(PluginDisableEvent event) {
        if (!event.getPlugin().getName().equals(EMOTES_PLUGIN)) return;
        if (watcher != null) watcher.stop();
        watcher = null;
        catalog = new Catalog(false, Map.of(), catalog.animations());
    }

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    /**
     * ExyliaEmotes' emotes, from its emotes.yml. A duet is left out: it is written for two people
     * reaching for each other, and an NPC alone would reach for nobody.
     */
    static Map<String, Emote> read(YamlConfiguration yml) {
        ConfigurationSection section = yml.getConfigurationSection("emotes");
        if (section == null) return Map.of();
        Map<String, Emote> emotes = new LinkedHashMap<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection emote = section.getConfigurationSection(id);
            if (emote == null || "duet".equalsIgnoreCase(emote.getString("kind", "solo"))) continue;
            NpcPerformance performance = NpcPerformance.lines(emote.getStringList("effects"));
            if (performance.isEmpty()) continue;
            emotes.put(id, new Emote(id, emote.getString("name", "{primary}&l" + id.toUpperCase(Locale.ROOT)),
                    emote.getString("material", "PLAYER_HEAD"), performance));
        }
        return Collections.unmodifiableMap(emotes);
    }

    /** The animation files of a folder, by the name {@code anim:} calls them. */
    static List<String> animations(File folder) {
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json"));
        if (files == null) return List.of();
        TreeSet<String> names = new TreeSet<>();
        for (File file : files) {
            String name = file.getName();
            String lower = name.toLowerCase(Locale.ROOT);
            names.add(name.substring(0, name.length() - (lower.endsWith(".animation.json") ? 15 : 5)));
        }
        return List.copyOf(names);
    }

    // ------------------------------------------------------------------
    // Asking
    // ------------------------------------------------------------------

    /** What {@code emote:<id>} plays, or {@code null} when there is no such emote. Called from any thread. */
    static @Nullable NpcPerformance emote(String id) {
        Emote emote = find(id);
        return emote == null ? null : emote.performance();
    }

    static @Nullable Emote find(String id) {
        if (id == null) return null;
        Map<String, Emote> emotes = catalog.emotes();
        Emote exact = emotes.get(id.trim());
        return exact != null ? exact : emotes.get(id.trim().toLowerCase(Locale.ROOT));
    }

    static Catalog catalog() {
        return catalog;
    }

    /** Replaces what there is to play; for tests. */
    static void use(Catalog replacement) {
        catalog = replacement == null ? Catalog.EMPTY : replacement;
    }

    /** Every performance that can be played by name: {@code emote:<id>}, then {@code anim:<name>}. */
    static List<String> names() {
        Catalog now = catalog;
        List<String> names = new ArrayList<>();
        now.emotes().keySet().forEach(id -> names.add("emote:" + id));
        now.animations().forEach(name -> names.add("anim:" + name));
        return names;
    }

    /**
     * Why a performance cannot play, as the message to send, or {@code null} when it can, or the
     * text is no performance at all (vanilla moves are checked by their own parser).
     */
    public static @Nullable String problem(String text) {
        String value = text == null ? "" : text.trim();
        if (!NpcPerformance.isPerformance(value)) return null;
        NpcsMessages.Animations words = NpcsMessages.get().animations();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("emote:")) {
            String id = value.substring(6).trim();
            Catalog now = catalog;
            if (!now.installed()) return Values.of().putText("id", id).apply(words.emotesMissing());
            if (find(id) == null) {
                return Values.of().putText("id", id)
                        .putText("emotes", String.join(", ", now.emotes().keySet())).apply(words.unknownEmote());
            }
            return null;
        }
        int keys = lower.indexOf("keys:");
        if (keys >= 0) {
            String frames = value.substring(keys + 5).trim();
            List<String> problems = new ArrayList<>();
            if (frames.isEmpty()) problems.add("no frames after keys:");
            else RagdollAnimation.parse(frames, problems::add);
            return problems.isEmpty() ? null
                    : Values.of().putText("problems", String.join("; ", problems)).apply(words.invalidFrames());
        }
        int anim = lower.indexOf("anim:");
        if (anim >= 0 && !value.substring(anim + 5).trim().isEmpty()) return null;
        return Values.of().putText("value", value).apply(words.invalidPerformance());
    }
}
