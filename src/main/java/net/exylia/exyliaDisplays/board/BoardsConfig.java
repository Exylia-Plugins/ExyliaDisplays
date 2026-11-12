package net.exylia.exyliaDisplays.board;

import net.exylia.exyliaDisplays.common.store.Position;
import net.exylia.lib.config.Comment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code boards.yml}: leaderboard panels standing in the world. Each player near one turns its pages and tabs on
 * their own by aiming and clicking; nobody is frozen and the panel never turns.
 *
 * @param boards every board, by name
 */
@Comment("Leaderboard panels standing in the world, drawn as one flat screen with real buttons.")
@Comment("Each player aims at a button and left-clicks it (right-click works holding an item): their own page, their own hover.")
@Comment("Example:")
@Comment("  top:")
@Comment("    location: {world: world, x: 0.5, y: 100.5, z: -6.5, yaw: 0.0, pitch: 0.0}")
@Comment("    width: 3.0")
@Comment("    rows: 10")
@Comment("    stats:")
@Comment("      - title: '<gradient:#b48fd9:#8a51c4>TOP KILLS</gradient>'")
@Comment("        tab: Kills")
@Comment("        name: '%myplugin_top_kills_{n}_name%'")
@Comment("        value: '%myplugin_top_kills_{n}_value%'")
@Comment("        self: '%myplugin_kills%'")
public record BoardsConfig(Map<String, Board> boards) {

    public BoardsConfig() {
        this(Map.of());
    }

    public BoardsConfig {
        boards = boards == null ? Map.of() : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(boards));
    }

    /**
     * One panel.
     *
     * @param location its middle, and which way it faces
     * @param width    blocks across; the height follows from the rows
     * @param rows     places shown on a page
     * @param tabs     a tab per stat above the rows, besides the arrows below
     * @param range    blocks a player sees it from
     * @param you      the label of the viewer's own row
     * @param template the markup file in {@code boards/} that draws it
     * @param stats    one page each, in order
     */
    public record Board(
            @Comment("Its middle, and which way it faces: 0 south, 90 west, 180 north, -90 east.")
            Position location,
            @Comment("How wide it is, in blocks. The height follows from the rows.")
            double width,
            @Comment("Places on a page.")
            int rows,
            @Comment("A tab per stat across the top, besides the arrows at the bottom.")
            boolean tabs,
            @Comment("How near a player must be to see it, in blocks.")
            double range,
            @Comment("The label of the viewer's own row at the bottom.")
            String you,
            @Comment("The look: a file in plugins/ExyliaDisplays/boards/ (its .css sits beside it). /displaysadmin reload applies edits.")
            String template,
            @Comment("One page each. In 'name' and 'value' {n} is the place: 1, 2, 3...")
            @Comment("'self' is the viewer's own value; empty hides their row.")
            List<Stat> stats) {

        public Board() {
            this(new Position(), 3.0, 10, true, 32, "You", "board.html", List.of(new Stat()));
        }

        public Board {
            if (location == null) location = new Position();
            if (width <= 0) width = 3.0;
            rows = Math.max(1, Math.min(rows, 20));
            if (range <= 0) range = 32;
            if (you == null) you = "You";
            if (template == null || template.isBlank() || template.contains("/") || template.contains("\\")) template = "board.html";
            stats = stats == null || stats.isEmpty() ? List.of(new Stat()) : List.copyOf(stats);
        }
    }

    /**
     * One page of a board.
     *
     * @param title what heads it
     * @param tab   its tab's label; empty for the title's
     * @param name  who holds place {n}
     * @param value their value
     * @param self  the viewer's own value
     */
    public record Stat(String title, String tab, String name, String value, String self) {

        public Stat() {
            this("TOP KILLS", "Kills", "%exyliadisplays_lb_kills:all-time:{n}:name%",
                    "%exyliadisplays_lb_kills:all-time:{n}:value%", "%exyliadisplays_lb_kills:all-time:me:value%");
        }

        public Stat {
            if (title == null) title = "";
            if (tab == null || tab.isBlank()) tab = title;
            if (name == null) name = "";
            if (value == null) value = "";
            if (self == null) self = "";
        }
    }
}
