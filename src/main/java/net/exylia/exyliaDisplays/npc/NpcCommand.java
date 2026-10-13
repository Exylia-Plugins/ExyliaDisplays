package net.exylia.exyliaDisplays.npc;

import net.exylia.exyliaDisplays.ExyliaDisplays;
import net.exylia.exyliaDisplays.Permissions;
import net.exylia.exyliaDisplays.common.store.Place;
import net.exylia.exyliaDisplays.hologram.HologramModule;
import net.exylia.exyliaDisplays.npc.migration.NpcImport;
import net.exylia.lib.config.ConfigIssue;
import net.exylia.lib.npc.NpcLook;
import net.exylia.lib.npc.NpcPose;
import net.exylia.lib.text.Values;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.CommandPlaceholder;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.autocomplete.SuggestionProvider;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import revxrsal.commands.bukkit.annotation.CommandPermission;
import revxrsal.commands.node.ExecutionContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * {@code /npc}: everything the screens do, as commands, for the console, for scripts and for
 * whoever types faster than they click.
 *
 * <p>Every edit goes through the same store the screens use. Action numbers are counted from 1,
 * as a player reads them.
 */
@Command({"npc", "npcs", "exylianpc"})
@CommandPermission(Permissions.NPCS)
public final class NpcCommand {

    private static ExyliaDisplays plugin() {
        return ExyliaDisplays.getInstance();
    }

    private static NpcsMessages messages() {
        return NpcsMessages.get();
    }

    private static void send(CommandSender to, String line, Values values) {
        plugin().getMessages().send(to, line, values);
    }

    @CommandPlaceholder
    public void root(CommandSender sender) {
        if (sender instanceof Player player) NpcViews.openList(player);
        else help(sender);
    }

    @Subcommand("help")
    public void help(CommandSender sender) {
        plugin().getMessages().send(sender, messages().help(), Values.of());
    }

    @Subcommand("list")
    public void list(CommandSender sender) {
        if (sender instanceof Player player) {
            NpcViews.openList(player);
            return;
        }
        Map<String, StoredNpc> all = NpcModule.get().store().all();
        if (all.isEmpty()) {
            send(sender, messages().listEmpty(), Values.of());
            return;
        }
        send(sender, messages().listHeader(), Values.of("count", all.size()));
        all.forEach((name, npc) -> send(sender, messages().listLine(), NpcViews.values(name, npc)));
    }

    /** The NPCs around the player, nearest first, each with a click to edit or go there. */
    @Subcommand("near")
    public void near(Player player, @Optional Double radius) {
        double within = radius == null || radius <= 0 ? NpcsConfig.get().nearRadius() : radius;
        Location here = player.getLocation();
        List<Map.Entry<String, Double>> near = NpcModule.get().store().all().entrySet().stream()
                .map(entry -> {
                    Location at = entry.getValue().location().toLocation();
                    double distance = at == null || at.getWorld() != here.getWorld() ? Double.MAX_VALUE : at.distance(here);
                    return Map.entry(entry.getKey(), distance);
                })
                .filter(entry -> entry.getValue() <= within)
                .sorted(Comparator.comparingDouble(Map.Entry::getValue))
                .toList();
        Values header = Values.of().put("radius", NpcViews.number(within)).put("count", near.size());
        if (near.isEmpty()) {
            send(player, messages().nearEmpty(), header);
            return;
        }
        send(player, messages().nearHeader(), header);
        near.forEach(entry -> NpcModule.get().store().get(entry.getKey()).ifPresent(npc -> send(player,
                messages().nearLine(), NpcViews.values(entry.getKey(), npc)
                        .put("distance", NpcViews.number(Math.round(entry.getValue() * 10) / 10.0)))));
    }

    /** A new NPC where the player stands; the skin is an account name, {@code @mirror} or {@code @none}. */
    @Subcommand("create")
    public void create(Player player, String name, @Optional String skin) {
        StoredNpc.Skin parsed = StoredNpc.Skin.parse(skin == null ? player.getName() : skin);
        if (parsed == null) {
            send(player, messages().invalidSkin(), Values.of().putText("skin", skin));
            return;
        }
        NpcDialogs.createHere(player, name.toLowerCase(Locale.ROOT), parsed, null, () -> { });
    }

