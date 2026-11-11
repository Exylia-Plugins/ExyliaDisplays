package net.exylia.exyliaDisplays.config;

import net.exylia.lib.config.Comment;

/**
 * The {@code modules} section of {@code config.yml}: which parts of the plugin run.
 *
 * @param holograms    the hologram module
 * @param leaderboards the leaderboard module
 * @param npcs         the NPC module
 */
public record ModulesConfig(
        @Comment("Holograms and everything built on them.")
        boolean holograms,

        @Comment("Leaderboards shown on holograms.")
        boolean leaderboards,

        @Comment("NPCs.")
        boolean npcs) {

    public ModulesConfig() {
        this(true, true, true);
    }

    /** Whether a module runs; one this section does not name does. */
    public boolean enabled(String module) {
        return switch (module) {
            case "holograms" -> holograms;
            case "leaderboards" -> leaderboards;
            case "npcs" -> npcs;
            default -> true;
        };
    }
}
