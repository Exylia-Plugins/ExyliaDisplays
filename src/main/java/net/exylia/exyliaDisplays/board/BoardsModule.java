package net.exylia.exyliaDisplays.board;

import net.exylia.exyliaDisplays.ExyliaDisplays;
import net.exylia.exyliaDisplays.module.DisplayModule;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.config.Configs;
import net.exylia.lib.placeholder.Placeholders;
import net.exylia.lib.screen.ScreenMarkup;
import net.exylia.lib.screen.ScreenSession;
import net.exylia.lib.screen.WorldScreen;
import org.bukkit.Location;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Boards: {@code boards.yml}, leaderboard panels standing in the world as library {@link WorldScreen}s. A reload
 * takes every board down and puts the file's back.
 */
public final class BoardsModule implements DisplayModule {

    public static final String ID = "boards";
    /** The template a board uses unless it names one; copied to {@code boards/} when missing, never overwritten. */
    private static final String DEFAULT = "board.html";

    private ExyliaDisplays core;
    private ConfigFile<BoardsConfig> file;
    private final List<WorldScreen> placed = new ArrayList<>();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ExyliaDisplays core) {
        this.core = core;
        file = Configs.define(core.getPlugin(), "boards", BoardsConfig.class).load();
        place();
    }

    @Override
    public void reload() {
        file.reload();
        place();
    }

    @Override
    public void disable() {
        takeDown();
    }

    private void place() {
        takeDown();
        file.get().boards().forEach((name, board) -> {
            Location at = board.location().toLocation();
            if (at == null) {
                core.getDebug().warn("boards.yml: " + name + ": world " + board.location().world() + " is not loaded.");
                return;
            }
            placed.add(WorldScreen.place(core.getPlugin(), at, BoardScreen.screen(markup(name, board), board),
                    board.width()).range(board.range()));
        });
        core.getDebug().log("Boards: " + placed.size());
    }

    /**
     * The board's template read from {@code boards/<template>} (and its sibling .css), or the bundled one when the
     * file does not read or has problems: the board never vanishes over a typo.
     */
    private ScreenMarkup markup(String name, BoardsConfig.Board board) {
        BiFunction<String, ScreenSession, String> fill = (text, session) -> Placeholders.apply(text, session.viewer());
        Path folder = core.getPlugin().getDataFolder().toPath().resolve("boards");
        String template = board.template();
        String sheet = template.replaceFirst("\\.[^.]*$", "") + ".css";
        try {
            Files.createDirectories(folder);
            for (String bundled : new String[]{DEFAULT, "board.css"}) {
                Path target = folder.resolve(bundled);
                if (!Files.exists(target)) Files.writeString(target, BoardScreen.bundled(bundled));
            }
            Path file = folder.resolve(template);
            if (!Files.isRegularFile(file)) {
                core.getDebug().warn("boards.yml: " + name + ": boards/" + template + " does not exist, using the bundled one.");
            } else {
                ScreenMarkup markup = BoardScreen.bind(ScreenMarkup.load(file, folder.resolve(sheet)), board, fill);
                List<String> problems = markup.problems();
                if (problems.isEmpty()) return markup;
                problems.forEach(p -> core.getDebug().warn("boards/" + template + ": " + p));
                core.getDebug().warn("boards.yml: " + name + ": boards/" + template + " has problems, using the bundled one.");
            }
        } catch (IOException | IllegalArgumentException e) {
            core.getDebug().warn("boards.yml: " + name + ": boards/" + template + ": " + e.getMessage() + ", using the bundled one.");
        }
        return BoardScreen.bind(ScreenMarkup.parse("board.html (bundled)", BoardScreen.bundled(DEFAULT), BoardScreen.bundled("board.css")), board, fill);
    }

    private void takeDown() {
        placed.forEach(WorldScreen::remove);
        placed.clear();
    }
}
