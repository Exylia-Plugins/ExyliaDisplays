package net.exylia.exyliaDisplays.hologram.migration;

import net.exylia.exyliaDisplays.common.store.Position;
import net.exylia.exyliaDisplays.hologram.StoredHologram;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.exylia.lib.hologram.HologramConfig;
import net.exylia.lib.hologram.HologramLine;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Holograms brought over from the other hologram plugins, read from their files.
 *
 * <p>Their files are read and never changed, so trying an import costs nothing: the original
 * plugin keeps working until it is removed. Text, item and head lines, pages, their colours,
 * placeholders and animations where a line tag matches, places, view distances, permissions, click
 * actions and the display settings come across. What has no counterpart here (entity lines, a
 * line's sideways offset) is left out, with a comment where it is read.
 */
public final class ForeignImport {

    /** Where to read from. */
    public enum Source {
        DECENTHOLOGRAMS("DecentHolograms"),
        HOLOGRAPHICDISPLAYS("HolographicDisplays"),
        FANCYHOLOGRAMS("FancyHolograms");

        private final String folder;

        Source(String folder) {
            this.folder = folder;
        }

        public String folder() {
            return folder;
        }
    }

    /**
     * What an import found.
     *
     * @param holograms the holograms read, by their name there
     * @param skipped   how many could not be read
     * @param links     the NPC each hologram stood over there, by hologram name
     */
    public record Result(Map<String, StoredHologram> holograms, int skipped, Map<String, String> links) {

        public Result(Map<String, StoredHologram> holograms, int skipped) {
            this(holograms, skipped, Map.of());
        }
    }

    private ForeignImport() {
        throw new AssertionError("No instances.");
    }

    /** The other plugin's folder, next to this one's. */
    public static File folder(Plugin plugin, Source source) {
        return new File(plugin.getDataFolder().getParentFile(), source.folder());
    }

    public static Result run(Plugin plugin, Source source) {
        File folder = folder(plugin, source);
        return switch (source) {
            case DECENTHOLOGRAMS -> decent(new File(folder, "holograms"));
            case HOLOGRAPHICDISPLAYS -> holographic(new File(folder, "database.yml"));
            case FANCYHOLOGRAMS -> fancy(folder);
        };
    }

    // ------------------------------------------------------------------
    // DecentHolograms: one file per hologram, lines in pages
    // ------------------------------------------------------------------

    /** Where a line's text sits here: the space a text line and a model line take, as ExyliaLib stacks them. */
    private static final double TEXT_HEIGHT = new HologramConfig.Properties().lineSpacing();
    private static final double MODEL_HEIGHT = 0.5;

    /** DecentHolograms' animation tags: {@code <#ANIM:wave:&e,&6>text</#ANIM>}. */
    private static final Pattern DECENT_ANIMATION = Pattern.compile("[<{]#?ANIM:(\\w+)(:\\S+)?[}>](.*?)[<{]/#?ANIM[}>]");
    /** {@code <#aaaaaa>text</#bbbbbb>} and the {@code {#..}} spelling, a gradient there, as its GradientPattern reads it. */
    private static final Pattern DECENT_GRADIENT = Pattern.compile(
            "[<{]#([0-9a-fA-F]{6})[}>]((?:(?![<{]#[0-9a-fA-F]{6}[}>]).)*)[<{]/#([0-9a-fA-F]{6})[}>]");
    private static final Pattern DECENT_RAINBOW = Pattern.compile("<RAINBOW\\d{1,3}>(.*?)</RAINBOW>");
    /** {@code {#RRGGBB}}, and a bare {@code #RRGGBB}, which DecentHolograms reads as a colour too. */
    private static final Pattern DECENT_HEX = Pattern.compile("\\{#([0-9a-fA-F]{6})}|(?<![&§<{:/#\\w])#([0-9a-fA-F]{6})");
    /** DecentHolograms' own symbols, used when its config.yml has none. */
    private static final Map<String, String> DECENT_SYMBOLS = Map.of("[x]", "█", "[X]", "█", "[/]", "▌",
            "[,]", "░", "[,,]", "▒", "[,,,]", "▓", "[p]", "•", "[P]", "•", "[|]", "⎹");

    /**
     * One DecentHolograms page as this plugin draws it.
     *
     * @param lines  the lines, top first, with their tags
     * @param height the room the lines take there, in blocks
     * @param click  the page's click actions as interaction lines
     */
    record DecentPage(List<String> lines, double height, List<String> click) {
    }

    /**
     * Every hologram in {@code holograms/}, plus the display holograms of 2.10 in {@code displays/}
     * next to it. Every page comes across; the place follows the first.
     */
    static Result decent(File directory) {
        File folder = directory.getParentFile();
        YamlConfiguration config = optional(new File(folder, "config.yml"));
        Map<String, StoredHologram> found = new LinkedHashMap<>();
        int skipped = 0;
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml"));
        for (File file : files == null ? new File[0] : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            Position at = colonLocation(yaml.getString("location"));
            Set<String> flags = upper(yaml.getStringList("flags"));
            List<Map<?, ?>> pages = new ArrayList<>(yaml.getMapList("pages"));
            // Before pages there was a 'lines' section, one numbered entry per line.
            if (pages.isEmpty() && yaml.isConfigurationSection("lines")) {
                pages.add(Map.of("lines", yaml.getConfigurationSection("lines").getValues(false).values().stream()
                        .map(line -> line instanceof ConfigurationSection section ? section.getValues(false) : line).toList()));
            }
            List<DecentPage> read = new ArrayList<>();
            for (int i = 0; i < pages.size(); i++) read.add(decentPage(pages.get(i), flags, config, i + 1, pages.size()));
            DecentPage page = read.isEmpty() ? null : read.get(0);
            if (at == null || page == null || page.lines().isEmpty()) {
                skipped++;
                continue;
            }
            // There the location is the top line's, or the bottom's with down-origin; here it is
            // where the bottom line stands, so the top lines up with where it was.
            double top = at.y() + (yaml.getBoolean("down-origin", config.getBoolean("defaults.down-origin")) ? page.height() : 0);
            at = at.withFacing((float) yaml.getDouble("facing", 0), 0f);
            StoredHologram hologram = StoredHologram.at(at.plus(0, top - at.y() - height(page.lines()), 0), page.lines(),
                            yaml.getDouble("display-range", config.getDouble("defaults.display-range", 48)))
                    .withEnabled(yaml.getBoolean("enabled", true))
                    .withPermission(yaml.getString("permission", ""))
                    .withRefresh(new HologramConfig.Refresh(
                            Math.max(1, yaml.getLong("update-interval", config.getLong("defaults.update-interval", 20))),
                            !flags.contains("DISABLE_UPDATING")));
            // update-range has no counterpart: every viewer in range is refreshed here.
            boolean clicks = !flags.contains("DISABLE_ACTIONS");
            if (read.stream().anyMatch(each -> !each.click().isEmpty()) && clicks) {
                hologram = hologram.withClick(new HologramConfig.Click(page.click(), 1.5,
                        Math.max(1, config.getLong("click-cooldown", 1)) * 50));
            }
            hologram = hologram.withPages(read.subList(1, read.size()).stream()
                    .map(each -> new HologramConfig.Page(each.lines(), clicks ? each.click() : List.of())).toList());
            found.put(file.getName().substring(0, file.getName().length() - 4), hologram);
        }
        Result displays = decentDisplays(new File(folder, "displays"), config);
        displays.holograms().forEach((name, hologram) -> found.put(found.containsKey(name) ? name + "-display" : name, hologram));
        return new Result(found, skipped + displays.skipped());
    }

    /**
     * One page as DecentHolograms saves it: {@code lines} (content, height, permission, offsetX,
     * offsetZ, facing, flags) and {@code actions} by click type. {@code {page}} and {@code {pages}}
     * are this page's number and the count: each page keeps its own lines here, so they are fixed.
     */
    static DecentPage decentPage(Map<?, ?> page, Set<String> hologramFlags, ConfigurationSection config,
                                 int number, int count) {
        Map<String, String> symbols = symbols(config);
        List<String> lines = new ArrayList<>();
        double height = 0;
        if (page.get("lines") instanceof List<?> list) {
            for (Object entry : list) {
                if (!(entry instanceof Map<?, ?> line)) continue;
                String content = String.valueOf(line.get("content") == null ? "Blank Line" : line.get("content"));
                Set<String> flags = new java.util.HashSet<>(hologramFlags);
                if (line.get("flags") instanceof List<?> own) flags.addAll(upper(own.stream().map(String::valueOf).toList()));
                String raw = decentLine(content.replace("{page}", String.valueOf(number))
                        .replace("{pages}", String.valueOf(count)), !flags.contains("DISABLE_ANIMATIONS"), symbols);
                String type = decentType(content);
                double fallback = config.getDouble("defaults.height." + type, switch (type) {
                    case "icon", "smallhead" -> 0.6;
                    case "head" -> 0.75;
                    default -> 0.3;
                });
                double lineHeight = line.get("height") instanceof Number written ? written.doubleValue() : fallback;
                height += lineHeight;
                HologramLine.Builder built = HologramLine.parse(raw).toBuilder();
                if (line.get("permission") instanceof String permission && !permission.isBlank()) built.permission(permission);
                // A line's own height only has a counterpart on an empty line, a gap: there it becomes its size.
                // On a line with text, size would scale the text too, so the line keeps the usual gap.
                // offsetX/offsetZ, a line's own facing and DISABLE_PLACEHOLDERS have none and are left out.
                HologramLine read = built.build();
                if (read.text().isBlank() && !read.isModel() && Math.abs(lineHeight - fallback) > 1e-6) {
                    built.size(Math.max(0.01, lineHeight / config.getDouble("defaults.height.text", 0.3)));
                }
                lines.add(built.build().raw());
            }
        }
        List<String> click = new ArrayList<>();
        if (page.get("actions") instanceof Map<?, ?> actions) {
            for (String type : List.of("LEFT", "RIGHT", "SHIFT_LEFT", "SHIFT_RIGHT")) {
                if (!(actions.get(type) instanceof List<?> list)) continue;
                for (Object action : list) {
                    String line = decentAction(String.valueOf(action));
                    // PERMISSION stops the rest for a player without it; nothing here can, so the
                    // rest is left out rather than run for everyone.
                    if (line == null) break;
                    if (!line.isEmpty()) click.add(type.toLowerCase(Locale.ROOT) + ": " + line);
                }
            }
        }
        return new DecentPage(lines, height, click);
    }

    /** The key of a line's default height in DecentHolograms' config.yml. */
    private static String decentType(String content) {
        String upper = content.trim().toUpperCase(Locale.ROOT);
        if (upper.startsWith("#ICON:")) return "icon";
        if (upper.startsWith("#SMALLHEAD:")) return "smallhead";
        if (upper.startsWith("#HEAD:")) return "head";
        return "text";
    }

    /**
     * One click action, {@code TYPE:data}, as an interaction line: empty when it does nothing
     * here, {@code null} for a PERMISSION check, after which nothing may run.
     */
    static String decentAction(String written) {
        String[] parts = written.split(":", 2);
        String data = parts.length > 1 ? parts[1].trim().replace("{player}", "%player_name%") : "";
        return switch (parts[0].trim().toUpperCase(Locale.ROOT)) {
            case "MESSAGE" -> "[MESSAGE] " + decentColours(data);
            // COMMAND is chatted as the player: '/spawn' runs it.
            case "COMMAND" -> data.replaceFirst("^/", "");
            case "CONSOLE" -> "console: " + data.replaceFirst("^/", "");
            case "CONNECT" -> "[SERVER] " + data;
            case "TELEPORT" -> {
                String[] at = data.replace(",", ".").split(":");
                // Without a world it is the player's own.
                String world = at.length == 3 || at.length == 5 ? "%player_world%;" : "";
                yield at.length >= 3 ? "[TELEPORT] " + world + String.join(";", at) : "";
            }
            case "SOUND" -> "[SOUND] " + data.replace(':', ';');
            case "PERMISSION" -> null;
            case "NEXT_PAGE" -> "[PAGE] next";
            case "PREV_PAGE" -> "[PAGE] previous";
            // PAGE:<hologram>:<n>, or PAGE:<n> on its own hologram.
            case "PAGE" -> {
                String page = data.substring(data.lastIndexOf(':') + 1).trim();
                yield page.matches("\\d+") ? "[PAGE] " + page : "";
            }
            // NONE does nothing, and an action another plugin registered there has no counterpart.
            default -> "";
        };
    }

    /** DecentHolograms' custom-replacements, or its own defaults. */
    private static Map<String, String> symbols(ConfigurationSection config) {
        ConfigurationSection custom = config.getConfigurationSection("custom-replacements");
        if (custom == null) return DECENT_SYMBOLS;
        Map<String, String> symbols = new LinkedHashMap<>();
        custom.getKeys(false).forEach(key -> symbols.put(key, custom.getString(key, "")));
        return symbols;
    }

    /** {@code #ICON: DIAMOND} and friends into tags, and DecentHolograms' colours into MiniMessage. */
    static String decentLine(String content) {
        return decentLine(content, true, DECENT_SYMBOLS);
    }

    static String decentLine(String content, boolean animations, Map<String, String> symbols) {
        String trimmed = content.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (upper.startsWith("#ICON:")) {
            return "<item:" + material(itemOnly(trimmed.substring(6))) + ">";
        }
        if (upper.startsWith("#SMALLHEAD:")) {
            return decentHead(trimmed.substring(11), true);
        }
        if (upper.startsWith("#HEAD:")) {
            return decentHead(trimmed.substring(6), false);
        }
        if (upper.startsWith("#ENTITY:")) {
            // A mob standing in the hologram has no counterpart: its place stays, empty.
            return "";
        }
        String text = content;
        for (Map.Entry<String, String> symbol : symbols.entrySet()) text = text.replace(symbol.getKey(), symbol.getValue());
        text = text.replace("{player}", "%player_name%");
        HologramLine.Builder tags = HologramLine.builder();
        if (text.contains("&u")) {
            text = text.replace("&u", "<rainbow>");
            if (animations) tags.flow(HologramLine.DEFAULT_FLOW);
        }
        Matcher animation = DECENT_ANIMATION.matcher(text);
        StringBuilder out = new StringBuilder();
        while (animation.find()) {
            String inner = animation.group(3);
            String replacement = inner;
            if (animations) {
                // A line moves as a whole here, so the animation of a part moves the line.
                switch (animation.group(1).toLowerCase(Locale.ROOT)) {
                    case "typewriter" -> tags.typewriter(HologramLine.DEFAULT_TYPEWRITER);
                    case "wave", "burn" -> tags.shine("");
                    case "colors" -> {
                        replacement = "<rainbow>" + inner + "</rainbow>";
                        tags.flow(HologramLine.DEFAULT_FLOW);
                    }
                    // scroll and custom animations from its animations folder have no counterpart.
                    default -> { }
                }
            }
            animation.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        animation.appendTail(out);
        String coloured = decentColours(out.toString());
        HologramLine line = tags.build();
        return line.hasTags() ? line.toBuilder().text(coloured).build().raw() : coloured;
    }

    /** A settings file that may not be there; Bukkit's loader reports a missing one as an error. */
    private static YamlConfiguration optional(File file) {
        return file.isFile() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    /** DecentHolograms' gradient, rainbow and hex spellings into MiniMessage; {@code &#RRGGBB} already works. */
    static String decentColours(String text) {
        String out = DECENT_GRADIENT.matcher(text).replaceAll("<gradient:#$1:#$3>$2</gradient>");
        out = DECENT_RAINBOW.matcher(out).replaceAll("<rainbow>$1</rainbow>");
        Matcher hex = DECENT_HEX.matcher(out.replace("§#", "&#"));
        return hex.replaceAll(match -> "<#" + (match.group(1) != null ? match.group(1) : match.group(2)) + ">");
    }

    /**
     * A {@code #HEAD:} or {@code #SMALLHEAD:} line (an armour stand's small head, half a full one):
     * {@code PLAYER_HEAD (owner, texture or URL)} becomes {@code <head:owner>}, any other item is worn
     * as it is. A head drawn per viewer ({@code {player}}, a placeholder) or from HeadDatabase has
     * nothing to carry over and stays a plain head.
     */
    static String decentHead(String content, boolean small) {
        int open = content.indexOf('(');
        int close = content.lastIndexOf(')');
        String owner = open > 0 && close > open ? content.substring(open + 1, close).trim() : "";
        String material = material(itemOnly(content));
        boolean head = (material.contains("HEAD") || material.contains("SKULL")) && !owner.isEmpty()
                && !owner.contains("{") && !owner.contains("%") && !owner.toUpperCase(Locale.ROOT).startsWith("HEADDATABASE_");
        HologramLine.Builder line = HologramLine.builder();
        if (head) line.head(owner);
        else line.item(material);
        if (small) line.size(0.5);
        return line.build().raw();
    }

    /** An item as DecentHolograms and HolographicDisplays write it, without its (extras), {nbt} and !ENCHANTED. */
    private static String itemOnly(String written) {
        String out = written.replace("!ENCHANTED", "");
        int open = out.indexOf('(');
        int close = out.lastIndexOf(')');
        if (open > 0 && close > open) out = out.substring(0, open) + out.substring(close + 1);
        int brace = out.indexOf('{');
        return brace > 0 ? out.substring(0, brace) : out;
    }

    /**
     * The display holograms DecentHolograms 2.10 added: {@code displays/<name>.yml} with a type,
     * a location, settings and attributes, each {@code value-type} plus {@code value}. An
     * attribute the file leaves out takes the server's {@code attribute-defaults.yml}.
     */
    static Result decentDisplays(File directory, ConfigurationSection config) {
        Map<String, StoredHologram> found = new LinkedHashMap<>();
        int skipped = 0;
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml"));
        YamlConfiguration defaults = optional(new File(directory.getParentFile(), "attribute-defaults.yml"));
        Map<String, String> symbols = symbols(config);
        for (File file : files == null ? new File[0] : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            String type = yaml.getString("type", "TEXT").toUpperCase(Locale.ROOT);
            Position at = sectionLocation(yaml.getConfigurationSection("location"));
            List<Map<?, ?>> pages = yaml.getMapList("pages");
            List<String> text = new ArrayList<>();
            if (!pages.isEmpty() && pages.get(0).get("lines") instanceof List<?> lines) {
                lines.forEach(line -> text.add(decentLine(String.valueOf(line)
                        .replace("{page}", "1").replace("{pages}", "1"), true, symbols)));
            }
            String material = switch (type) {
                case "ITEM" -> yaml.getString("item");
                case "BLOCK" -> yaml.getString("block");
                default -> "";
            };
            if (at == null || material == null || ("TEXT".equals(type) && text.isEmpty())) {
                skipped++;
                continue;
            }
            java.util.function.Function<String, Object> attribute = key -> {
                if (yaml.isConfigurationSection("attributes." + key)) return yaml.get("attributes." + key + ".value");
                String path = "display." + type + "." + key;
                return defaults.getBoolean(path + ".enabled", false) ? defaults.get(path + ".value") : null;
            };
            ConfigurationSection scale = section(attribute.apply("scale"));
            ConfigurationSection translation = section(attribute.apply("translation"));
            ConfigurationSection light = section(attribute.apply("brightness"));
            Object yaw = attribute.apply("yaw");
            Object pitch = attribute.apply("pitch");
            Object background = attribute.apply("background-color");
            Object width = attribute.apply("line-width");
            Object opacity = attribute.apply("text-opacity");
            Fancy display = new Fancy(
                    at.plus(sectionNumber(translation, "x", 0), sectionNumber(translation, "y", 0), sectionNumber(translation, "z", 0))
                            .withFacing(yaw instanceof Number n ? n.floatValue() : at.yaw(), pitch instanceof Number n ? n.floatValue() : at.pitch()),
                    type, text, material(material), rgba(section(attribute.apply("glow-color")), false),
                    String.valueOf(Objects.requireNonNullElse(attribute.apply("billboard"), "TEXT".equals(type) ? "CENTER" : "FIXED")).toUpperCase(Locale.ROOT),
                    String.valueOf(Objects.requireNonNullElse(attribute.apply("alignment"), "CENTER")).toUpperCase(Locale.ROOT),
                    sectionNumber(scale, "x", 1), sectionNumber(scale, "y", 1), sectionNumber(scale, "z", 1),
                    Boolean.TRUE.equals(attribute.apply("text-shadow")), Boolean.TRUE.equals(attribute.apply("see-through")),
                    // Without the defaults file its own default is a quarter-black background.
                    background == null && !defaults.contains("display") ? "#40000000" : rgba(section(background), true),
                    opacity instanceof Number n ? n.intValue() : 255,
                    light == null ? -1 : (int) Math.clamp(Math.max(sectionNumber(light, "block-light", 0), sectionNumber(light, "sky-light", 0)), 0, 15),
                    yaml.getDouble("settings.display-range", 256), false,
                    yaml.getLong("settings.update-interval", 20) * 50, "",
                    width instanceof Number n ? n.intValue() : 300);
            // shadow-radius and shadow-strength (the entity's shadow on the ground), and an item's
            // display-type, enchanted, leather-color and skull-texture have no counterpart here.
            StoredHologram hologram = display.toStored(file.getName());
            if (hologram == null) {
                skipped++;
                continue;
            }
            found.put(file.getName().substring(0, file.getName().length() - 4),
                    hologram.withEnabled(yaml.getBoolean("settings.enabled", true)));
        }
        return new Result(found, skipped);
    }

    /** A DecentHolograms rgba value, as {@code #AARRGGBB} or {@code #RRGGBB}; {@code null} for none or an animated one. */
    private static String rgba(ConfigurationSection value, boolean alpha) {
        if (value == null || !value.contains("red")) return null;
        long colour = (long) value.getInt("alpha", 255) << 24 | value.getInt("red") << 16 | value.getInt("green") << 8 | value.getInt("blue");
        return alpha ? argb(colour) : String.format("#%06X", colour & 0xFFFFFF);
    }

    private static ConfigurationSection section(Object value) {
        return value instanceof ConfigurationSection section ? section : null;
    }

    private static double sectionNumber(ConfigurationSection section, String key, double otherwise) {
        return section == null ? otherwise : section.getDouble(key, otherwise);
    }

    private static Set<String> upper(List<String> values) {
        Set<String> out = new java.util.HashSet<>();
        values.forEach(value -> out.add(value.trim().toUpperCase(Locale.ROOT)));
        return out;
    }

    /** The room lines take here, as ExyliaLib stacks them. */
    static double height(List<String> lines) {
        double total = 0;
        for (String raw : lines) {
            HologramLine line = HologramLine.parse(raw);
            double size = line.size() == null ? 1 : line.size();
            total += (line.isModel() ? MODEL_HEIGHT : TEXT_HEIGHT) * size;
        }
        return total;
    }

    // ------------------------------------------------------------------
    // HolographicDisplays: database.yml, one section per hologram
    // ------------------------------------------------------------------

    /** {@code {name}} and {@code {name: argument}}, optionally {@code {plugin/name}}; {@code \{} is a brace. */
    private static final Pattern HOLOGRAPHIC_PLACEHOLDER =
            Pattern.compile("(?<!\\\\)\\{(?:[^{}:/\\s]+/)?([A-Za-z_]+)(?:\\s*:\\s*([^{}]*?))?\\s*}");

    /**
     * Its database.yml, version 2 or 3, with its custom-placeholders.yml (symbols.yml in version 2)
     * and the view range, time format and pinged servers of its config.yml.
     */
    static Result holographic(File file) {
        Map<String, StoredHologram> found = new LinkedHashMap<>();
        int skipped = 0;
        if (!file.isFile()) {
            return new Result(found, 0);
        }
        File folder = file.getParentFile();
        YamlConfiguration config = optional(new File(folder, "config.yml"));
        Holographic context = new Holographic(holographicSymbols(folder), new LinkedHashMap<>(),
                config.getString("time.format", "H:mm"), new File(folder, "animations"));
        for (String server : config.getStringList("bungee.pinger.servers")) {
            String[] parts = server.split(":", 2);
            if (parts.length == 2) context.servers().put(parts[0].trim(), parts[1].trim());
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String name : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(name);
            if (section == null) {
                skipped++;
                continue;
            }
            // Version 3 writes a position section; version 2 a 'world, x, y, z' string.
            Position at = section.isConfigurationSection("position")
                    ? sectionLocation(section.getConfigurationSection("position"))
                    : commaLocation(section.getString("location"));
            List<String> lines = section.getStringList("lines").stream().map(line -> holographicLine(line, context)).toList();
            if (at == null || lines.isEmpty()) {
                skipped++;
                continue;
            }
            // HolographicDisplays hangs its lines down from the position; here they stand on it.
            // Its space-between-lines setting is left out: lines keep this plugin's spacing.
            found.put(name, StoredHologram.at(at.plus(0, -height(lines), 0), lines, config.getDouble("holograms-view-range", 48)));
        }
        return new Result(found, skipped);
    }

    /**
     * What a HolographicDisplays line needs besides itself.
     *
     * @param symbols    its static replacements
     * @param servers    the pinged servers' addresses, by name
     * @param timeFormat the {time} format
     * @param animations the folder of its animation files
     */
    record Holographic(Map<String, String> symbols, Map<String, String> servers, String timeFormat, File animations) {
    }

    private static Map<String, String> holographicSymbols(File folder) {
        Map<String, String> symbols = new LinkedHashMap<>();
        ConfigurationSection custom = optional(new File(folder, "custom-placeholders.yml"))
                .getConfigurationSection("placeholders");
        if (custom != null) {
            custom.getKeys(false).forEach(key -> symbols.put(key, custom.getString(key, "")));
            return symbols;
        }
        // Version 2: one 'target: replacement' per line; version 3 moves them on its first start.
        try {
            for (String line : java.nio.file.Files.readAllLines(new File(folder, "symbols.yml").toPath())) {
                String[] parts = line.split(":", 2);
                if (!line.startsWith("#") && parts.length == 2 && !parts[0].isBlank()) {
                    symbols.put(parts[0].trim(), parts[1].trim());
                }
            }
        } catch (java.io.IOException none) {
            // No symbols file: nothing to replace.
        }
        return symbols;
    }

    static String holographicLine(String line) {
        return holographicLine(line, new Holographic(Map.of(), Map.of(), "H:mm", new File("animations")));
    }

    static String holographicLine(String line, Holographic context) {
        String trimmed = line.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("icon:")) {
            return "<item:" + material(itemOnly(trimmed.substring(5))) + ">";
        }
        String text = line;
        for (Map.Entry<String, String> symbol : context.symbols().entrySet()) text = text.replace(symbol.getKey(), symbol.getValue());
        text = text.replace("&u", "{rainbow}");
        boolean[] rainbow = {false};
        Matcher placeholder = HOLOGRAPHIC_PLACEHOLDER.matcher(text);
        String converted = placeholder.replaceAll(match -> {
            String argument = match.group(2) == null ? null : match.group(2).trim();
            // A list of servers or worlds is summed there; here the first one stands for it.
            String first = argument == null ? null : argument.split(",")[0].trim();
            String address = first == null ? null : context.servers().getOrDefault(first, first);
            String out = switch (match.group(1).toLowerCase(Locale.ROOT)) {
                case "empty", "slowest", "slow", "medium", "fast", "fastest" -> "";
                case "rainbow" -> {
                    rainbow[0] = true;
                    yield "<rainbow>";
                }
                case "time" -> "%server_time_" + context.timeFormat() + "%";
                case "player" -> "%player_name%";
                case "displayname" -> "%player_displayname%";
                case "papi" -> argument == null ? "" : "%" + argument + "%";
                case "online" -> first == null ? "%server_online%" : "%bungee_" + first + "%";
                case "max_players" -> first == null ? "%server_max_players%" : "%pinger_max_" + address + "%";
                case "status" -> first == null ? "" : "%pinger_isonline_" + address + "%";
                case "motd", "motd2" -> first == null ? "" : "%pinger_motd_" + address + "%";
                case "world" -> first == null ? "" : "%world_players_" + first + "%";
                case "animation" -> argument == null ? "" : holographicFrame(new File(context.animations(), argument));
                // Another plugin's placeholder stays as written.
                default -> match.group();
            };
            return Matcher.quoteReplacement(out);
        }).replace("\\{", "{").replace("\\}", "}");
        if (converted.isBlank()) return " ";
        // The rainbow cycles there; here its colours flow along the line.
        return rainbow[0] ? HologramLine.builder().flow(HologramLine.DEFAULT_FLOW).text(converted).build().raw() : converted;
    }

    /** The first frame of an animation: animation-frames in version 3, the lines after 'speed:' in version 2. */
    private static String holographicFrame(File file) {
        if (!file.isFile()) return "";
        List<String> frames = YamlConfiguration.loadConfiguration(file).getStringList("animation-frames");
        if (frames.isEmpty()) {
            try {
                frames = java.nio.file.Files.readAllLines(file.toPath()).stream()
                        .filter(line -> !line.toLowerCase(Locale.ROOT).startsWith("speed:")).toList();
            } catch (java.io.IOException unreadable) {
                return "";
            }
        }
        return frames.isEmpty() ? "" : frames.get(0);
    }
    // ------------------------------------------------------------------
    // FancyHolograms: holograms.yml, already display entities
    // ------------------------------------------------------------------

    /** Where FancyHolograms wraps a line: one display holds every line there, and it never wraps them. */
    private static final int FANCY_LINE_WIDTH = 1000;

    /** Both of FancyHolograms' formats: holograms.yml up to 2.x, then one JSON file per group from 3.x. */
    static Result fancy(File folder) {
        Result yaml = fancyYaml(new File(folder, "holograms.yml"));
        Result json = fancyJson(new File(folder, "data/holograms"));
        Map<String, StoredHologram> found = new LinkedHashMap<>(yaml.holograms());
        found.putAll(json.holograms());
        fancyPages(found, new File(folder, "data/traits/multiple_pages_trait"));
        Map<String, String> links = new LinkedHashMap<>(yaml.links());
        links.putAll(json.links());
        return new Result(found, yaml.skipped() + json.skipped(), links);
    }

    /**
     * One display-entity hologram: FancyHolograms', whichever file it came from, or a DecentHolograms display.
     *
     * @param background {@code #AARRGGBB}, or {@code null} for the client's own background
     * @param glow       the outline of an item or block, {@code #RRGGBB}, or {@code null}
     * @param brightness a fixed light level, or -1 for the light where it stands
     * @param lineWidth  where a long line wraps, in pixels
     */
    private record Fancy(Position at, String type, List<String> text, String material, String glow,
                         String billboard, String alignment, double scaleX, double scaleY, double scaleZ,
                         boolean shadow, boolean seeThrough, String background, int opacity, int brightness,
                         double distance, boolean permission, long intervalMillis, String npc, int lineWidth) {

        /** The hologram here, or {@code null} when it has nothing to show. */
        StoredHologram toStored(String name) {
            List<String> lines = switch (type) {
                case "ITEM" -> List.of(HologramLine.builder().item(material).glow(glow).build().raw());
                case "BLOCK" -> List.of(HologramLine.builder().block(material).glow(glow).build().raw());
                default -> text;
            };
            if (lines.isEmpty()) return null;
            HologramConfig.Properties base = new HologramConfig.Properties();
            HologramConfig.Properties look = new HologramConfig.Properties(billboard, alignment, scaleX, scaleY, scaleZ,
                    shadow, seeThrough, lineWidth,
                    // One FancyHolograms display holds every line, so its lines grow with its scale;
                    // here each line is its own display and the gap has to grow with them.
                    base.lineSpacing() * Math.max(0.1, scaleY), opacity,
                    background == null, background == null ? "#00000000" : background, brightness, false);
            StoredHologram hologram = StoredHologram.at(at, lines, distance > 0 ? distance : 48).withAppearance(look);
            if (permission) hologram = hologram.withPermission("fancyholograms.viewhologram." + name);
            // Milliseconds there, ticks here.
            if (intervalMillis > 0) hologram = hologram.withRefresh(new HologramConfig.Refresh(Math.max(1, intervalMillis / 50), true));
            return hologram;
        }
    }

    static Result fancyYaml(File file) {
        Map<String, StoredHologram> found = new LinkedHashMap<>();
        Map<String, String> links = new LinkedHashMap<>();
        int skipped = 0;
        ConfigurationSection all = file.isFile()
                ? YamlConfiguration.loadConfiguration(file).getConfigurationSection("holograms") : null;
        if (all == null) {
            return new Result(found, 0);
        }
        for (String name : all.getKeys(false)) {
            ConfigurationSection section = all.getConfigurationSection(name);
            Position at = section == null ? null : sectionLocation(section.getConfigurationSection("location"));
            if (at == null) {
                skipped++;
                continue;
            }
            String type = section.getString("type", "TEXT").toUpperCase(Locale.ROOT);
            String material = switch (type) {
                case "ITEM" -> fancyItem(section.get("item"));
                case "BLOCK" -> material(section.getString("block", "GRASS_BLOCK"));
                default -> "";
            };
            int block = Math.min(15, section.getInt("block_brightness", -1));
            int sky = Math.min(15, section.getInt("sky_brightness", -1));
            String visibility = section.getString("visibility");
            Fancy fancy = new Fancy(
                    at.plus(section.getDouble("translation_x"), section.getDouble("translation_y"), section.getDouble("translation_z")),
                    type, section.getStringList("text"), material, null,
                    section.getString("billboard", "CENTER").toUpperCase(Locale.ROOT),
                    section.getString("text_alignment", "CENTER").toUpperCase(Locale.ROOT),
                    section.getDouble("scale_x", 1), section.getDouble("scale_y", 1), section.getDouble("scale_z", 1),
                    section.getBoolean("text_shadow", false), section.getBoolean("see_through", false),
                    fancyYamlBackground(section.getString("background")), 255,
                    block < 0 && sky < 0 ? -1 : Math.max(block, sky),
                    section.getDouble("visibility_distance", -1),
                    visibility == null ? !section.getBoolean("visible_by_default", true)
                            : "PERMISSION_REQUIRED".equalsIgnoreCase(visibility),
                    section.getLong("update_text_interval", -1), section.getString("linkedNpc", ""), FANCY_LINE_WIDTH);
            StoredHologram hologram = fancy.toStored(name);
            if (hologram == null) {
                skipped++;
                continue;
            }
            found.put(name, hologram);
            if (!fancy.npc().isBlank()) links.put(name, fancy.npc());
        }
        return new Result(found, skipped, links);
    }

    static Result fancyJson(File directory) {
        Map<String, StoredHologram> found = new LinkedHashMap<>();
        Map<String, String> links = new LinkedHashMap<>();
        int skipped = 0;
        if (!directory.isDirectory()) {
            return new Result(found, 0);
        }
        List<java.nio.file.Path> files;
        try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(directory.toPath())) {
            files = walk.filter(path -> path.toString().endsWith(".json")).sorted().toList();
        } catch (java.io.IOException unreadable) {
            return new Result(found, 0);
        }
        for (java.nio.file.Path path : files) {
            JsonArray entries;
            try {
                entries = JsonParser.parseString(java.nio.file.Files.readString(path)).getAsJsonArray();
            } catch (Exception unreadable) {
                skipped++;
                continue;
            }
            for (JsonElement element : entries) {
                try {
                    JsonObject union = element.getAsJsonObject();
                    JsonObject hologram = object(union, "hologram_data");
                    JsonObject display = object(union, "display_data");
                    JsonObject text = object(union, "text_data");
                    JsonObject location = object(hologram, "location");
                    String name = string(hologram, "name", "");
                    if (name.isEmpty()) {
                        skipped++;
                        continue;
                    }
                    String type = string(hologram, "type", "TEXT").toUpperCase(Locale.ROOT);
                    String material = switch (type) {
                        case "ITEM" -> fancyItemBytes(string(object(union, "item_data"), "item", ""));
                        case "BLOCK" -> material(string(object(union, "block_data"), "block_material", "GRASS_BLOCK"));
                        default -> "";
                    };
                    JsonObject scale = object(display, "scale");
                    JsonObject translation = object(display, "translation");
                    JsonObject light = object(display, "brightness");
                    List<String> lines = new ArrayList<>();
                    if (text.get("text") instanceof JsonArray array) array.forEach(line -> lines.add(line.getAsString()));
                    String background = string(text, "background_color", "");
                    Fancy fancy = new Fancy(
                            new Position(string(hologram, "world_name", string(location, "world", "world")),
                                    number(location, "x", 0) + number(translation, "x", 0),
                                    number(location, "y", 0) + number(translation, "y", 0),
                                    number(location, "z", 0) + number(translation, "z", 0),
                                    (float) number(location, "yaw", 0), (float) number(location, "pitch", 0)),
                            type, lines, material, glow(string(display, "glowing_color", "")),
                            string(display, "billboard", "CENTER").toUpperCase(Locale.ROOT),
                            string(text, "text_alignment", "CENTER").toUpperCase(Locale.ROOT),
                            number(scale, "x", 1), number(scale, "y", 1), number(scale, "z", 1),
                            bool(text, "text_shadow"), bool(text, "see_through"),
                            background.isEmpty() ? null : argb(Long.parseLong(background.substring(1), 16)),
                            (int) number(text, "text_opacity", 255),
                            light.has("sky_light") ? (int) Math.clamp(Math.max(number(light, "block_light", 0),
                                    number(light, "sky_light", 0)), 0, 15) : -1,
                            number(hologram, "visibility_distance", -1),
                            "PERMISSION_REQUIRED".equalsIgnoreCase(string(hologram, "visibility", "")),
                            (long) number(text, "text_update_interval", -1), string(hologram, "linked_npc_name", ""),
                            FANCY_LINE_WIDTH);
                    StoredHologram stored = fancy.toStored(name);
                    if (stored == null) {
                        skipped++;
                        continue;
                    }
                    found.put(name, stored);
                    if (!fancy.npc().isBlank()) links.put(name, fancy.npc());
                } catch (RuntimeException unreadable) {
                    skipped++;
                }
            }
        }
        return new Result(found, skipped, links);
    }

    /**
     * FancyHolograms 3's {@code multiple_pages_trait}: one JSON file per hologram,
     * {@code {"mode":"CYCLE","cycleDelay":1000,"pages":[{"lines":[...]}]}}. The trait replaces
     * the hologram's text with the page it shows, so its first page is the lines here. It turns
     * everybody's page together; here each player's page turns on its own, every
     * {@code cycleDelay}. {@code MANUAL} never turns.
     */
    static void fancyPages(Map<String, StoredHologram> found, File directory) {
        found.replaceAll((name, hologram) -> {
            File file = new File(directory, name + ".json");
            if (!file.isFile()) return hologram;
            try {
                JsonObject trait = JsonParser.parseString(java.nio.file.Files.readString(file.toPath())).getAsJsonObject();
                List<List<String>> pages = new ArrayList<>();
                if (trait.get("pages") instanceof JsonArray array) {
                    for (JsonElement page : array) {
                        List<String> lines = new ArrayList<>();
                        if (page.getAsJsonObject().get("lines") instanceof JsonArray text) {
                            text.forEach(line -> lines.add(line.getAsString()));
                        }
                        pages.add(lines);
                    }
                }
                if (pages.isEmpty()) return hologram;
                boolean manual = "MANUAL".equalsIgnoreCase(string(trait, "mode", "CYCLE"));
                long ticks = manual ? 0 : Math.max(1, Math.round(number(trait, "cycleDelay", 1000) / 50));
                return hologram.withPageLines(pages).withPageInterval(ticks);
            } catch (Exception unreadable) {
                return hologram;
            }
        });
    }

    /** The material of an item as holograms.yml keeps it: a serialised item, or a bare name. */
    private static String fancyItem(Object item) {
        if (item instanceof ItemStack stack) return stack.getType().name();
        if (item instanceof ConfigurationSection section) return material(section.getString("id", section.getString("type", "APPLE")));
        return item instanceof String name ? material(name) : "APPLE";
    }

    /** The material of an item as the JSON files keep it: the item's bytes, in Base64. */
    private static String fancyItemBytes(String encoded) {
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded)).getType().name();
        } catch (Throwable unreadable) {
            // Only a running server can read an item's bytes.
            return "APPLE";
        }
    }

    /** holograms.yml's background: none for the client's own, transparent, #RRGGBB, #AARRGGBB or a colour name. */
    static String fancyYamlBackground(String written) {
        if (written == null || written.isBlank()) return null;
        String value = written.trim();
        if (value.equalsIgnoreCase("transparent")) return "#00000000";
        try {
            if (value.startsWith("#")) {
                String hex = value.substring(1);
                long colour = Long.parseLong(hex, 16);
                // A colour without alpha is solid there.
                return argb(hex.length() == 6 ? colour | 0xFF000000L : colour);
            }
        } catch (NumberFormatException unreadable) {
            return null;
        }
        NamedTextColor named = NamedTextColor.NAMES.value(value.toLowerCase(Locale.ROOT).replace(' ', '_'));
        return named == null ? null : argb(named.value() | 0xC8000000L);
    }

    /** A FancyHolograms glow colour, such as GOLD, as {@code #RRGGBB}; {@code null} when it does not glow. */
    private static String glow(String colour) {
        NamedTextColor named = colour.isEmpty() ? null : NamedTextColor.NAMES.value(colour.toLowerCase(Locale.ROOT));
        return named == null ? null : String.format("#%06X", named.value());
    }

    private static String argb(long colour) {
        return String.format("#%08X", colour & 0xFFFFFFFFL);
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent.get(key) instanceof JsonObject child ? child : new JsonObject();
    }

    private static String string(JsonObject parent, String key, String otherwise) {
        JsonElement value = parent.get(key);
        return value == null || value.isJsonNull() ? otherwise : value.getAsString();
    }

    private static double number(JsonObject parent, String key, double otherwise) {
        JsonElement value = parent.get(key);
        return value == null || value.isJsonNull() ? otherwise : value.getAsDouble();
    }

    private static boolean bool(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value != null && !value.isJsonNull() && value.getAsBoolean();
    }

    // ------------------------------------------------------------------
    // Locations, as each plugin writes them
    // ------------------------------------------------------------------

    /** {@code world:x:y:z}, DecentHolograms', whose numbers follow the server's locale: {@code 10,500} too. */
    static Position colonLocation(String written) {
        return split(written == null ? null : written.replace(',', '.'), ":");
    }

    /** {@code world, x, y, z}, HolographicDisplays 2's. */
    static Position commaLocation(String written) {
        return split(written, ",");
    }

    private static Position split(String written, String separator) {
        if (written == null) {
            return null;
        }
        String[] parts = written.split(Pattern.quote(separator));
        if (parts.length < 4) {
            return null;
        }
        try {
            return new Position(parts[0].trim(), Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim()), Double.parseDouble(parts[3].trim()));
        } catch (NumberFormatException unreadable) {
            return null;
        }
    }

    private static Position sectionLocation(ConfigurationSection section) {
        if (section == null || !section.contains("world")) {
            return null;
        }
        return new Position(section.getString("world"), section.getDouble("x"), section.getDouble("y"),
                section.getDouble("z"), (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }

    /**
     * A material as written there: {@code diamond}, {@code minecraft:diamond}, {@code DIAMOND 1},
     * or the legacy {@code WOOL:14}, whose number is a data value.
     */
    static String material(String written) {
        String name = written.trim().split("[\\s{\\[]", 2)[0];
        int colon = name.indexOf(':');
        if (colon >= 0) {
            String after = name.substring(colon + 1);
            name = after.matches("\\d+") ? name.substring(0, colon) : after;
        }
        return name.toUpperCase(Locale.ROOT);
    }
}
