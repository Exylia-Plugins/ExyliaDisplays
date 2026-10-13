package net.exylia.exyliaDisplays.hologram;

import net.exylia.lib.config.Comment;
import net.exylia.lib.config.ConfigFile;

import java.util.List;

/**
 * Everything a player or an admin reads, in {@code lang/<code>/messages.yml}.
 *
 * @param states  the words menus and messages show for a state
 * @param dialogs the titles, labels and hints of every window that asks something
 */
@Comment("Messages of the hologram module of ExyliaDisplays.")
@Comment("")
@Comment("Colours use palette tokens such as {primary}, {error} or {highlight},")
@Comment("so recolouring the server never means editing this file.")
@Comment("")
@Comment("A message may open with an effect tag, which is an instruction and never")
@Comment("reaches the screen: [sound:NAME|volume|pitch] plays a sound to the player.")
@Comment("")
@Comment("%prefix% is the prefix set in messages.yml.")
public record HologramsMessages(
        @Comment("Placeholders in the messages below: %name% is the hologram's name.")
        String created,
        String deleted,
        String notFound,
        String alreadyExists,
        @Comment("A name that is not lowercase letters, numbers, - and _ (32 at most).")
        String invalidName,
        @Comment("Placeholders: %name%, %new%.")
        String renamed,
        @Comment("Placeholders: %name%, %new%.")
        String cloned,
        String moved,
        String centred,
        String teleported,
        @Comment("The hologram's world is not loaded. Placeholder: %world%.")
        String worldMissing,
        @Comment("Placeholders: %name%, %status%.")
        String stateChanged,
        @Comment("Placeholders: %name%, %line%.")
        String lineAdded,
        String lineSet,
        String lineRemoved,
        @Comment("Placeholders: %name%, %line%, %to%.")
        String lineMoved,
        @Comment("A line number that does not exist. Placeholders: %line%, %count%.")
        String lineNotFound,
        @Comment("Placeholders: %name%, %count%.")
        String linesSaved,
        @Comment("Told after a change made from a window. Placeholder: %name%.")
        String saved,
        @Comment("An item or block line needs a placeable block. Placeholder: %material%.")
        String notABlock,
        @Comment("Placeholders: %count%, %issues%.")
        String reloaded,
        @Comment("Told to admins when holograms.yml was edited by hand and applied. Placeholder: %count%.")
        String fileApplied,
        @Comment("Told when holograms.yml has a mistake. Placeholder: %issues%.")
        String fileIssues,
        @Comment("Placeholders: %source%, %count%, %skipped%.")
        String importDone,
        @Comment("Nothing found to import. Placeholders: %source%, %folder%.")
        String importNothing,
        @Comment("/holo help. Lines with <click> run or suggest the command.")
        List<String> help,
        @Comment("/holo list for the console, and /holo near. Placeholders: %count%, %radius%.")
        String listHeader,
        @Comment("One line per hologram. Placeholders: %name%, %world%, %x%, %y%, %z%, %lines%, %distance%, %status%.")
        String listLine,
        String listEmpty,
        String nearHeader,
        String nearLine,
        String nearEmpty,
        @Comment("/holo info. Placeholders: %name%, %world%, %x%, %y%, %z%, %lines%, %status%, %permission%,")
        @Comment("%view_distance%, %click%, %per_player%.")
        List<String> info,
        @Comment("/holo info, one line per hologram line. Placeholders: %line%, %text%.")
        String infoLine,
        States states,
        Dialogs dialogs) {

    public HologramsMessages {
        if (help == null) help = List.of();
        if (info == null) info = List.of();
        if (states == null) states = new States();
        if (dialogs == null) dialogs = new Dialogs();
    }

    public HologramsMessages() {
        this("[sound:ENTITY_EXPERIENCE_ORB_PICKUP|1.0|1.0]%prefix% {success}Hologram {highlight}%name% {success}created.",
                "%prefix% {warning}Hologram {highlight}%name% {warning}deleted.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}There is no hologram called {highlight}%name%{error}.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}A hologram called {highlight}%name% {error}already exists.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}Use lowercase letters, numbers, {highlight}- {error}and {highlight}_{error}, 32 at most.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|1.0|1.0]%prefix% {success}Hologram {highlight}%name% {success}is now {highlight}%new%{success}.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|1.0|1.0]%prefix% {success}Copied {highlight}%name% {success}as {highlight}%new%{success}.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|1.0|1.0]%prefix% {success}Moved {highlight}%name% {success}here.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|1.0|1.0]%prefix% {success}Centred {highlight}%name% {success}on its block.",
                "%prefix% {letters}Teleported to {highlight}%name%{letters}.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}The world {highlight}%world% {error}is not loaded.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|1.0|1.0]%prefix% {letters}Hologram {highlight}%name% {letters_black}» %status%",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|1.0|1.0]%prefix% {success}Line {highlight}%line% {success}added to {highlight}%name%{success}.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|1.0|1.0]%prefix% {success}Line {highlight}%line% {success}of {highlight}%name% {success}updated.",
                "%prefix% {warning}Line {highlight}%line% {warning}removed from {highlight}%name%{warning}.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|1.0|1.0]%prefix% {success}Line {highlight}%line% {success}moved to {highlight}%to%{success}.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}There is no line {highlight}%line%{error}; it has {highlight}%count%{error}.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|1.0|1.0]%prefix% {success}Saved {highlight}%count% {success}lines on {highlight}%name%{success}.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|1.0|1.0]%prefix% {success}Saved {highlight}%name%{success}.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {highlight}%material% {error}is not a block.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|1.0|1.0]%prefix% {success}Reloaded {highlight}%count% {success}holograms. {letters_black}(%issues% issues)",
                "%prefix% {letters}holograms.yml changed: {highlight}%count% {letters}holograms applied.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}holograms.yml has {highlight}%issues% {error}mistake(s); see the console.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|1.0|1.0]%prefix% {success}Imported {highlight}%count% {success}holograms from {highlight}%source%{success}. {letters_black}(%skipped% skipped)",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}Nothing to import from {highlight}%source%{error}. Looked in {letters_black}%folder%",
                List.of(
                        "",
                        "[center]{primary}&lEXYLIA HOLOGRAMS",
                        "[center]{letters_black}Click a command to use it",
                        "",
                        " <click:run_command:'/holo'>{warning}/holo</click> {letters_black}» {letters}every hologram, on a screen",
                        " <click:suggest_command:'/holo create '>{warning}/holo create <name> [text]</click> {letters_black}» {letters}a new one here",
                        " <click:suggest_command:'/holo edit '>{warning}/holo edit <name></click> {letters_black}» {letters}its editor",
                        " <click:suggest_command:'/holo addline '>{warning}/holo addline <name> <text></click> {letters_black}» {letters}add a line",
                        " <click:suggest_command:'/holo setline '>{warning}/holo setline <name> <line> <text></click>",
                        " <click:suggest_command:'/holo insertline '>{warning}/holo insertline <name> <line> <text></click>",
                        " <click:suggest_command:'/holo removeline '>{warning}/holo removeline <name> <line></click>",
                        " <click:suggest_command:'/holo moveline '>{warning}/holo moveline <name> <from> <to></click>",
                        " <click:suggest_command:'/holo movehere '>{warning}/holo movehere <name></click> {letters_black}» {letters}to where you stand",
                        " <click:suggest_command:'/holo tp '>{warning}/holo tp <name></click> {letters_black}» {letters}go to it",
                        " <click:run_command:'/holo near'>{warning}/holo near [radius]</click> {letters_black}» {letters}the ones around you",
                        " <click:suggest_command:'/holo clone '>{warning}/holo clone <name> <new></click> {letters_black}· <click:suggest_command:'/holo rename '>{warning}rename</click> {letters_black}· <click:suggest_command:'/holo delete '>{warning}delete</click>",
                        " <click:suggest_command:'/holo import '>{warning}/holo import <plugin> [replace]</click> {letters_black}» {letters}DecentHolograms, HolographicDisplays, FancyHolograms",
                        " <click:run_command:'/holo reload'>{warning}/holo reload</click> {letters_black}» {letters}read holograms.yml again",
                        ""),
                "%prefix% {letters}Holograms {letters_black}({info}%count%{letters_black})",
                " <click:run_command:'/holo edit %name%'><hover:show_text:'{letters}Click to edit'>{highlight}%name%</hover></click> {letters_black}» {letters}%world% {info}%x% %y% %z% {letters_black}· {info}%lines% {letters}lines {letters_black}· %status%",
                "%prefix% {letters}There are no holograms yet. {warning}<click:suggest_command:'/holo create '>[Create one]</click>",
                "%prefix% {letters}Holograms within {info}%radius% {letters}blocks {letters_black}({info}%count%{letters_black})",
                " <click:run_command:'/holo edit %name%'><hover:show_text:'{letters}Click to edit'>{highlight}%name%</hover></click> {letters_black}» {info}%distance%m {letters_black}· {info}%lines% {letters}lines {letters_black}· <click:run_command:'/holo tp %name%'>{warning}[TP]</click>",
                "%prefix% {letters}No holograms within {info}%radius% {letters}blocks.",
                List.of(
                        "",
                        "[center]{primary}&l%name%",
                        "",
                        " {letters_black}▎ {letters}State {letters_black}» %status%",
                        " {letters_black}▎ {letters}Where {letters_black}» {letters}%world% {info}%x% %y% %z%",
                        " {letters_black}▎ {letters}Seen by {letters_black}» %permission% {letters_black}· {info}%view_distance% {letters}blocks",
                        " {letters_black}▎ {letters}Per player {letters_black}» %per_player%",
                        " {letters_black}▎ {letters}Click {letters_black}» %click%",
                        " {letters_black}▎ {letters}Lines {letters_black}» {info}%lines%"),
                "   {letters_black}%line%. <reset>%text%",
                new States(),
                new Dialogs());
    }

    /**
     * The words shown for a state, in menus and messages alike.
     *
     * @param enabled    shown
     * @param disabled   hidden
     * @param waiting    shown, but its world is not loaded
     * @param yes        a switch that is on
     * @param no         a switch that is off
     * @param everyone   no permission needed to see it
     * @param noClick    nothing happens on click
     * @param clickCount commands run on click. Placeholder: %count%
     * @param noStyle    a line with no tags
     * @param itemLine   what an item line reads as. Placeholder: %material%
     * @param blockLine  what a block line reads as. Placeholder: %material%
     * @param emptyLine  what a blank line reads as
     */
    public record States(
            String enabled,
            String disabled,
            String waiting,
            String yes,
            String no,
            String everyone,
            String noClick,
            @Comment("Placeholder: %count%.")
            String clickCount,
            String noStyle,
            @Comment("Placeholder: %material%.")
            String itemLine,
            @Comment("Placeholder: %material%.")
            String blockLine,
            String emptyLine) {

        public States() {
            this("{success}Shown", "{error}Hidden", "{warning}Waiting for its world",
                    "{success}Yes", "{error}No", "{letters}Everyone", "{muted}Nothing",
                    "{info}%count% {letters}action(s)", "{muted}No tags",
                    "{info}Item {letters_black}» {letters}%material%",
                    "{info}Block {letters_black}» {letters}%material%",
                    "{muted}(empty line)");
        }
    }

    /**
     * The titles, labels and hints of every window that asks something.
     */
    public record Dialogs(
            String createTitle,
            String nameLabel,
            String nameHint,
            String presetLabel,
            @Comment("Placeholder: %line%.")
            String lineTitle,
            String newLineTitle,
            String textLabel,
            String textHint,
            String allLinesTitle,
            String allLinesLabel,
            String allLinesHint,
            @Comment("Placeholder: %line%.")
            String styleTitle,
            String sizeLabel,
            String opacityLabel,
            String opacityHint,
            String shadowLabel,
            String inherit,
            String on,
            String off,
            String backgroundLabel,
            String backgroundHint,
            String glowLabel,
            String glowHint,
            String spinLabel,
            String bobLabel,
            String pulseLabel,
            String shineLabel,
            String shineHint,
            String flowLabel,
            String typewriterLabel,
            String rangeLabel,
            String linePermissionLabel,
            String linePermissionHint,
            String conditionLabel,
            String conditionHint,
            String motionHint,
            String appearanceTitle,
            String billboardLabel,
            String alignmentLabel,
            String scaleXLabel,
            String scaleYLabel,
            String scaleZLabel,
            String spacingLabel,
            String lineWidthLabel,
            String textOpacityLabel,
            String seeThroughLabel,
            String defaultBackgroundLabel,
            String brightnessLabel,
            String brightnessHint,
            String visibilityTitle,
            String viewDistanceLabel,
            String permissionLabel,
            String permissionHint,
            String perPlayerLabel,
            String intervalLabel,
            String autoUpdateLabel,
            String clickTitle,
            String commandsLabel,
            String commandsHint,
            String widthLabel,
            String cooldownLabel,
            String hoverLabel,
            String hoverHint,
            String locationTitle,
            String worldLabel,
            String renameTitle,
            String cloneTitle,
            @Comment("Placeholder: %name%.")
            String confirmDelete,
            @Comment("Placeholder: %line%.")
            String confirmDeleteLine,
            String itemTitle,
            String blockTitle) {

        public Dialogs() {
            this("{primary}&lNEW HOLOGRAM",
                    "Name",
                    "Lowercase letters, numbers, - and _. Used in /holo commands.",
                    "Start from",
                    "{primary}&lLINE %line%",
                    "{primary}&lNEW LINE",
                    "Text",
                    "Colours (& codes and {colour} names), MiniMessage and %placeholders% all work.",
                    "{primary}&lALL LINES",
                    "Lines",
                    "One per row, top first. Tags go at the start of a row: <size:2> <opacity:160> <background:#66000000> <shadow:false> · <item:DIAMOND> <block:BEACON> <glow> <glow:#8a51c4> · <spin> <bob> <pulse> <shine> <flow> <typewriter> · <range:8> <perm:node> <perm:!node> <if:\"%player_level% >= 10\">. Colours, MiniMessage and %placeholders% work in the text.",
                    "{primary}&lLINE %line% {letters_black}» {highlight}STYLE",
                    "Size (1 = normal)",
                    "Opacity (0-255)",
                    "Leave empty to use the hologram's.",
                    "Shadow",
                    "Hologram's",
                    "On",
                    "Off",
                    "Background",
                    "#AARRGGBB, e.g. #66000000 for a soft dark pill. Empty for the hologram's.",
                    "Glow (item and block lines)",
                    "Empty for none, 'team' for white, or a colour like #8a51c4.",
                    "Spin (degrees a second)",
                    "Bob (blocks up and down)",
                    "Pulse (fraction of its size)",
                    "Shine",
                    "A band of light that sweeps over it. Empty for none, 'on' for the default, or a colour like highlight or #ffd700.",
                    "Flow (colours moving, characters a second)",
                    "Typewriter (characters a second)",
                    "Only within (blocks)",
                    "Only with permission",
                    "Start with ! to show it to players without it. Empty for everyone.",
                    "Only when",
                    "A comparison such as %player_level% >= 10. Empty for always.",
                    "0 turns a motion off.",
                    "{primary}&lAPPEARANCE",
                    "Facing",
                    "Alignment",
                    "Scale X",
                    "Scale Y",
                    "Scale Z",
                    "Space between lines (blocks)",
                    "Wrap width (pixels)",
                    "Text opacity (0-255)",
                    "Seen through blocks",
                    "Client's own background",
                    "Light level (0-15)",
                    "-1 uses the light where it stands; 15 keeps it bright at night.",
                    "{primary}&lVISIBILITY",
                    "View distance (blocks)",
                    "Permission to see it",
                    "Empty for everyone.",
                    "Placeholders per player",
                    "Refresh every (ticks, 20 = 1s)",
                    "Refresh on its own",
                    "{primary}&lCLICK ACTIONS",
                    "Actions",
                    "One per row, run in order. 'right:' or 'left:' first limits a row to that click. A bare line is a command as the player, 'console: ...' from the console; [SERVER] lobby, [MESSAGE] text, [SOUND] name, [TITLE] a;b, [DELAY] 1s, [TELEPORT] world;x;y;z.",
                    "Click box width (blocks)",
                    "Cooldown (milliseconds)",
                    "Grow when looked at",
                    "1 turns it off; 1.08 grows it a little.",
                    "{primary}&lLOCATION",
                    "World",
                    "{primary}Write the new name:",
                    "{primary}Name the copy:",
                    "{error}Delete the hologram {highlight}%name%{error}? This cannot be undone.",
                    "{error}Remove line {highlight}%line%{error}?",
                    "{primary}&lITEM LINE",
                    "{primary}&lBLOCK LINE");
        }
    }

    private static ConfigFile<HologramsMessages> file;

    /** Keeps the loaded file so {@link #get()} reads the current snapshot. */
    public static void install(ConfigFile<HologramsMessages> loaded) {
        file = loaded;
    }

    /** The current snapshot, or the defaults while the file has not loaded: never {@code null}. */
    public static HologramsMessages get() {
        return file == null ? new HologramsMessages() : file.get();
    }
}
