package net.exylia.exyliaDisplays.hologram;

import net.exylia.exyliaDisplays.common.store.Position;
import net.exylia.lib.config.Comment;
import net.exylia.lib.hologram.HologramConfig;

import java.util.ArrayList;
import java.util.List;

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
        HologramConfig.Click click) {

    public StoredHologram() {
        this(true, new Position(), List.of(), "", 48.0, false,
                new HologramConfig.Properties(), new HologramConfig.Refresh(), new HologramConfig.Click());
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
        boolean personal = perPlayer || lines.stream().anyMatch(line -> PLACEHOLDER.matcher(line).find());
        return new HologramConfig(enabled, HologramConfig.Kind.TEXT, lines, "", "", personal,
                viewDistance, 0, 0, 0, appearance, refresh, click);
    }

    public StoredHologram withEnabled(boolean value) {
        return new StoredHologram(value, location, lines, permission, viewDistance, perPlayer, appearance, refresh, click);
    }

    public StoredHologram withLocation(Position value) {
        return new StoredHologram(enabled, value, lines, permission, viewDistance, perPlayer, appearance, refresh, click);
    }

    public StoredHologram withLines(List<String> value) {
        return new StoredHologram(enabled, location, value, permission, viewDistance, perPlayer, appearance, refresh, click);
    }

    public StoredHologram withPermission(String value) {
        return new StoredHologram(enabled, location, lines, value, viewDistance, perPlayer, appearance, refresh, click);
    }

    public StoredHologram withViewDistance(double value) {
        return new StoredHologram(enabled, location, lines, permission, value, perPlayer, appearance, refresh, click);
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
        return new StoredHologram(enabled, location, lines, permission, viewDistance, value, appearance, refresh, click);
    }

    public StoredHologram withAppearance(HologramConfig.Properties value) {
        return new StoredHologram(enabled, location, lines, permission, viewDistance, perPlayer, value, refresh, click);
    }

    public StoredHologram withRefresh(HologramConfig.Refresh value) {
        return new StoredHologram(enabled, location, lines, permission, viewDistance, perPlayer, appearance, value, click);
    }

    public StoredHologram withClick(HologramConfig.Click value) {
        return new StoredHologram(enabled, location, lines, permission, viewDistance, perPlayer, appearance, refresh, value);
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
}
