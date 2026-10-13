package net.exylia.exyliaDisplays.hologram;

import net.exylia.exyliaDisplays.ExyliaDisplays;
import net.exylia.exyliaDisplays.Permissions;
import net.exylia.exyliaDisplays.hologram.HologramActions;
import net.exylia.exyliaDisplays.hologram.HologramsConfig;
import net.exylia.exyliaDisplays.hologram.HologramsMessages;
import net.exylia.exyliaDisplays.hologram.HologramDialogs;
import net.exylia.exyliaDisplays.hologram.HologramViews;
import net.exylia.exyliaDisplays.hologram.migration.ForeignImport;
import net.exylia.exyliaDisplays.common.store.Position;
import net.exylia.exyliaDisplays.hologram.StoredHologram;
import net.exylia.lib.config.ConfigIssue;
import net.exylia.lib.hologram.HologramLine;
import net.exylia.lib.text.Values;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.CommandPlaceholder;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.Suggest;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.autocomplete.SuggestionProvider;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import revxrsal.commands.bukkit.annotation.CommandPermission;
import revxrsal.commands.node.ExecutionContext;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * {@code /holo}: everything the screens do, as commands, for the console, for scripts and for
 * whoever types faster than they click.
 *
 * <p>Every edit goes through the same store the screens use, so a command and a click can never
 * disagree. Line numbers are counted from 1, top first, as a player reads them.
 */
@Command({"holo", "hologram", "holograms", "exyliaholograms"})
@CommandPermission(Permissions.HOLOGRAMS)
public final class HologramCommand {

    private static ExyliaDisplays plugin() {
        return ExyliaDisplays.getInstance();
    }

    private static HologramsMessages messages() {
        return HologramsMessages.get();
    }

    private static void send(CommandSender to, String line, Values values) {
        plugin().getMessages().send(to, line, values);
    }

    @CommandPlaceholder
    public void root(CommandSender sender) {
        if (sender instanceof Player player) {
            HologramViews.openList(player);
        } else {
            help(sender);
        }
    }

    @Subcommand("help")
    public void help(CommandSender sender) {
        plugin().getMessages().send(sender, messages().help(), Values.of());
    }

    @Subcommand("list")
    public void list(CommandSender sender) {
        if (sender instanceof Player player) {
            HologramViews.openList(player);
            return;
        }
        Map<String, StoredHologram> all = HologramModule.get().store().all();
        if (all.isEmpty()) {
            send(sender, messages().listEmpty(), Values.of());
            return;
        }
        send(sender, messages().listHeader(), Values.of("count", all.size()));
        all.forEach((name, hologram) -> send(sender, messages().listLine(), HologramViews.values(name, hologram)));
    }

    /** The holograms around the player, nearest first, each with a click to edit or go there. */
    @Subcommand("near")
    public void near(Player player, @Optional Double radius) {
        double within = radius == null || radius <= 0 ? HologramsConfig.get().nearRadius() : radius;
        Location here = player.getLocation();
        List<Map.Entry<String, Double>> near = HologramModule.get().store().all().entrySet().stream()
                .map(entry -> {
                    Location at = entry.getValue().location().toLocation();
                    double distance = at == null || at.getWorld() != here.getWorld() ? Double.MAX_VALUE : at.distance(here);
                    return Map.entry(entry.getKey(), distance);
                })
                .filter(entry -> entry.getValue() <= within)
                .sorted(Comparator.comparingDouble(Map.Entry::getValue))
                .toList();
        Values header = Values.of().put("radius", HologramViews.number(within)).put("count", near.size());
        if (near.isEmpty()) {
            send(player, messages().nearEmpty(), header);
            return;
        }
        send(player, messages().nearHeader(), header);
        near.forEach(entry -> HologramModule.get().store().get(entry.getKey()).ifPresent(hologram -> send(player,
                messages().nearLine(), HologramViews.values(entry.getKey(), hologram)
                        .put("distance", HologramViews.number(Math.round(entry.getValue() * 10) / 10.0)))));
    }

