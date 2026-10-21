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
 * @param demoSeeded      after /displays demo seed: what was built and how to frame it
 * @param demoCleared     after /displays demo clear
 * @param demoNone        /displays demo clear with nothing to remove
 * @param demoModules     the demo asked for while a module it builds on is off
 * @param demoInvalidSkin a skin given to the demo that is no account name
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
        String off,
        List<String> demoSeeded,
        @Comment("Placeholders: %holograms%, %npcs%.")
        String demoCleared,
        String demoNone,
        String demoModules,
        @Comment("Placeholder: %skin%.")
        String demoInvalidSkin) {

    public DisplaysMessages {
        if (help == null) help = List.of();
        if (demoSeeded == null) demoSeeded = List.of();
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
                        " <click:run_command:'/displays editor'>{warning}/displays editor</click> {letters_black}» {letters}holograms and NPCs in the browser",
                        " <click:suggest_command:'/displays demo seed'>{warning}/displays demo seed</click> {letters_black}» {letters}a showcase around you {letters_black}· <click:run_command:'/displays demo clear'>{info}[clear]</click>",
                        ""),
                " {letters_black}▎ {letters}%module% {letters_black}» %status%",
                "{success}Running",
                "{muted}Switched off",
                List.of(
                        "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|1.0|1.0]",
                        "[center]{primary}&lDEMO READY",
                        "[center]{letters_black}Three scenes around the spot you stand on",
                        "",
                        " {secondary}Lines of every size {letters_black}» {letters}five blocks ahead, frame it from here",
                        " {secondary}NPCs that speak {letters_black}» {letters}on your right, right-click the guide",
                        " {secondary}A cast of NPCs {letters_black}» {letters}turn around, five in an arc",
                        "",
                        " <click:run_command:'/holo demo-welcome lines'>{warning}/holo demo-welcome lines</click> {letters_black}» {letters}the lines screen; click a line for its window",
                        " <click:run_command:'/npc demo-guide'>{warning}/npc demo-guide</click> {letters_black}» {letters}the NPC editor",
                        " <click:run_command:'/displays demo clear'>{error}/displays demo clear</click> {letters_black}» {letters}remove it all",
                        ""),
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|1.0|1.0]%prefix% {success}Removed {highlight}%holograms% {success}holograms and {highlight}%npcs% {success}NPCs of the demo.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}There is no demo to remove.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}The demo needs the holograms and NPCs modules running.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {highlight}%skin% {error}is not an account name.");
    }

    private static ConfigFile<DisplaysMessages> file;

    public static void install(ConfigFile<DisplaysMessages> loaded) {
        file = loaded;
    }

    public static DisplaysMessages get() {
        return file == null ? new DisplaysMessages() : file.get();
    }
}
