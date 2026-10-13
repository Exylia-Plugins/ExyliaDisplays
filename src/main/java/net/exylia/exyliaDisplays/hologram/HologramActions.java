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
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

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
