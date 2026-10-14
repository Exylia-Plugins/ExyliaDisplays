package net.exylia.exyliaDisplays.hologram;

import net.exylia.lib.config.Comment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code holograms} section of {@code config.yml}.
 *
 * @param spawnHeight         how far above the feet a new hologram stands
 * @param defaultViewDistance the view distance of a new hologram
 * @param nearRadius          the radius /holoadmin near uses when none is given
 * @param presets             what a new hologram can start from
 * @param linePresets         tags a line can take at once, by name
 */
public record HologramsConfig(
        @Comment("How far above your feet a hologram you create stands, in blocks.")
        double spawnHeight,

        @Comment("How far away new holograms are seen, in blocks.")
        double defaultViewDistance,

        @Comment("The radius /holoadmin near uses when none is given, in blocks.")
        double nearRadius,

        @Comment("What a hologram created from the menu can start from. Add your own:")
        @Comment("'name' is shown in the list, and %hologram% in a line becomes the new hologram's name.")
        Map<String, Preset> presets,

        @Comment("Line presets: several tags put on a line at once, from its style window or with")
        @Comment("/holo <name> line <n> preset <preset>. The preset's tags replace the line's own of the same")
        @Comment("kind; its text and its other tags stay. Add your own: name: '<tag><tag>...'")
        Map<String, String> linePresets) {

    public HologramsConfig() {
        this(2.0, 48.0, 20.0, defaultPresets(), defaultLinePresets());
    }

    public HologramsConfig {
        if (presets == null) presets = Map.of();
        if (linePresets == null) linePresets = Map.of();
        if (spawnHeight < 0) spawnHeight = 0;
        if (defaultViewDistance <= 0) defaultViewDistance = 48.0;
        if (nearRadius <= 0) nearRadius = 20.0;
    }

    /**
     * A starting point for a new hologram.
     *
     * @param name  what the list shows
     * @param lines the lines it starts with
     */
    public record Preset(String name, List<String> lines) {

        public Preset() {
            this("Simple", List.of("{primary}&l%hologram%"));
        }

        public Preset {
            if (name == null || name.isBlank()) name = "Preset";
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    private static Map<String, Preset> defaultPresets() {
        Map<String, Preset> presets = new LinkedHashMap<>();
        presets.put("simple", new Preset("Simple title", List.of(
                "<size:1.4>{primary}&l%hologram%",
                "{letters}Edit me with {highlight}/holo %hologram%")));
        // A floating item that spins and bobs over a title whose colours flow.
        presets.put("portal", new Preset("Portal sign", List.of(
                "<item:ENDER_EYE><spin:120><bob><glow:#59a4ff>",
                "<size:1.8><flow:6>{info}&lTHE END",
                "{letters}A dragon waits beyond",
                "<range:6><typewriter:30>{letters_black}Step through when ready")));
        // Two lines for two audiences: who has the permission, and who does not.
        presets.put("lounge", new Preset("Members only", List.of(
                "<size:1.6><shine:highlight>{highlight}&l✦ LOUNGE ✦",
                "<opacity:200>{letters_black}Rooftop bar · open all night",
                "<size:0.5> ",
                "<perm:!lounge.access>{error}Members only",
                "<perm:lounge.access>{success}The door is open for you")));
        // A spinning block that breathes, above a short, dimmed reminder.
        presets.put("checkpoint", new Preset("Checkpoint", List.of(
                "<block:GOLD_BLOCK><spin:60><pulse:0.05>",
                "<size:1.5>{warning}&lCHECKPOINT",
                "<opacity:170>{letters}Fall, and you start again here")));
        presets.put("welcome", new Preset("Welcome sign", List.of(
                "<size:2.4><shine>{primary}&lWELCOME",
                "{letters}to the server",
                "<size:0.5> ",
                "<range:12><typewriter>{letters_black}Read the rules at the board behind you")));
        // A news board: a live dot, a headline that takes turns and a ticker that scrolls.
        presets.put("news", new Preset("News board", List.of(
                "<size:0.9><blink:1.2>{error}● {letters}LIVE",
                "<size:1.6>{primary}&l%hologram%",
                "<frames:3>{letters}Season II is here||{letters}Double drops this weekend||{letters}Vote for rewards",
                "<gap:0.1><marquee:30,speed=5>{letters_black}Read the full notes on our Discord · Report bugs with /report ·")));
        // Stays readable from across the map: it grows with the viewer's distance.
        presets.put("landmark", new Preset("Landmark", List.of(
                "<size:2.4><autoscale:8,40,3><brightness:15>{primary}&l%hologram%",
                "<autoscale:8,40,3><seethrough><opacity:150>{letters_black}Seen from afar")));
        return presets;
    }

    private static Map<String, String> defaultLinePresets() {
        Map<String, String> presets = new LinkedHashMap<>();
        presets.put("title", "<size:1.6><gap:0.15><shine>");
        presets.put("subtitle", "<size:0.9><opacity:191>");
        presets.put("highlight", "<rainbow>");
        presets.put("subtle", "<opacity:200><fade:4>");
        presets.put("ticker", "<marquee:28,speed=5>");
        // A line of its own to click: it runs click.buttons.main.
        presets.put("button", "<gap:0.12><size:1.15><button:main>");
        // A tinted pill behind a short label.
        presets.put("badge", "<size:0.9><background:#998a51c4><shadow:false>");
        // Only readable up close, and quietly.
        presets.put("whisper", "<range:6><opacity:170>");
        // Random glyphs settle into the text when a player first sees it.
        presets.put("glitch", "<scramble:18>");
        // Shows and hides, like a recording light.
        presets.put("live", "<blink:1.2>");
        // A paragraph that wraps late, or one that wraps early and reads from the left.
        presets.put("wide", "<width:320>");
        presets.put("left", "<align:left><width:150>");
        // An item or block line that turns and floats.
        presets.put("showcase", "<spin:60><bob:0.08,period=4><glow>");
        // Grows with the viewer's distance, so it is still readable far away.
        presets.put("far", "<autoscale:6,30,2.5>");
        return presets;
    }

    /** The current settings. */
    public static HologramsConfig get() {
        return net.exylia.exyliaDisplays.config.DisplaysConfig.get().holograms();
    }
}
