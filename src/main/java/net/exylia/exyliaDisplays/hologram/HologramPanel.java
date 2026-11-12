package net.exylia.exyliaDisplays.hologram;

import net.exylia.lib.config.Comment;
import net.exylia.lib.hologram.HologramLine;
import net.exylia.lib.placeholder.Placeholders;
import net.exylia.lib.placeholder.Request;
import net.exylia.lib.text.Centering;
import net.exylia.lib.text.Text;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The {@code panel} section of a hologram: every text line drawn exactly {@link #width} pixels
 * wide, so each line's background is a bar of the same width and the hologram reads as one panel
 * instead of a stack of ragged boxes.
 *
 * <p>A line is brought to the width with spaces. Where they go is the line's {@code <fill>}
 * marks, like a spacer between columns: {@code 1. %name%<fill>%kills%} puts the name on the left
 * and the kills against the right edge. A line without one is filled on both sides (centred),
 * after it (left aligned) or before it (right aligned). A line with {@code <size:1.5>} is filled to
 * {@code width / 1.5}, so its bar is as wide as the others.
 *
 * <p>Text without placeholders is filled here, once. Text with them is measured once its values
 * are in, per viewer: each {@code <fill>} becomes {@code %exyliadisplays_fill_<id>%}, which
 * resolves the line again and answers its share of the spaces still missing.
 *
 * @param enabled whether the lines are laid out as a panel
 * @param width   how wide every line is, in pixels at size 1
 */
public record HologramPanel(
        @Comment("Draw every text line as a bar of the same width, so the backgrounds form one panel.")
        @Comment("Put <fill> where the spaces go: '1. %name%<fill>%kills%' pins the kills to the right edge.")
        @Comment("A line without <fill> is centred (or follows its alignment). Item, block and head lines are left as they are.")
        boolean enabled,

        @Comment("How wide every line is, in pixels at size 1. A letter is about 6, a bold one 7.")
        int width) {

    /** Where the spaces go, in a line laid out as a panel. */
    public static final String FILL = "<fill>";

    /** A space's advance, in pixels: the only step the padding can take. */
    private static final int SPACE = 4;

    /** What each fill placeholder measures: the line without its fills, how wide it must be, and which share it is. */
    private record Fill(String body, double target, int index, int count) {
    }

    /** Fill placeholders by id. Ids are handed out once per distinct fill, so editing a line only adds one. */
    private static final Map<String, Fill> FILLS = new ConcurrentHashMap<>();
    private static final Map<Fill, String> IDS = new ConcurrentHashMap<>();
    private static final AtomicInteger NEXT = new AtomicInteger();

    public HologramPanel() {
        this(false, 160);
    }

    public HologramPanel {
        width = Math.max(10, width);
    }

    /** Answers {@code %exyliadisplays_fill_<id>%} for the lines this lays out. Not async: it resolves other placeholders. */
    public static void register(Plugin plugin) {
        Placeholders.group(plugin, "exyliadisplays")
                .verbatim()
                .describe("Panel holograms: fill_<id>, the spaces that bring a line to the panel's width")
                .add("fill", HologramPanel::resolve)
                .register();
    }

    /** One page's lines laid out, a line without its own {@code <align>} following {@code alignment}; as they are when off. */
    public List<String> lay(List<String> lines, String alignment) {
        if (!enabled) {
            return lines;
        }
        List<String> laid = new ArrayList<>(lines.size());
        lines.forEach(line -> laid.add(lay(line, alignment)));
        return laid;
    }

    /** One line brought to the panel's width. */
    String lay(String raw, String alignment) {
        HologramLine line = HologramLine.parse(raw);
        if (line.isModel() || line.image() != null) {
            return raw;
        }
        String text = line.text();
        String tags = raw.substring(0, raw.length() - text.length());
        double size = line.size() == null || line.size() <= 0 ? 1 : line.size();
        double target = width / size;
        if (!text.contains(FILL)) {
            String align = (line.align() != null ? line.align() : alignment).toUpperCase(Locale.ROOT);
            text = switch (align) {
                case "LEFT" -> text + FILL;
                case "RIGHT" -> FILL + text;
                default -> FILL + text + FILL;
            };
        }
        // Wide enough that the client never wraps it, whatever the hologram's line-width says.
        String wrap = line.width() == null ? "<width:" + (int) Math.ceil(target + SPACE * 2) + ">" : "";
        String[] parts = text.split(FILL, -1);
        String body = String.join("", parts);
        int count = parts.length - 1;
        StringBuilder out = new StringBuilder(wrap).append(tags);
        if (!body.contains("%")) {
            int[] shares = shares(missing(body, target), count);
            for (int i = 0; i < parts.length; i++) {
                out.append(parts[i]);
                if (i < count) out.append(" ".repeat(shares[i]));
            }
            return out.toString();
        }
        for (int i = 0; i < parts.length; i++) {
            out.append(parts[i]);
            if (i < count) out.append("%exyliadisplays_fill_").append(id(new Fill(body, target, i, count))).append('%');
        }
        return out.toString();
    }

    private static String id(Fill fill) {
        return IDS.computeIfAbsent(fill, key -> {
            String id = Integer.toString(NEXT.getAndIncrement(), 36);
            FILLS.put(id, key);
            return id;
        });
    }

    private static Object resolve(Request request) {
        Fill fill = FILLS.get(request.arg(0, ""));
        if (fill == null) {
            return "";
        }
        // ponytail: the line is resolved again for every fill in it, each refresh; memoise per
        // viewer and tick if panels with many placeholders ever show in timings.
        String filled = Placeholders.apply(fill.body(), request.viewer());
        return " ".repeat(shares(missing(filled, fill.target()), fill.count())[fill.index()]);
    }

    /** How many spaces bring text to the target width; never negative. */
    static int missing(String text, double target) {
        int drawn = Centering.pixelWidth(Text.component(text));
        // ponytail: a space is 4 pixels, so a bar can be up to 3 pixels short; a 1-pixel glyph
        // in a custom font would make it exact.
        return Math.max(0, (int) Math.floor((target - drawn) / SPACE));
    }

    /** {@code spaces} split over {@code count} fills, the first ones taking what does not divide. */
    static int[] shares(int spaces, int count) {
        int[] shares = new int[count];
        for (int i = 0; i < count; i++) {
            shares[i] = spaces / count + (i < spaces % count ? 1 : 0);
        }
        return shares;
    }
}
