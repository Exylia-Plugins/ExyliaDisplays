package net.exylia.exyliaDisplays.board;

import net.exylia.exyliaDisplays.ExyliaDisplays;
import net.exylia.exyliaDisplays.module.DisplayModule;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.config.Configs;
import net.exylia.lib.placeholder.Placeholders;
import net.exylia.lib.screen.WorldScreen;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * Boards: {@code boards.yml}, leaderboard panels standing in the world as library {@link WorldScreen}s. A reload
 * takes every board down and puts the file's back.
 */
public final class BoardsModule implements DisplayModule {

    public static final String ID = "boards";

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
            placed.add(WorldScreen.place(core.getPlugin(), at,
                    BoardScreen.of(board, (text, session) -> Placeholders.apply(text, session.viewer())),
                    board.width()).range(board.range()));
        });
        core.getDebug().log("Boards: " + placed.size());
    }

    private void takeDown() {
        placed.forEach(WorldScreen::remove);
        placed.clear();
    }
}
