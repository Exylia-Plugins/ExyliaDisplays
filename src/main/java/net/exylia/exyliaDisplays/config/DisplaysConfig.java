package net.exylia.exyliaDisplays.config;

import net.exylia.exyliaDisplays.hologram.HologramsConfig;
import net.exylia.exyliaDisplays.npc.NpcsConfig;
import net.exylia.lib.config.Comment;
import net.exylia.lib.config.ConfigFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code config.yml}: how the plugin behaves. What it shows lives in {@code holograms.yml},
 * {@code npcs.yml} and the other module files.
 *
 * @param debug      extra console output
 * @param watchFiles apply hand edits of the module files on their own
 * @param modules    which modules run
 * @param holograms  the hologram module's settings
 * @param npcs       the NPC module's settings
 */
@Comment("ExyliaDisplays settings. What it shows lives in holograms.yml, npcs.yml and friends.")
public record DisplaysConfig(
        @Comment("Extra console output, for reporting a problem.")
        boolean debug,

        @Comment("Apply holograms.yml, npcs.yml... by themselves when they are saved by hand, without a reload.")
        boolean watchFiles,

        @Comment("Which parts of the plugin run. A module switched off costs nothing; restart to apply.")
        Map<String, Boolean> modules,

        @Comment("Holograms.")
        HologramsConfig holograms,

        @Comment("NPCs.")
        NpcsConfig npcs) {

    public DisplaysConfig() {
        this(false, true, defaultModules(), new HologramsConfig(), new NpcsConfig());
    }

    public DisplaysConfig {
        if (modules == null) modules = Map.of();
        if (holograms == null) holograms = new HologramsConfig();
        if (npcs == null) npcs = new NpcsConfig();
    }

    /** Whether a module runs; one the file does not name does. */
    public boolean enabled(String module) {
        return modules.getOrDefault(module, true);
    }

    private static Map<String, Boolean> defaultModules() {
        Map<String, Boolean> modules = new LinkedHashMap<>();
        modules.put("holograms", true);
        modules.put("npcs", true);
        return modules;
    }

    private static ConfigFile<DisplaysConfig> file;

    public static void install(ConfigFile<DisplaysConfig> loaded) {
        file = loaded;
    }

    public static DisplaysConfig get() {
        return file == null ? new DisplaysConfig() : file.get();
    }
}
