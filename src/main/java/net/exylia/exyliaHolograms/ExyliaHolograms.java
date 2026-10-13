package net.exylia.exyliaHolograms;

import lombok.Getter;
import net.exylia.exyliaHolograms.action.HologramActions;
import net.exylia.exyliaHolograms.command.HologramCommand;
import net.exylia.exyliaHolograms.config.HologramsConfig;
import net.exylia.exyliaHolograms.config.HologramsMessages;
import net.exylia.exyliaHolograms.menu.HologramMenus;
import net.exylia.exyliaHolograms.service.FileWatcher;
import net.exylia.exyliaHolograms.service.HologramRenderer;
import net.exylia.exyliaHolograms.store.HologramFile;
import net.exylia.exyliaHolograms.store.HologramStore;
import net.exylia.lib.action.Actions;
import net.exylia.lib.action.PluginActions;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.config.ConfigIssue;
import net.exylia.lib.config.Configs;
import net.exylia.lib.debug.Debug;
import net.exylia.lib.input.Inputs;
import net.exylia.lib.input.PluginInputs;
import net.exylia.lib.reload.Reloads;
import net.exylia.lib.task.TaskScheduler;
import net.exylia.lib.task.Tasks;
import net.exylia.lib.text.PluginMessages;
import net.exylia.lib.text.Prefixes;
import net.exylia.lib.text.Values;
import org.bukkit.Server;
import org.bukkit.plugin.java.JavaPlugin;
import revxrsal.commands.bukkit.BukkitLamp;

import java.io.File;
import java.util.List;

/**
 * ExyliaHolograms: holograms for Paper and Folia, edited in game through commands and screens, or
 * by hand in {@code holograms.yml}, and drawn by ExyliaLib's packet holograms.
 *
 * <p>Not the Bukkit plugin itself: {@link ExyliaHologramsPlugin} is, so it can install ExyliaLib
 * when the server lacks it. Every library view is asked for with {@link #plugin}, because the
 * library keys its per-plugin views by Bukkit plugin.
 */
@Getter
public final class ExyliaHolograms {

    /** The namespace the menu buttons' actions are written against. */
    public static final String NAMESPACE = "exyliaholograms";

    @Getter
    private static ExyliaHolograms instance;

    private JavaPlugin plugin;
    private TaskScheduler tasks;
    private Debug debug;
    private PluginMessages messages;
    private PluginInputs inputs;
    private PluginActions actions;
    private HologramMenus menus;
    private Reloads reloads;
    private HologramStore store;
    private HologramRenderer renderer;
    private FileWatcher watcher;

    public Server getServer() {
        return plugin.getServer();
    }

    /** Starts every module on the Bukkit plugin that loaded this core. */
    public void start(JavaPlugin plugin) {
        instance = this;
        this.plugin = plugin;

        tasks = Tasks.of(plugin);
        debug = Debug.of(plugin);
        messages = PluginMessages.of(plugin);
        inputs = Inputs.of(plugin);

        loadConfigs();
        debug.motd();

        renderer = new HologramRenderer(plugin);
        ConfigFile<HologramFile> file = Configs.define(plugin, "holograms", HologramFile.class).load();
        store = new HologramStore(file, renderer::apply);
        renderer.applyAll(store.all());

        watcher = new FileWatcher(new File(plugin.getDataFolder(), "holograms.yml").toPath(), tasks,
                store::lastWrite, this::applyFileEdit, plugin.getLogger());
        if (HologramsConfig.get().watchFile()) watcher.start();

        actions = Actions.of(plugin, NAMESPACE);
        new HologramActions(this).registerAll();
        // After the actions: compiling a menu resolves every action its buttons name.
        menus = new HologramMenus(this, NAMESPACE);
        menus.load();

        BukkitLamp.builder(plugin).build().register(new HologramCommand());

        loadReloads();
        debug.success("ExyliaHolograms enabled with " + store.all().size() + " holograms");
    }

    /** Takes the holograms down; everything else the library owns is released by the library. */
    public void shutdown() {
        if (watcher != null) watcher.stop();
        if (renderer != null) renderer.clear();
        instance = null;
        if (debug != null) debug.success("ExyliaHolograms disabled");
    }

    /**
     * Reads holograms.yml again and shows what it says now.
     *
     * @return the mistakes found in it
     */
    public List<ConfigIssue> reloadHolograms() {
        // A key left out is not a mistake: a hand-written hologram only needs what it changes,
        // and the defaults are written in for it.
        List<ConfigIssue> issues = store.reload().stream()
                .filter(issue -> issue.type() != ConfigIssue.Type.MISSING_KEY)
                .toList();
        issues.forEach(issue -> debug.warn("holograms.yml: " + issue.describe()));
        renderer.applyAll(store.all());
        if (watcher != null) watcher.remember();
        return issues;
    }

    /** A hand edit of holograms.yml, noticed by the watcher: applied, and the admins told. */
    private void applyFileEdit() {
        List<ConfigIssue> issues = reloadHolograms();
        Values values = Values.of().put("count", store.all().size()).put("issues", issues.size());
        String line = issues.isEmpty() ? HologramsMessages.get().fileApplied() : HologramsMessages.get().fileIssues();
        getServer().getOnlinePlayers().stream()
                .filter(player -> player.hasPermission(Permissions.ADMIN))
                .forEach(player -> messages.send(player, line, values));
    }

    private void loadConfigs() {
        HologramsConfig.install(Configs.define(plugin, "config", HologramsConfig.class).load());
        HologramsMessages.install(Configs.define(plugin, "messages", HologramsMessages.class).translated().load());
        applyConfigs();
    }

    /** What has to follow every read of the files. */
    private void applyConfigs() {
        Prefixes.set(plugin, HologramsMessages.get().prefix());
        debug.enabled(HologramsConfig.get().debug());
    }

    /**
     * What {@code /holo reload} does.
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
                .step("holograms", () -> {
                    renderer.applyAll(store.all());
                    watcher.stop();
                    if (HologramsConfig.get().watchFile()) watcher.start();
                })
                .stepAlsoOnLibraryReload("menus", menus::reload);
    }
}
