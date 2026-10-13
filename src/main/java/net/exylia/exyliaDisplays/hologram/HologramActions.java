package net.exylia.exyliaDisplays.hologram;

import net.exylia.exyliaDisplays.ExyliaDisplays;
import net.exylia.exyliaDisplays.Permissions;
import net.exylia.exyliaDisplays.hologram.HologramsMessages;
import net.exylia.exyliaDisplays.hologram.HologramDialogs;
import net.exylia.exyliaDisplays.hologram.HologramViews;
import net.exylia.exyliaDisplays.common.store.Position;
import net.exylia.exyliaDisplays.hologram.StoredHologram;
import net.exylia.lib.action.ActionContext;
import net.exylia.lib.action.ActionResult;
import net.exylia.lib.action.PluginActions;
import net.exylia.lib.text.Values;
import net.exylia.lib.ui.UiKeys;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Comparator;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Every {@code exyliaholograms:*} action the screens' buttons call.
 *
 * <p>Each one is registered with {@link Permissions#ADMIN}: a menu file can be copied, and a button
 * pasted into another screen must not hand its power to whoever clicks it.
 *
 * <p>A button on a screen about one hologram reads which one from the screen's own context, and a
 * row reads which hologram or line it is from the row: never from a map keyed by player, which two
 * open screens could make disagree.
 */
public final class HologramActions {

    /** Every action of this module starts with it, so another module's {@code edit} never clashes. */
    private static final String PREFIX = "holo_";

    private final ExyliaDisplays plugin;
    private final PluginActions actions;

    public HologramActions(ExyliaDisplays plugin) {
        this.plugin = plugin;
        this.actions = plugin.getActions();
    }

    public void registerAll() {
        // The list.
        actions.registerSync(PREFIX + "create", Permissions.HOLOGRAMS, (ctx, args) -> {
            Player player = ctx.player();
            HologramDialogs.create(player, () -> HologramViews.openList(player));
            return ActionResult.success();
        });
        row("edit", (player, name) -> HologramViews.openHub(player, name));
        row("row_teleport", HologramActions::teleport);
        row("row_delete", (player, name) -> HologramDialogs.delete(player, name, () -> HologramViews.openList(player)));
        simple("open_list", (player, name) -> HologramViews.openList(player));

        // The hub.
        hub("toggle", (player, name) -> {
            HologramModule.get().store().edit(name, current -> current.withEnabled(!current.enabled())).ifPresent(saved ->
                    plugin.getMessages().send(player, HologramsMessages.get().stateChanged(),
                            HologramViews.values(name, saved)));
            HologramViews.openHub(player, name);
        });
        hub("rename", HologramDialogs::rename);
        hub("open_hub", HologramViews::openHub);
        hub("open_lines", HologramViews::openLines);
        hub("appearance", HologramDialogs::appearance);
        hub("visibility", HologramDialogs::visibility);
        hub("click", HologramDialogs::click);
        hub("location", HologramDialogs::location);
        hub("move_here", (player, name) -> {
            Position here = Position.facing(player.getLocation());
            HologramModule.get().store().edit(name, current -> current.withLocation(here));
            plugin.getMessages().send(player, HologramsMessages.get().moved(), Values.of().putText("name", name));
            HologramViews.openHub(player, name);
        });
        hub("centre", (player, name) -> {
            HologramModule.get().store().edit(name, current -> current.withLocation(current.location().centred()));
            plugin.getMessages().send(player, HologramsMessages.get().centred(), Values.of().putText("name", name));
            HologramViews.openHub(player, name);
        });
        hub("face", (player, name) -> {
            float yaw = Position.facing(player.getLocation()).yaw();
            HologramModule.get().store().edit(name, current -> current.withFacing(yaw, 0f)).ifPresent(saved ->
                    plugin.getMessages().send(player, HologramsMessages.get().propertySet(), Values.of()
                            .putText("name", name).putText("property", "facing")
                            .put("value", HologramViews.facing(saved))));
            HologramViews.openHub(player, name);
        });
        hub("teleport", HologramActions::teleport);
        hub("clone", HologramDialogs::cloneHologram);
        hub("delete", (player, name) -> HologramDialogs.delete(player, name, () -> HologramViews.openHub(player, name)));

        // The lines.
        hub("add_line", HologramDialogs::addLine);
        hub("add_item", (player, name) -> HologramDialogs.addModel(player, name, false));
        hub("add_block", (player, name) -> HologramDialogs.addModel(player, name, true));
        hub("all_lines", HologramDialogs::allLines);
        line("line_text", HologramDialogs::lineText);
        line("line_style", HologramDialogs::lineStyle);
        line("line_delete", HologramDialogs::deleteLine);
        line("line_up", (player, name, index) -> moveLine(player, name, index, index - 1));
        line("line_down", (player, name, index) -> moveLine(player, name, index, index + 1));
        line("line_duplicate", (player, name, index) -> {
            HologramModule.get().store().edit(name, current -> current.hasLine(index) ? current.duplicateLine(index) : current)
                    .ifPresent(saved -> plugin.getMessages().send(player, HologramsMessages.get().lineAdded(),
                            Values.of().putText("name", name).put("line", index + 2)));
            HologramViews.openLines(player, name);
        });

        registerAudienceAndAttach();
    }

    /** Moves a line one step and redraws the list in place, keeping the page. */
    private void moveLine(Player player, String name, int from, int to) {
        HologramModule.get().store().edit(name, current -> current.hasLine(from) && current.hasLine(to)
                ? current.moveLine(from, to) : current);
        HologramViews.openLines(player, name);
    }

    /** Goes to a hologram, a little in front of it so it is in view. */
    public static void teleport(Player player, String name) {
        ExyliaDisplays plugin = ExyliaDisplays.getInstance();
        StoredHologram hologram = HologramModule.get().store().get(name).orElse(null);
        if (hologram == null) {
            plugin.getMessages().send(player, HologramsMessages.get().notFound(), Values.of().putText("name", name));
            return;
        }
        Location at = hologram.location().toLocation();
        if (at == null) {
            plugin.getMessages().send(player, HologramsMessages.get().worldMissing(),
                    Values.of("world", hologram.location().world()));
            return;
        }
        // Two blocks south, at the hologram's foot, looking north at it.
        Location view = at.clone().add(0, -1.5, 2.5);
        view.setYaw(180f);
        view.setPitch(-10f);
        player.closeInventory();
        player.teleportAsync(view, PlayerTeleportEvent.TeleportCause.PLUGIN).thenRun(() ->
                plugin.getMessages().send(player, HologramsMessages.get().teleported(), Values.of().putText("name", name)));
    }

    // ------------------------------------------------------------------
    // Which players see it, and what it rides
    // ------------------------------------------------------------------

    /** How far {@code nearest} looks for an entity, in blocks. */
    private static final double NEAREST_RADIUS = 16;

    private void registerAudienceAndAttach() {
        hub("visibility_mode", (player, name) -> {
            HologramModule.get().store().edit(name, current -> current.withVisibility(current.visibility().withMode(
                    current.visibility().manual() ? StoredHologram.Visibility.ALL : StoredHologram.Visibility.MANUAL)))
                    .ifPresent(saved -> plugin.getMessages().send(player, HologramsMessages.get().audience().modeSet(),
                            Values.of().putText("name", name).put("mode", HologramViews.visibility(saved))));
            HologramViews.openHub(player, name);
        });
        hub("attach", HologramDialogs::attach);
        hub("detach", (player, name) -> {
            detach(player, name);
            HologramViews.openHub(player, name);
        });
    }

    /** A player by name or UUID who has joined this server, or {@code null}. */
    public static OfflinePlayer player(String nameOrId) {
        Player online = Bukkit.getPlayerExact(nameOrId);
        if (online != null) return online;
        OfflinePlayer known;
        try {
            known = Bukkit.getOfflinePlayer(UUID.fromString(nameOrId));
        } catch (IllegalArgumentException notAnId) {
            known = Bukkit.getOfflinePlayerIfCached(nameOrId);
        }
        return known != null && (known.isOnline() || known.hasPlayedBefore()) ? known : null;
    }

    /**
     * Makes a hologram ride a player, an entity or the entity nearest to the sender, telling them
     * how it went. A player or an entity saved with its chunk is written to holograms.yml; any
     * other entity is ridden until it is gone or the server restarts.
     *
     * @return whether it was attached
     */
    public static boolean attach(CommandSender sender, String name, String target) {
        ExyliaDisplays plugin = ExyliaDisplays.getInstance();
        HologramsMessages.Attach words = HologramsMessages.get().attach();
        if (!HologramModule.get().store().exists(name)) {
            plugin.getMessages().send(sender, HologramsMessages.get().notFound(), Values.of().putText("name", name));
            return false;
        }
        Entity entity = entity(sender, target.trim());
        UUID saved;
        String label;
        if (entity == null) {
            OfflinePlayer away = player(target.trim());
            if (away == null) {
                plugin.getMessages().send(sender, words.targetNotFound(), Values.of().putText("target", target));
                return false;
            }
            saved = away.getUniqueId();
            label = HologramViews.targetName(saved);
        } else {
            saved = entity.getUniqueId();
            label = entity.getName();
        }
        Values values = Values.of().putText("name", name).putText("target", label);
        HologramRenderer renderer = HologramModule.get().renderer();
        if (entity != null && !(entity instanceof Player) && !entity.isPersistent()) {
            HologramModule.get().store().edit(name, current -> current.withAttach(current.attach().withEntity(null)));
            renderer.attachUnsaved(name, entity);
            plugin.getMessages().send(sender, words.attachedUnsaved(), values);
            return true;
        }
        renderer.forget(name);
        HologramModule.get().store().edit(name, current -> current.withAttach(current.attach().withEntity(saved)));
        plugin.getMessages().send(sender, words.attached(), values);
        return true;
    }

    /** Back to its location: drops what it rides, saved or not. */
    public static void detach(CommandSender sender, String name) {
        ExyliaDisplays plugin = ExyliaDisplays.getInstance();
        if (!HologramModule.get().store().exists(name)) {
            plugin.getMessages().send(sender, HologramsMessages.get().notFound(), Values.of().putText("name", name));
            return;
        }
        HologramModule.get().renderer().forget(name);
        HologramModule.get().store().edit(name, current -> current.withAttach(current.attach().withEntity(null)));
        plugin.getMessages().send(sender, HologramsMessages.get().attach().detached(), Values.of().putText("name", name));
    }

    /** {@code nearest} to a player, an online player by name, or a loaded entity by UUID. */
    private static Entity entity(CommandSender sender, String target) {
        if (target.equalsIgnoreCase("nearest")) {
            if (!(sender instanceof Player player)) return null;
            Location at = player.getLocation();
            return player.getNearbyEntities(NEAREST_RADIUS, NEAREST_RADIUS, NEAREST_RADIUS).stream()
                    .min(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(at)))
                    .orElse(null);
        }
        Player online = Bukkit.getPlayerExact(target);
        if (online != null) return online;
        try {
            return Bukkit.getEntity(UUID.fromString(target));
        } catch (IllegalArgumentException notAnId) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Where the hologram and the line come from
    // ------------------------------------------------------------------

    @FunctionalInterface
    private interface LineAction {
        void run(Player player, String name, int index);
    }

    private void simple(String id, BiConsumer<Player, String> action) {
        actions.registerSync(PREFIX + id, Permissions.HOLOGRAMS, (ctx, args) -> {
            action.accept(ctx.player(), null);
            return ActionResult.success();
        });
    }

    /** A button on a screen about one hologram. */
    private void hub(String id, BiConsumer<Player, String> action) {
        actions.registerSync(PREFIX + id, Permissions.HOLOGRAMS, (ctx, args) -> {
            String name = screenHologram(ctx);
            if (name == null) {
                return ActionResult.success();
            }
            if (HologramModule.get().store().get(name).isEmpty()) {
                HologramViews.openList(ctx.player());
                return ActionResult.success();
            }
            action.accept(ctx.player(), name);
            return ActionResult.success();
        });
    }

    /** A row of the list, which is a hologram's name. */
    private void row(String id, BiConsumer<Player, String> action) {
        actions.registerSync(PREFIX + id, Permissions.HOLOGRAMS, (ctx, args) -> {
            ctx.get(UiKeys.ENTRY).filter(String.class::isInstance).map(String.class::cast)
                    .ifPresent(name -> action.accept(ctx.player(), name));
            return ActionResult.success();
        });
    }

    /** A row of the lines screen, which is a line's index. */
    private void line(String id, LineAction action) {
        actions.registerSync(PREFIX + id, Permissions.HOLOGRAMS, (ctx, args) -> {
            String name = screenHologram(ctx);
            Integer index = ctx.get(UiKeys.ENTRY).filter(Integer.class::isInstance).map(Integer.class::cast).orElse(null);
            if (name != null && index != null) {
                action.run(ctx.player(), name, index);
            }
            return ActionResult.success();
        });
    }

    private static String screenHologram(ActionContext ctx) {
        return ctx.get(UiKeys.SESSION).flatMap(session -> session.context(HologramViews.NAME, String.class)).orElse(null);
    }
}
