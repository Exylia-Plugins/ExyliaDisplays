package net.exylia.exyliaDisplays.board;

import net.exylia.exyliaDisplays.common.store.Position;
import net.exylia.lib.screen.Node;
import net.exylia.lib.screen.Screen;
import net.exylia.lib.screen.ScreenEvent;
import net.exylia.lib.screen.ScreenSession;
import net.exylia.lib.screen.internal.Layout;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardScreenTest {

    private static final BoardsConfig.Board BOARD = new BoardsConfig.Board(new Position(), 3, 5, true, 32, "You", List.of(
            new BoardsConfig.Stat("TOP KILLS", "Kills", "k{n}", "{n}0", "7"),
            new BoardsConfig.Stat("TOP LEVEL", "Level", "l{n}", "{n}", "3")));

    /** A session of its own per viewer, as a world screen gives each one. */
    private static final class Session implements ScreenSession {
        private final Map<String, Object> state = new HashMap<>();
        private final Player viewer;

        Session(String name) {
            viewer = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                    (self, method, args) -> method.getName().equals("getName") ? name : null);
        }

        @Override public @NotNull Player viewer() { return viewer; }
        @Override public @NotNull Screen screen() { throw new UnsupportedOperationException(); }
        @SuppressWarnings("unchecked")
        @Override public <T> @NotNull T state(@NotNull String key, @NotNull T fallback) {
            Object v = state.get(key);
            return fallback.getClass().isInstance(v) ? (T) v : fallback;
        }
        @Override public void set(@NotNull String key, Object value) { state.put(key, value); }
        @Override public void redraw() { }
        @Override public boolean open() { return true; }
        @Override public void close() { }
    }

    private static List<Layout.Placed> lay(Session session) {
        Node root = BoardScreen.render(BOARD, session, (text, s) -> text);
        return Layout.lay(root, BoardScreen.WIDTH, BoardScreen.height(BOARD));
    }

    private static Layout.Placed byId(List<Layout.Placed> placed, String id) {
        return placed.stream().filter(p -> id.equals(p.node().id())).findFirst().orElseThrow();
    }

    private static String text(Layout.Placed box) {
        return PlainTextComponentSerializer.plainText().serialize(box.node().text());
    }

    private static void click(Session session, Layout.Placed box) {
        box.node().onClick().accept(new ScreenEvent(session, ScreenEvent.Kind.LEFT, 0, box.node().id()));
    }

    @Test
    void arrowsAndTabsTurnOnlyTheirViewersPage() {
        Session a = new Session("A"), b = new Session("B");
        click(a, byId(lay(a), "next"));
        assertEquals("TOP LEVEL", text(byId(lay(a), "title")));
        assertEquals("TOP KILLS", text(byId(lay(b), "title")));
        click(a, byId(lay(a), "next")); // past the last page, back to the first
        assertEquals("TOP KILLS", text(byId(lay(a), "title")));
        click(b, byId(lay(b), "prev")); // before the first, the last
        assertEquals("TOP LEVEL", text(byId(lay(b), "title")));
        click(a, byId(lay(a), "tab-1"));
        assertEquals("TOP LEVEL", text(byId(lay(a), "title")));
    }

    @Test
    void everythingFitsOnThePanelAndValuesEndOnOneEdge() {
        List<Layout.Placed> placed = lay(new Session("A"));
        double bottom = placed.stream().mapToDouble(p -> p.y() + p.h()).max().orElseThrow();
        assertTrue(bottom <= BoardScreen.height(BOARD), "laid to " + bottom);
        List<Layout.Placed> values = placed.stream()
                .filter(p -> p.node().text() != null && p.node().textAlign() == Node.Align.END).toList();
        assertEquals(6, values.size(), "five places and the viewer's own");
        double edge = values.getFirst().x() + values.getFirst().w();
        for (Layout.Placed value : values) assertEquals(edge, value.x() + value.w(), 1e-9);
        assertTrue(byId(placed, "next").x() + byId(placed, "next").w() <= BoardScreen.WIDTH);
    }
}
