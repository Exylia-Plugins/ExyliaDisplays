package net.exylia.exyliaDisplays.npc;

import net.exylia.lib.config.Comment;
import net.exylia.lib.config.ConfigFile;

import java.util.List;

/**
 * Everything a player or an admin reads about NPCs, in {@code lang/<code>/messages/npcs.yml}.
 *
 * @param states  the words menus and messages show for a state
 * @param dialogs the titles, labels and hints of every window that asks something
 */
@Comment("Messages of the NPC module of ExyliaDisplays.")
@Comment("")
@Comment("Colours use palette tokens such as {primary}, {error} or {highlight},")
@Comment("so recolouring the server never means editing this file.")
@Comment("")
@Comment("A message may open with an effect tag, which is an instruction and never")
@Comment("reaches the screen: [sound:NAME|volume|pitch] plays a sound to the player.")
@Comment("")
@Comment("%prefix% is the prefix set in messages.yml.")
public record NpcsMessages(
        @Comment("Placeholders in the messages below: %name% is the NPC's name.")
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
        String faced,
        String teleported,
        @Comment("The NPC's world is not loaded. Placeholder: %world%.")
        String worldMissing,
        @Comment("Placeholders: %name%, %status%.")
        String stateChanged,
        @Comment("Told after any change to how it looks or behaves. Placeholder: %name%.")
        String saved,
        @Comment("Not an account name, @mirror or @none. Placeholder: %skin%.")
        String invalidSkin,
        @Comment("Not a #RRGGBB colour. Placeholder: %value%.")
        String invalidColour,
        @Comment("Placeholders: %slot%, %slots%.")
        String invalidSlot,
        @Comment("Placeholders: %name%, %slot%.")
        String equipped,
        String unequipped,
        String equipmentCleared,
        @Comment("Placeholders: %name%, %count%.")
        String labelSaved,
        String labelCleared,
        @Comment("Placeholders: %name%, %index%.")
        String actionAdded,
        String actionRemoved,
        String actionsCleared,
        @Comment("An action number that does not exist. Placeholders: %index%, %count%.")
        String actionNotFound,
        @Comment("/npc action list. Placeholders: %name%, %count%.")
        String actionsHeader,
        @Comment("Placeholders: %index%, %action%.")
        String actionLine,
        String actionsEmpty,
        @Comment("Placeholders: %name%, %hologram%.")
        String linked,
        String unlinked,
        @Comment("Placeholder: %hologram%.")
        String hologramNotFound,
        @Comment("Linking needs the hologram module running.")
        String hologramsOff,
        @Comment("Told to a player who clicks an NPC without its interaction permission.")
        String interactionDenied,
        @Comment("Told to a player who clicks again too soon. Placeholder: %remaining% (seconds).")
        String interactionCooldown,
        @Comment("How a [SAY] bubble is drawn: a hologram line, so line tags work. Placeholder: %text%.")
        String sayBubble,
        @Comment("Placeholders: %count%, %issues%.")
        String reloaded,
        @Comment("Told to admins when npcs.yml was edited by hand and applied. Placeholder: %count%.")
        String fileApplied,
        @Comment("Told when npcs.yml has a mistake. Placeholder: %issues%.")
        String fileIssues,
        @Comment("Placeholders: %source%, %count%, %skipped%.")
        String importDone,
        @Comment("Nothing found to import. Placeholders: %source%, %folder%.")
        String importNothing,
        @Comment("/npc help. Lines with <click> run or suggest the command.")
        List<String> help,
        @Comment("/npc list for the console, and /npc near. Placeholders: %count%, %radius%.")
        String listHeader,
        @Comment("One line per NPC. Placeholders: %name%, %world%, %x%, %y%, %z%, %skin%, %status%, %distance%.")
        String listLine,
        String listEmpty,
        String nearHeader,
        String nearLine,
        String nearEmpty,
        @Comment("/npc info. Placeholders: %name%, %status%, %world%, %x%, %y%, %z%, %skin%, %pose%, %scale%,")
        @Comment("%glow%, %look%, %look_range%, %view_distance%, %permission%, %label%, %link%, %actions%,")
        @Comment("%use_permission%, %cooldown%, %equipment%.")
        List<String> info,
        States states,
        Dialogs dialogs) {

    public NpcsMessages {
        if (help == null) help = List.of();
        if (info == null) info = List.of();
        if (states == null) states = new States();
        if (dialogs == null) dialogs = new Dialogs();
    }

    private static final String OK = "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|1.0|1.0]%prefix% ";
    private static final String NO = "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% ";

    public NpcsMessages() {
        this(OK + "{success}NPC {highlight}%name% {success}created.",
                "%prefix% {warning}NPC {highlight}%name% {warning}deleted.",
                NO + "{error}There is no NPC called {highlight}%name%{error}.",
                NO + "{error}An NPC called {highlight}%name% {error}already exists.",
                NO + "{error}Use lowercase letters, numbers, {highlight}- {error}and {highlight}_{error}, 32 at most.",
                OK + "{success}NPC {highlight}%name% {success}is now {highlight}%new%{success}.",
                OK + "{success}Copied {highlight}%name% {success}as {highlight}%new%{success}.",
                OK + "{success}Moved {highlight}%name% {success}here.",
                OK + "{highlight}%name% {success}now faces you.",
                "%prefix% {letters}Teleported to {highlight}%name%{letters}.",
                NO + "{error}The world {highlight}%world% {error}is not loaded.",
                OK + "{letters}NPC {highlight}%name% {letters_black}» %status%",
                OK + "{success}Saved {highlight}%name%{success}.",
                NO + "{highlight}%skin% {error}is not an account name. Use a name, {highlight}@mirror {error}or {highlight}@none{error}.",
                NO + "{highlight}%value% {error}is not a colour. Use {highlight}#RRGGBB{error}, or {highlight}none{error}.",
                NO + "{highlight}%slot% {error}is not a slot. Use {highlight}%slots%{error}.",
                OK + "{success}{highlight}%name% {success}now wears that in {highlight}%slot%{success}.",
                "%prefix% {warning}Emptied {highlight}%slot% {warning}on {highlight}%name%{warning}.",
                "%prefix% {warning}{highlight}%name% {warning}wears nothing now.",
                OK + "{success}Saved {highlight}%count% {success}label lines on {highlight}%name%{success}.",
                "%prefix% {warning}{highlight}%name% {warning}has no label now.",
                OK + "{success}Action {highlight}%index% {success}added to {highlight}%name%{success}.",
                "%prefix% {warning}Action {highlight}%index% {warning}removed from {highlight}%name%{warning}.",
                "%prefix% {warning}{highlight}%name% {warning}does nothing on click now.",
                NO + "{error}There is no action {highlight}%index%{error}; it has {highlight}%count%{error}.",
                "%prefix% {letters}Actions of {highlight}%name% {letters_black}({info}%count%{letters_black})",
                "   {letters_black}%index%. {letters}%action%",
                "%prefix% {letters}It does nothing on click yet. {warning}<click:suggest_command:'/npc action add '>[Add an action]</click>",
                OK + "{highlight}%hologram% {success}now floats above {highlight}%name%{success}.",
                "%prefix% {warning}{highlight}%name% {warning}carries no hologram now.",
                NO + "{error}There is no hologram called {highlight}%hologram%{error}.",
                NO + "{error}The hologram module is switched off in config.yml.",
                NO + "{error}You cannot use this.",
                NO + "{error}Wait {highlight}%remaining%s ⌚ {error}before using it again.",
                "<typewriter:30><background:#66000000>{letters}%text%",
                OK + "{success}Reloaded {highlight}%count% {success}NPCs. {letters_black}(%issues% issues)",
                "%prefix% {letters}npcs.yml changed: {highlight}%count% {letters}NPCs applied.",
                NO + "{error}npcs.yml has {highlight}%issues% {error}mistake(s); see the console.",
                OK + "{success}Imported {highlight}%count% {success}NPCs from {highlight}%source%{success}. {letters_black}(%skipped% skipped)",
                NO + "{error}Nothing to import from {highlight}%source%{error}. Looked in {letters_black}%folder%",
                List.of(
                        "",
                        "[center]{primary}&lEXYLIA NPCS",
                        "[center]{letters_black}Click a command to use it",
                        "",
                        " <click:run_command:'/npc'>{warning}/npc</click> {letters_black}» {letters}every NPC, on a screen",
                        " <click:suggest_command:'/npc create '>{warning}/npc create <name> [skin]</click> {letters_black}» {letters}a new one here",
                        " <click:suggest_command:'/npc edit '>{warning}/npc edit <name></click> {letters_black}» {letters}its editor",
                        " <click:suggest_command:'/npc skin '>{warning}/npc skin <name> <player|@mirror|@none></click>",
                        " <click:suggest_command:'/npc pose '>{warning}/npc pose</click> {letters_black}· <click:suggest_command:'/npc look '>{warning}look</click> {letters_black}· <click:suggest_command:'/npc glow '>{warning}glow</click> {letters_black}· <click:suggest_command:'/npc scale '>{warning}scale</click> {letters_black}» {letters}how it looks",
                        " <click:suggest_command:'/npc equip '>{warning}/npc equip <name> <slot|clear></click> {letters_black}» {letters}from your hand",
                        " <click:suggest_command:'/npc label set '>{warning}/npc label set|add|clear <name> [text]</click>",
                        " <click:suggest_command:'/npc action add '>{warning}/npc action add|remove|clear|list <name></click>",
                        " <click:suggest_command:'/npc link '>{warning}/npc link <name> <hologram></click> {letters_black}· <click:suggest_command:'/npc unlink '>{warning}unlink</click>",
                        " <click:suggest_command:'/npc movehere '>{warning}/npc movehere <name></click> {letters_black}· <click:suggest_command:'/npc tp '>{warning}tp</click> {letters_black}· <click:run_command:'/npc near'>{warning}near</click>",
                        " <click:suggest_command:'/npc clone '>{warning}/npc clone <name> <new></click> {letters_black}· <click:suggest_command:'/npc rename '>{warning}rename</click> {letters_black}· <click:suggest_command:'/npc delete '>{warning}delete</click>",
                        " <click:suggest_command:'/npc import '>{warning}/npc import <plugin></click> {letters_black}» {letters}Citizens, FancyNpcs",
                        " <click:run_command:'/npc reload'>{warning}/npc reload</click> {letters_black}» {letters}read npcs.yml again",
                        ""),
                "%prefix% {letters}NPCs {letters_black}({info}%count%{letters_black})",
                " <click:run_command:'/npc edit %name%'><hover:show_text:'{letters}Click to edit'>{highlight}%name%</hover></click> {letters_black}» {letters}%world% {info}%x% %y% %z% {letters_black}· %skin% {letters_black}· %status%",
                "%prefix% {letters}There are no NPCs yet. {warning}<click:suggest_command:'/npc create '>[Create one]</click>",
                "%prefix% {letters}NPCs within {info}%radius% {letters}blocks {letters_black}({info}%count%{letters_black})",
                " <click:run_command:'/npc edit %name%'><hover:show_text:'{letters}Click to edit'>{highlight}%name%</hover></click> {letters_black}» {info}%distance%m {letters_black}· %skin% {letters_black}· <click:run_command:'/npc tp %name%'>{warning}[TP]</click>",
                "%prefix% {letters}No NPCs within {info}%radius% {letters}blocks.",
                List.of(
                        "",
                        "[center]{primary}&l%name%",
                        "",
                        " {letters_black}▎ {letters}State {letters_black}» %status%",
                        " {letters_black}▎ {letters}Where {letters_black}» {letters}%world% {info}%x% %y% %z%",
                        " {letters_black}▎ {letters}Skin {letters_black}» %skin%",
                        " {letters_black}▎ {letters}Pose {letters_black}» {info}%pose% {letters_black}· {letters}Scale {info}%scale% {letters_black}· {letters}Glow %glow%",
                        " {letters_black}▎ {letters}Looks {letters_black}» {info}%look% {letters_black}· {info}%look_range% {letters}blocks",
                        " {letters_black}▎ {letters}Seen by {letters_black}» %permission% {letters_black}· {info}%view_distance% {letters}blocks",
                        " {letters_black}▎ {letters}Wears {letters_black}» %equipment%",
                        " {letters_black}▎ {letters}Label {letters_black}» %label% {letters_black}· {letters}Hologram %link%",
                        " {letters_black}▎ {letters}On click {letters_black}» %actions% {letters_black}· %use_permission% {letters_black}· {info}%cooldown%s ⌚",
                        ""),
                new States(),
                new Dialogs());
    }

    /**
     * The words shown for a state, in menus and messages alike.
     */
    public record States(
            String enabled,
            String disabled,
            String waiting,
            String yes,
            String no,
            String everyone,
            String nothing,
            @Comment("Placeholder: %count%.")
            String actionCount,
            @Comment("Placeholder: %count%.")
            String labelLines,
            @Comment("Placeholder: %count%.")
            String equipmentCount,
            @Comment("Placeholder: %player%.")
            String skinPlayer,
            String skinTexture,
            String skinMirror,
            String skinNone,
            String noGlow,
            String emptySlot,
            String wornSlot,
            @Comment("A linked hologram that holograms.yml no longer has. Placeholder: %hologram%.")
            String linkMissing) {

        public States() {
            this("{success}Shown", "{error}Hidden", "{warning}Waiting for its world",
                    "{success}Yes", "{error}No", "{letters}Everyone", "{muted}Nothing",
                    "{info}%count% {letters}action(s)", "{info}%count% {letters}line(s)",
                    "{info}%count% {letters}item(s)",
                    "{letters}Player {highlight}%player%", "{letters}Custom texture",
                    "{letters}Mirror {letters_black}(each viewer)", "{muted}Default",
                    "{muted}None", "{muted}Empty", "{success}Worn",
                    "{error}%hologram% {letters_black}(missing)");
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
            String skinLabel,
            String skinHint,
            String appearanceTitle,
            String skinTypeLabel,
            String skinPlayerLabel,
            String textureLabel,
            String textureHint,
            String signatureLabel,
            String poseLabel,
            String scaleLabel,
            String glowLabel,
            String glowHint,
            String labelTitle,
            String labelLinesLabel,
            String labelLinesHint,
            String heightLabel,
            String linkTitle,
            String linkLabel,
            String linkNone,
            String interactionsTitle,
            String actionsLabel,
            String actionsHint,
            String usePermissionLabel,
            String usePermissionHint,
            String cooldownLabel,
            String cooldownHint,
            String denyLabel,
            String cooldownMessageLabel,
            String messageHint,
            String faceClickerLabel,
            String visibilityTitle,
            String lookLabel,
            String lookRangeLabel,
            String viewDistanceLabel,
            String permissionLabel,
            String permissionHint,
            String locationTitle,
            String worldLabel,
            String yawLabel,
            String pitchLabel,
            String renameTitle,
            String cloneTitle,
            @Comment("Placeholder: %name%.")
            String confirmDelete) {

        public Dialogs() {
            this("{primary}&lNEW NPC",
                    "Name",
                    "Lowercase letters, numbers, - and _. Used in /npc commands.",
                    "Start from",
                    "Skin",
                    "An account name, @mirror for each viewer's own, or empty for the default.",
                    "{primary}&lAPPEARANCE",
                    "Skin from",
                    "Account name (for PLAYER)",
                    "Texture (for TEXTURE)",
                    "The value and signature mineskin.org gives for an uploaded skin.",
                    "Signature",
                    "Pose",
                    "Scale (1 = a player)",
                    "Glow",
                    "A colour such as #8a51c4, drawn in the nearest team colour. Empty for none.",
                    "{primary}&lLABEL",
                    "Lines",
                    "One per row, top first. Colours, %placeholders% and hologram tags like <size:1.4> work. Empty for no label.",
                    "Gap above its head (blocks)",
                    "{primary}&lLINKED HOLOGRAM",
                    "Hologram",
                    "None",
                    "{primary}&lINTERACTIONS",
                    "Actions",
                    "One per row, run in order. 'right:' or 'left:' first limits a row to that click. A bare line is a command as the player, 'console: ...' from the console; [SERVER] lobby, [MESSAGE] text, [SOUND] name, [DELAY] 1s, [SAY] text;5s, [ANIMATE] swing, [LOOK].",
                    "Permission to use it",
                    "Empty for everyone.",
                    "Cooldown",
                    "Between two uses by the same player, such as 500ms or 2s. 0 for none.",
                    "Message without permission",
                    "Message while waiting",
                    "Empty for the one in messages/npcs.yml.",
                    "Turn to whoever clicks",
                    "{primary}&lVISIBILITY",
                    "Head turns to",
                    "Turn range (blocks)",
                    "View distance (blocks)",
                    "Permission to see it",
                    "Empty for everyone.",
                    "{primary}&lLOCATION",
                    "World",
                    "Yaw (0 south, 90 west, 180 north)",
                    "Pitch",
                    "{primary}Write the new name:",
                    "{primary}Name the copy:",
                    "{error}Delete the NPC {highlight}%name%{error}? This cannot be undone.");
        }
    }

    private static ConfigFile<NpcsMessages> file;

    /** Keeps the loaded file so {@link #get()} reads the current snapshot. */
    public static void install(ConfigFile<NpcsMessages> loaded) {
        file = loaded;
    }

    /** The current snapshot, or the defaults while the file has not loaded: never {@code null}. */
    public static NpcsMessages get() {
        return file == null ? new NpcsMessages() : file.get();
    }
}
