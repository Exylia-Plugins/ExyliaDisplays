package net.exylia.exyliaDisplays.common;

import net.exylia.exyliaDisplays.ExyliaDisplays;
import net.exylia.lib.config.Languages;
import net.exylia.lib.ui.Menus;
import net.exylia.lib.ui.PluginMenus;
import net.exylia.lib.ui.UiDefinition;
import net.exylia.lib.ui.UiEntry;
import net.exylia.lib.ui.UiSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * This plugin's screens: read once from the jar, opened cheaply.
 *
 * <p>Each module names the screens it opens; they are admin screens, so they are rewritten from
 * the jar on every start and a release always reaches them; their header says so.
 */
public final class DisplayMenus {

    private final ExyliaDisplays plugin;
    private final PluginMenus menus;
    private final List<String> ids = new ArrayList<>();

    public DisplayMenus(ExyliaDisplays plugin, String namespace) {
        this.plugin = plugin;
        this.menus = Menus.of(plugin.getPlugin(), namespace);
    }

    /** Adds the screens a module opens. */
    public void register(List<String> menuIds) {
        ids.addAll(menuIds);
    }

    /** Writes the packaged screens out and compiles every one. */
    public void load() {
        Languages.replace(plugin.getPlugin(), ExyliaDisplays.class, "menus/admin");
        for (String id : ids) {
            YamlConfiguration config = read(id);
            if (config != null) menus.load(id, config);
        }
    }

    /**
     * Reads one menu file, or reports why it could not be read: a broken file is named in the log
     * and left unregistered, rather than registered as an empty menu.
     */
    private YamlConfiguration read(String id) {
        File file = Languages.file(plugin.getPlugin(), id + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
            return config;
        } catch (IOException | InvalidConfigurationException failure) {
            plugin.getDebug().error("The menu \"" + id + "\" could not be read, so it is not"
                    + " registered. Fix " + file.getPath() + " and reload.", failure);
            return null;
        }
    }

    public void reload() {
        menus.unload();
        load();
    }

    /**
     * Opens a screen with its list already filled, on the player's own thread.
     *
     * <p>When the player already has that screen open it is redrawn in place instead: the page is
     * kept and the open sound does not play again, which is what an edit should feel like.
     */
    public void show(Player player, String id, Map<String, Object> context, List<UiEntry> rows) {
        Runnable open = () -> {
            if (menus.update(player, id, context, rows)) return;
            Optional<UiDefinition> definition = menus.definition(id);
            if (definition.isEmpty()) {
                plugin.getDebug().warn("Something asked to open the menu \"" + id + "\", which is not loaded.");
                return;
            }
            menus.openNow(player, definition.get(), context,
                    rows == null ? Map.of() : Map.of(UiSection.MAIN, rows));
        };
        if (plugin.getTasks().isOwnedBy(player)) {
            open.run();
        } else {
            plugin.getTasks().runAtEntity(player, open);
        }
    }
}
