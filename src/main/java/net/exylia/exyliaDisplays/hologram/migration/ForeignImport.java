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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Holograms brought over from the other hologram plugins, read from their files.
 *
 * <p>Their files are read and never changed, so trying an import costs nothing: the original
 * plugin keeps working until it is removed. What only those plugins have (pages, animations of
 * their own, entity lines) is left out; text and item lines, places, view distances and the
 * common display settings come across.
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

    /** {@code {#RRGGBB}}, which here would read as a palette name. {@code &#RRGGBB} already works. */
    private static final Pattern DECENT_HEX = Pattern.compile("\\{#([0-9a-fA-F]{6})}");

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

    static Result decent(File directory) {
        Map<String, StoredHologram> found = new LinkedHashMap<>();
        int skipped = 0;
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return new Result(found, 0);
        }
        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            Position at = colonLocation(yaml.getString("location"));
            List<Map<?, ?>> pages = yaml.getMapList("pages");
            if (at == null || pages.isEmpty()) {
                skipped++;
                continue;
            }
            List<String> lines = new ArrayList<>();
            Object firstLines = pages.get(0).get("lines");
            if (firstLines instanceof List<?> list) {
                for (Object entry : list) {
                    if (entry instanceof Map<?, ?> line && line.get("content") != null) {
                        lines.add(decentLine(String.valueOf(line.get("content"))));
                    }
                }
            }
            // DecentHolograms hangs its lines down from the location; here they stand on it.
            double drop = lines.size() * 0.25;
            // DecentHolograms writes which way a hologram faces as 'facing', a yaw.
            at = at.withFacing((float) yaml.getDouble("facing", 0), 0f);
            StoredHologram hologram = StoredHologram.at(at.plus(0, -drop, 0), lines,
                    yaml.getDouble("display-range", 48))
                    .withEnabled(yaml.getBoolean("enabled", true))
                    .withPermission(yaml.getString("permission", ""))
                    .withRefresh(new HologramConfig.Refresh(Math.max(1, yaml.getLong("update-interval", 20)), true));
            found.put(file.getName().substring(0, file.getName().length() - 4), hologram);
        }
        return new Result(found, skipped);
    }

    /** {@code #ICON: DIAMOND} and friends into tags, and DecentHolograms' hex colours into MiniMessage. */
    static String decentLine(String content) {
        String trimmed = content.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (upper.startsWith("#ICON:")) {
            return "<item:" + material(trimmed.substring(6)) + ">";
        }
        if (upper.startsWith("#SMALLHEAD:") || upper.startsWith("#HEAD:")) {
            return "<item:PLAYER_HEAD>";
        }
        if (upper.startsWith("#ENTITY:")) {
            return "";
        }
        Matcher hex = DECENT_HEX.matcher(content);
        return hex.replaceAll("<#$1>");
    }

    // ------------------------------------------------------------------
    // HolographicDisplays: database.yml, one section per hologram
    // ------------------------------------------------------------------

    static Result holographic(File file) {
        Map<String, StoredHologram> found = new LinkedHashMap<>();
        int skipped = 0;
        if (!file.isFile()) {
            return new Result(found, 0);
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
            List<String> lines = section.getStringList("lines").stream().map(ForeignImport::holographicLine).toList();
            if (at == null || lines.isEmpty()) {
                skipped++;
                continue;
            }
            // HolographicDisplays hangs its lines down from the position; here they stand on it.
            found.put(name, StoredHologram.at(at.plus(0, -lines.size() * 0.25, 0), lines, 48));
        }
        return new Result(found, skipped);
    }

    static String holographicLine(String line) {
        String trimmed = line.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (upper.startsWith("ICON:")) {
            return "<item:" + material(trimmed.substring(5)) + ">";
        }
        return "{empty}".equalsIgnoreCase(trimmed) ? " " : line;
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
        Map<String, String> links = new LinkedHashMap<>(yaml.links());
        links.putAll(json.links());
        return new Result(found, yaml.skipped() + json.skipped(), links);
    }

    /**
     * One FancyHolograms hologram, whichever file it came from.
     *
     * @param background {@code #AARRGGBB}, or {@code null} for the client's own background
     * @param glow       the outline of an item or block, {@code #RRGGBB}, or {@code null}
     * @param brightness a fixed light level, or -1 for the light where it stands
     */
    private record Fancy(Position at, String type, List<String> text, String material, String glow,
                         String billboard, String alignment, double scaleX, double scaleY, double scaleZ,
                         boolean shadow, boolean seeThrough, String background, int opacity, int brightness,
                         double distance, boolean permission, long intervalMillis, String npc) {

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
                    shadow, seeThrough, FANCY_LINE_WIDTH,
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
                    section.getLong("update_text_interval", -1), section.getString("linkedNpc", ""));
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
                            (long) number(text, "text_update_interval", -1), string(hologram, "linked_npc_name", ""));
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

    /** {@code world:x:y:z}, DecentHolograms'. */
    static Position colonLocation(String written) {
        return split(written, ":");
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

    /** A material as written there: {@code diamond}, {@code minecraft:diamond}, {@code DIAMOND 1}. */
    private static String material(String written) {
        String name = written.trim().split("[\\s{\\[]", 2)[0];
        if (name.contains(":")) {
            name = name.substring(name.indexOf(':') + 1);
        }
        return name.toUpperCase(Locale.ROOT);
    }
}
