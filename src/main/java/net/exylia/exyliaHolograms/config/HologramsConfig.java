package net.exylia.exyliaHolograms.config;

import net.exylia.lib.config.Comment;
import net.exylia.lib.config.ConfigFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code config.yml}: how the plugin behaves. The holograms themselves are in
 * {@code holograms.yml}.
 *
 * @param debug               extra console output
 * @param watchFile           apply hand edits of holograms.yml on their own
 * @param spawnHeight         how far above the feet a new hologram stands
 * @param defaultViewDistance the view distance of a new hologram
 * @param nearRadius          the radius /holo near uses when none is given
 * @param presets             what a new hologram can start from
 */
@Comment("ExyliaHolograms settings. The holograms themselves live in holograms.yml.")
public record HologramsConfig(
        @Comment("Extra console output, for reporting a problem.")
        boolean debug,

        @Comment("Apply holograms.yml by itself when it is saved by hand, without /holo reload.")
        boolean watchFile,

        @Comment("How far above your feet a hologram you create stands, in blocks.")
        double spawnHeight,

        @Comment("How far away new holograms are seen, in blocks.")
        double defaultViewDistance,

        @Comment("The radius /holo near uses when none is given, in blocks.")
        double nearRadius,

        @Comment("What a hologram created from the menu can start from. Add your own:")
        @Comment("'name' is shown in the list, and %hologram% in a line becomes the new hologram's name.")
        Map<String, Preset> presets) {

    public HologramsConfig() {
        this(false, true, 2.0, 48.0, 20.0, defaultPresets());
    }

    public HologramsConfig {
        if (presets == null) presets = Map.of();
        if (spawnHeight < 0) spawnHeight = 0;
        if (defaultViewDistance <= 0) defaultViewDistance = 48.0;
        if (nearRadius <= 0) nearRadius = 20.0;
    }

    /**
     * A starting point for a new hologram.
     *
     * @param name  what the list shows
     * @param lines the lines it starts with
     */
    public record Preset(String name, List<String> lines) {

        public Preset() {
            this("Simple", List.of("{primary}&l%hologram%"));
        }

        public Preset {
            if (name == null || name.isBlank()) name = "Preset";
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    private static Map<String, Preset> defaultPresets() {
        Map<String, Preset> presets = new LinkedHashMap<>();
        presets.put("simple", new Preset("Simple title", List.of(
                "<size:1.4>{primary}&l%hologram%",
                "{letters}Edit me with {highlight}/holo edit %hologram%")));
        presets.put("shop", new Preset("NPC label", List.of(
                "<size:0.8>{highlight}⚓ — EXYLIA — ⚓",
                "<size:2.2><pulse><shine>{primary}&lSHOP",
                "<typewriter>{letters_black}+12 trades available",
                "<size:0.5> ",
                "<range:8><background:#55000000>{warning}▸ Click to trade ◂")));
        presets.put("showcase", new Preset("Spinning showcase", List.of(
                "<block:DIAMOND_BLOCK><spin><bob><glow:#59a4ff>",
                "<size:1.6><flow>{primary}&lSHOWCASE",
                "{letters}Something worth looking at")));
        presets.put("welcome", new Preset("Welcome sign", List.of(
                "<size:2.4><shine>{primary}&lWELCOME",
                "{letters}to the server",
                "<size:0.5> ",
                "<range:12><typewriter>{letters_black}Have fun, and be kind.")));
        return presets;
    }

    private static ConfigFile<HologramsConfig> file;

    public static void install(ConfigFile<HologramsConfig> loaded) {
        file = loaded;
    }

    public static HologramsConfig get() {
        return file == null ? new HologramsConfig() : file.get();
    }
}
