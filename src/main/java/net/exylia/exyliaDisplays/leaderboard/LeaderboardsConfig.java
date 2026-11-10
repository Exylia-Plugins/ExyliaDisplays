package net.exylia.exyliaDisplays.leaderboard;

import net.exylia.lib.config.Comment;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code leaderboards.yml}: what players are ranked by, and when days, weeks, months and years
 * start over. A hologram turned into a leaderboard names the stats it shows from here.
 *
 * @param timeZone    the zone periods are counted in; blank for the server's
 * @param dayStartsAt the hour a day starts at, 0-23
 * @param weekStartsOn the day a week starts on
 * @param sampleEvery how often online players are read
 * @param sortEvery   how often the boards are sorted again
 * @param stats       what can be ranked, by id
 */
@Comment("Leaderboards that keep their own values: no stats plugin is needed behind them.")
@Comment("Turn a hologram into one from its screen (LEADERBOARD) or with /holo <name> leaderboard.")
public record LeaderboardsConfig(
        @Comment("The time zone days, weeks, months and years are counted in, such as Europe/Madrid.")
        @Comment("Empty for the server's own. Give every server of a network the same one.")
        String timeZone,

        @Comment("The hour a day starts at, 0 to 23. At 5, 03:00 still counts as yesterday.")
        int dayStartsAt,

        @Comment("The day a week starts on: MONDAY, SUNDAY...")
        String weekStartsOn,

        @Comment("How often online players are read: 30s, 1m, 5m.")
        Duration sampleEvery,

        @Comment("How often the boards are sorted again, so new values show: 10s, 30s, 1m.")
        Duration sortEvery,

        @Comment("What players can be ranked by. Each one: 'name' as players read it, 'source' and 'format'.")
        @Comment("  source: statistic:PLAYER_KILLS · statistic:MINE_BLOCK:DIAMOND_ORE · statistic:KILL_ENTITY:ZOMBIE")
        @Comment("          playtime · balance · balance:<currency> · placeholder:%some_placeholder%")
        @Comment("  format: empty (12,345) · compact (1.2K) · money · duration (for playtime) · a pattern like #,##0.#")
        @Comment("  per-server: true keeps each server's value apart and adds them up (statistics always do).")
        @Comment("Statistics, play time and balances count offline players too; a placeholder only players who join.")
        Map<String, Stat> stats) {

    public LeaderboardsConfig() {
        this("", 0, "MONDAY", Duration.ofMinutes(1), Duration.ofSeconds(30), defaultStats());
    }

    public LeaderboardsConfig {
        if (timeZone == null) timeZone = "";
        dayStartsAt = Math.floorMod(dayStartsAt, 24);
        if (weekStartsOn == null || weekStartsOn.isBlank()) weekStartsOn = "MONDAY";
        if (sampleEvery == null || sampleEvery.toSeconds() < 1) sampleEvery = Duration.ofMinutes(1);
        if (sortEvery == null || sortEvery.toSeconds() < 1) sortEvery = Duration.ofSeconds(30);
        stats = stats == null ? Map.of() : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(stats));
    }

    /**
     * One thing players are ranked by.
     *
     * @param name      what players read, such as "Kills"
     * @param source    where the value comes from
     * @param format    how a value is written
     * @param perServer each server keeps its own value and the board adds them up
     */
    public record Stat(String name, String source, String format, boolean perServer) {

        public Stat() {
            this("Kills", "statistic:PLAYER_KILLS", "", false);
        }

        public Stat {
            if (name == null || name.isBlank()) name = "Stat";
            if (source == null) source = "";
            if (format == null) format = "";
        }
    }

    private static Map<String, Stat> defaultStats() {
        Map<String, Stat> stats = new LinkedHashMap<>();
        stats.put("kills", new Stat("Kills", "statistic:PLAYER_KILLS", "", false));
        stats.put("deaths", new Stat("Deaths", "statistic:DEATHS", "", false));
        stats.put("mobs", new Stat("Mobs killed", "statistic:MOB_KILLS", "", false));
        stats.put("playtime", new Stat("Play time", "playtime", "duration", false));
        stats.put("money", new Stat("Balance", "balance", "money", false));
        stats.put("diamonds", new Stat("Diamonds mined", "statistic:MINE_BLOCK:DIAMOND_ORE", "", false));
        stats.put("jumps", new Stat("Jumps", "statistic:JUMP", "compact", false));
        return stats;
    }
}
