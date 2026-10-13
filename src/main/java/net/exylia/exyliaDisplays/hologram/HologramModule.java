package net.exylia.exyliaDisplays.hologram;

import net.exylia.exyliaDisplays.ExyliaDisplays;
import net.exylia.exyliaDisplays.Permissions;
import net.exylia.exyliaDisplays.common.FileWatcher;
import net.exylia.exyliaDisplays.common.store.EntryStore;
import net.exylia.exyliaDisplays.config.DisplaysConfig;
import net.exylia.exyliaDisplays.module.DisplayModule;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.config.ConfigIssue;
import net.exylia.lib.config.Configs;
import net.exylia.lib.text.Values;

import java.io.File;
import java.util.List;

/**
 * Holograms: {@code holograms.yml}, {@code /holo}, and the screens and windows that edit them.
 */
public final class HologramModule implements DisplayModule {

    public static final String ID = "holograms";
    public static final String MENU_LIST = "menus/admin/hologram_list";
    public static final String MENU_HUB = "menus/admin/hologram_hub";
    public static final String MENU_LINES = "menus/admin/hologram_lines";

    private static HologramModule instance;

    private ExyliaDisplays core;
    private EntryStore<HologramFile, StoredHologram> store;
    private HologramRenderer renderer;
    private FileWatcher watcher;

    /** The running module; only called by code that runs because the module does. */
    public static HologramModule get() {
        return instance;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<String> menus() {
        return List.of(MENU_LIST, MENU_HUB, MENU_LINES);
    }

    @Override
    public void enable(ExyliaDisplays core) {
        instance = this;
        this.core = core;
        HologramsMessages.install(Configs.define(core.getPlugin(), "messages/holograms", HologramsMessages.class)
                .translated().load());

        renderer = new HologramRenderer(core.getPlugin());
        ConfigFile<HologramFile> file = Configs.define(core.getPlugin(), "holograms", HologramFile.class).load();
        store = new EntryStore<>(file, HologramFile::holograms, HologramFile::new, renderer::apply);
        renderer.applyAll(store.all());

        watcher = new FileWatcher(new File(core.getPlugin().getDataFolder(), "holograms.yml").toPath(),
                core.getTasks(), store::lastWrite, this::applyFileEdit, core.getPlugin().getLogger());
        if (DisplaysConfig.get().watchFiles()) watcher.start();

        new HologramActions(core).registerAll();
        core.getLamp().register(new HologramCommand());
        core.getDebug().log("Holograms: " + store.all().size());
    }

    @Override
    public void reload() {
        renderer.applyAll(store.all());
        watcher.stop();
        if (DisplaysConfig.get().watchFiles()) watcher.start();
    }

    @Override
    public void disable() {
        if (watcher != null) watcher.stop();
        if (renderer != null) renderer.clear();
        instance = null;
    }

    public EntryStore<HologramFile, StoredHologram> store() {
        return store;
    }

    public HologramRenderer renderer() {
        return renderer;
    }

    /**
     * Reads holograms.yml again and shows what it says now.
     *
     * @return the mistakes found in it
     */
    public List<ConfigIssue> reloadFile() {
        // A key left out is not a mistake: a hand-written hologram only needs what it changes,
        // and the defaults are written in for it.
        List<ConfigIssue> issues = store.reload().stream()
                .filter(issue -> issue.type() != ConfigIssue.Type.MISSING_KEY)
                .toList();
        issues.forEach(issue -> core.getDebug().warn("holograms.yml: " + issue.describe()));
        renderer.applyAll(store.all());
        watcher.remember();
        return issues;
    }

    /** A hand edit of holograms.yml, noticed by the watcher: applied, and the admins told. */
    private void applyFileEdit() {
        List<ConfigIssue> issues = reloadFile();
        Values values = Values.of().put("count", store.all().size()).put("issues", issues.size());
        String line = issues.isEmpty() ? HologramsMessages.get().fileApplied() : HologramsMessages.get().fileIssues();
        core.getServer().getOnlinePlayers().stream()
                .filter(player -> player.hasPermission(Permissions.HOLOGRAMS))
                .forEach(player -> core.getMessages().send(player, line, values));
    }
}
