package net.exylia.exyliaDisplays.npc;

import net.exylia.exyliaDisplays.common.store.Place;
import net.exylia.lib.config.Comment;
import net.exylia.lib.config.Time;
import net.exylia.lib.item.Items;
import net.exylia.lib.npc.NpcAnimations;
import net.exylia.lib.npc.NpcLook;
import net.exylia.lib.npc.NpcPose;
import net.exylia.lib.npc.NpcSkin;
import net.exylia.lib.npc.NpcSpec;
import net.exylia.lib.text.Text;
import org.bukkit.inventory.EquipmentSlot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * One NPC as {@code npcs.yml} writes it.
 *
 * <p>The file is the single source of truth, as for holograms: commands, the screens and a hand
 * edit all change this record, and the NPC in the world is drawn from it. Every section has
 * defaults, so a hand-written NPC only needs a location.
 *
 * @param enabled        whether it is shown
 * @param location       where it stands and which way it faces
 * @param skin           what it wears
 * @param pose           how it holds itself
 * @param scale          how big it is, 1 being a player
 * @param glow           an outline colour as {@code #RRGGBB}, or empty
 * @param equipment      what it wears and holds, by slot name, as icon strings
 * @param look           where it turns its head
 * @param viewDistance   how far away it is seen
 * @param permission     who sees it; empty for everyone
 * @param label          the lines floating above its head
 * @param linkedHologram a hologram of holograms.yml kept above its head
 * @param interactions   what clicking it does
 * @param sitting        whether it sits on an invisible seat
 * @param animations     the gestures it plays on a click, on its own, or to greet a player
 */
public record StoredNpc(
        @Comment("Set to false to keep it in this file without showing it.")
        boolean enabled,

        @Comment("Where it stands, and which way it faces.")
        Place location,

        @Comment("What it wears.")
        Skin skin,

        @Comment("standing, sneaking, lying, crawling or spinning.")
        NpcPose pose,

        @Comment("Its size: 1 is a player's, from 0.0625 to 16.")
        double scale,

        @Comment("An outline colour such as '#8a51c4', drawn in the nearest team colour. Empty for none.")
        String glow,

        @Comment("What it wears and holds, by slot: head, chest, legs, feet, hand, off_hand.")
        @Comment("A material (DIAMOND_SWORD), a head (playerhead-Notch, basehead-<texture>) or a bytes: item.")
        Map<String, String> equipment,

        @Comment("Where it turns its head.")
        Look look,

        @Comment("How far away it is seen, in blocks.")
        double viewDistance,

        @Comment("Only players with this permission see it. Leave empty for everyone.")
        String permission,

        @Comment("Lines floating above its head, the name players read. Every hologram line tag works.")
        Label label,

        @Comment("The name of a hologram in holograms.yml to keep above its head, following it when it moves.")
        @Comment("Empty for none. Use it for a hologram edited on its own; for a simple name, use label.")
        String linkedHologram,

        @Comment("What clicking it does, and who may.")
        Interactions interactions,

        @Comment("Sit on an invisible seat, hips at its location: place it on a stair or a block's top.")
        boolean sitting,

        @Comment("Gestures it plays: on a left or right click, on its own every while, or to greet a player.")
        @Comment("Each is vanilla moves (wave, 'swing, 250ms, hurt') or one performance, a whole-body animation:")
        @Comment("anim:<file> (plugins/ExyliaDisplays/animations), emote:<id> (ExyliaEmotes) or keys:<frames>.")
        @Comment("perform-range is how close a player has to be to see one; a sitting NPC never performs.")
        @Comment("/npc <name> animation <trigger> <text> sets one; /npc <name> play <animation> tries it.")
        NpcAnimations animations) {

    /** Slot names as the file writes them, in the order screens show them. */
    public static final List<String> SLOTS = List.of("head", "chest", "legs", "feet", "hand", "off_hand");

    private static final Pattern HEX = Pattern.compile("#?[0-9a-fA-F]{6}");

    public StoredNpc() {
        this(true, new Place(), new Skin(), NpcPose.STANDING, 1.0, "", Map.of(), new Look(), 48.0, "",
                new Label(), "", new Interactions(), false, new NpcAnimations());
    }

    public StoredNpc {
        if (location == null) location = new Place();
        if (skin == null) skin = new Skin();
        if (pose == null) pose = NpcPose.STANDING;
        scale = scale <= 0 ? 1.0 : Math.clamp(scale, 0.0625, 16.0);
        if (glow == null) glow = "";
        equipment = equipment == null ? Map.of() : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(equipment));
        if (look == null) look = new Look();
        if (viewDistance <= 0) viewDistance = 48.0;
        if (permission == null) permission = "";
        if (label == null) label = new Label();
        if (linkedHologram == null) linkedHologram = "";
        if (interactions == null) interactions = new Interactions();
        if (animations == null) animations = new NpcAnimations();
    }

    // ------------------------------------------------------------------
    // Sections
    // ------------------------------------------------------------------

    /**
     * What it wears.
     *
     * @param type      where the skin comes from
     * @param player    the account, for {@code PLAYER}; or a placeholder that names one
     * @param texture   the texture value, for {@code TEXTURE}
     * @param signature the texture's signature, for {@code TEXTURE}
     * @param fallback  the account worn while {@code player} is a placeholder naming nobody
     */
    public record Skin(
            @Comment("player: the skin an account wears. texture: the texture and signature below.")
            @Comment("mirror: every viewer sees their own skin. none: the default skin.")
            Type type,
            @Comment("The account, for player. A placeholder that gives one works too, such as a leaderboard's")
            @Comment("top name: the NPC then wears whoever holds that spot, read again every npcs.skin-refresh.")
            String player,
            @Comment("The base64 texture value, for texture (mineskin.org gives both).")
            String texture,
            @Comment("Mojang's signature of the texture; without it the client draws the default skin.")
            String signature,
            @Comment("The account worn while 'player' is a placeholder that names nobody yet. Empty for the default skin.")
            String fallback) {

        public enum Type { PLAYER, TEXTURE, MIRROR, NONE }

        /** What a Minecraft account name may be. */
        private static final Pattern ACCOUNT = Pattern.compile("[A-Za-z0-9_]{1,16}");

        public Skin() {
            this(Type.NONE, "", "", "", "");
        }

        /** A skin with no fallback, as written before placeholders. */
        public Skin(Type type, String player, String texture, String signature) {
            this(type, player, texture, signature, "");
        }

        public Skin {
            if (type == null) type = Type.NONE;
            if (player == null) player = "";
            if (texture == null) texture = "";
            if (signature == null) signature = "";
            if (fallback == null) fallback = "";
        }

        public static Skin player(String name) {
            return new Skin(Type.PLAYER, name, "", "");
        }

        /** Whoever a placeholder names, the fallback account while it names nobody. */
        public static Skin placeholder(String placeholder, String fallback) {
            return new Skin(Type.PLAYER, placeholder, "", "", fallback);
        }

        public static Skin texture(String value, String signature) {
            return new Skin(Type.TEXTURE, "", value, signature);
        }

        public static Skin mirror() {
            return new Skin(Type.MIRROR, "", "", "");
        }

        /**
         * A skin as a command or a preset writes it: an account name, a placeholder that names one
         * ({@code %top_kills_1%}), {@code @mirror}, or {@code @none} (or nothing).
         *
         * @return the skin, or {@code null} when it is none of those
         */
        public static Skin parse(String written) {
            String trimmed = written == null ? "" : written.trim();
            if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("@none")) return new Skin();
            if (trimmed.equalsIgnoreCase("@mirror")) return mirror();
            if (isPlaceholder(trimmed)) return placeholder(trimmed, "");
            return isAccount(trimmed) ? player(trimmed) : null;
        }

        /** Whether a name could be a Minecraft account. */
        public static boolean isAccount(String name) {
            return name != null && ACCOUNT.matcher(name).matches();
        }

        private static boolean isPlaceholder(String text) {
            int open = text.indexOf('%');
            return open >= 0 && text.indexOf('%', open + 1) > open + 1;
        }

        /** Whether it wears whoever a placeholder names, so it is read again now and then. */
        public boolean isPlaceholder() {
            return type == Type.PLAYER && isPlaceholder(player);
        }

        /**
         * The account a placeholder gave, read: its formatting stripped, or {@code null} when what
         * is left is no account name (an empty leaderboard spot, "None", the placeholder unresolved).
         */
        public static String account(String resolved) {
            String name = Text.strip(resolved).trim();
            return isAccount(name) ? name : null;
        }

        /** What ExyliaLib draws; a section missing what its type needs is the default skin. */
        public NpcSkin toSkin() {
            return wearing(null);
        }

        /**
         * What ExyliaLib draws, given what its placeholder names now.
         *
         * @param resolved the account the placeholder named last, or {@code null} for nobody
         */
        public NpcSkin wearing(String resolved) {
            if (isPlaceholder()) {
                String account = resolved != null ? resolved : fallback;
                return isAccount(account) ? NpcSkin.player(account) : NpcSkin.none();
            }
            return switch (type) {
                case PLAYER -> player.isBlank() ? NpcSkin.none() : NpcSkin.player(player);
                case TEXTURE -> texture.isBlank() ? NpcSkin.none()
                        : NpcSkin.texture(texture, signature.isBlank() ? null : signature);
                case MIRROR -> NpcSkin.mirror();
                case NONE -> NpcSkin.none();
            };
        }

        /** A head that looks like this skin, as a menu's material; a plain head when there is none. */
        public String head() {
            String account = isPlaceholder() ? fallback : player;
            return switch (type) {
                case PLAYER -> isAccount(account) ? "playerhead-" + account : "PLAYER_HEAD";
                case TEXTURE -> texture.isBlank() ? "PLAYER_HEAD" : "basehead-" + texture;
                default -> "PLAYER_HEAD";
            };
        }

        public Skin withFallback(String value) {
            return new Skin(type, player, texture, signature, value);
        }
    }

    /**
     * Where it turns its head.
     *
     * @param mode  how
     * @param range how close a player has to be for it to turn to them
     */
    public record Look(
            @Comment("fixed: the way it faces. nearest: at the nearest player, the same for all.")
            @Comment("each-viewer: every player sees it looking at them.")
            NpcLook mode,
            @Comment("How close a player has to be for it to turn to them, in blocks.")
            double range) {

        public Look() {
            this(NpcLook.EACH_VIEWER, 8.0);
        }

        public Look {
            if (mode == null) mode = NpcLook.EACH_VIEWER;
            if (range < 0) range = 0;
        }
    }

    /**
     * The lines above its head.
     *
     * @param lines        top first; empty for no label
     * @param heightOffset the gap between the top of its head and the bottom line
     */
    public record Label(
            @Comment("Top first. Leave empty for no label.")
            List<String> lines,
            @Comment("The gap between the top of its head and the bottom line, in blocks.")
            double heightOffset) {

        public Label() {
            this(List.of(), 0.25);
        }

        public Label {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }

        public Label withLines(List<String> value) {
            return new Label(value, heightOffset);
        }
    }

    /**
     * What clicking it does.
     *
     * @param actions         interaction lines, run in order
     * @param permission      what a player needs to use it
     * @param cooldown        milliseconds between two uses by the same player
     * @param denyMessage     told to a player without the permission
     * @param cooldownMessage told to a player still waiting
     * @param faceClicker     whether it turns to whoever clicked it
     */
    public record Interactions(
            @Comment("One per line, run in order. 'left:', 'right:', 'shift_left:', 'shift_right:' or 'shift:'")
            @Comment("first limits a line to that click. A bare line is a command as the player, 'console: ...'")
            @Comment("from the console. [SERVER] lobby, [TELEPORT] world;x;y;z, [MESSAGE] text, [SOUND] name,")
            @Comment("[TITLE] a;b, [DELAY] 1s, [ACTION] ns:id, plus [SAY] text;5s (a speech bubble only the")
            @Comment("clicker sees), [ANIMATE] wave (a preset, moves like 'swing, 250ms, hurt', or a performance")
            @Comment("like 'anim:bow' or 'emote:salute') and [LOOK]")
            @Comment("(turn to the clicker). %npc% is its name.")
            List<String> actions,
            @Comment("Only players with this permission can use it. Leave empty for everyone.")
            String permission,
            @Comment("How long the same player waits between two uses. Reads 500ms, 2s, 1m.")
            @Time(Time.Unit.MILLIS) long cooldown,
            @Comment("Told to a player without the permission. Empty for the one in messages/npcs.yml.")
            String denyMessage,
            @Comment("Told to a player still waiting; %remaining% is the seconds left. Empty for the default.")
            String cooldownMessage,
            @Comment("Turn to whoever clicked it for a moment.")
            boolean faceClicker) {

        public Interactions() {
            this(List.of(), "", 0, "", "", true);
        }

        public Interactions {
            actions = actions == null ? List.of() : List.copyOf(actions);
            if (permission == null) permission = "";
            cooldown = Math.max(0, cooldown);
            if (denyMessage == null) denyMessage = "";
            if (cooldownMessage == null) cooldownMessage = "";
        }

        public Interactions withActions(List<String> value) {
            return new Interactions(value, permission, cooldown, denyMessage, cooldownMessage, faceClicker);
        }
    }

    // ------------------------------------------------------------------
    // What ExyliaLib draws
    // ------------------------------------------------------------------

    /** The spec ExyliaLib places. Reads the equipment's items, so it needs a running server. */
    public NpcSpec toSpec() {
        return toSpec(null);
    }

    /**
     * The spec ExyliaLib places, wearing whoever its skin's placeholder named last.
     *
     * @param resolved that account, or {@code null} for nobody (the fallback)
     */
    public NpcSpec toSpec(String resolved) {
        NpcSpec.Builder spec = NpcSpec.builder()
                .skin(skin.wearing(resolved))
                .pose(pose)
                .scale(scale)
                .glow(glowRgb())
                .look(look.mode())
                .lookRange(look.range())
                .viewDistance(viewDistance)
                .sitting(sitting)
                .animations(animations);
        equipment.forEach((slot, icon) -> {
            EquipmentSlot parsed = slot(slot);
            if (parsed != null && !icon.isBlank()) spec.equip(parsed, Items.icon(icon));
        });
        return spec.build();
    }

    /** Whether another version of it would be drawn differently, apart from where it stands. */
    public boolean looksLike(StoredNpc other) {
        return other != null && skin.equals(other.skin) && pose == other.pose && scale == other.scale
                && glow.equals(other.glow) && equipment.equals(other.equipment) && look.equals(other.look)
                && viewDistance == other.viewDistance && sitting == other.sitting
                && animations.equals(other.animations);
    }

    /** The outline as {@code 0xRRGGBB}, or -1 for none or a colour that cannot be read. */
    public int glowRgb() {
        return isColour(glow) ? Integer.parseInt(glow.trim().replace("#", ""), 16) : -1;
    }

    /** Whether a glow can be read; empty counts, as "no glow". */
    public static boolean isColour(String written) {
        return written != null && HEX.matcher(written.trim()).matches();
    }

    /**
     * How high the top of its head is above its feet: a player's height in its pose, times its
     * scale. Labels, linked holograms and speech bubbles all stand on it.
     */
    public double headHeight() {
        double standing = switch (pose) {
            case STANDING -> 1.8;
            case SNEAKING -> 1.5;
            case LYING, CRAWLING, SPINNING -> 0.6;
        };
        // Sitting lowers the head by about half a block: an estimate of the seat's drop, never below a crawl.
        return (sitting ? Math.max(0.6, standing - 0.5) : standing) * scale;
    }

    /** Where the label's bottom line stands, above its feet. */
    public double labelHeight() {
        return headHeight() + label.heightOffset();
    }

    /**
     * Above everything already floating over its head: the label's lines, if any. Linked
     * holograms and speech bubbles stand here so they never cover the label.
     */
    public double topHeight() {
        return labelHeight() + label.lines().size() * 0.3;
    }

    /** A slot name as the file writes it, read; {@code null} for a name that is no slot. */
    public static EquipmentSlot slot(String name) {
        return switch (name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replace('-', '_')) {
            case "head", "helmet" -> EquipmentSlot.HEAD;
            case "chest", "chestplate" -> EquipmentSlot.CHEST;
            case "legs", "leggings" -> EquipmentSlot.LEGS;
            case "feet", "boots" -> EquipmentSlot.FEET;
            case "hand", "main_hand", "mainhand" -> EquipmentSlot.HAND;
            case "off_hand", "offhand" -> EquipmentSlot.OFF_HAND;
            default -> null;
        };
    }

    /** The file's name for a slot. */
    public static String slotName(EquipmentSlot slot) {
        return slot.name().toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------
    // Withers
    // ------------------------------------------------------------------

    public StoredNpc withEnabled(boolean value) {
        return new StoredNpc(value, location, skin, pose, scale, glow, equipment, look, viewDistance, permission, label, linkedHologram, interactions, sitting, animations);
    }

    public StoredNpc withLocation(Place value) {
        return new StoredNpc(enabled, value, skin, pose, scale, glow, equipment, look, viewDistance, permission, label, linkedHologram, interactions, sitting, animations);
    }

    public StoredNpc withSkin(Skin value) {
        return new StoredNpc(enabled, location, value, pose, scale, glow, equipment, look, viewDistance, permission, label, linkedHologram, interactions, sitting, animations);
    }

    public StoredNpc withPose(NpcPose value) {
        return new StoredNpc(enabled, location, skin, value, scale, glow, equipment, look, viewDistance, permission, label, linkedHologram, interactions, sitting, animations);
    }

    public StoredNpc withScale(double value) {
        return new StoredNpc(enabled, location, skin, pose, value, glow, equipment, look, viewDistance, permission, label, linkedHologram, interactions, sitting, animations);
    }

    public StoredNpc withGlow(String value) {
        return new StoredNpc(enabled, location, skin, pose, scale, value, equipment, look, viewDistance, permission, label, linkedHologram, interactions, sitting, animations);
    }

    public StoredNpc withEquipment(Map<String, String> value) {
        return new StoredNpc(enabled, location, skin, pose, scale, glow, value, look, viewDistance, permission, label, linkedHologram, interactions, sitting, animations);
    }

    public StoredNpc withLook(Look value) {
        return new StoredNpc(enabled, location, skin, pose, scale, glow, equipment, value, viewDistance, permission, label, linkedHologram, interactions, sitting, animations);
    }

    public StoredNpc withViewDistance(double value) {
        return new StoredNpc(enabled, location, skin, pose, scale, glow, equipment, look, value, permission, label, linkedHologram, interactions, sitting, animations);
    }

    public StoredNpc withPermission(String value) {
        return new StoredNpc(enabled, location, skin, pose, scale, glow, equipment, look, viewDistance, value, label, linkedHologram, interactions, sitting, animations);
    }

    public StoredNpc withLabel(Label value) {
        return new StoredNpc(enabled, location, skin, pose, scale, glow, equipment, look, viewDistance, permission, value, linkedHologram, interactions, sitting, animations);
    }

    public StoredNpc withLinkedHologram(String value) {
        return new StoredNpc(enabled, location, skin, pose, scale, glow, equipment, look, viewDistance, permission, label, value, interactions, sitting, animations);
    }

    public StoredNpc withSitting(boolean value) {
        return new StoredNpc(enabled, location, skin, pose, scale, glow, equipment, look, viewDistance, permission, label, linkedHologram, interactions, value, animations);
    }

    public StoredNpc withAnimations(NpcAnimations value) {
        return new StoredNpc(enabled, location, skin, pose, scale, glow, equipment, look, viewDistance, permission, label, linkedHologram, interactions, sitting, value);
    }

    public StoredNpc withInteractions(Interactions value) {
        return new StoredNpc(enabled, location, skin, pose, scale, glow, equipment, look, viewDistance, permission, label, linkedHologram, value, sitting, animations);
    }

    // ------------------------------------------------------------------
    // Edits commands and screens share
    // ------------------------------------------------------------------

    /** Puts an item in a slot, or empties it when the icon is blank or air. */
    public StoredNpc equip(EquipmentSlot slot, String icon) {
        Map<String, String> edited = new LinkedHashMap<>(equipment);
        String key = slotName(slot);
        // An old spelling of the same slot ("helmet") would otherwise survive next to the new one.
        edited.keySet().removeIf(written -> slot(written) == slot);
        if (icon != null && !icon.isBlank() && !icon.equalsIgnoreCase("AIR")) {
            edited.put(key, icon);
        }
        return withEquipment(edited);
    }

    public boolean hasAction(int index) {
        return index >= 0 && index < interactions.actions().size();
    }

    public StoredNpc addAction(String line) {
        List<String> edited = new ArrayList<>(interactions.actions());
        edited.add(line);
        return withInteractions(interactions.withActions(edited));
    }

    public StoredNpc setAction(int index, String line) {
        List<String> edited = new ArrayList<>(interactions.actions());
        edited.set(index, line);
        return withInteractions(interactions.withActions(edited));
    }

    public StoredNpc removeAction(int index) {
        List<String> edited = new ArrayList<>(interactions.actions());
        edited.remove(index);
        return withInteractions(interactions.withActions(edited));
    }

    /** The triggers {@link #withAnimation} takes, as npcs.yml writes them. */
    public static final List<String> TRIGGERS = List.of("left-click", "right-click", "idle", "greet");

    /**
     * Plays another animation on one trigger; an empty text plays nothing there.
     *
     * @return the changed NPC, or {@code null} when the trigger is none of {@link #TRIGGERS}
     */
    public StoredNpc withAnimation(String trigger, String text) {
        NpcAnimations a = animations;
        String t = text == null ? "" : text.trim();
        NpcAnimations edited = switch (trigger == null ? "" : trigger.trim().toLowerCase(Locale.ROOT)) {
            case "left-click", "left" -> new NpcAnimations(t, a.rightClick(), a.idle(), a.idleEvery(), a.greet(), a.greetRange(), a.greetCooldown(), a.performRange());
            case "right-click", "right" -> new NpcAnimations(a.leftClick(), t, a.idle(), a.idleEvery(), a.greet(), a.greetRange(), a.greetCooldown(), a.performRange());
            case "idle" -> new NpcAnimations(a.leftClick(), a.rightClick(), t, a.idleEvery(), a.greet(), a.greetRange(), a.greetCooldown(), a.performRange());
            case "greet" -> new NpcAnimations(a.leftClick(), a.rightClick(), a.idle(), a.idleEvery(), t, a.greetRange(), a.greetCooldown(), a.performRange());
            default -> null;
        };
        return edited == null ? null : withAnimations(edited);
    }

    public StoredNpc withLabelLines(List<String> lines) {
        return withLabel(label.withLines(lines));
    }
}
