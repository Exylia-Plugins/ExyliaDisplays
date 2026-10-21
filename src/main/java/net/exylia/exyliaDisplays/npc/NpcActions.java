package net.exylia.exyliaDisplays.npc;

import net.exylia.exyliaDisplays.ExyliaDisplays;
import net.exylia.exyliaDisplays.Permissions;
import net.exylia.exyliaDisplays.common.store.Place;
import net.exylia.lib.action.ActionContext;
import net.exylia.lib.action.ActionResult;
import net.exylia.lib.action.PluginActions;
import net.exylia.lib.text.Values;
import net.exylia.lib.ui.UiKeys;
import net.exylia.lib.util.teleport.Teleports;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

import java.util.function.BiConsumer;

/**
 * Every {@code exyliadisplays:npc_*} action the NPC screens' buttons call.
 *
 * <p>Gated by {@link Permissions#NPCS}, so a button pasted into another screen does not hand its
 * power to whoever clicks it. A button reads which NPC from its screen's context, and a list row
 * from the row itself, as the hologram screens do.
 */
public final class NpcActions {

    /** Every action of this module starts with it, so another module's {@code edit} never clashes. */
    private static final String PREFIX = "npc_";

    private final ExyliaDisplays plugin;
    private final PluginActions actions;

    public NpcActions(ExyliaDisplays plugin) {
        this.plugin = plugin;
        this.actions = plugin.getActions();
    }

    public void registerAll() {
        // The list.
        actions.registerSync(PREFIX + "create", Permissions.NPCS, (ctx, args) -> {
            Player player = ctx.player();
            NpcDialogs.create(player, () -> NpcViews.openList(player));
            return ActionResult.success();
        });
        row("edit", NpcViews::openHub);
        row("row_teleport", NpcActions::teleport);
        row("row_delete", (player, name) -> NpcDialogs.delete(player, name, () -> NpcViews.openList(player)));
        actions.registerSync(PREFIX + "open_list", Permissions.NPCS, (ctx, args) -> {
            NpcViews.openList(ctx.player());
            return ActionResult.success();
        });

        // The hub.
        hub("toggle", (player, name) -> {
            NpcModule.get().store().edit(name, current -> current.withEnabled(!current.enabled())).ifPresent(saved ->
                    plugin.getMessages().send(player, NpcsMessages.get().stateChanged(), NpcViews.values(name, saved)));
            NpcViews.openHub(player, name);
        });
        hub("rename", NpcDialogs::rename);
        hub("open_hub", NpcViews::openHub);
        hub("appearance", (player, name) -> NpcDialogs.appearance(player, name, false));
        hub("appearance_body", (player, name) -> NpcDialogs.appearance(player, name, true));
        hub("undo", (player, name) -> {
            NpcDialogs.undo(player, name);
            NpcViews.openHub(player, name);
        });
        hub("open_equipment", NpcViews::openEquipment);
        hub("label", NpcDialogs::label);
        hub("link", NpcDialogs::link);
        hub("interactions", (player, name) -> NpcDialogs.interactions(player, name, false));
        hub("interactions_rules", (player, name) -> NpcDialogs.interactions(player, name, true));
        hub("animations", (player, name) -> NpcDialogs.animations(player, name, false));
        hub("animations_auto", (player, name) -> NpcDialogs.animations(player, name, true));
        hub("open_performances", NpcViews::openPerformances);
        performance("perform_pick", NpcDialogs::pickPerformance);
        // The same as /npc <name> play, said the same way; the picker stays open.
        performance("perform_try", (player, name, value) -> new NpcCommand().play(player, name, value));
        hub("visibility", NpcDialogs::visibility);
        hub("location", NpcDialogs::location);
        hub("move_here", (player, name) -> {
            Location here = player.getLocation();
            NpcModule.get().store().edit(name, current -> current.withLocation(Place.of(here)));
            plugin.getMessages().send(player, NpcsMessages.get().moved(), Values.of().putText("name", name));
            NpcViews.openHub(player, name);
        });
        hub("centre", (player, name) -> {
            NpcModule.get().store().edit(name, current -> current.withLocation(current.location().centred()))
                    .ifPresent(saved -> plugin.getMessages().send(player, NpcsMessages.get().propertySet(), Values.of()
                            .putText("name", name).putText("property", "location").putText("value", NpcViews.xyz(saved))));
            NpcViews.openHub(player, name);
        });
        hub("face_me", (player, name) -> {
            NpcDialogs.faceMe(player, player, name);
            NpcViews.openHub(player, name);
        });
        hub("adjust", (player, name) -> NpcModule.get().adjust().toggle(player, name));
        hub("teleport", NpcActions::teleport);
        hub("clone", NpcDialogs::cloneNpc);
        hub("delete", (player, name) -> NpcDialogs.delete(player, name, () -> NpcViews.openHub(player, name)));

        // The equipment: the slot is the button's argument, as in "npc_equip head".
        slot("equip", (player, name, slot) -> NpcDialogs.equipFromHand(player, player, name, slot));
        slot("unequip", (player, name, slot) -> NpcDialogs.unequip(player, name, slot));
    }

