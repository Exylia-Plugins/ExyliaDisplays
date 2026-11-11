package net.exylia.exyliaDisplays.config;

import net.exylia.exyliaDisplays.ExyliaDisplays;
import net.exylia.exyliaDisplays.Permissions;
import net.exylia.exyliaDisplays.leaderboard.LeaderboardModule;
import net.exylia.lib.action.ActionResult;
import net.exylia.lib.action.PluginActions;
import net.exylia.lib.config.ConfigEdits;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.ui.UiKeys;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The settings an admin really tunes, chosen and explained by hand: a hub with one card per
 * theme, each opening a page. Everything else stays in the files.
 *
 * <p>Every page is {@code menus/admin/settings_<page>}; its values are
 * {@code %<page>_<key>%} (the YAML key with dots and dashes as underscores) and its buttons run
 * {@code exyliadisplays:setting <page> <key>}.
 */
public final class SettingsMenus {

    public static final String HUB = "menus/admin/settings";

    /** Every screen of the settings, for the menu loader. */
    public static final List<String> MENUS = List.of(HUB, HUB + "_holograms", HUB + "_npcs",
            HUB + "_leaderboards");

    /** A page: the file it edits and the keys its hub card shows. */
    private record Page(Supplier<ConfigFile<?>> file, String... shown) {
    }

    private static final Map<String, Page> PAGES = new LinkedHashMap<>();

    /** Where Back on the hub goes: the list the admin opened the settings from. */
    private static final Map<UUID, Consumer<Player>> ORIGIN = new ConcurrentHashMap<>();

    static {
        Supplier<ConfigFile<?>> config = DisplaysConfig::file;
        PAGES.put("holograms", new Page(config, "holograms.default-view-distance", "holograms.spawn-height"));
        PAGES.put("npcs", new Page(config, "npcs.default-view-distance", "npcs.default-look"));
        PAGES.put("leaderboards", new Page(() -> {
            LeaderboardModule module = LeaderboardModule.get();
            return module == null ? null : module.file();
        }, "week-starts-on", "day-starts-at"));
    }

    private SettingsMenus() {
        throw new AssertionError("No instances.");
    }

    /** The hub, remembering where Back leads. */
    public static void openHub(Player admin, Consumer<Player> origin) {
        ORIGIN.put(admin.getUniqueId(), origin);
        openHub(admin);
    }

    private static void openHub(Player admin) {
        Map<String, Object> context = new LinkedHashMap<>();
        PAGES.forEach((id, page) -> {
            for (String key : page.shown()) context.put(name(id, key), "{muted}—");
            if (page.file().get() != null) context.putAll(edits(page).placeholders(id + "_", List.of(page.shown())));
            else for (String key : page.shown()) context.put(name(id, key), DisplaysMessages.get().off());
        });
        ExyliaDisplays.getInstance().getMenus().show(admin, SettingsMenus.HUB, context, null);
    }

    /** One page; a module that is off has no file, so its page says so. */
    private static void openPage(Player admin, String id) {
        Page page = PAGES.get(id);
        if (page == null) return;
        if (page.file().get() == null) {
            ExyliaDisplays.getInstance().getMessages().send(admin, DisplaysMessages.get().off());
            return;
        }
        ExyliaDisplays.getInstance().getMenus().show(admin, HUB + "_" + id, edits(page).placeholders(id + "_"), null);
    }

    public static void register(PluginActions actions) {
        actions.registerSync("settings_hub", Permissions.ADMIN, (ctx, args) -> {
            if (ctx.player() != null) openHub(ctx.player());
            return ActionResult.success();
        });
        actions.registerSync("settings_back", Permissions.ADMIN, (ctx, args) -> {
            Player admin = ctx.player();
            Consumer<Player> origin = admin == null ? null : ORIGIN.get(admin.getUniqueId());
            if (origin != null) origin.accept(admin);
            return ActionResult.success();
        });
        actions.registerSync("settings_page", Permissions.ADMIN, (ctx, args) -> {
            if (ctx.player() != null) openPage(ctx.player(), args.string(0, ""));
            return ActionResult.success();
        });
        actions.registerSync("setting", Permissions.ADMIN, (ctx, args) -> {
            Player admin = ctx.player();
            String id = args.string(0, "");
            Page page = PAGES.get(id);
            if (admin == null || page == null || page.file().get() == null) return ActionResult.success();
            edits(page).card(admin, args.string(1, ""), ctx.get(UiKeys.CLICK).orElse(null), () -> openPage(admin, id));
            return ActionResult.success();
        });
    }

    private static String name(String page, String key) {
        return page + "_" + key.replace('.', '_').replace('-', '_');
    }

    private static ConfigEdits edits(Page page) {
        return ConfigEdits.of(ExyliaDisplays.getInstance().getPlugin(), page.file());
    }
}
