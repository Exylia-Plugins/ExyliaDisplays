package net.exylia.exyliaDisplays.leaderboard;

import net.exylia.lib.config.Comment;
import net.exylia.lib.config.ConfigFile;

/**
 * {@code messages/leaderboards}: what leaderboard holograms and their editor say.
 */
public record LeaderboardsMessages(
        @Comment("The names of the periods, as the period button and %span% show them.")
        String allTime,
        String daily,
        String weekly,
        String monthly,
        String yearly,
        @Comment("%resets% in a leaderboard line: when the period starts over. %time% is how long until then.")
        String resetsIn,
        @Comment("%resets% on an all-time board, which never starts over.")
        String resetsNever,
        @Comment("Told when the stat a board names is not in leaderboards.yml. Placeholder: %stat%.")
        String unknownStat,
        @Comment("Placeholders: %name%, %stats%, %spans%.")
        String turnedOn,
        @Comment("Placeholder: %name%.")
        String turnedOff,
        @Comment("Placeholder: %name%: the board's current lines are kept as plain lines.")
        String flattened,
        @Comment("A period name that is not all-time, daily, weekly, monthly or yearly. Placeholder: %span%.")
        String unknownSpan,
        @Comment("A board reset. Placeholders: %stat%, %span%.")
        String reset,
        @Comment("The leaderboard window.")
        String dialogTitle,
        String enabledLabel,
        String statsLabel,
        @Comment("Placeholder: %stats%: the ids leaderboards.yml defines.")
        String statsHint,
        String spansLabel,
        String spansHint,
        String rowsLabel,
        String rowLabel,
        String rowHint,
        String youLabel,
        String youHint,
        @Comment("The states on the hologram's screen. %count% is how many stats or periods it turns through.")
        String off,
        String on,
        String moduleOff) {

    public LeaderboardsMessages() {
        this("All time", "Today", "This week", "This month", "This year",
                "{letters_black}Resets in {info}%time%",
                "{letters_black}Since the start",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}leaderboards.yml has no stat called {highlight}%stat%{error}.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|1.0|1.0]%prefix% {highlight}%name% {success}is now a leaderboard of {highlight}%stats% {letters_black}(%spans%){success}.",
                "%prefix% {highlight}%name% {warning}is a plain hologram again.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|1.0|1.0]%prefix% {highlight}%name% {success}keeps its board as plain lines you can edit.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}No period called {highlight}%span%{error}: all-time, daily, weekly, monthly or yearly.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|1.0|1.0]%prefix% {success}The {highlight}%stat% {success}board starts over {letters_black}(%span%){success}.",
                "{primary}&lLEADERBOARD",
                "Show it as a leaderboard",
                "Stats (one per line, the first shown first)",
                "From leaderboards.yml: %stats%",
                "Periods (one per line)",
                "all-time, daily, weekly, monthly, yearly. Left-click the period button for the next one.",
                "Places shown",
                "A place",
                "%rank%, %name% and %value%. <head:%name%> draws their face (1.21.9+).",
                "Your own place",
                "%rank% and %value%. Empty to leave it out.",
                "{muted}Off",
                "{success}%stats% {letters_black}· {info}%spans%",
                "{warning}Leaderboards are switched off in config.yml");
    }

    private static ConfigFile<LeaderboardsMessages> file;

    public static void install(ConfigFile<LeaderboardsMessages> loaded) {
        file = loaded;
    }

    public static LeaderboardsMessages get() {
        return file == null ? new LeaderboardsMessages() : file.get();
    }
}
