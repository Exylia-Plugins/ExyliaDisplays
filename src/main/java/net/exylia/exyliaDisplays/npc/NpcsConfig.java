package net.exylia.exyliaDisplays.npc;

import net.exylia.exyliaDisplays.config.DisplaysConfig;
import net.exylia.lib.config.Comment;
import net.exylia.lib.config.Sparse;
import net.exylia.lib.npc.NpcAnimations;
import net.exylia.lib.npc.NpcLook;

import java.time.Duration;
import java.util.Collections;
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
 * @param nearRadius          the radius /npcadmin near uses when none is given
 * @param skinRefresh         how often a skin written as a placeholder is read again
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

        @Comment("The radius /npcadmin near uses when none is given, in blocks.")
        double nearRadius,

        @Comment("How often a skin written as a placeholder (a leaderboard's top name) is read again. Reads 30s, 5m.")
        Duration skinRefresh,

        @Comment("What an NPC created from the menu can start from. Add your own:")
        @Comment("'name' is shown in the list, 'skin' is an account name, a placeholder or @mirror (empty for the one typed),")
        @Comment("and %npc% in a label line becomes the new NPC's name.")
        Map<String, Preset> presets) {

    public NpcsConfig() {
        this(48.0, NpcLook.EACH_VIEWER, true, Duration.ofSeconds(5), Duration.ofSeconds(4), 20.0, Duration.ofSeconds(30),
                defaultPresets());
    }

    public NpcsConfig {
        if (defaultViewDistance <= 0) defaultViewDistance = 48.0;
        if (defaultLook == null) defaultLook = NpcLook.EACH_VIEWER;
        if (sayDuration == null || sayDuration.isNegative() || sayDuration.isZero()) sayDuration = Duration.ofSeconds(5);
        if (turnBack == null || turnBack.isNegative()) turnBack = Duration.ofSeconds(4);
        if (nearRadius <= 0) nearRadius = 20.0;
        if (skinRefresh == null || skinRefresh.compareTo(Duration.ofSeconds(1)) < 0) skinRefresh = Duration.ofSeconds(30);
        if (presets == null) presets = Map.of();
    }

    /**
     * A starting point for a new NPC.
     *
     * @param name    what the list shows
     * @param skin    an account name or {@code @mirror}; empty keeps the one typed in the window
     * @param label   the label it starts with
     * @param actions the interaction lines it starts with
     * @param body    how it sits, looks, glows, dresses and moves; left out, the defaults
     */
    public record Preset(String name, String skin, List<String> label, List<String> actions,
                         @Comment("How it sits, looks, glows, dresses and moves. Leave out for the defaults.")
                         Body body) {

        public Preset() {
            this("Simple", "", List.of("{primary}&l%npc%"), List.of(), new Body());
        }

        /** A preset as written before {@code body} existed. */
        public Preset(String name, String skin, List<String> label, List<String> actions) {
            this(name, skin, label, actions, new Body());
        }

        public Preset {
            if (name == null || name.isBlank()) name = "Preset";
            if (skin == null) skin = "";
            label = label == null ? List.of() : List.copyOf(label);
            actions = actions == null ? List.of() : List.copyOf(actions);
            if (body == null) body = new Body();
        }
    }

    /**
     * What a preset sets on a new NPC beyond its name, skin and clicks. Only written to the file
     * when it sets something.
     *
     * @param sitting    whether it sits on an invisible seat
     * @param glow       an outline colour such as {@code #8a51c4}; empty for none
     * @param equipment  what it wears and holds, by slot
     * @param look       where it turns its head; the default leaves {@code default-look} in charge
     * @param animations the gestures it plays
     */
    public record Body(
            @Comment("Sit on an invisible seat: place it on a stair or a block's top.")
            boolean sitting,
            @Comment("An outline colour such as '#8a51c4'. Empty for none.")
            String glow,
            @Comment("What it wears and holds, by slot: head, chest, legs, feet, hand, off_hand.")
            Map<String, String> equipment,
            @Comment("Where it turns its head, and how close a player has to be.")
            StoredNpc.Look look,
            @Comment("Gestures on a click, every while, or to greet a player who walks up: vanilla moves,")
            @Comment("or one performance (anim:<file>, emote:<id>, keys:<frames>); a sitting NPC never performs.")
            NpcAnimations animations) implements Sparse {

        public Body() {
            this(false, "", Map.of(), new StoredNpc.Look(), new NpcAnimations());
        }

        public Body {
            if (glow == null) glow = "";
            equipment = equipment == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(equipment));
            if (look == null) look = new StoredNpc.Look();
            if (animations == null) animations = new NpcAnimations();
        }

        @Override
        public boolean isEmpty() {
            return !sitting && glow.isBlank() && equipment.isEmpty() && look.equals(new StoredNpc.Look())
                    && animations.isEmpty();
        }

        /** The new NPC wearing this body; what it leaves at its default stays the NPC's. */
        public StoredNpc applyTo(StoredNpc npc) {
            if (isEmpty()) return npc;
            StoredNpc dressed = npc.withSitting(sitting).withGlow(glow).withEquipment(equipment)
                    .withAnimations(animations);
            return look.equals(new StoredNpc.Look()) ? dressed : dressed.withLook(look);
        }
    }

    /**
     * A bow as a performance: a hand to the chest, a fold at the waist, held, and back up. Frames
     * written in place, so it needs no animation file and no other plugin.
     */
    static final String GREETER_BOW = "follow:1;breathe:1;keys:0.26 plant=1 up=0.01 spine=-4 head=-8 arms=0,0,4 elbows=12 ease=sine_out"
            + " | 0.10 arm_r=40,-30,6 elbow_r=60,-30 arm_l=-10,0,10 elbow_l=24,-60 ease=sine_in"
            + " | 0.12 arm_r=34,-26,4 elbow_r=118,-40 arm_l=-24,0,16 elbow_l=60,-90 spine=-2 head=-4 ease=quad_out"
            + " | 0.14 flip=14 up=-0.01 spine=4 head=0 ease=sine_in"
            + " | 0.20 flip=32 up=-0.035 spine=15 head=8 ease=cubic_out"
            + " | 0.40 flip=35 up=-0.045 spine=18 head=10 ease=sine_in_out"
            + " | 0.16 flip=20 up=-0.025 spine=12 head=6 ease=sine_in"
            + " | 0.20 flip=0 up=0 spine=-3 head=-6 arm_r=30,-22,4 elbow_r=84,-30 arm_l=-10,0,12 elbow_l=30,-60 ease=cubic_out"
            + " | 0.12 arm_r=12,-10,6 elbow_r=46,-10 arm_l=0,0,8 elbow_l=18,0 spine=0 ease=sine_in"
            + " | 0.14 arm_r=4,-4,5 elbow_r=14,0 arm_l=0,0,6 elbow_l=12,0 head=3 spine=1 ease=back_out"
            + " | 0.22 stand ease=sine_in_out";

    private static Map<String, Preset> defaultPresets() {
        Map<String, Preset> presets = new LinkedHashMap<>();
        presets.put("simple", new Preset("Simple name", "", List.of("<size:1.2>{primary}&l%npc%"), List.of()));
        // Waves at whoever walks up, nods now and then, and bows with its whole body when clicked.
        presets.put("greeter", new Preset("Greeter", "", List.of(
                "<size:1.3><shine:highlight,pause=4>{primary}&l%npc%",
                "<opacity:200>{letters_black}Your first friend here"), List.of(
                "right: [SAY] {letters}Welcome, {highlight}%player_name%{letters}! The portals are behind me.;6s",
                "left: [SAY] {error}Hey, that hurt!;2s"),
                new Body(false, "", Map.of(), new StoredNpc.Look(NpcLook.EACH_VIEWER, 10),
                        new NpcAnimations("flinch", GREETER_BOW, "nod", Duration.ofSeconds(25), "wave", 5,
                                Duration.ofMinutes(2), NpcAnimations.DEFAULT_PERFORM_RANGE))));
        // Sits behind the bar, raises a glass now and then and pours one when asked. Vanilla moves
        // only: a sitting NPC never performs.
        presets.put("bartender", new Preset("Sitting bartender", "", List.of(
                "<size:1.2>{highlight}&lBARTENDER",
                "<range:6><typewriter>{letters_black}What can I get you?"), List.of(
                "right: [SOUND] ENTITY_GENERIC_DRINK;0.8;1.1",
                "right: [SAY] {letters}One {highlight}starlight tonic{letters}, on the house.;4s"),
                new Body(true, "", Map.of("hand", "POTION"), new StoredNpc.Look(NpcLook.EACH_VIEWER, 6),
                        new NpcAnimations("", "cheer", "swing, 600ms, swing", Duration.ofSeconds(18), "nod", 4,
                                Duration.ofMinutes(1)))));
        // Armoured and outlined, follows the nearest player with its eyes and bows to visitors.
        // Vanilla moves: a performance's body does not draw chest, legs and feet armour.
        presets.put("guard", new Preset("Watchful guard", "", List.of(
                "<size:1.2>{error}&lGUARD",
                "<opacity:190>{letters_black}Keep the peace"), List.of(
                "[SAY] {letters}Move along, citizen.;3s"),
                new Body(false, "#a33b53", Map.of(
                        "head", "IRON_HELMET", "chest", "IRON_CHESTPLATE", "legs", "IRON_LEGGINGS",
                        "feet", "IRON_BOOTS", "hand", "IRON_SWORD", "off_hand", "SHIELD"),
                        new StoredNpc.Look(NpcLook.NEAREST, 12),
                        new NpcAnimations("punch", "bow", "", Duration.ofSeconds(20), "bow", 6,
                                Duration.ofMinutes(5)))));
        // Swings an arm and runs the player's shop command.
        presets.put("merchant", new Preset("Merchant", "", List.of(
                "<size:1.3><shine:highlight>{highlight}&lMERCHANT",
                "<frames:3>{letters}Rare goods||{letters}Fair prices||{letters}New stock daily",
                "{warning}➥ {letters}Right-click to trade"), List.of(
                "right: [SOUND] ENTITY_VILLAGER_TRADE;1;1.1",
                "right: shop"),
                new Body(false, "#ffd700", Map.of("hand", "EMERALD", "head", "LEATHER_HELMET"),
                        new StoredNpc.Look(NpcLook.EACH_VIEWER, 8),
                        new NpcAnimations("", "swing", "wave-offhand", Duration.ofSeconds(30), "", 4,
                                Duration.ofSeconds(30)))));
        // Frozen where it was placed, for a leaderboard spot or a champion: point its skin at a
        // placeholder (/npc <name> skin %your_top_1_name% Notch) and it wears whoever holds the spot.
        presets.put("statue", new Preset("Statue", "", List.of(
                "<size:1.3><shine:highlight,pause=6>{highlight}&l%npc%",
                "{letters_black}▎ {letters}Hall of fame"), List.of(),
                new Body(false, "#ffd700", Map.of(), new StoredNpc.Look(NpcLook.FIXED, 0), new NpcAnimations())));
        // Sends the player to another server of the network.
        presets.put("portal", new Preset("Server portal", "", List.of(
                "<size:1.4><flow:6>{info}&lSURVIVAL",
                "{letters}Click to play"), List.of(
                "[ANIMATE] swing",
                "[SOUND] BLOCK_PORTAL_TRIGGER;0.4;1.6",
                "[SERVER] survival")));
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