    @Subcommand("create")
    public void create(Player player, String name, @Optional String text) {
        String id = name.toLowerCase(java.util.Locale.ROOT);
        List<String> lines = text == null || text.isBlank() ? List.of("{primary}&l" + id) : List.of(text);
        HologramDialogs.createHere(player, id, lines, () -> { });
    }

    @Subcommand("edit")
    public void edit(Player player, @SuggestWith(Names.class) String name) {
        if (exists(player, name)) HologramViews.openHub(player, name);
    }

    @Subcommand("lines")
    public void lines(Player player, @SuggestWith(Names.class) String name) {
        if (exists(player, name)) HologramViews.openLines(player, name);
    }

    @Subcommand("info")
    public void info(CommandSender sender, @SuggestWith(Names.class) String name) {
        StoredHologram hologram = HologramModule.get().store().get(name).orElse(null);
        if (hologram == null) {
            send(sender, messages().notFound(), Values.of().putText("name", name));
            return;
        }
        plugin().getMessages().send(sender, messages().info(), HologramViews.values(name, hologram));
        for (int i = 0; i < hologram.lines().size(); i++) {
            HologramLine line = HologramLine.parse(hologram.lines().get(i));
            send(sender, messages().infoLine(), Values.of().put("line", i + 1).put("text", HologramViews.lineText(line)));
        }
    }

    @Subcommand("delete")
    public void delete(CommandSender sender, @SuggestWith(Names.class) String name) {
        if (sender instanceof Player player) {
            if (exists(player, name)) HologramDialogs.delete(player, name, () -> { });
            return;
        }
        Values values = Values.of().putText("name", name);
        send(sender, HologramModule.get().store().delete(name) ? messages().deleted() : messages().notFound(), values);
    }

    @Subcommand("rename")
    public void rename(CommandSender sender, @SuggestWith(Names.class) String name, String renamed) {
        HologramDialogs.rename(sender, name, renamed.toLowerCase(java.util.Locale.ROOT));
    }

    @Subcommand("clone")
    public void cloneHologram(CommandSender sender, @SuggestWith(Names.class) String name, String copy) {
        HologramDialogs.copy(sender, name, copy.toLowerCase(java.util.Locale.ROOT));
    }

    @Subcommand("enable")
    public void enable(CommandSender sender, @SuggestWith(Names.class) String name) {
        state(sender, name, true);
    }

    @Subcommand("disable")
    public void disable(CommandSender sender, @SuggestWith(Names.class) String name) {
        state(sender, name, false);
    }

    @Subcommand("tp")
    public void tp(Player player, @SuggestWith(Names.class) String name) {
        HologramActions.teleport(player, name);
    }

    @Subcommand("movehere")
    public void moveHere(Player player, @SuggestWith(Names.class) String name) {
        Position here = Position.facing(player.getLocation());
        edited(player, name, current -> current.withLocation(here), messages().moved(), Values.of());
    }

    @Subcommand("move")
    public void move(CommandSender sender, @SuggestWith(Names.class) String name, double x, double y, double z,
                     @Optional String world) {
        edited(sender, name, current -> current.withLocation(new Position(
                world == null ? current.location().world() : world, x, y, z)), messages().moved(), Values.of());
    }

    @Subcommand("center")
    public void centre(CommandSender sender, @SuggestWith(Names.class) String name) {
        edited(sender, name, current -> current.withLocation(current.location().centred()), messages().centred(), Values.of());
    }

    @Subcommand("addline")
    public void addLine(CommandSender sender, @SuggestWith(Names.class) String name, String text) {
        StoredHologram saved = edited(sender, name, current -> current.addLine(text), null, Values.of());
        if (saved != null) {
            send(sender, messages().lineAdded(), Values.of().putText("name", name).put("line", saved.lines().size()));
        }
    }

    @Subcommand("setline")
    public void setLine(CommandSender sender, @SuggestWith(Names.class) String name, int line, String text) {
        if (hasLine(sender, name, line)) {
            edited(sender, name, current -> current.setLine(line - 1, text), messages().lineSet(), Values.of("line", line));
        }
    }

