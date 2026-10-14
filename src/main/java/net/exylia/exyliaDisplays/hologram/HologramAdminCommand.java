package net.exylia.exyliaDisplays.hologram;

import net.exylia.exyliaDisplays.ExyliaDisplays;
import net.exylia.exyliaDisplays.Permissions;
import net.exylia.exyliaDisplays.common.store.Names;
import net.exylia.exyliaDisplays.hologram.migration.ForeignImport;
import net.exylia.exyliaDisplays.npc.NpcModule;
import net.exylia.lib.config.ConfigIssue;
import net.exylia.lib.text.Values;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.CommandPlaceholder;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.Suggest;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code /holoadmin}: what is not about one hologram. Opens the list, creates, lists, reloads and
 * imports. Editing one hologram is {@link HologramCommand}.
 */
@Command({"holoadmin", "hologramadmin"})
@CommandPermission(Permissions.HOLOGRAMS)
public final class HologramAdminCommand {

    /** {@code --template <name>} in the text of {@code /holoadmin create}. */
    private static final Pattern TEMPLATE_FLAG = Pattern.compile("(?:^|\\s)--template\\s+(\\S+)");

    private static HologramsMessages messages() {
        return HologramsMessages.get();
    }

    private static void send(CommandSender to, String line, Values values) {
        ExyliaDisplays.getInstance().getMessages().send(to, line, values);
    }

    @CommandPlaceholder
    public void root(CommandSender sender) {
        list(sender);
    }

    @Subcommand("help")
    public void help(CommandSender sender) {
        ExyliaDisplays.getInstance().getMessages().send(sender, messages().help(), Values.of());
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

    /** A new hologram where the player stands; {@code --template <t>} anywhere in the text starts from one. */
    @Subcommand("create")
    public void create(Player player, String name, @Optional String text) {
        String id = name.toLowerCase(Locale.ROOT);
        HologramTemplate template = null;
        Matcher flag = TEMPLATE_FLAG.matcher(text == null ? "" : text);
        if (flag.find()) {
            String wanted = flag.group(1).toLowerCase(Locale.ROOT);
            template = HologramModule.get().templates().get(wanted).orElse(null);
            if (template == null) {
                send(player, messages().templateNotFound(), Values.of().putText("template", wanted));
                return;
            }
            text = flag.replaceFirst("").trim();
        }
        List<String> lines = text == null || text.isBlank() ? List.of("{primary}&l" + id) : List.of(text);
        HologramDialogs.createHere(player, id, lines, template);
    }

    /** Every template, with what it holds. */
    @Subcommand("templates")
    public void templates(CommandSender sender) {
        Map<String, HologramTemplate> all = HologramModule.get().templates().all();
        if (all.isEmpty()) {
            send(sender, messages().templatesEmpty(), Values.of());
            return;
        }
        send(sender, messages().templatesHeader(), Values.of("count", all.size()));
        all.forEach((name, template) -> send(sender, messages().templatesLine(), Values.of().putText("template", name)
                .put("lines", template.lines().size()).put("pages", template.pages().size() + 1)));
    }

    @Subcommand("templates delete")
    public void deleteTemplate(CommandSender sender, @SuggestWith(HologramCommand.Templates.class) String template) {
        String name = template.toLowerCase(Locale.ROOT);
        send(sender, HologramModule.get().templates().delete(name) ? messages().templateDeleted()
                : messages().templateNotFound(), Values.of().putText("template", name));
    }

    @Subcommand("reload")
    public void reload(CommandSender sender) {
        ExyliaDisplays.getInstance().getReloads().run(sender);
        List<ConfigIssue> issues = HologramModule.get().reloadFile();
        send(sender, messages().reloaded(), Values.of().put("count", HologramModule.get().store().all().size()).put("issues", issues.size()));
    }

    /** Brings holograms over from another plugin; its files are read, never changed. */
    @Subcommand("import")
    public void importFrom(CommandSender sender, ForeignImport.Source source, @Optional @Suggest("replace") String mode) {
        ExyliaDisplays plugin = ExyliaDisplays.getInstance();
        ForeignImport.Result result = ForeignImport.run(plugin.getPlugin(), source);
        Values values = Values.of().put("source", source.folder()).put("count", 0).put("skipped", result.skipped())
                .put("folder", ForeignImport.folder(plugin.getPlugin(), source).getPath());
        if (result.holograms().isEmpty()) {
            send(sender, messages().importNothing(), values);
            return;
        }
        // 'replace' corrects what an earlier import of the same plugin brought, instead of copying it.
        Map<String, String> saved = HologramModule.get().store().importAll(result.holograms(), "replace".equalsIgnoreCase(mode));
        // A hologram that stood over an NPC there follows the NPC of that name here, once that NPC is imported.
        NpcModule npcs = NpcModule.get();
        if (npcs != null) {
            result.links().forEach((hologram, npc) -> npcs.store().edit(Names.sanitize(npc),
                    current -> current.withLinkedHologram(saved.getOrDefault(hologram, hologram))));
        }
        send(sender, messages().importDone(), values.put("count", saved.size()));
    }
}
