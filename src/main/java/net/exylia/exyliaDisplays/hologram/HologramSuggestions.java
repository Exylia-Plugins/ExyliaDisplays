package net.exylia.exyliaDisplays.hologram;

import net.exylia.exyliaDisplays.common.Completion;
import net.exylia.exyliaDisplays.common.store.Position;
import net.exylia.lib.hologram.HologramConfig;
import net.exylia.lib.hologram.HologramLine;
import net.exylia.lib.interaction.Interactions;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static net.exylia.exyliaDisplays.common.Completion.word;

/**
 * Completes every argument of {@code /holo <name> ...} from the hologram the name points at:
 * its line numbers, a line's text and tags, its pages, and each setting's current value next to
 * the values it takes.
 */
public final class HologramSuggestions implements Completion {

    /** What a line tag takes besides its current value; {@code on} is the tag's own default. */
    private static final Map<String, List<String>> TAG_VALUES = Map.ofEntries(
            Map.entry("size", List.of("0.5", "1", "1.5", "2")),
            Map.entry("opacity", List.of("255", "160", "64")),
            Map.entry("background", List.of("#66000000", "#00000000")),
            Map.entry("shadow", List.of("on", "off")),
            Map.entry("glow", List.of("team", "#8a51c4")),
            Map.entry("gap", List.of("0.3")),
            Map.entry("align", List.of("left", "center", "right")),
            Map.entry("width", List.of("120", "200")),
            Map.entry("billboard", List.of("fixed", "center", "vertical", "horizontal")),
            Map.entry("seethrough", List.of("on")),
            Map.entry("brightness", List.of("15", "0")),
            Map.entry("autoscale", List.of("on", "4,24,3")),
            Map.entry("spin", List.of("on", "90")),
            Map.entry("sway", List.of("on", "15,period=4")),
            Map.entry("bob", List.of("on", "0.08,period=3")),
            Map.entry("pulse", List.of("on", "0.06,period=2")),
            Map.entry("shine", List.of("on", "highlight", "accent", "primary", "secondary", "info", "success",
                    "highlight,speed=10,pause=2.5,width=3")),
            Map.entry("flow", List.of("on", "4")),
            Map.entry("rainbow", List.of("on", "6")),
            Map.entry("typewriter", List.of("on", "24")),
            Map.entry("scramble", List.of("on", "20")),
            Map.entry("marquee", List.of("on", "24,speed=6")),
            Map.entry("fade", List.of("on", "3")),
            Map.entry("blink", List.of("on", "1")),
            Map.entry("frames", List.of("on", "2")),
            Map.entry("range", List.of("8", "16")));
    /** Command tag names that a line writes under another name. */
    private static final Map<String, String> WRITTEN_AS = Map.of("permission", "perm", "condition", "if");
    private static final List<String> PALETTE = List.of("{primary}", "{secondary}", "{secondary_light}", "{accent}",
            "{info}", "{success}", "{highlight}");

    @Override
    public Collection<String> suggest(List<String> words, @Nullable Player player) {
        HologramModule module = HologramModule.get();
        StoredHologram hologram = module == null ? null : module.store().get(word(words, 1)).orElse(null);
        if (hologram == null) return List.of();
        return suggest(hologram, words, Completion.here(player), HologramsConfig.get().linePresets().keySet());
    }