    @Subcommand("insertline")
    public void insertLine(CommandSender sender, @SuggestWith(Names.class) String name, int line, String text) {
        StoredHologram hologram = HologramModule.get().store().get(name).orElse(null);
        if (hologram == null) {
            send(sender, messages().notFound(), Values.of().putText("name", name));
            return;
        }
        int at = Math.clamp(line - 1, 0, hologram.lines().size());
        edited(sender, name, current -> current.insertLine(at, text), messages().lineAdded(), Values.of("line", at + 1));
    }

    @Subcommand("removeline")
    public void removeLine(CommandSender sender, @SuggestWith(Names.class) String name, int line) {
        if (hasLine(sender, name, line)) {
            edited(sender, name, current -> current.removeLine(line - 1), messages().lineRemoved(), Values.of("line", line));
        }
    }

    @Subcommand("moveline")
    public void moveLine(CommandSender sender, @SuggestWith(Names.class) String name, int from, int to) {
        if (hasLine(sender, name, from) && hasLine(sender, name, to)) {
            edited(sender, name, current -> current.moveLine(from - 1, to - 1), messages().lineMoved(),
                    Values.of().put("line", from).put("to", to));
        }
    }

    @Subcommand("reload")
    public void reload(CommandSender sender) {
        plugin().getReloads().run(sender);
        List<ConfigIssue> issues = HologramModule.get().reloadFile();
        send(sender, messages().reloaded(), Values.of().put("count", HologramModule.get().store().all().size()).put("issues", issues.size()));
    }

    /** Brings holograms over from another plugin; its files are read, never changed. */
    @Subcommand("import")
    public void importFrom(CommandSender sender, ForeignImport.Source source, @Optional @Suggest("replace") String mode) {
        ForeignImport.Result result = ForeignImport.run(plugin().getPlugin(), source);
        Values values = Values.of().put("source", source.folder()).put("count", 0).put("skipped", result.skipped())
                .put("folder", ForeignImport.folder(plugin().getPlugin(), source).getPath());
        if (result.holograms().isEmpty()) {
            send(sender, messages().importNothing(), values);
            return;
        }
        // 'replace' corrects what an earlier import of the same plugin brought, instead of copying it.
        int added = HologramModule.get().store().addAll(result.holograms(), "replace".equalsIgnoreCase(mode));
        send(sender, messages().importDone(), values.put("count", added));
    }

    // ------------------------------------------------------------------

    private static void state(CommandSender sender, String name, boolean enabled) {
        StoredHologram saved = edited(sender, name, current -> current.withEnabled(enabled), null, Values.of());
        if (saved != null) send(sender, messages().stateChanged(), HologramViews.values(name, saved));
    }

    /**
     * Applies a change, then tells the sender {@code done} with the hologram's name added.
     *
     * @return the hologram after the change, or {@code null} when there is none by that name
     */
    private static StoredHologram edited(CommandSender sender, String name, UnaryOperator<StoredHologram> change,
                                         String done, Values values) {
        StoredHologram saved = HologramModule.get().store().edit(name, change).orElse(null);
        if (saved == null) {
            send(sender, messages().notFound(), Values.of().putText("name", name));
            return null;
        }
        if (done != null) send(sender, done, values.putText("name", name));
        return saved;
    }

    private static boolean exists(CommandSender sender, String name) {
        if (HologramModule.get().store().exists(name)) return true;
        send(sender, messages().notFound(), Values.of().putText("name", name));
        return false;
    }

    private static boolean hasLine(CommandSender sender, String name, int line) {
        StoredHologram hologram = HologramModule.get().store().get(name).orElse(null);
        if (hologram == null) {
            send(sender, messages().notFound(), Values.of().putText("name", name));
            return false;
        }
        if (!hologram.hasLine(line - 1)) {
            send(sender, messages().lineNotFound(), Values.of().put("line", line).put("count", hologram.lines().size()));
            return false;
        }
        return true;
    }

    /** Every hologram's name, for tab completion. */
    public static final class Names implements SuggestionProvider<BukkitCommandActor> {

        @Override
        public @NotNull Collection<String> getSuggestions(@NotNull ExecutionContext<BukkitCommandActor> context) {
            ExyliaDisplays plugin = ExyliaDisplays.getInstance();
            return plugin == null ? List.of() : HologramModule.get().store().names();
        }
    }
}
