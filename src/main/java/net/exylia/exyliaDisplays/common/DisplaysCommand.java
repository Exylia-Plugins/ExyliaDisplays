package net.exylia.exyliaDisplays.common;

import net.exylia.exyliaDisplays.ExyliaDisplays;
import net.exylia.exyliaDisplays.Permissions;
import net.exylia.exyliaDisplays.config.DisplaysMessages;
import net.exylia.exyliaDisplays.module.DisplayModule;
import net.exylia.exyliaDisplays.module.Modules;
import net.exylia.lib.text.Values;
import net.exylia.lib.web.WebEditor;
import org.bukkit.command.CommandSender;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.CommandPlaceholder;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.annotation.CommandPermission;

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
}
