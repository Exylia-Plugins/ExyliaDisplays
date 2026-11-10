package net.exylia.exyliaDisplays.leaderboard;

import net.exylia.lib.config.Comment;

import java.util.List;

/**
 * The {@code leaderboard} section of a hologram: while it is on, the hologram's lines are written
 * from it, one page for each stat and period, with buttons that turn between them.
 *
 * @param enabled     whether the hologram is a leaderboard
 * @param stats       the stats it turns through, ids from {@code leaderboards.yml}
 * @param spans       the periods it turns through
 * @param rows        how many places it shows
 * @param header      the lines above the places
 * @param row         a place somebody holds
 * @param emptyRow    a place nobody holds yet
 * @param you         the viewer's own place
 * @param youUnranked the viewer when they are not ranked
 * @param footer      the lines below the places
 * @param statButton  the button that turns the stat
 * @param spanButton  the button that turns the period
 */
public record BoardSection(
        @Comment("Show it as a leaderboard: the lines below are written for every stat and period, and")
        @Comment("'lines' and 'pages' are kept for when it is turned off.")
        boolean enabled,

        @Comment("What it ranks by, ids from leaderboards.yml. With more than one, a button turns through them.")
        List<String> stats,

        @Comment("Which periods: all-time, daily, weekly, monthly, yearly. With more than one, a button turns through them.")
        List<String> spans,

        @Comment("How many places it shows, 1 to 30.")
        int rows,

        @Comment("Lines above the places. %stat% is the stat's name, %span% the period's, %resets% when it starts over.")
        List<String> header,

        @Comment("A place somebody holds: %rank%, %name% and %value%. <head:%name%> draws their face on 1.21.9+ servers and clients.")
        String row,

        @Comment("A place nobody holds yet: %rank%.")
        String emptyRow,

        @Comment("The viewer's own place, under the others: %rank%, %value% and %name%. Empty to leave it out.")
        String you,

        @Comment("The viewer when they hold no place in this period yet.")
        String youUnranked,

        @Comment("Lines below the places, before the buttons. Same placeholders as the header.")
        List<String> footer,

        @Comment("The button that turns the stat (left-click next, right-click back): %stat%. Empty for none.")
        String statButton,

        @Comment("The button that turns the period (left-click next, right-click back): %span%. Empty for none.")
        String spanButton) {

    public static final int MAX_ROWS = 30;

    public BoardSection() {
        this(false, List.of("kills"), List.of("all-time", "weekly"), 10,
                List.of("<size:1.6><shine:highlight>{highlight}&lTOP %stat%", "%resets%", "<size:0.4> "),
                "{highlight}%rank%. {letters}%name% {letters_black}· {info}%value%",
                "{muted}%rank%. ---",
                "<gap:0.1>{success}You {letters_black}· {highlight}#%rank% {letters_black}· {info}%value%",
                "<gap:0.1>{muted}You are not on this board yet",
                List.of("<size:0.4> "),
                "<gap:0.12>{info}◀ {letters}%stat% {info}▶",
                "{info}◀ {letters}%span% {info}▶");
    }

    public BoardSection {
        stats = stats == null ? List.of() : List.copyOf(stats);
        spans = spans == null || spans.isEmpty() ? List.of("all-time") : List.copyOf(spans);
        rows = Math.clamp(rows, 1, MAX_ROWS);
        header = header == null ? List.of() : List.copyOf(header);
        footer = footer == null ? List.of() : List.copyOf(footer);
        if (row == null) row = "%rank%. %name% %value%";
        if (emptyRow == null) emptyRow = "";
        if (you == null) you = "";
        if (youUnranked == null) youUnranked = "";
        if (statButton == null) statButton = "";
        if (spanButton == null) spanButton = "";
    }

    public BoardSection withEnabled(boolean value) {
        return new BoardSection(value, stats, spans, rows, header, row, emptyRow, you, youUnranked, footer, statButton, spanButton);
    }

    public BoardSection withStats(List<String> value) {
        return new BoardSection(enabled, value, spans, rows, header, row, emptyRow, you, youUnranked, footer, statButton, spanButton);
    }

    public BoardSection withSpans(List<String> value) {
        return new BoardSection(enabled, stats, value, rows, header, row, emptyRow, you, youUnranked, footer, statButton, spanButton);
    }

    public BoardSection withRows(int value) {
        return new BoardSection(enabled, stats, spans, value, header, row, emptyRow, you, youUnranked, footer, statButton, spanButton);
    }

    public BoardSection withRow(String value) {
        return new BoardSection(enabled, stats, spans, rows, header, value, emptyRow, you, youUnranked, footer, statButton, spanButton);
    }

    public BoardSection withYou(String value) {
        return new BoardSection(enabled, stats, spans, rows, header, row, emptyRow, value, youUnranked, footer, statButton, spanButton);
    }
}
