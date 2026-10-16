package net.exylia.exyliaDisplays.npc;

import net.exylia.exyliaDisplays.ExyliaDisplays;
import net.exylia.exyliaDisplays.Permissions;
import net.exylia.exyliaDisplays.common.FileWatcher;
import net.exylia.exyliaDisplays.common.store.EntryStore;
import net.exylia.exyliaDisplays.config.DisplaysConfig;
import net.exylia.exyliaDisplays.module.DisplayModule;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.web.WebEditor;
import net.exylia.lib.config.ConfigIssue;
import net.exylia.lib.config.Configs;
import net.exylia.lib.text.Values;

import java.io.File;
import java.util.List;

/**
 * NPCs: {@code npcs.yml}, {@code /npc}, {@code /npcadmin}, and the screens and windows that edit them.
 *
 * <p>Starts after holograms: its labels are holograms, and a linked hologram is moved through the
 * hologram module's own store.
 */
public final class NpcModule implements DisplayModule {

    public static final String ID = "npcs";
    public static final String MENU_LIST = "menus/admin/npc_list";
    public static final String MENU_HUB = "menus/admin/npc_hub";
    public static final String MENU_EQUIPMENT = "menus/admin/npc_equipment";
    public static final String MENU_PERFORMANCES = "menus/admin/npc_performances";

    private static NpcModule instance;

    private ExyliaDisplays core;
    private EntryStore<NpcFile, StoredNpc> store;
    private NpcRenderer renderer;
    private FileWatcher watcher;
    private NpcPerformances performances;
    private final NpcAdjust adjust = new NpcAdjust();

    /** The running module; only called by code that runs because the module does. */
    public static NpcModule get() {
        return instance;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<String> menus() {
        return List.of(MENU_LIST, MENU_HUB, MENU_EQUIPMENT, MENU_PERFORMANCES);
    }

    @Override
    public void enable(ExyliaDisplays core) {
        instance = this;
        this.core = core;
        NpcsMessages.install(Configs.define(core.getPlugin(), "messages/npcs", NpcsMessages.class)
                .translated().load());

        // Before any NPC shows: an emote: performance asks the resolver this registers.
        performances = new NpcPerformances(core);
        performances.enable();
        renderer = new NpcRenderer(core, adjust::preview);
        // Before the first NPC compiles its lines: a token unknown then is a line skipped.
        new NpcTokens(core, renderer).register();
        ConfigFile<NpcFile> file = Configs.define(core.getPlugin(), "npcs", NpcFile.class).load();
        store = new EntryStore<>(file, NpcFile::npcs, NpcFile::new, (name, npc) -> {
            if (npc == null) adjust.removed(name);
            renderer.apply(name, npc);
        });
        renderer.applyAll(store.all());

        watcher = new FileWatcher(new File(core.getPlugin().getDataFolder(), "npcs.yml").toPath(),
                core.getTasks(), this::applyFileEdit, core.getPlugin().getLogger());
        store.onWritten(watcher::remember);
        if (DisplaysConfig.get().watchFiles()) watcher.start();
        // Edited in the browser as a form drawn from the file's own schema.
        WebEditor.of(core.getPlugin()).subject(ID, file)
                .title("NPCs")
                .permission(Permissions.NPCS)
                .sensitive("npcs.*.interactions.actions")
                .onApplied(this::applyFileEdit);

        new NpcActions(core).registerAll();
        core.getLamp().register(new NpcCommand());
        core.getLamp().register(new NpcAdminCommand());
        core.getDebug().log("NPCs: " + store.all().size());
    }

    @Override
    public void reload() {
        performances.refresh();
        renderer.applyAll(store.all());
        watcher.stop();
        if (DisplaysConfig.get().watchFiles()) watcher.start();
    }

    @Override
    public void disable() {
        adjust.saveAll();
        WebEditor.of(core.getPlugin()).remove(ID);
        if (watcher != null) watcher.stop();
        if (renderer != null) renderer.clear();
        if (performances != null) performances.disable();
        instance = null;
    }

    public EntryStore<NpcFile, StoredNpc> store() {
        return store;
    }

    public NpcRenderer renderer() {
        return renderer;
    }

    public NpcAdjust adjust() {
        return adjust;
    }

    /**
     * Reads npcs.yml again and shows what it says now.
     *
     * @return the mistakes found in it
     */
    public List<ConfigIssue> reloadFile() {
        // A key left out is not a mistake: a hand-written NPC only needs what it changes.
        List<ConfigIssue> issues = store.reload().stream()
                .filter(issue -> issue.type() != ConfigIssue.Type.MISSING_KEY)
                .toList();
        issues.forEach(issue -> core.getDebug().warn("npcs.yml: " + issue.describe()));
        adjust.prune();
        renderer.applyAll(store.all());
        watcher.remember();
        return issues;
    }

    /** A hand edit of npcs.yml, noticed by the watcher: applied, and the admins told. */
    private void applyFileEdit() {
        List<ConfigIssue> issues = reloadFile();
        Values values = Values.of().put("count", store.all().size()).put("issues", issues.size());
        String line = issues.isEmpty() ? NpcsMessages.get().fileApplied() : NpcsMessages.get().fileIssues();
        core.getServer().getOnlinePlayers().stream()
                .filter(player -> player.hasPermission(Permissions.NPCS))
                .forEach(player -> core.getMessages().send(player, line, values));
    }
}
