package net.exylia.exyliaDisplays.npc;

import net.exylia.exyliaDisplays.config.DisplaysConfig;
import net.exylia.lib.config.Comment;
import net.exylia.lib.npc.NpcLook;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code npcs} section of {@code config.yml}.
 *
 * @param defaultViewDistance the view distance of a new NPC
 * @param defaultLook         where a new NPC turns its head
 * @param faceCreator         whether a new NPC faces whoever created it
 * @param sayDuration         how long a {@code [SAY]} bubble stays when the line names no time
 * @param turnBack            how long an NPC keeps facing whoever clicked it
 * @param nearRadius          the radius /npc near uses when none is given
 * @param presets             what a new NPC can start from
 */
public record NpcsConfig(
        @Comment("How far away new NPCs are seen, in blocks.")
        double defaultViewDistance,

        @Comment("Where a new NPC turns its head: fixed, nearest or each-viewer.")
        NpcLook defaultLook,

        @Comment("A new NPC faces you instead of looking where you look.")
        boolean faceCreator,

        @Comment("How long a [SAY] speech bubble stays when its line names no time. Reads 5s, 1m.")
        Duration sayDuration,

        @Comment("How long an NPC keeps facing whoever clicked it before turning back. Reads 4s.")
        Duration turnBack,

        @Comment("The radius /npc near uses when none is given, in blocks.")
        double nearRadius,

        @Comment("What an NPC created from the menu can start from. Add your own:")
        @Comment("'name' is shown in the list, 'skin' is an account name or @mirror (empty for the one typed),")
        @Comment("and %npc% in a label line becomes the new NPC's name.")
        Map<String, Preset> presets) {

    public NpcsConfig() {
        this(48.0, NpcLook.EACH_VIEWER, true, Duration.ofSeconds(5), Duration.ofSeconds(4), 20.0, defaultPresets());
    }

    public NpcsConfig {
        if (defaultViewDistance <= 0) defaultViewDistance = 48.0;
        if (defaultLook == null) defaultLook = NpcLook.EACH_VIEWER;
        if (sayDuration == null || sayDuration.isNegative() || sayDuration.isZero()) sayDuration = Duration.ofSeconds(5);
        if (turnBack == null || turnBack.isNegative()) turnBack = Duration.ofSeconds(4);
        if (nearRadius <= 0) nearRadius = 20.0;
        if (presets == null) presets = Map.of();
    }

    /**
     * A starting point for a new NPC.
     *
     * @param name    what the list shows
     * @param skin    an account name or {@code @mirror}; empty keeps the one typed in the window
     * @param label   the label it starts with
     * @param actions the interaction lines it starts with
     */
    public record Preset(String name, String skin, List<String> label, List<String> actions) {

        public Preset() {
            this("Simple", "", List.of("{primary}&l%npc%"), List.of());
        }

        public Preset {
            if (name == null || name.isBlank()) name = "Preset";
            if (skin == null) skin = "";
            label = label == null ? List.of() : List.copyOf(label);
            actions = actions == null ? List.of() : List.copyOf(actions);
        }
    }

    private static Map<String, Preset> defaultPresets() {
        Map<String, Preset> presets = new LinkedHashMap<>();
        presets.put("simple", new Preset("Simple name", "", List.of("<size:1.2>{primary}&l%npc%"), List.of()));
        // Greets whoever right-clicks it in a bubble only they see, and flinches when hit.
        presets.put("guide", new Preset("Guide", "", List.of(
                "<size:1.2>{primary}&lGUIDE",
                "<opacity:200>{letters_black}Right-click me"), List.of(
                "right: [SOUND] ENTITY_VILLAGER_AMBIENT;1;1.2",
                "right: [SAY] {letters}Welcome, {highlight}%player_name%{letters}! The portals are behind me.;6s",
                "left: [ANIMATE] hurt",
                "left: [SAY] {error}Hey, that hurt!;2s")));
        // Sends the player to another server of the network.
        presets.put("portal", new Preset("Server portal", "", List.of(
                "<size:1.4><flow:6>{info}&lSURVIVAL",
                "{letters}Click to play"), List.of(
                "[ANIMATE] swing",
                "[SOUND] BLOCK_PORTAL_TRIGGER;0.4;1.6",
                "[SERVER] survival")));
        // Swings an arm and runs the player's shop command.
        presets.put("shopkeeper", new Preset("Shopkeeper", "", List.of(
                "<shine:highlight>{highlight}&lSHOP",
                "<opacity:200>{letters_black}Right-click to trade"), List.of(
                "right: [ANIMATE] swing",
                "right: shop")));
        // Every player sees themselves standing there.
        presets.put("mirror", new Preset("Mirror", "@mirror", List.of(
                "<typewriter>{letters_black}This is you"), List.of(
                "[SAY] {letters}Looking good.;3s")));
        return presets;
    }

    /** The current settings. */
    public static NpcsConfig get() {
        return DisplaysConfig.get().npcs();
    }
}
