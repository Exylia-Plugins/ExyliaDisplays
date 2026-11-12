package net.exylia.exyliaDisplays.board;

import net.exylia.lib.screen.Node;
import net.exylia.lib.screen.Screen;
import net.exylia.lib.screen.ScreenSession;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * What a board draws: one panel, a title, tabs, the places of the viewer's page, their own row and the arrows.
 * Every viewer has a session of their own, so {@code page} is theirs.
 */
final class BoardScreen {

    /** Layout pixels across; the board's width in blocks scales them. */
    static final double WIDTH = 240;
    private static final double ROW = 12;
    private static final String PANEL = "#0e0b16";
    private static final String EDGE = "#2b2140";
    private static final String STRIPE = "#15111f";
    private static final String BUTTON = "#1c1629";
    private static final String ACCENT = "#8a51c4";
    private static final String[] PODIUM = {"#ffd56b", "#d6d6e6", "#e09a5c"};

    private BoardScreen() {
    }

    /**
     * @param fill a text with the viewer's placeholders filled
     */
    static Screen of(BoardsConfig.Board board, BiFunction<String, ScreenSession, String> fill) {
        return Screen.of(session -> render(board, session, fill)).size(WIDTH, height(board));
    }

    static double height(BoardsConfig.Board board) {
        return 16 + 16 + (board.tabs() ? 17 : 0) + 3 + board.rows() * (ROW + 1) - 1 + 3 + 13 + 3 + 16;
    }

    /** The page a viewer is on, kept in range when stats were taken out. */
    static int page(BoardsConfig.Board board, ScreenSession session) {
        return Math.floorMod(session.state("page", 0), board.stats().size());
    }

    static Node render(BoardsConfig.Board board, ScreenSession session, BiFunction<String, ScreenSession, String> fill) {
        List<BoardsConfig.Stat> stats = board.stats();
        int page = page(board, session);
        BoardsConfig.Stat stat = stats.get(page);
        String viewer = session.viewer().getName();
        List<Node> parts = new ArrayList<>();
        parts.add(Node.text(fill.apply(stat.title(), session)).scale(1.3).height(16).id("title"));
        if (board.tabs()) {
            List<Node> tabs = new ArrayList<>();
            for (int i = 0; i < stats.size(); i++) {
                int to = i;
                boolean on = i == page;
                tabs.add(Node.text((on ? "<white>" : "<#b48fd9>") + stats.get(i).tab()).scale(0.75).grow(1).height(14)
                        .background(on ? ACCENT : BUTTON).hover(on ? ACCENT : EDGE).hoverOutline(ACCENT)
                        .outline(on ? ACCENT : EDGE, 1).id("tab-" + i)
                        .onClick(event -> event.session().set("page", to)));
            }
            parts.add(Node.row(tabs.toArray(Node[]::new)).gap(2).height(14));
        }
        List<Node> places = new ArrayList<>();
        for (int n = 1; n <= board.rows(); n++) {
            String name = fill.apply(stat.name().replace("{n}", String.valueOf(n)), session);
            String value = fill.apply(stat.value().replace("{n}", String.valueOf(n)), session);
            boolean mine = !viewer.isEmpty() && name.trim().equalsIgnoreCase(viewer);
            String rank = n <= PODIUM.length ? PODIUM[n - 1] : ACCENT;
            places.add(row("<" + rank + ">" + n, "<#e7cfff>" + name, "<#b48fd9>" + value,
                    mine ? "#2a1d40" : n % 2 == 1 ? STRIPE : null));
        }
        parts.add(Node.column(places.toArray(Node[]::new)).gap(1));
        if (!stat.self().isBlank()) {
            parts.add(row("<" + ACCENT + ">" + board.you(), "<#e7cfff>" + viewer,
                    "<#ffc58f>" + fill.apply(stat.self(), session), "#2a1d40").height(13));
        }
        parts.add(Node.row(
                arrow("◀", "prev", page - 1),
                Node.text("<#8a7aa3>" + (page + 1) + " / " + stats.size()).scale(0.8).grow(1).height(16),
                arrow("▶", "next", page + 1)).height(16));
        return Node.column(parts.toArray(Node[]::new)).gap(3).padding(8).background(PANEL).outline(EDGE, 1);
    }

    private static Node row(String rank, String name, String value, String background) {
        return Node.row(
                Node.text(rank).width(20).textAlign(Node.Align.START),
                Node.text(name).grow(1).textAlign(Node.Align.START),
                Node.text(value).width(70).textAlign(Node.Align.END)).height(ROW).padding(0, 4).background(background);
    }

    private static Node arrow(String glyph, String id, int to) {
        return Node.text("<#e7cfff>" + glyph).width(24).height(16).background(BUTTON).hover(EDGE)
                .outline(EDGE, 1).hoverOutline(ACCENT).id(id)
                .onClick(event -> event.session().set("page", to));
    }
}
