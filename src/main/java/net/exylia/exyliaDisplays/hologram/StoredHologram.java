package net.exylia.exyliaDisplays.hologram;

import net.exylia.exyliaDisplays.common.store.Position;
import net.exylia.lib.config.Comment;
import net.exylia.lib.hologram.HologramConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * One hologram as {@code holograms.yml} writes it.
 *
 * <p>The file is the single source of truth: commands, the screens and a hand edit of the file all
 * change this record, and the hologram in the world is drawn from it. The sections below the lines
 * are ExyliaLib's own hologram settings, so they read the same as in every other Exylia plugin.
 *
 * @param enabled      whether it is shown
 * @param location     where its bottom line stands
 * @param lines        its lines, top first, with their tags
 * @param permission   who sees it; empty for everyone
 * @param viewDistance how far away it is seen
 * @param perPlayer    whether each viewer gets their own placeholder values
 * @param appearance   how it is drawn
 * @param refresh      how often its placeholders refresh
 * @param click        what clicking it does
 * @param visibility   which players see it, on top of the permission
 * @param attach       the entity it rides, if any
 */
public record StoredHologram(
        @Comment("Set to false to keep it in this file without showing it.")
        boolean enabled,

        @Comment("Where the bottom line stands. Lines stack upwards from here.")
        Position location,

        @Comment("The lines, top first. Colours like {primary}, MiniMessage and %placeholders% work.")
        @Comment("Start a line with tags to style it alone, e.g. '<size:2><shine>{accent}&lTHE ARENA':")
        @Comment("  look: <size:2> <opacity:160> <background:#66000000> <shadow:false>")
        @Comment("  models: <item:DIAMOND> <block:BEACON> <glow> <glow:#8a51c4>")
        @Comment("  motion: <spin> <bob> <pulse> <typewriter> <shine> <flow>")
        @Comment("  who sees it: <range:8> <perm:vip.rank> <perm:!vip.rank> <if:\"%player_level% >= 10\">")
        List<String> lines,

        @Comment("Only players with this permission see it. Leave empty for everyone.")
        String permission,

        @Comment("How far away it is seen, in blocks.")
        double viewDistance,

        @Comment("Give every viewer their own copy, so %player_...% placeholders show their own values.")
        @Comment("Costs one set of packets per viewer, so only turn it on when the lines need it.")
        boolean perPlayer,

        @Comment("How it is drawn: billboard, scale, background, shadow, spacing...")
        HologramConfig.Properties appearance,

        @Comment("How often the placeholders refresh, in ticks (20 = one second).")
        HologramConfig.Refresh refresh,

        @Comment("What clicking it does: actions such as 'right: [SERVER] lobby' or 'console: ...'. With none it cannot be clicked.")
        HologramConfig.Click click,

        @Comment("Which players see it, on top of the permission. 'all': everyone but the players in")
        @Comment("hidden-from. 'manual': only the players in shown-to. /holo <name> show|hide edits them.")
        Visibility visibility,

        @Comment("An entity it rides, by UUID: a player is ridden again whenever they join, a mob as")
        @Comment("long as it lives. Empty to stand at its location. /holo <name> attach sets it.")
        Attachment attach) {

    public StoredHologram() {
        this(true, new Position(), List.of(), "", 48.0, false,
                new HologramConfig.Properties(), new HologramConfig.Refresh(), new HologramConfig.Click(),
                new Visibility(), new Attachment());
    }

    public StoredHologram {
        lines = lines == null ? List.of() : List.copyOf(lines);
        if (location == null) {
            location = new Position();
        }
        if (permission == null) {
            permission = "";
        }
        if (viewDistance <= 0) {
            viewDistance = 48.0;
        }
        if (appearance == null) {
            appearance = new HologramConfig.Properties();
        }
        if (refresh == null) {
            refresh = new HologramConfig.Refresh();
        }
        if (click == null) {
            click = new HologramConfig.Click();
        }
        if (visibility == null) {
            visibility = new Visibility();
        }
        if (attach == null) {
            attach = new Attachment();
        }
    }

    /** A new hologram at a place, with its first lines. */
    public static StoredHologram at(Position location, List<String> lines, double viewDistance) {
        return new StoredHologram().withLocation(location).withLines(lines).withViewDistance(viewDistance);
    }

    /** A placeholder as PlaceHolderAPI and ExyliaLib write it: {@code %identifier_value%}. */
    private static final java.util.regex.Pattern PLACEHOLDER =
            java.util.regex.Pattern.compile("%[A-Za-z0-9]+_[^%\\s]+%");

    /**
     * What ExyliaLib draws.
     *
     * <p>A hologram whose lines name a placeholder is drawn for each viewer whatever
     * {@code per-player} says: drawn once for everybody, {@code %player_name%} has nobody to be
     * about and would show as written. {@code per-player} still turns it on for lines without one.
     */
    public HologramConfig toConfig() {
        return toConfig(0);
    }

    /** {@link #toConfig()}, raised: how high above its mount an attached hologram rides. */
    public HologramConfig toConfig(double offsetY) {
        boolean personal = perPlayer || lines.stream().anyMatch(line -> PLACEHOLDER.matcher(line).find());
        return new HologramConfig(enabled, HologramConfig.Kind.TEXT, lines, "", "", personal,
                viewDistance, 0, offsetY, 0, appearance, refresh, click);
    }

    public StoredHologram withEnabled(boolean value) {
        return new StoredHologram(value, location, lines, permission, viewDistance, perPlayer, appearance, refresh, click, visibility, attach);
    }

    public StoredHologram withLocation(Position value) {
        return new StoredHologram(enabled, value, lines, permission, viewDistance, perPlayer, appearance, refresh, click, visibility, attach);
    }

    public StoredHologram withLines(List<String> value) {
        return new StoredHologram(enabled, location, value, permission, viewDistance, perPlayer, appearance, refresh, click, visibility, attach);
    }

    public StoredHologram withPermission(String value) {
        return new StoredHologram(enabled, location, lines, value, viewDistance, perPlayer, appearance, refresh, click, visibility, attach);
    }

    public StoredHologram withViewDistance(double value) {
        return new StoredHologram(enabled, location, lines, permission, value, perPlayer, appearance, refresh, click, visibility, attach);
    }

    /**
     * Faces another way. A CENTER hologram turns to every viewer and would never show it, so
     * it becomes FIXED; the other billboards keep the axes they do not turn.
     */
    public StoredHologram withFacing(float yaw, float pitch) {
        HologramConfig.Properties look = appearance;
        if (look.billboard().equalsIgnoreCase("CENTER")) {
            look = new HologramConfig.Properties("FIXED", look.alignment(), look.scaleX(), look.scaleY(), look.scaleZ(),
                    look.shadow(), look.seeThrough(), look.lineWidth(), look.lineSpacing(), look.textOpacity(),
                    look.defaultBackground(), look.backgroundColor(), look.brightness(), look.glowing());
        }
        return withLocation(location.withFacing(yaw, pitch)).withAppearance(look);
    }

    public StoredHologram withPerPlayer(boolean value) {
        return new StoredHologram(enabled, location, lines, permission, viewDistance, value, appearance, refresh, click, visibility, attach);
    }

    public StoredHologram withAppearance(HologramConfig.Properties value) {
        return new StoredHologram(enabled, location, lines, permission, viewDistance, perPlayer, value, refresh, click, visibility, attach);
    }

    public StoredHologram withRefresh(HologramConfig.Refresh value) {
        return new StoredHologram(enabled, location, lines, permission, viewDistance, perPlayer, appearance, value, click, visibility, attach);
    }

    public StoredHologram withClick(HologramConfig.Click value) {
        return new StoredHologram(enabled, location, lines, permission, viewDistance, perPlayer, appearance, refresh, value, visibility, attach);
    }

    public StoredHologram withVisibility(Visibility value) {
        return new StoredHologram(enabled, location, lines, permission, viewDistance, perPlayer, appearance, refresh, click, value, attach);
    }

    public StoredHologram withAttach(Attachment value) {
        return new StoredHologram(enabled, location, lines, permission, viewDistance, perPlayer, appearance, refresh, click, visibility, value);
    }

    // ------------------------------------------------------------------
    // Lines, by zero-based index. Callers check the index with hasLine.
    // ------------------------------------------------------------------

    public boolean hasLine(int index) {
        return index >= 0 && index < lines.size();
    }

    public StoredHologram addLine(String raw) {
        List<String> edited = new ArrayList<>(lines);
        edited.add(raw);
        return withLines(edited);
    }

    public StoredHologram insertLine(int index, String raw) {
        List<String> edited = new ArrayList<>(lines);
        edited.add(Math.clamp(index, 0, edited.size()), raw);
        return withLines(edited);
    }

    public StoredHologram setLine(int index, String raw) {
        List<String> edited = new ArrayList<>(lines);
        edited.set(index, raw);
        return withLines(edited);
    }

    /** Puts a copy of a line right below it. */
    public StoredHologram duplicateLine(int index) {
        return insertLine(index + 1, lines.get(index));
    }

    public StoredHologram removeLine(int index) {
        List<String> edited = new ArrayList<>(lines);
        edited.remove(index);
        return withLines(edited);
    }

    /** Moves a line to another place, the others closing ranks around it. */
    public StoredHologram moveLine(int from, int to) {
        List<String> edited = new ArrayList<>(lines);
        String line = edited.remove(from);
        edited.add(Math.clamp(to, 0, edited.size()), line);
        return withLines(edited);
    }

    // ------------------------------------------------------------------
    // Who sees it, and what it rides
    // ------------------------------------------------------------------

    /**
     * Which players see it, kept as UUIDs so a renamed player keeps their place. Only the
     * exceptions are written, so a hologram everybody sees costs two empty lists.
     *
     * @param mode       {@code all} or {@code manual}
     * @param shownTo    who sees it in {@code manual}
     * @param hiddenFrom who does not see it in {@code all}
     */
    public record Visibility(
            @Comment("all or manual.")
            String mode,
            @Comment("UUIDs of the players who see it while mode is manual.")
            List<String> shownTo,
            @Comment("UUIDs of the players who do not see it while mode is all.")
            List<String> hiddenFrom) {

        public static final String ALL = "all";
        public static final String MANUAL = "manual";

        public Visibility() {
            this(ALL, List.of(), List.of());
        }

        public Visibility {
            mode = MANUAL.equalsIgnoreCase(mode) ? MANUAL : ALL;
            shownTo = shownTo == null ? List.of() : List.copyOf(new LinkedHashSet<>(shownTo));
            hiddenFrom = hiddenFrom == null ? List.of() : List.copyOf(new LinkedHashSet<>(hiddenFrom));
        }

        public boolean manual() {
            return MANUAL.equals(mode);
        }

        /** The list the mode reads: who sees it in {@code manual}, who does not in {@code all}. */
        public List<String> players() {
            return manual() ? shownTo : hiddenFrom;
        }

        public boolean sees(UUID player) {
            String id = player.toString();
            return manual() ? shownTo.contains(id) : !hiddenFrom.contains(id);
        }

        public Visibility withMode(String value) {
            return new Visibility(value, shownTo, hiddenFrom);
        }

        /** Shows it to these players: added to {@code shown-to}, or taken out of {@code hidden-from}. */
        public Visibility show(Collection<UUID> players) {
            List<String> ids = players.stream().map(UUID::toString).toList();
            return manual() ? new Visibility(mode, concat(shownTo, ids), hiddenFrom)
                    : new Visibility(mode, shownTo, without(hiddenFrom, ids));
        }

        /** Hides it from these players: taken out of {@code shown-to}, or added to {@code hidden-from}. */
        public Visibility hide(Collection<UUID> players) {
            List<String> ids = players.stream().map(UUID::toString).toList();
            return manual() ? new Visibility(mode, without(shownTo, ids), hiddenFrom)
                    : new Visibility(mode, shownTo, concat(hiddenFrom, ids));
        }

        /** Empties the list the mode reads: everyone sees it in {@code all}, nobody in {@code manual}. */
        public Visibility cleared() {
            return manual() ? new Visibility(mode, List.of(), hiddenFrom) : new Visibility(mode, shownTo, List.of());
        }

        private static List<String> concat(List<String> list, List<String> added) {
            List<String> joined = new ArrayList<>(list);
            joined.addAll(added);
            return joined;
        }

        private static List<String> without(List<String> list, List<String> removed) {
            List<String> left = new ArrayList<>(list);
            left.removeAll(removed);
            return left;
        }
    }

    /**
     * The entity a hologram rides.
     *
     * @param entity the entity's UUID, empty for none
     * @param offset how high above the entity it rides, in blocks
     */
    public record Attachment(
            @Comment("The UUID of the player or entity it rides. Empty for none.")
            String entity,
            @Comment("How high above the entity it rides, in blocks.")
            double offset) {

        public static final double DEFAULT_OFFSET = 0.5;

        public Attachment() {
            this("", DEFAULT_OFFSET);
        }

        public Attachment {
            entity = entity == null ? "" : entity.trim();
        }

        /** The entity's UUID, or {@code null} when it rides nothing or the file holds a typo. */
        public UUID target() {
            try {
                return entity.isEmpty() ? null : UUID.fromString(entity);
            } catch (IllegalArgumentException typo) {
                return null;
            }
        }

        public Attachment withEntity(UUID value) {
            return new Attachment(value == null ? "" : value.toString(), offset);
        }

        public Attachment withOffset(double value) {
            return new Attachment(entity, value);
        }
    }
}