    /** Goes to an NPC, a little in front of it and facing it. */
    public static void teleport(Player player, String name) {
        ExyliaDisplays plugin = ExyliaDisplays.getInstance();
        StoredNpc npc = NpcModule.get().store().get(name).orElse(null);
        if (npc == null) {
            plugin.getMessages().send(player, NpcsMessages.get().notFound(), Values.of().putText("name", name));
            return;
        }
        Location at = npc.location().toLocation();
        if (at == null) {
            plugin.getMessages().send(player, NpcsMessages.get().worldMissing(), Values.of("world", npc.location().world()));
            return;
        }
        // Three blocks out along the way it faces, turned back towards it.
        Location level = at.clone();
        level.setPitch(0f);
        Location view = at.clone().add(level.getDirection().multiply(3));
        view.setYaw(at.getYaw() + 180f);
        view.setPitch(0f);
        player.closeInventory();
        Teleports.of(plugin.getPlugin()).to(player, view).warmup(0).start().future().thenAccept(result -> {
            if (result.isSuccess()) {
                plugin.getMessages().send(player, NpcsMessages.get().teleported(), Values.of().putText("name", name));
            }
        });
    }

    // ------------------------------------------------------------------
    // Where the NPC comes from
    // ------------------------------------------------------------------

    @FunctionalInterface
    private interface SlotAction {
        void run(Player player, String name, EquipmentSlot slot);
    }

    /** A button on a screen about one NPC. */
    private void hub(String id, BiConsumer<Player, String> action) {
        actions.registerSync(PREFIX + id, Permissions.NPCS, (ctx, args) -> {
            String name = screenNpc(ctx);
            if (name == null) return ActionResult.success();
            if (NpcModule.get().store().get(name).isEmpty()) {
                NpcViews.openList(ctx.player());
                return ActionResult.success();
            }
            action.accept(ctx.player(), name);
            return ActionResult.success();
        });
    }

    /** A slot button of the equipment screen, which redraws itself after the change. */
    private void slot(String id, SlotAction action) {
        actions.registerSync(PREFIX + id, Permissions.NPCS, (ctx, args) -> {
            String name = screenNpc(ctx);
            EquipmentSlot slot = args.isEmpty() ? null : StoredNpc.slot(args.string(0));
            if (name != null && slot != null && NpcModule.get().store().exists(name)) {
                action.run(ctx.player(), name, slot);
                NpcViews.openEquipment(ctx.player(), name);
            }
            return ActionResult.success();
        });
    }

    @FunctionalInterface
    private interface PerformanceAction {
        void run(Player player, String name, String performance);
    }

    /** A row of the performance picker, which is the performance, on the screen of one NPC. */
    private void performance(String id, PerformanceAction action) {
        actions.registerSync(PREFIX + id, Permissions.NPCS, (ctx, args) -> {
            String name = screenNpc(ctx);
            String value = ctx.get(UiKeys.ENTRY).filter(String.class::isInstance).map(String.class::cast).orElse(null);
            if (name != null && value != null && NpcModule.get().store().exists(name)) {
                action.run(ctx.player(), name, value);
            }
            return ActionResult.success();
        });
    }

    /** A row of the list, which is an NPC's name. */
    private void row(String id, BiConsumer<Player, String> action) {
        actions.registerSync(PREFIX + id, Permissions.NPCS, (ctx, args) -> {
            ctx.get(UiKeys.ENTRY).filter(String.class::isInstance).map(String.class::cast)
                    .ifPresent(name -> action.accept(ctx.player(), name));
            return ActionResult.success();
        });
    }

    private static String screenNpc(ActionContext ctx) {
        return ctx.get(UiKeys.SESSION).flatMap(session -> session.context(NpcViews.NAME, String.class)).orElse(null);
    }
}