    /**
     * What to offer after {@code /holo <name>}, read from the hologram alone.
     *
     * @param words   every word typed, the label first and the word being typed last
     * @param here    where the player stands, facing them, or {@code null} for the console
     * @param presets the line presets' names
     */
    static List<String> suggest(StoredHologram hologram, List<String> words, @Nullable Position here,
                                Collection<String> presets) {
        // "/holo <name> page <p> setline ..." edits page p; every other line command, page 1.
        int at = 2;
        int page = 0;
        if (word(words, 2).equalsIgnoreCase("page") && words.size() > 5 && Completion.number(word(words, 3)) > 0) {
            page = Completion.number(word(words, 3)) - 1;
            at = 4;
        }
        String command = word(words, at).toLowerCase(Locale.ROOT);
        List<String> args = words.subList(Math.min(at + 1, words.size()), words.size());
        int index = args.size() - 1;
        if (index < 0) return List.of();
        List<String> lines = hologram.pageView(page).lines();
        HologramConfig.Properties look = hologram.appearance();
        HologramConfig.Click click = hologram.click();
        return switch (command) {
            case "setline" -> index == 0 ? Completion.numbers(1, lines.size()) : List.of(line(lines, args.get(0)));
            case "insertline" -> index == 0 ? Completion.numbers(1, lines.size() + 1) : List.of();
            case "removeline" -> index == 0 ? Completion.numbers(1, lines.size()) : List.of();
            case "moveline" -> index <= 1 ? Completion.numbers(1, lines.size()) : List.of();
            case "line" -> switch (index) {
                case 0 -> Completion.numbers(1, lines.size());
                case 1 -> HologramCommand.TAGS;
                default -> tagValues(line(lines, args.get(0)), args.get(1), presets, click.buttons().keySet());
            };
            case "page" -> index == 0 || (index == 1 && args.get(0).equalsIgnoreCase("remove"))
                    ? Completion.numbers(1, hologram.pageCount()) : List.of();
            case "pageinterval" -> index == 0 ? List.of(String.valueOf(hologram.pageInterval()), "0") : List.of();
            case "rename" -> index == 0 ? List.of(word(words, 1)) : List.of();
            case "clone" -> index == 0 ? List.of(word(words, 1) + "-copy") : List.of();
            case "move" -> Completion.move(hologram.location(), here, index);
            case "rotate" -> switch (index) {
                case 0 -> withCurrent(number(hologram.location().yaw()),
                        here == null ? List.of("0", "90", "180", "-90") : List.of(number(here.yaw()), "0", "90", "180", "-90"));
                case 1 -> List.of(number(hologram.location().pitch()), "0");
                default -> List.of();
            };
            case "billboard" -> index == 0 ? withCurrent(look.billboard(), List.of("center", "vertical", "horizontal", "fixed")) : List.of();
            case "alignment" -> index == 0 ? withCurrent(look.alignment(), List.of("center", "left", "right")) : List.of();
            case "scale" -> switch (index) {
                case 0 -> List.of(number(look.scaleX()), "1");
                case 1 -> List.of(number(look.scaleY()));
                case 2 -> List.of(number(look.scaleZ()));
                default -> List.of();
            };
            case "linewidth" -> index == 0 ? List.of(String.valueOf(look.lineWidth()), "200") : List.of();
            case "spacing" -> index == 0 ? List.of(number(look.lineSpacing())) : List.of();
            case "opacity" -> index == 0 ? List.of(String.valueOf(look.textOpacity()), "255") : List.of();
            case "background" -> index == 0 ? withCurrent(look.defaultBackground() ? "default" : look.backgroundColor(),
                    List.of("default", "none", "#66000000")) : List.of();
            case "brightness" -> index == 0 ? List.of(String.valueOf(look.brightness()), "-1", "15") : List.of();
            case "distancescale" -> {
                HologramConfig.DistanceScale curve = look.distanceScale();
                yield List.of("on", "off", number(curve.start()) + " "
                        + number(curve.end()) + " " + number(curve.min()) + " " + number(curve.max()));
            }
            case "viewdistance" -> index == 0 ? List.of(number(hologram.viewDistance())) : List.of();
            case "permission" -> List.of(hologram.permission());
            case "refresh" -> index == 0 ? List.of(String.valueOf(hologram.refresh().updateInterval())) : List.of();
            case "attachoffset" -> index == 0 ? List.of(number(hologram.attach().offset())) : List.of();
            case "visibility" -> index == 0 ? withCurrent(hologram.visibility().mode(), List.of("all", "manual")) : List.of();
            case "click" -> index == 0 ? List.of() : click(click, args.get(0).toLowerCase(Locale.ROOT));
            case "button" -> index == 0 ? withCurrent("main", new ArrayList<>(click.buttons().keySet()))
                    : click.buttons().containsKey(args.get(0).toLowerCase(Locale.ROOT)) ? List.of("remove") : List.of();
            default -> List.of();
        };
    }

