package net.exylia.exyliaDisplays;

import lombok.Getter;
import net.exylia.exyliaDisplays.common.DisplayMenus;
import net.exylia.exyliaDisplays.common.DisplaysCommand;
import net.exylia.exyliaDisplays.config.DisplaysConfig;
import net.exylia.exyliaDisplays.config.DisplaysMessages;
import net.exylia.exyliaDisplays.module.DisplayModule;
import net.exylia.exyliaDisplays.module.Modules;
import net.exylia.lib.action.Actions;
import net.exylia.lib.action.PluginActions;
import net.exylia.lib.config.Configs;
import net.exylia.lib.debug.Debug;
import net.exylia.lib.input.Inputs;
import net.exylia.lib.input.PluginInputs;
import net.exylia.lib.reload.Reloads;
import net.exylia.lib.task.TaskScheduler;
import net.exylia.lib.task.Tasks;
import net.exylia.lib.text.PluginMessages;
import net.exylia.lib.text.Prefixes;
import org.bukkit.Server;
import org.bukkit.plugin.java.JavaPlugin;
import revxrsal.commands.Lamp;
import revxrsal.commands.bukkit.BukkitLamp;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * ExyliaDisplays: holograms, NPCs and whatever else is put in the world, edited in game through
 * commands and screens or by hand in their files, and drawn by ExyliaLib as packets.
 *
 * <p>The core holds what the modules share — inputs, menus, actions, commands, reloads — and starts
 * the modules {@code config.yml} switches on. A module owns everything about its subject; see
 * {@link DisplayModule}.
 *
 * <p>Not the Bukkit plugin itself: {@link ExyliaDisplaysPlugin} is, so it can install ExyliaLib
 * when the server lacks it.
 */
@Getter
public final class ExyliaDisplays {

    /** The namespace every module's menu buttons are written against. */
    public static final String NAMESPACE = "exyliadisplays";

    @Getter
    private static ExyliaDisplays instance;

    private JavaPlugin plugin;
    private TaskScheduler tasks;
    private Debug debug;
    private PluginMessages messages;
    private PluginInputs inputs;
    private PluginActions actions;
    private DisplayMenus menus;
    private Lamp<BukkitCommandActor> lamp;
    private Reloads reloads;
    private final List<DisplayModule> modules = new ArrayList<>();

    public Server getServer() {
        return plugin.getServer();
    }

    /** Starts the core, then every module the config switches on. */
    public void start(JavaPlugin plugin) {
        instance = this;
        this.plugin = plugin;

        tasks = Tasks.of(plugin);
        debug = Debug.of(plugin);
        messages = PluginMessages.of(plugin);
        inputs = Inputs.of(plugin);

        adoptHologramsFolder();
        loadConfigs();
        debug.motd();

        actions = Actions.of(plugin, NAMESPACE);
        menus = new DisplayMenus(this, NAMESPACE);
        lamp = BukkitLamp.builder(plugin).build();

        for (Supplier<DisplayModule> factory : Modules.all()) {
            DisplayModule module = factory.get();
            if (!DisplaysConfig.get().enabled(module.id())) {
                debug.log("Module " + module.id() + " is switched off in config.yml.");
                continue;
            }
            module.enable(this);
            menus.register(module.menus());
            modules.add(module);
        }
        // After the modules: compiling a menu resolves every action its buttons name.
        menus.load();
        lamp.register(new DisplaysCommand());

        loadReloads();
        debug.success("ExyliaDisplays enabled: " + String.join(", ", modules.stream().map(DisplayModule::id).toList()));
    }

    public void shutdown() {
        for (int i = modules.size() - 1; i >= 0; i--) {
            modules.get(i).disable();
        }
        modules.clear();
        instance = null;
        if (debug != null) debug.success("ExyliaDisplays disabled");
    }

    /** A running module by id. */
    public Optional<DisplayModule> module(String id) {
        return modules.stream().filter(module -> module.id().equals(id)).findFirst();
    }

    /** Whether a module runs. */
    public boolean isRunning(String id) {
        return module(id).isPresent();
    }

    private void loadConfigs() {
        DisplaysConfig.install(Configs.define(plugin, "config", DisplaysConfig.class).load());
        DisplaysMessages.install(Configs.define(plugin, "messages", DisplaysMessages.class).translated().load());
        applyConfigs();
    }

    /** What has to follow every read of the files. */
    private void applyConfigs() {
        Prefixes.set(plugin, DisplaysMessages.get().prefix());
        debug.enabled(DisplaysConfig.get().debug());
    }

    /**
     * What {@code /displays reload} does: every file, then every module, then the screens.
     *
     * <p>Menus are also a library-reload step: their items hold the parsed palette, so a recolour
     * through {@code /exylialib reload} would otherwise leave them on the old one.
     */
    private void loadReloads() {
        reloads = Reloads.of(plugin)
                .step("configs", () -> {
                    Configs.reloadAll(plugin);
                    applyConfigs();
                })
                .step("modules", () -> modules.forEach(DisplayModule::reload))
                .stepAlsoOnLibraryReload("menus", menus::reload);
    }

    /**
     * Brings the holograms over from ExyliaHolograms, the name this plugin had before it grew NPCs.
     *
     * <p>Copied, never moved: the old folder stays as it was, so nothing is lost if the old jar is
     * put back.
     */
    private void adoptHologramsFolder() {
        Path old = new File(plugin.getDataFolder().getParentFile(), "ExyliaHolograms").toPath().resolve("holograms.yml");
        Path mine = plugin.getDataFolder().toPath().resolve("holograms.yml");
        if (!Files.isRegularFile(old) || Files.exists(mine)) {
            return;
        }
        try {
            Files.createDirectories(mine.getParent());
            Files.copy(old, mine);
            debug.success("Brought the holograms over from plugins/ExyliaHolograms/holograms.yml.");
        } catch (IOException failure) {
            debug.error("Could not copy plugins/ExyliaHolograms/holograms.yml; copy it here by hand.", failure);
        }
        if (getServer().getPluginManager().getPlugin("ExyliaHolograms") != null) {
            debug.warn("ExyliaHolograms is still installed: remove its jar, ExyliaDisplays replaces it"
                    + " and both would draw every hologram.");
        }
    }
}
