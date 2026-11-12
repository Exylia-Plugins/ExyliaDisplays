package net.exylia.exyliaDisplays.board;

import net.exylia.lib.screen.Screen;
import net.exylia.lib.screen.ScreenMarkup;
import net.exylia.lib.screen.ScreenSession;
import net.exylia.lib.ui.UiEntry;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * What a board shows, as data for {@code boards/board.html}: the markup owns the looks, this owns the logic (which
 * stat, which page of places, who is in them, which arrows work). Every viewer has a session of their own, so
 * {@code page} (the stat) and {@code offset} (the page of places) are theirs.
 */
final class BoardScreen {

    /** Layout pixels across; the board's width in blocks scales them. */
    static final double WIDTH = 240;
    private static final double ROW = 12;
    /** Pages of places the arrows go through at most. */
    private static final int MAX_PAGES = 10;

    private BoardScreen() {
    }

    /** The bundled {@code board.html} or {@code board.css}. */
    static String bundled(String file) {
        try (InputStream in = BoardScreen.class.getResourceAsStream("/boards/" + file)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The screen of a markup bound with {@link #bind}, as tall as the rows need. */
    static Screen screen(ScreenMarkup markup, BoardsConfig.Board board) {
        return markup.screen().size(WIDTH, height(board));
    }

    static double height(BoardsConfig.Board board) {
        return 16 + 16 + (board.tabs() ? 17 : 0) + 3 + board.rows() * (ROW + 1) - 1 + 3 + 13 + 3 + 16;
    }

    /**
     * Gives the markup what it reads and does.
     *
     * @param fill a text with the viewer's placeholders filled
     */
    static ScreenMarkup bind(ScreenMarkup markup, BoardsConfig.Board board, BiFunction<String, ScreenSession, String> fill) {
        return markup
                .value("title", s -> fill.apply(stat(board, s).title(), s))
                .value("you", s -> board.you())
                .text("viewer", s -> s.viewer().getName())
                .value("self", s -> fill.apply(stat(board, s).self(), s))
                .value("tabs", s -> board.tabs())
                .value("counter", s -> counter(board, s, fill))
                .value("has_prev", s -> board.tabs() ? offset(board, s, fill) > 0 : true)
                .value("has_next", s -> board.tabs() ? hasNext(board, s, fill) : true)
                .rows("stats", s -> {
                    List<UiEntry> rows = new ArrayList<>();
                    for (int i = 0; i < board.stats().size(); i++) {
                        rows.add(UiEntry.of(i).with("index", i).with("tab", board.stats().get(i).tab())
                                .with("on", i == page(board, s)).build());
                    }
                    return rows;
                })
                .rows("places", s -> places(board, s, fill))
                .action("tab", (e, args) -> {
                    e.session().set("page", Integer.parseInt(args.get(0)));
                    e.session().set("offset", 0);
                })
                .action("prev", (e, args) -> step(board, e.session(), -1, fill))
                .action("next", (e, args) -> step(board, e.session(), 1, fill));
    }

    /** The place's name and value come from placeholders: they are drawn as plain text, never as markup. */
    private static List<UiEntry> places(BoardsConfig.Board board, ScreenSession session, BiFunction<String, ScreenSession, String> fill) {
        BoardsConfig.Stat stat = stat(board, session);
        String viewer = session.viewer().getName();
        int first = offset(board, session, fill) * board.rows() + 1;
        List<UiEntry> rows = new ArrayList<>();
        for (int n = first; n < first + board.rows(); n++) {
            String name = fill.apply(stat.name().replace("{n}", String.valueOf(n)), session);
            String value = fill.apply(stat.value().replace("{n}", String.valueOf(n)), session);
            boolean mine = !viewer.isEmpty() && name.trim().equalsIgnoreCase(viewer);
            rows.add(UiEntry.of(n).with("rank", n).with("name", name).with("value", value).with("mine", mine).build());
        }
        return rows;
    }

    private static void step(BoardsConfig.Board board, ScreenSession session, int by, BiFunction<String, ScreenSession, String> fill) {
        if (board.tabs()) {
            int offset = offset(board, session, fill);
            if (by < 0 ? offset > 0 : hasNext(board, session, fill)) session.set("offset", offset + by);
        } else {
            session.set("page", page(board, session) + by); // walks the stats, round the ends
        }
    }

    private static String counter(BoardsConfig.Board board, ScreenSession session, BiFunction<String, ScreenSession, String> fill) {
        if (!board.tabs()) return (page(board, session) + 1) + " / " + board.stats().size();
        int first = offset(board, session, fill) * board.rows() + 1;
        return "#" + first + " - #" + (first + board.rows() - 1);
    }

    private static BoardsConfig.Stat stat(BoardsConfig.Board board, ScreenSession session) {
        return board.stats().get(page(board, session));
    }

    /** The stat a viewer is on, kept in range when stats were taken out. */
    static int page(BoardsConfig.Board board, ScreenSession session) {
        return Math.floorMod(session.state("page", 0), board.stats().size());
    }

    /** The page of places a viewer is on, stepped back while nobody holds its first place. */
    static int offset(BoardsConfig.Board board, ScreenSession session, BiFunction<String, ScreenSession, String> fill) {
        BoardsConfig.Stat stat = stat(board, session);
        int offset = board.tabs() ? Math.max(0, Math.min(session.state("offset", 0), MAX_PAGES - 1)) : 0;
        while (offset > 0 && !taken(stat, offset * board.rows() + 1, session, fill)) offset--;
        return offset;
    }

    private static boolean hasNext(BoardsConfig.Board board, ScreenSession session, BiFunction<String, ScreenSession, String> fill) {
        int offset = offset(board, session, fill);
        return offset + 1 < MAX_PAGES && taken(stat(board, session), (offset + 1) * board.rows() + 1, session, fill);
    }

    /** Whether place {@code n} has someone in it: the sources answer "-", blank or the raw placeholder past the end. */
    static boolean taken(BoardsConfig.Stat stat, int n, ScreenSession session, BiFunction<String, ScreenSession, String> fill) {
        String name = fill.apply(stat.name().replace("{n}", String.valueOf(n)), session).trim();
        return !name.isEmpty() && !name.equals("-") && !name.contains("%");
    }
}
