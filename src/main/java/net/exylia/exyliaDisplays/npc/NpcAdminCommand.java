package net.exylia.exyliaDisplays.npc;

import net.exylia.exyliaDisplays.ExyliaDisplays;
import net.exylia.exyliaDisplays.Permissions;
import net.exylia.exyliaDisplays.common.Completion;
import net.exylia.exyliaDisplays.common.store.Names;
import net.exylia.exyliaDisplays.hologram.HologramModule;
import net.exylia.exyliaDisplays.hologram.migration.ForeignImport;
import net.exylia.exyliaDisplays.npc.migration.NpcImport;
import net.exylia.lib.config.ConfigIssue;
import net.exylia.lib.npc.NpcAnimation;
import net.exylia.lib.npc.NpcPerformance;
import net.exylia.lib.npc.Npcs;
import net.exylia.lib.npc.PlacedNpc;
import net.exylia.lib.ragdoll.RagdollAnimations;
import net.exylia.lib.text.Values;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.CommandPlaceholder;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.annotation.Single;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.Suggest;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@code /npcadmin}: what is not about one NPC. Opens the list, creates, lists, reloads, imports,
 * plays groups and lists the animation files. Editing one NPC is {@link NpcCommand}.
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
    public void near(Player player, @Optional @SuggestWith(NearRadius.class) Double radius) {
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
        NpcDialogs.createHere(player, name.toLowerCase(Locale.ROOT), parsed, null);
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
        Map<String, String> saved = NpcModule.get().store().importAll(result.npcs(), "replace".equalsIgnoreCase(mode));
        if (source == NpcImport.Source.FANCYNPCS) linkFancyHolograms(plugin, saved);
        send(sender, messages().importDone(), values.put("count", saved.size()));
    }

    /**
     * Holograms brought from FancyHolograms before these NPCs follow them now: a FancyHologram
     * names the FancyNpc it stands over, and that link waits for the NPC to be imported. An NPC
     * already carrying a hologram keeps it.
     */
    private static void linkFancyHolograms(ExyliaDisplays plugin, Map<String, String> saved) {
        HologramModule holograms = HologramModule.get();
        if (holograms == null) return;
        ForeignImport.run(plugin.getPlugin(), ForeignImport.Source.FANCYHOLOGRAMS).links().forEach((hologram, npc) -> {
            String name = saved.get(npc);
            String linked = Names.sanitize(hologram);
            if (name == null || !holograms.store().exists(linked)) return;
            NpcModule.get().store().edit(name, current -> current.linkedHologram().isBlank()
                    ? current.withLinkedHologram(linked) : current);
        });
    }

    // ------------------------------------------------------------------
    // Groups: NPCs that share an idle-group, played, stopped and paused together
    // ------------------------------------------------------------------

    /** Every group and its NPCs, by name, read from what npcs.yml says. */
    static Map<String, List<String>> groups(Map<String, StoredNpc> all) {
        Map<String, List<String>> groups = new TreeMap<>();
        all.forEach((name, npc) -> {
            String group = npc.group().toLowerCase(Locale.ROOT);
            if (!group.isBlank()) groups.computeIfAbsent(group, key -> new ArrayList<>()).add(name);
        });
        groups.values().forEach(names -> names.sort(null));
        return groups;
    }

    @Subcommand("group")
    public void group(CommandSender sender) {
        groupList(sender);
    }

    @Subcommand("group list")
    public void groupList(CommandSender sender) {
        NpcsMessages.Playback words = messages().playback();
        Map<String, List<String>> groups = groups(NpcModule.get().store().all());
        if (groups.isEmpty()) {
            send(sender, words.groupsEmpty(), Values.of());
            return;
        }
        send(sender, words.groupsHeader(), Values.of("count", groups.size()));
        groups.forEach((group, names) -> send(sender, words.groupLine(), Values.of().putText("group", group)
                .put("count", names.size()).putText("members", String.join(", ", names))));
    }

    /**
     * Plays an animation on every NPC of a group at once: one start and one tempo, so their loops
     * stay in step. Vanilla moves play on each NPC shown.
     */
    @Subcommand("group play")
    public void groupPlay(CommandSender sender, @Single @SuggestWith(Groups.class) String group,
                          @SuggestWith(GroupAnimations.class) String animation) {
        List<String> names = members(sender, group);
        if (names == null || !NpcDialogs.readable(sender, animation)) return;
        List<PlacedNpc> live = live(names);
        String written = animation.trim();
        int started;
        if (NpcPerformance.isPerformance(written)) {
            started = Npcs.of(ExyliaDisplays.getInstance().getPlugin()).perform(live, NpcPerformance.parse(written));
        } else {
            NpcAnimation moves = NpcAnimation.parse(written);
            live.forEach(npc -> npc.play(moves));
            started = live.size();
        }
        send(sender, messages().playback().groupPlayed(), Values.of().putText("group", group.toLowerCase(Locale.ROOT))
                .putText("animation", written).put("started", started).put("count", names.size())
                .put("skipped", names.size() - started));
    }

    @Subcommand("group stop")
    public void groupStop(CommandSender sender, @SuggestWith(Groups.class) String group) {
        List<String> names = members(sender, group);
        if (names == null) return;
        int stopped = (int) live(names).stream().filter(PlacedNpc::stopPerformance).count();
        send(sender, messages().playback().groupStopped(), Values.of().putText("group", group.toLowerCase(Locale.ROOT))
                .put("stopped", stopped).put("count", names.size()));
    }

    @Subcommand("group pause")
    public void groupPause(CommandSender sender, @SuggestWith(Groups.class) String group) {
        groupPaused(sender, group, true);
    }

    @Subcommand("group resume")
    public void groupResume(CommandSender sender, @SuggestWith(Groups.class) String group) {
        groupPaused(sender, group, false);
    }

    private static void groupPaused(CommandSender sender, String group, boolean paused) {
        List<String> names = members(sender, group);
        if (names == null) return;
        names.forEach(name -> NpcModule.get().renderer().pause(name, paused));
        NpcsMessages.Playback words = messages().playback();
        send(sender, paused ? words.groupPaused() : words.groupResumed(),
                Values.of().putText("group", group.toLowerCase(Locale.ROOT)).put("count", names.size()));
    }

    /** The NPCs of a group, or {@code null} after telling the sender there are none. */
    private static List<String> members(CommandSender sender, String group) {
        List<String> names = groups(NpcModule.get().store().all()).get(group.trim().toLowerCase(Locale.ROOT));
        if (names == null) send(sender, messages().playback().groupNotFound(), Values.of().putText("group", group.trim()));
        return names;
    }

    private static List<PlacedNpc> live(List<String> names) {
        List<PlacedNpc> live = new ArrayList<>();
        names.forEach(name -> NpcModule.get().renderer().live(name).ifPresent(live::add));
        return live;
    }

    // ------------------------------------------------------------------
    // Animation files
    // ------------------------------------------------------------------

    /**
     * The files of the animations folder with the clips each one offers, or one file's clips:
     * what {@code anim:<clip>} plays. Read from ExyliaLib's cache, so a project just saved in
     * Blockbench is listed at once.
     */
    @Subcommand("animations")
    public void animations(CommandSender sender, @Optional @SuggestWith(AnimationFiles.class) String file) {
        NpcsMessages.Playback words = messages().playback();
        Map<String, List<RagdollAnimations.Clip>> files = NpcPerformances.catalog().files().get();
        if (file != null && !file.isBlank()) {
            String wanted = file.trim();
            Map.Entry<String, List<RagdollAnimations.Clip>> found = files.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(wanted) || base(entry.getKey()).equalsIgnoreCase(wanted))
                    .findFirst().orElse(null);
            if (found == null) {
                send(sender, words.fileNotFound(), Values.of().putText("file", wanted));
                return;
            }
            send(sender, words.fileHeader(), Values.of().putText("file", found.getKey()).put("count", found.getValue().size()));
            clips(sender, found.getValue());
            return;
        }
        if (files.isEmpty()) {
            send(sender, words.filesEmpty(), Values.of());
            return;
        }
        send(sender, words.filesHeader(), Values.of("count", files.size()));
        files.forEach((name, clips) -> {
            send(sender, words.fileLine(), Values.of().putText("file", name).put("count", clips.size()));
            clips(sender, clips);
        });
        send(sender, words.filesFooter(), Values.of());
    }

    private static void clips(CommandSender sender, List<RagdollAnimations.Clip> clips) {
        NpcsMessages.Playback words = messages().playback();
        if (clips.isEmpty()) {
            send(sender, words.fileEmpty(), Values.of());
            return;
        }
        clips.forEach(clip -> send(sender, words.clipLine(), Values.of().putText("clip", clip.name())
                .put("loop", clip.loops() ? words.clipLoops() : words.clipOnce())
                .put("length", NpcViews.seconds(clip.length()))));
    }

    /** A file's name without its extension: {@code wave} for {@code wave.animation.json}. */
    static String base(String file) {
        String lower = file.toLowerCase(Locale.ROOT);
        for (String extension : List.of(".animation.json", ".json", ".bbmodel")) {
            if (lower.endsWith(extension)) return file.substring(0, file.length() - extension.length());
        }
        return file;
    }

    /** Every group an NPC is in. */
    public static final class Groups implements Completion {

        @Override
        public Collection<String> suggest(List<String> words, Player player) {
            return NpcModule.get() == null ? List.of() : groups(NpcModule.get().store().all()).keySet();
        }
    }

    /** What a group can play: the presets and every performance by name. */
    public static final class GroupAnimations implements Completion {

        @Override
        public Collection<String> suggest(List<String> words, Player player) {
            List<String> values = new ArrayList<>(NpcPerformances.names());
            values.addAll(NpcAnimation.presets());
            return values;
        }
    }

    /** The files of the animations folder. */
    public static final class AnimationFiles implements Completion {

        @Override
        public Collection<String> suggest(List<String> words, Player player) {
            return NpcPerformances.catalog().files().get().keySet();
        }
    }

    /** The radius near uses when none is given. */
    public static final class NearRadius implements Completion {

        @Override
        public Collection<String> suggest(List<String> words, Player player) {
            return List.of(NpcViews.number(NpcsConfig.get().nearRadius()));
        }
    }
}