    @Subcommand("edit")
    public void edit(Player player, @SuggestWith(Names.class) String name) {
        if (exists(player, name)) NpcViews.openHub(player, name);
    }

    @Subcommand("info")
    public void info(CommandSender sender, @SuggestWith(Names.class) String name) {
        StoredNpc npc = NpcModule.get().store().get(name).orElse(null);
        if (npc == null) {
            send(sender, messages().notFound(), Values.of().putText("name", name));
            return;
        }
        plugin().getMessages().send(sender, messages().info(), NpcViews.values(name, npc));
    }

    @Subcommand("tp")
    public void tp(Player player, @SuggestWith(Names.class) String name) {
        NpcActions.teleport(player, name);
    }

    @Subcommand("movehere")
    public void moveHere(Player player, @SuggestWith(Names.class) String name) {
        Place here = Place.of(player.getLocation());
        edited(player, name, current -> current.withLocation(here), messages().moved());
    }

    @Subcommand("face")
    public void face(Player player, @SuggestWith(Names.class) String name) {
        NpcDialogs.faceMe(player, player, name);
    }

    @Subcommand("skin")
    public void skin(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(Skins.class) String skin) {
        StoredNpc.Skin parsed = StoredNpc.Skin.parse(skin);
        if (parsed == null) {
            send(sender, messages().invalidSkin(), Values.of().putText("skin", skin));
            return;
        }
        edited(sender, name, current -> current.withSkin(parsed), messages().saved());
    }

    @Subcommand("pose")
    public void pose(CommandSender sender, @SuggestWith(Names.class) String name, NpcPose pose) {
        edited(sender, name, current -> current.withPose(pose), messages().saved());
    }

    @Subcommand("look")
    public void look(CommandSender sender, @SuggestWith(Names.class) String name, NpcLook mode, @Optional Double range) {
        edited(sender, name, current -> current.withLook(new StoredNpc.Look(mode,
                range == null ? current.look().range() : range)), messages().saved());
    }

    /** An outline colour as {@code #RRGGBB}, or {@code none}. */
    @Subcommand("glow")
    public void glow(CommandSender sender, @SuggestWith(Names.class) String name, String colour) {
        String glow = colour.equalsIgnoreCase("none") || colour.equalsIgnoreCase("off") ? "" : colour.trim();
        if (!glow.isEmpty() && !StoredNpc.isColour(glow)) {
            send(sender, messages().invalidColour(), Values.of().putText("value", colour));
            return;
        }
        edited(sender, name, current -> current.withGlow(glow.isEmpty() || glow.startsWith("#") ? glow : "#" + glow),
                messages().saved());
    }

    @Subcommand("scale")
    public void scale(CommandSender sender, @SuggestWith(Names.class) String name, double scale) {
        edited(sender, name, current -> current.withScale(scale), messages().saved());
    }

    /** Puts what the player holds into a slot (an empty hand empties it); {@code clear} empties all. */
    @Subcommand("equip")
    public void equip(Player player, @SuggestWith(Names.class) String name, @SuggestWith(Slots.class) String slot) {
        if (slot.equalsIgnoreCase("clear")) {
            edited(player, name, current -> current.withEquipment(Map.of()), messages().equipmentCleared());
            return;
        }
        EquipmentSlot parsed = StoredNpc.slot(slot);
        if (parsed == null) {
            send(player, messages().invalidSlot(), Values.of().putText("slot", slot)
                    .put("slots", String.join(", ", StoredNpc.SLOTS)));
            return;
        }
        if (exists(player, name)) NpcDialogs.equipFromHand(player, player, name, parsed);
    }

    @Subcommand("equipment")
    public void equipment(Player player, @SuggestWith(Names.class) String name) {
        if (exists(player, name)) NpcViews.openEquipment(player, name);
    }

    // ------------------------------------------------------------------
    // Label
    // ------------------------------------------------------------------

    @Subcommand("label set")
    public void labelSet(CommandSender sender, @SuggestWith(Names.class) String name, String text) {
        label(sender, name, current -> List.of(text));
    }

    @Subcommand("label add")
    public void labelAdd(CommandSender sender, @SuggestWith(Names.class) String name, String text) {
        label(sender, name, current -> {
            List<String> lines = new ArrayList<>(current);
            lines.add(text);
            return lines;
        });
    }

