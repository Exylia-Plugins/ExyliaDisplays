package net.exylia.exyliaDisplays.npc;

import net.exylia.exyliaDisplays.ExyliaDisplays;
import net.exylia.exyliaDisplays.Permissions;
import net.exylia.exyliaDisplays.npc.migration.NpcImport;
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

/**
 * {@code /npcadmin}: what is not about one NPC. Opens the list, creates, lists, reloads and
 * imports. Editing one NPC is {@link NpcCommand}.
 */
@Command({"npcadmin", "npcsadmin"})
@CommandPermission(Permissions.NPCS)
public final class NpcAdminCommand {

    private static NpcsMessages messages() {
        return NpcsMessages.get();
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
    public void create(Player player, String name, @Optional @SuggestWith(NpcCommand.Skins.class) String skin) {
        StoredNpc.Skin parsed = StoredNpc.Skin.parse(skin == null ? player.getName() : skin);
        if (parsed == null) {
            send(player, messages().invalidSkin(), Values.of().putText("skin", skin));
            return;
        }
        NpcDialogs.createHere(player, name.toLowerCase(Locale.ROOT), parsed, null, () -> { });
    }

    @Subcommand("reload")
    public void reload(CommandSender sender) {
        ExyliaDisplays.getInstance().getReloads().run(sender);
        List<ConfigIssue> issues = NpcModule.get().reloadFile();
        send(sender, messages().reloaded(), Values.of().put("count", NpcModule.get().store().all().size())
                .put("issues", issues.size()));
    }

    /** Brings NPCs over from another plugin; its files are read, never changed. */
    @Subcommand("import")
    public void importFrom(CommandSender sender, NpcImport.Source source, @Optional @Suggest("replace") String mode) {
        ExyliaDisplays plugin = ExyliaDisplays.getInstance();
        NpcImport.Result result = NpcImport.run(plugin.getPlugin(), source);
        Values values = Values.of().put("source", source.folder()).put("count", 0).put("skipped", result.skipped())
                .put("folder", NpcImport.folder(plugin.getPlugin(), source).getPath());
        if (result.npcs().isEmpty()) {
            send(sender, messages().importNothing(), values);
            return;
        }
        // 'replace' corrects what an earlier import of the same plugin brought, instead of copying it.
        int added = NpcModule.get().store().addAll(result.npcs(), "replace".equalsIgnoreCase(mode));
        send(sender, messages().importDone(), values.put("count", added));
    }
}
