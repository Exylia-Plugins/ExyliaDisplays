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
        // A floating item that spins and bobs over a title whose colours flow.
        presets.put("portal", new Preset("Portal sign", List.of(
                "<item:ENDER_EYE><spin:120><bob><glow:#59a4ff>",
                "<size:1.8><flow:6>{info}&lTHE END",
                "{letters}A dragon waits beyond",
                "<range:6><typewriter:30>{letters_black}Step through when ready")));
        // Two lines for two audiences: who has the permission, and who does not.
        presets.put("lounge", new Preset("Members only", List.of(
                "<size:1.6><shine:highlight>{highlight}&l✦ LOUNGE ✦",
                "<opacity:200>{letters_black}Rooftop bar · open all night",
                "<size:0.5> ",
                "<perm:!lounge.access>{error}Members only",
                "<perm:lounge.access>{success}The door is open for you")));
        // A spinning block that breathes, above a short, dimmed reminder.
        presets.put("checkpoint", new Preset("Checkpoint", List.of(
                "<block:GOLD_BLOCK><spin:60><pulse:0.05>",
                "<size:1.5>{warning}&lCHECKPOINT",
                "<opacity:170>{letters}Fall, and you start again here")));
        presets.put("welcome", new Preset("Welcome sign", List.of(
                "<size:2.4><shine>{primary}&lWELCOME",
                "{letters}to the server",
                "<size:0.5> ",
                "<range:12><typewriter>{letters_black}Read the rules at the board behind you")));
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