    /** {@code /holo <name> click <setting> <value>}. */
    private static List<String> click(HologramConfig.Click click, String setting) {
        return switch (setting) {
            case "add" -> Interactions.starters();
            case "remove" -> Completion.numbers(1, click.commands().size());
            case "width" -> List.of(number(click.width()));
            case "cooldown" -> List.of(String.valueOf(click.cooldown()), "0");
            case "hover" -> List.of(number(click.hoverScale()), "1");
            case "style" -> withCurrent(click.style(), List.of("button", "none"));
            case "color" -> withCurrent(click.color(), PALETTE);
            case "hovercolor" -> withCurrent(click.hoverColor(), PALETTE);
            case "sound" -> withCurrent(click.sound(), List.of("UI_BUTTON_CLICK;0.6;1.4", "none"));
            default -> List.of();
        };
    }

    /** What {@code line <n> <tag>} offers: the tag's value on that line first, then what it takes. */
    static List<String> tagValues(String raw, String tag, Collection<String> presets, Collection<String> buttons) {
        String key = tag.toLowerCase(Locale.ROOT);
        List<String> values = new ArrayList<>();
        switch (key) {
            case "text" -> values.add(HologramLine.parse(raw).text());
            case "preset" -> values.addAll(presets);
            case "button" -> {
                String current = tags(raw).get("button");
                if (current != null) values.add(current);
                values.addAll(buttons);
            }
            default -> {
                String current = tags(raw).get(WRITTEN_AS.getOrDefault(key, key));
                if (current != null) values.add(current.isEmpty() ? "on" : current);
                values.addAll(TAG_VALUES.getOrDefault(key, List.of()));
            }
        }
        if (!key.equals("text") && !key.equals("preset") && tags(raw).containsKey(WRITTEN_AS.getOrDefault(key, key))) {
            values.add("reset");
        }
        return values.stream().distinct().toList();
    }

    /** The tags a line carries, by the name it writes them under, each with its value as written. */
    static Map<String, String> tags(String raw) {
        HologramLine line = HologramLine.parse(raw);
        String written = line.raw();
        String rest = written.substring(0, written.length() - line.text().length());
        Map<String, String> tags = new HashMap<>();
        while (rest.startsWith("<")) {
            int colon = -1;
            int close = -1;
            for (int i = 1; i < rest.length(); i++) {
                char c = rest.charAt(i);
                if (c == ':' && colon < 0) {
                    colon = i;
                    if (i + 1 < rest.length() && rest.charAt(i + 1) == '"') {
                        int quote = rest.indexOf('"', i + 2);
                        if (quote < 0) break;
                        i = quote;
                    }
                } else if (c == '>') {
                    close = i;
                    break;
                }
            }
            if (close < 0) break;
            String value = colon < 0 ? "" : rest.substring(colon + 1, close);
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length() - 1);
            tags.put(rest.substring(1, colon < 0 ? close : colon).toLowerCase(Locale.ROOT), value);
            rest = rest.substring(close + 1);
        }
        return tags;
    }

    private static String line(List<String> lines, String typed) {
        int index = Completion.number(typed) - 1;
        return index >= 0 && index < lines.size() ? lines.get(index) : "";
    }

    private static List<String> withCurrent(String current, List<String> options) {
        List<String> values = new ArrayList<>();
        // Constants such as CENTER are typed in lower case; colours and sounds as they are.
        if (current != null) values.add(current.matches("[A-Z_]+") ? current.toLowerCase(Locale.ROOT) : current);
        values.addAll(options);
        return values;
    }

    private static String number(double value) {
        return HologramViews.number(value);
    }
}
