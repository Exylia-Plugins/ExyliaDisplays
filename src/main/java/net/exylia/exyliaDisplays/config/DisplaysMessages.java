package net.exylia.exyliaDisplays.config;

import net.exylia.lib.config.Comment;
import net.exylia.lib.config.ConfigFile;

import java.util.List;

/**
 * What every module shares, in {@code lang/<code>/messages.yml}. Each module keeps its own words in
 * {@code lang/<code>/messages/<module>.yml}.
 *
 * @param prefix   substituted into every %prefix%
 * @param reloaded after /displays reload
 * @param help     /displays help
 * @param module   one line per module in /displays modules
 * @param on       a module that runs
 * @param off      a module switched off
 */
@Comment("Messages every part of ExyliaDisplays shares. Each module has its own file in messages/.")
@Comment("")
@Comment("Colours use palette tokens such as {primary}, {error} or {highlight}.")
@Comment("A message may open with [sound:NAME|volume|pitch], which plays a sound and never shows.")
public record DisplaysMessages(
        @Comment("Prefix substituted into every %prefix% in every message file.")
        String prefix,
        @Comment("Placeholder: %modules%.")
        String reloaded,
        List<String> help,
        @Comment("Placeholders: %module%, %status%.")
        String module,
        String on,
        String off) {

    public DisplaysMessages {
        if (help == null) help = List.of();
    }

    public DisplaysMessages() {
        this("{primary}&lDISPLAYS {letters_black}•<reset>",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|1.0|1.0]%prefix% {success}Reloaded {highlight}%modules%{success}.",
                List.of(
                        "",
                        "[center]{primary}&lEXYLIA DISPLAYS",
                        "[center]{letters_black}Holograms and NPCs, edited in game or in YAML",
                        "",
                        " <click:run_command:'/holoadmin'>{warning}/holoadmin</click> {letters_black}» {letters}holograms, on a screen {letters_black}· <click:run_command:'/holoadmin help'>{info}[help]</click>",
                        " <click:run_command:'/npcadmin'>{warning}/npcadmin</click> {letters_black}» {letters}NPCs, on a screen {letters_black}· <click:run_command:'/npcadmin help'>{info}[help]</click>",
                        " <click:run_command:'/displays modules'>{warning}/displays modules</click> {letters_black}» {letters}what is running",
                        " <click:run_command:'/displays reload'>{warning}/displays reload</click> {letters_black}» {letters}read every file again",
                        ""),
                " {letters_black}▎ {letters}%module% {letters_black}» %status%",
                "{success}Running",
                "{muted}Switched off");
    }

    private static ConfigFile<DisplaysMessages> file;

    public static void install(ConfigFile<DisplaysMessages> loaded) {
        file = loaded;
    }

    public static DisplaysMessages get() {
        return file == null ? new DisplaysMessages() : file.get();
    }
}
