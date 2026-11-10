package net.exylia.exyliaDisplays.leaderboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Writes a leaderboard's lines: one page for every stat and period, best place first.
 *
 * <p>Nothing here knows a value. Each place reads the {@code %exyliadisplays_lb_...%}
 * placeholders the {@link LeaderboardModule} answers from memory, so the board refreshes like
 * any other hologram line, and a place nobody holds swaps for its empty line through an
 * {@code <if>} tag, per viewer. The buttons turn the page with {@code [PAGE]}: page
 * {@code stat * spans + span}, so no click needs to know which page a player is on.
 *
 * @param pages   every page's lines, the first stat and period first
 * @param buttons the button lines the pages name, to add to the hologram's own
 */
public record BoardLayout(List<List<String>> pages, Map<String, List<String>> buttons) {

    /** The placeholder group the module registers. */
    public static final String PREFIX = "%exyliadisplays_lb_";

    /**
     * @param board    the hologram's section
     * @param statName a stat id to the name players read
     * @param spanName a period id to the name players read
     */
    public static BoardLayout of(BoardSection board, UnaryOperator<String> statName, UnaryOperator<String> spanName) {
        List<String> stats = board.stats().isEmpty() ? List.of("?") : board.stats();
        List<String> spans = board.spans();
        List<List<String>> pages = new ArrayList<>();
        Map<String, List<String>> buttons = new LinkedHashMap<>();
        for (int s = 0; s < stats.size(); s++) {
            for (int p = 0; p < spans.size(); p++) {
                int page = s * spans.size() + p;
                String stat = stats.get(s);
                String span = spans.get(p);
                String key = stat + ":" + span + ":";
                UnaryOperator<String> words = text -> text
                        .replace("%stat%", statName.apply(stat))
                        .replace("%span%", spanName.apply(span))
                        .replace("%resets%", PREFIX + key + "resets%");
                List<String> lines = new ArrayList<>();
                board.header().forEach(line -> lines.add(words.apply(line)));
                for (int rank = 1; rank <= board.rows(); rank++) {
                    String at = PREFIX + key + rank + ":";
                    String taken = "<if:\"" + at + "taken% == 1\">";
                    lines.add(taken + words.apply(board.row())
                            .replace("%rank%", String.valueOf(rank))
                            .replace("%name%", at + "name%")
                            .replace("%value%", at + "value%"));
                    if (!board.emptyRow().isBlank()) {
                        lines.add("<if:\"" + at + "taken% == 0\">"
                                + words.apply(board.emptyRow()).replace("%rank%", String.valueOf(rank)));
                    }
                }
                String me = PREFIX + key + "me:";
                if (!board.you().isBlank()) {
                    lines.add("<if:\"" + me + "rank% > 0\">" + words.apply(board.you())
                            .replace("%rank%", me + "rank%")
                            .replace("%value%", me + "value%")
                            .replace("%name%", "%player_name%"));
                }
                if (!board.youUnranked().isBlank()) {
                    lines.add("<if:\"" + me + "rank% == 0\">" + words.apply(board.youUnranked()));
                }
                board.footer().forEach(line -> lines.add(words.apply(line)));
                if (stats.size() > 1 && !board.statButton().isBlank()) {
                    String name = "lb" + page + "stat";
                    lines.add("<button:" + name + ">" + words.apply(board.statButton()));
                    buttons.put(name, turn(spans.size() * stats.size(), page, spans.size()));
                }
                if (spans.size() > 1 && !board.spanButton().isBlank()) {
                    String name = "lb" + page + "span";
                    lines.add("<button:" + name + ">" + words.apply(board.spanButton()));
                    int first = s * spans.size();
                    buttons.put(name, List.of(
                            "left: [PAGE] " + (first + Math.floorMod(p + 1, spans.size()) + 1),
                            "right: [PAGE] " + (first + Math.floorMod(p - 1, spans.size()) + 1)));
                }
                pages.add(List.copyOf(lines));
            }
        }
        return new BoardLayout(List.copyOf(pages), Map.copyOf(buttons));
    }

    /** The stat button: the same period of the next stat on left-click, the previous on right-click. */
    private static List<String> turn(int total, int page, int step) {
        return List.of(
                "left: [PAGE] " + (Math.floorMod(page + step, total) + 1),
                "right: [PAGE] " + (Math.floorMod(page - step, total) + 1));
    }
}
