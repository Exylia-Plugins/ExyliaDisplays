package net.exylia.exyliaDisplays.hologram.migration;

import net.exylia.exyliaDisplays.common.store.Position;
import net.exylia.exyliaDisplays.hologram.StoredHologram;
import net.exylia.lib.hologram.HologramConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
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
            case FANCYHOLOGRAMS -> fancy(new File(folder, "holograms.yml"));
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

    /** One fixed light level from FancyHolograms' block and sky light, or -1 when it set none. */
    private static int brightness(ConfigurationSection section) {
        ConfigurationSection light = section.getConfigurationSection("brightness");
        if (light == null) return -1;
        return Math.clamp(Math.max(light.getInt("block", -1), light.getInt("sky", -1)), -1, 15);
    }

    static Result fancy(File file) {
        Map<String, StoredHologram> found = new LinkedHashMap<>();
        Map<String, String> links = new LinkedHashMap<>();
        int skipped = 0;
        if (!file.isFile()) {
            return new Result(found, 0);
        }
        ConfigurationSection all = YamlConfiguration.loadConfiguration(file).getConfigurationSection("holograms");
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
            // FancyHolograms shifts the display from its location; here the location itself moves.
            at = new Position(at.world(), at.x() + section.getDouble("translation_x"),
                    at.y() + section.getDouble("translation_y"), at.z() + section.getDouble("translation_z"),
                    at.yaw(), at.pitch());
            String type = section.getString("type", "TEXT").toUpperCase(Locale.ROOT);
            List<String> lines = switch (type) {
                case "ITEM" -> List.of("<item:" + material(section.getString("item", "STONE")) + ">");
                case "BLOCK" -> List.of("<block:" + material(section.getString("block", "STONE")) + ">");
                default -> section.getStringList("text");
            };
            if (lines.isEmpty()) {
                skipped++;
                continue;
            }
            String background = section.getString("background", "");
            String colour = background.startsWith("#") ? background : "#00000000";
            HologramConfig.Properties base = new HologramConfig.Properties();
            HologramConfig.Properties look = new HologramConfig.Properties(
                    section.getString("billboard", "CENTER").toUpperCase(Locale.ROOT),
                    section.getString("text_alignment", "CENTER").toUpperCase(Locale.ROOT),
                    section.getDouble("scale_x", 1), section.getDouble("scale_y", 1), section.getDouble("scale_z", 1),
                    section.getBoolean("text_shadow", false), section.getBoolean("see_through", false),
                    // One FancyHolograms display holds every line, so its lines grow with its scale;
                    // here each line is its own display and the gap has to grow with them.
                    base.lineWidth(), base.lineSpacing() * Math.max(0.1, section.getDouble("scale_y", 1)),
                    base.textOpacity(),
                    "DEFAULT".equalsIgnoreCase(background), colour, brightness(section), false);
            double distance = section.getDouble("visibility_distance", -1);
            StoredHologram hologram = StoredHologram.at(at, lines, distance > 0 ? distance : 48).withAppearance(look);
            if ("PERMISSION_REQUIRED".equalsIgnoreCase(section.getString("visibility", ""))) {
                hologram = hologram.withPermission("fancyholograms.viewhologram." + name);
            }
            long interval = section.getLong("update_text_interval", -1);
            if (interval > 0) {
                // Milliseconds there, ticks here.
                hologram = hologram.withRefresh(new HologramConfig.Refresh(Math.max(1, interval / 50), true));
            }
            found.put(name, hologram);
            String npc = section.getString("linkedNpc", "");
            if (npc != null && !npc.isBlank()) links.put(name, npc);
        }
        return new Result(found, skipped, links);
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
        String name = written.trim().split("[\\s{]", 2)[0];
        if (name.contains(":")) {
            name = name.substring(name.indexOf(':') + 1);
        }
        return name.toUpperCase(Locale.ROOT);
    }
}