    @Subcommand("label clear")
    public void labelClear(CommandSender sender, @SuggestWith(Names.class) String name) {
        edited(sender, name, current -> current.withLabelLines(List.of()), messages().labelCleared());
    }

    private static void label(CommandSender sender, String name, UnaryOperator<List<String>> change) {
        StoredNpc saved = edited(sender, name, current -> current.withLabelLines(change.apply(current.label().lines())), null);
        if (saved != null) {
            send(sender, messages().labelSaved(), Values.of().putText("name", name).put("count", saved.label().lines().size()));
        }
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    @Subcommand("action add")
    public void actionAdd(CommandSender sender, @SuggestWith(Names.class) String name, String line) {
        StoredNpc saved = edited(sender, name, current -> current.addAction(line), null);
        if (saved != null) {
            send(sender, messages().actionAdded(), Values.of().putText("name", name)
                    .put("index", saved.interactions().actions().size()));
        }
    }

    @Subcommand("action remove")
    public void actionRemove(CommandSender sender, @SuggestWith(Names.class) String name, int index) {
        StoredNpc npc = NpcModule.get().store().get(name).orElse(null);
        if (npc == null) {
            send(sender, messages().notFound(), Values.of().putText("name", name));
            return;
        }
        if (!npc.hasAction(index - 1)) {
            send(sender, messages().actionNotFound(), Values.of().put("index", index)
                    .put("count", npc.interactions().actions().size()));
            return;
        }
        edited(sender, name, current -> current.hasAction(index - 1) ? current.removeAction(index - 1) : current,
                messages().actionRemoved(), Values.of("index", index));
    }

    @Subcommand("action clear")
    public void actionClear(CommandSender sender, @SuggestWith(Names.class) String name) {
        edited(sender, name, current -> current.withInteractions(current.interactions().withActions(List.of())),
                messages().actionsCleared());
    }

    @Subcommand("action list")
    public void actionList(CommandSender sender, @SuggestWith(Names.class) String name) {
        StoredNpc npc = NpcModule.get().store().get(name).orElse(null);
        if (npc == null) {
            send(sender, messages().notFound(), Values.of().putText("name", name));
            return;
        }
        List<String> actions = npc.interactions().actions();
        if (actions.isEmpty()) {
            send(sender, messages().actionsEmpty(), Values.of().putText("name", name));
            return;
        }
        send(sender, messages().actionsHeader(), Values.of().putText("name", name).put("count", actions.size()));
        for (int i = 0; i < actions.size(); i++) {
            send(sender, messages().actionLine(), Values.of().put("index", i + 1).putText("action", actions.get(i)));
        }
    }

    // ------------------------------------------------------------------
    // Linked hologram
    // ------------------------------------------------------------------

    @Subcommand("link")
    public void link(CommandSender sender, @SuggestWith(Names.class) String name,
                     @SuggestWith(Holograms.class) String hologram) {
        HologramModule holograms = HologramModule.get();
        if (holograms == null) {
            send(sender, messages().hologramsOff(), Values.of());
            return;
        }
        if (!holograms.store().exists(hologram)) {
            send(sender, messages().hologramNotFound(), Values.of().putText("hologram", hologram));
            return;
        }
        edited(sender, name, current -> current.withLinkedHologram(hologram), messages().linked(),
                Values.of().putText("hologram", hologram));
    }

    @Subcommand("unlink")
    public void unlink(CommandSender sender, @SuggestWith(Names.class) String name) {
        edited(sender, name, current -> current.withLinkedHologram(""), messages().unlinked());
    }

    // ------------------------------------------------------------------
    // The NPC as a whole
    // ------------------------------------------------------------------

    @Subcommand("enable")
    public void enable(CommandSender sender, @SuggestWith(Names.class) String name) {
        state(sender, name, true);
    }

    @Subcommand("disable")
    public void disable(CommandSender sender, @SuggestWith(Names.class) String name) {
        state(sender, name, false);
    }

    @Subcommand("clone")
    public void cloneNpc(CommandSender sender, @SuggestWith(Names.class) String name, String copy) {
        NpcDialogs.copy(sender, name, copy.toLowerCase(Locale.ROOT));
    }

    @Subcommand("rename")
    public void rename(CommandSender sender, @SuggestWith(Names.class) String name, String renamed) {
        NpcDialogs.rename(sender, name, renamed.toLowerCase(Locale.ROOT));
    }

    @Subcommand("delete")
    public void delete(CommandSender sender, @SuggestWith(Names.class) String name) {
        if (sender instanceof Player player) {
            if (exists(player, name)) NpcDialogs.delete(player, name, () -> { });
            return;
        }
        send(sender, NpcModule.get().store().delete(name) ? messages().deleted() : messages().notFound(),
                Values.of().putText("name", name));
    }

    /** Brings NPCs over from another plugin; its files are read, never changed. */
    @Subcommand("import")
    public void importFrom(CommandSender sender, NpcImport.Source source) {
        NpcImport.Result result = NpcImport.run(plugin().getPlugin(), source);
        Values values = Values.of().put("source", source.folder()).put("count", 0).put("skipped", result.skipped())
                .put("folder", NpcImport.folder(plugin().getPlugin(), source).getPath());
        if (result.npcs().isEmpty()) {
            send(sender, messages().importNothing(), values);
            return;
        }
        int added = NpcModule.get().store().addAll(result.npcs());
        send(sender, messages().importDone(), values.put("count", added));
    }

    @Subcommand("reload")
    public void reload(CommandSender sender) {
        plugin().getReloads().run(sender);
        List<ConfigIssue> issues = NpcModule.get().reloadFile();
        send(sender, messages().reloaded(), Values.of().put("count", NpcModule.get().store().all().size())
                .put("issues", issues.size()));
    }

    // ------------------------------------------------------------------

    private static void state(CommandSender sender, String name, boolean enabled) {
        StoredNpc saved = edited(sender, name, current -> current.withEnabled(enabled), null);
        if (saved != null) send(sender, messages().stateChanged(), NpcViews.values(name, saved));
    }

    private static StoredNpc edited(CommandSender sender, String name, UnaryOperator<StoredNpc> change, String done) {
        return edited(sender, name, change, done, Values.of());
    }

    /**
     * Applies a change, then tells the sender {@code done} with the NPC's name added.
     *
     * @return the NPC after the change, or {@code null} when there is none by that name
     */
    private static StoredNpc edited(CommandSender sender, String name, UnaryOperator<StoredNpc> change,
                                    String done, Values values) {
        StoredNpc saved = NpcModule.get().store().edit(name, change).orElse(null);
        if (saved == null) {
            send(sender, messages().notFound(), Values.of().putText("name", name));
            return null;
        }
        if (done != null) send(sender, done, values.putText("name", name));
        return saved;
    }

    private static boolean exists(CommandSender sender, String name) {
        if (NpcModule.get().store().exists(name)) return true;
        send(sender, messages().notFound(), Values.of().putText("name", name));
        return false;
    }

    // ------------------------------------------------------------------
    // Tab completion
    // ------------------------------------------------------------------

    /** Every NPC's name. */
    public static final class Names implements SuggestionProvider<BukkitCommandActor> {
        @Override
        public @NotNull Collection<String> getSuggestions(@NotNull ExecutionContext<BukkitCommandActor> context) {
            return NpcModule.get() == null ? List.of() : NpcModule.get().store().names();
        }
    }

    /** Every hologram's name, while the hologram module runs. */
    public static final class Holograms implements SuggestionProvider<BukkitCommandActor> {
        @Override
        public @NotNull Collection<String> getSuggestions(@NotNull ExecutionContext<BukkitCommandActor> context) {
            return HologramModule.get() == null ? List.of() : HologramModule.get().store().names();
        }
    }

    /** The slots, and {@code clear}. */
    public static final class Slots implements SuggestionProvider<BukkitCommandActor> {
        @Override
        public @NotNull Collection<String> getSuggestions(@NotNull ExecutionContext<BukkitCommandActor> context) {
            List<String> slots = new ArrayList<>(StoredNpc.SLOTS);
            slots.add("clear");
            return slots;
        }
    }

    /** The special skins, and the players online. */
    public static final class Skins implements SuggestionProvider<BukkitCommandActor> {
        @Override
        public @NotNull Collection<String> getSuggestions(@NotNull ExecutionContext<BukkitCommandActor> context) {
            List<String> skins = new ArrayList<>(List.of("@mirror", "@none"));
            plugin().getServer().getOnlinePlayers().forEach(player -> skins.add(player.getName()));
            return skins;
        }
    }
}
