package net.exylia.exyliaDisplays.common;

import net.exylia.exyliaDisplays.ExyliaDisplays;
import net.exylia.exyliaDisplays.Permissions;
import net.exylia.exyliaDisplays.config.DisplaysMessages;
import net.exylia.exyliaDisplays.demo.DemoShowcase;
import net.exylia.exyliaDisplays.hologram.HologramModule;
import net.exylia.exyliaDisplays.module.DisplayModule;
import net.exylia.exyliaDisplays.module.Modules;
import net.exylia.exyliaDisplays.npc.NpcModule;
import net.exylia.exyliaDisplays.npc.StoredNpc;
import net.exylia.lib.text.Values;
import net.exylia.lib.web.WebEditor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.CommandPlaceholder;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** {@code /displays}: the plugin as a whole — what runs, and reading every file again. */
@Command({"displays", "exyliadisplays", "edisplays"})
@CommandPermission(Permissions.ADMIN)
public final class DisplaysCommand {

    private static ExyliaDisplays core() {
        return ExyliaDisplays.getInstance();
    }

    @CommandPlaceholder
    public void root(CommandSender sender) {
        help(sender);
    }

    @Subcommand("help")
    public void help(CommandSender sender) {
        core().getMessages().send(sender, DisplaysMessages.get().help(), Values.of());
    }

    @Subcommand("modules")
    public void modules(CommandSender sender) {
        DisplaysMessages messages = DisplaysMessages.get();
        for (Supplier<DisplayModule> factory : Modules.all()) {
            String id = factory.get().id();
            core().getMessages().send(sender, messages.module(), Values.of().put("module", id)
                    .put("status", core().isRunning(id) ? messages.on() : messages.off()));
        }
    }

    /** Every module's file in the browser; the link and the rest of the flow come from ExyliaLib. */
    @Subcommand("editor")
    public void editor(CommandSender sender) {
        WebEditor.open(sender, core().getPlugin());
    }

    @Subcommand("reload")
    public void reload(CommandSender sender) {
        core().getReloads().run(sender);
        String running = String.join(", ", core().getModules().stream().map(DisplayModule::id).toList());
        core().getMessages().send(sender, DisplaysMessages.get().reloaded(), Values.of("modules", running));
    }

    /**
     * A showcase of holograms and NPCs around the player, for screenshots; account names, if
     * given, are the NPCs' skins in order (the guide first), the built-in skins filling the rest.
     */
    @Subcommand("demo seed")
    public void demoSeed(Player player, @Optional String skins) {
        if (!demoReady(player)) return;
        List<StoredNpc.Skin> parsed = new ArrayList<>();
        for (String name : skins == null ? new String[0] : skins.trim().split("\\s+")) {
            if (name.isEmpty() || parsed.size() == DemoShowcase.SKIN_COUNT) continue;
            StoredNpc.Skin skin = StoredNpc.Skin.parse(name);
            if (skin == null || skin.type() != StoredNpc.Skin.Type.PLAYER) {
                core().getMessages().send(player, DisplaysMessages.get().demoInvalidSkin(), Values.of().putText("skin", name));
                return;
            }
            parsed.add(skin);
        }
        DemoShowcase.seed(player.getLocation(), parsed);
        core().getMessages().send(player, DisplaysMessages.get().demoSeeded(), Values.of());
    }

    /** Removes every hologram and NPC named demo-*, and nothing else. */
    @Subcommand("demo clear")
    public void demoClear(CommandSender sender) {
        if (!demoReady(sender)) return;
        DemoShowcase.Removed removed = DemoShowcase.clear();
        DisplaysMessages messages = DisplaysMessages.get();
        core().getMessages().send(sender, removed.holograms() + removed.npcs() == 0 ? messages.demoNone() : messages.demoCleared(),
                Values.of().put("holograms", removed.holograms()).put("npcs", removed.npcs()));
    }

    /** The demo builds both holograms and NPCs, so it needs both modules running. */
    private boolean demoReady(CommandSender sender) {
        if (HologramModule.get() != null && NpcModule.get() != null) return true;
        core().getMessages().send(sender, DisplaysMessages.get().demoModules(), Values.of());
        return false;
    }
}
