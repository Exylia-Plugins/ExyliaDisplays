package net.exylia.exyliaDisplays.npc.migration;

import net.exylia.exyliaDisplays.common.store.Place;
import net.exylia.exyliaDisplays.npc.StoredNpc;
import net.exylia.lib.npc.NpcLook;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * NPCs brought over from Citizens and FancyNpcs, read from their save files.
 *
 * <p>Their files are read and never changed, so trying an import costs nothing. What comes across
 * is what both plugins share with this one: place and facing, skin, the name (as the label),
 * held and worn items, turning to players, and click commands. Traits of their own (waypoints,
 * holograms of theirs, non-player entity types) are left out; a non-player NPC is skipped rather
 * than imported as a person.
 *
 * <p>The two formats are read as those plugins wrote them in their recent versions, with the
 * older spellings that are known accepted too. Neither is documented as a contract, so a key this
 * reader does not know is ignored rather than guessed at.
 */
public final class NpcImport {

    /** Where to read from. */
    public enum Source {
        CITIZENS("Citizens"),
        FANCYNPCS("FancyNpcs");

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
     * @param npcs    the NPCs read, by their name there
     * @param skipped how many could not be read, or were not players
     */
    public record Result(Map<String, StoredNpc> npcs, int skipped) {
    }

    private NpcImport() {
        throw new AssertionError("No instances.");
    }

    /** The other plugin's folder, next to this one's. */
    public static File folder(Plugin plugin, Source source) {
        return new File(plugin.getDataFolder().getParentFile(), source.folder());
    }

    public static Result run(Plugin plugin, Source source) {
        File folder = folder(plugin, source);
        return switch (source) {
            case CITIZENS -> citizens(new File(folder, "saves.yml"));
            case FANCYNPCS -> fancy(new File(folder, "npcs.yml"));
        };
    }

    // ------------------------------------------------------------------
    // Citizens: saves.yml, npc.<id>.traits.*
    // ------------------------------------------------------------------

    static Result citizens(File file) {
        Map<String, StoredNpc> found = new LinkedHashMap<>();
        int skipped = 0;
        if (!file.isFile()) return new Result(found, 0);
        ConfigurationSection all = YamlConfiguration.loadConfiguration(file).getConfigurationSection("npc");
        if (all == null) return new Result(found, 0);
        for (String id : all.getKeys(false)) {
            ConfigurationSection npc = all.getConfigurationSection(id);
            ConfigurationSection traits = npc == null ? null : npc.getConfigurationSection("traits");
            Place at = traits == null ? null : place(traits.getConfigurationSection("location"));
            String type = traits == null ? "PLAYER" : traits.getString("type", "PLAYER");
            if (at == null || !"PLAYER".equalsIgnoreCase(type)) {
                skipped++;
                continue;
            }
            String name = npc.getString("name", "npc-" + id);
            StoredNpc imported = new StoredNpc().withLocation(at)
                    .withSkin(citizensSkin(traits.getConfigurationSection("skintrait"), name))
                    .withLabelLines(List.of(name));
            ConfigurationSection look = traits.getConfigurationSection("lookclose");
            if (look != null && look.getBoolean("enabled", false)) {
                imported = imported.withLook(new StoredNpc.Look(NpcLook.NEAREST, number(look.get("range"), 8)));
            } else {
                imported = imported.withLook(new StoredNpc.Look(NpcLook.FIXED, 8));
            }
            imported = equipment(imported, traits.getConfigurationSection("equipment"));
            imported = citizensCommands(imported, traits.getConfigurationSection("commandtrait"));
            found.put(plain(name).isBlank() ? "npc-" + id : plain(name), imported);
        }
        return new Result(found, skipped);
    }

    /** A written texture first, then a named account, then Citizens' own default: the NPC's name. */
    private static StoredNpc.Skin citizensSkin(ConfigurationSection skin, String name) {
        if (skin != null) {
            String texture = skin.getString("textureRaw", "");
            if (!texture.isBlank()) return StoredNpc.Skin.texture(texture, skin.getString("signature", ""));
            String account = skin.getString("skinName", "");
            if (!account.isBlank()) return StoredNpc.Skin.player(account);
            if (!skin.getBoolean("fetchDefaultSkin", true)) return new StoredNpc.Skin();
        }
        StoredNpc.Skin fallback = StoredNpc.Skin.parse(plain(name));
        return fallback == null ? new StoredNpc.Skin() : fallback;
    }

    /**
     * {@code commandtrait.commands.<n>}: {@code command}, {@code hand} (LEFT, RIGHT, BOTH...),
     * {@code player} (run as the player rather than the console) and {@code cooldown} in seconds.
     */
    private static StoredNpc citizensCommands(StoredNpc npc, ConfigurationSection trait) {
        ConfigurationSection commands = trait == null ? null : trait.getConfigurationSection("commands");
        if (commands == null) return npc;
        List<String> lines = new ArrayList<>();
        double cooldown = 0;
        for (String key : commands.getKeys(false)) {
            ConfigurationSection command = commands.getConfigurationSection(key);
            if (command == null || command.getString("command", "").isBlank()) continue;
            String filter = switch (command.getString("hand", "BOTH").toUpperCase(Locale.ROOT)) {
                case "LEFT" -> "left: ";
                case "RIGHT" -> "right: ";
                case "SHIFT_LEFT" -> "shift_left: ";
                case "SHIFT_RIGHT" -> "shift_right: ";
                default -> "";
            };
            String text = command.getString("command").trim().replaceFirst("^/", "")
                    .replace("<p>", "%player_name%");
            lines.add(filter + (command.getBoolean("player", false) ? "" : "console: ") + text);
            cooldown = Math.max(cooldown, number(command.get("cooldown"), 0));
        }
        StoredNpc.Interactions rules = npc.interactions();
        return npc.withInteractions(new StoredNpc.Interactions(lines, rules.permission(), Math.round(cooldown * 1000),
                rules.denyMessage(), rules.cooldownMessage(), rules.faceClicker()));
    }

    // ------------------------------------------------------------------
    // FancyNpcs: npcs.yml, npcs.<uuid>.*
    // ------------------------------------------------------------------

    static Result fancy(File file) {
        Map<String, StoredNpc> found = new LinkedHashMap<>();
        int skipped = 0;
        if (!file.isFile()) return new Result(found, 0);
        ConfigurationSection all = YamlConfiguration.loadConfiguration(file).getConfigurationSection("npcs");
        if (all == null) return new Result(found, 0);
        for (String id : all.getKeys(false)) {
            ConfigurationSection npc = all.getConfigurationSection(id);
            Place at = npc == null ? null : place(npc.getConfigurationSection("location"));
            if (at == null || !"PLAYER".equalsIgnoreCase(npc.getString("type", "PLAYER"))) {
                skipped++;
                continue;
            }
            String name = npc.getString("name", id);
            String display = npc.getString("displayName", name);
            StoredNpc imported = new StoredNpc().withLocation(at)
                    .withSkin(fancySkin(npc))
                    .withLabelLines(display.isBlank() || display.equalsIgnoreCase("<empty>") ? List.of() : List.of(display))
                    .withScale(number(npc.get("scale"), 1));
            double distance = number(npc.get("visibility_distance"), -1);
            if (distance > 0) imported = imported.withViewDistance(distance);
            if (npc.getBoolean("glowing", false)) {
                NamedTextColor colour = NamedTextColor.NAMES.value(npc.getString("glowingColor", "white").toLowerCase(Locale.ROOT));
                imported = imported.withGlow((colour == null ? NamedTextColor.WHITE : colour).asHexString());
            }
            double range = number(npc.get("turnToPlayerDistance"), -1);
            imported = imported.withLook(npc.getBoolean("turnToPlayer", false)
                    ? new StoredNpc.Look(NpcLook.EACH_VIEWER, range > 0 ? range : 8)
                    : new StoredNpc.Look(NpcLook.FIXED, 8));
            imported = equipment(imported, npc.getConfigurationSection("equipment"));
            imported = fancyActions(imported, npc);
            found.put(name, imported);
        }
        return new Result(found, skipped);
    }

    /** {@code skin.identifier} with {@code skin.value}/{@code skin.signature}, or the older top-level keys. */
    private static StoredNpc.Skin fancySkin(ConfigurationSection npc) {
        if (npc.getBoolean("skin.mirrorSkin", npc.getBoolean("mirrorSkin", false))) return StoredNpc.Skin.mirror();
        String texture = npc.getString("skin.value", "");
        if (!texture.isBlank()) return StoredNpc.Skin.texture(texture, npc.getString("skin.signature", ""));
        StoredNpc.Skin account = StoredNpc.Skin.parse(npc.getString("skin.identifier", ""));
        return account == null ? new StoredNpc.Skin() : account;
    }

    /**
     * {@code actions.<TRIGGER>.<n>} with {@code action} and {@code value}, plus the older
     * {@code message}, {@code playerCommand} and {@code serverCommand} keys. An action type with
     * no counterpart here is left out.
     */
    private static StoredNpc fancyActions(StoredNpc npc, ConfigurationSection section) {
        List<String> lines = new ArrayList<>();
        ConfigurationSection triggers = section.getConfigurationSection("actions");
        if (triggers != null) {
            for (String trigger : triggers.getKeys(false)) {
                String filter = switch (trigger.toUpperCase(Locale.ROOT)) {
                    case "LEFT_CLICK" -> "left: ";
                    case "RIGHT_CLICK" -> "right: ";
                    case "ANY_CLICK" -> "";
                    default -> null;
                };
                ConfigurationSection actions = triggers.getConfigurationSection(trigger);
                if (filter == null || actions == null) continue;
                for (String key : actions.getKeys(false)) {
                    String line = fancyAction(actions.getString(key + ".action", ""), actions.getString(key + ".value", ""));
                    if (line != null) lines.add(filter + line);
                }
            }
        }
        if (!section.getString("message", "").isBlank()) lines.add("[MESSAGE] " + section.getString("message"));
        if (!section.getString("playerCommand", "").isBlank()) lines.add(section.getString("playerCommand"));
        if (!section.getString("serverCommand", "").isBlank()) lines.add("console: " + section.getString("serverCommand"));
        double cooldown = number(section.get("interactionCooldown"), 0);
        StoredNpc.Interactions rules = npc.interactions();
        return npc.withInteractions(new StoredNpc.Interactions(lines, rules.permission(), Math.round(cooldown * 1000),
                rules.denyMessage(), rules.cooldownMessage(), rules.faceClicker()));
    }

    static String fancyAction(String action, String value) {
        String text = value.replace("{player}", "%player_name%").trim();
        return switch (action.toLowerCase(Locale.ROOT)) {
            case "message" -> "[MESSAGE] " + text;
            case "player_command" -> text.replaceFirst("^/", "");
            case "console_command" -> "console: " + text.replaceFirst("^/", "");
            case "send_to_server" -> "[SERVER] " + text;
            case "play_sound" -> "[SOUND] " + text;
            case "wait" -> "[DELAY] " + (text.isEmpty() ? "1" : text) + "s";
            default -> null;
        };
    }

    // ------------------------------------------------------------------
    // Shared
    // ------------------------------------------------------------------

    /**
     * Worn items, under either plugin's slot names. An item written as a plain material is read
     * as one; a serialised item is read whole, which needs a running server, and is skipped
     * without one.
     */
    private static StoredNpc equipment(StoredNpc npc, ConfigurationSection section) {
        if (section == null) return npc;
        StoredNpc equipped = npc;
        for (String key : section.getKeys(false)) {
            EquipmentSlot slot = StoredNpc.slot(key);
            String icon = icon(section, key);
            if (slot != null && icon != null) equipped = equipped.equip(slot, icon);
        }
        return equipped;
    }

    private static String icon(ConfigurationSection section, String key) {
        Object value = section.get(key);
        if (value instanceof String material) return material(material);
        ConfigurationSection item = section.getConfigurationSection(key);
        if (item != null) {
            for (String field : List.of("type", "id", "material")) {
                if (item.isString(field)) return material(item.getString(field));
            }
        }
        try {
            ItemStack stack = section.getItemStack(key);
            return stack == null || stack.getType().isAir() ? null : net.exylia.lib.item.Source.of(stack).raw();
        } catch (RuntimeException | LinkageError noServer) {
            return null;
        }
    }

    /** A place section; Citizens writes its numbers as strings, so both are read. */
    static Place place(ConfigurationSection section) {
        if (section == null || !section.contains("world")) return null;
        return new Place(section.getString("world"), number(section.get("x"), 0), number(section.get("y"), 0),
                number(section.get("z"), 0), number(section.get("yaw"), 0), number(section.get("pitch"), 0));
    }

    private static double number(Object written, double fallback) {
        if (written instanceof Number number) return number.doubleValue();
        try {
            return written == null ? fallback : Double.parseDouble(String.valueOf(written).trim());
        } catch (NumberFormatException unreadable) {
            return fallback;
        }
    }

    /** A name without its colour codes, for a key or an account. */
    static String plain(String name) {
        return name.replaceAll("(?i)[&§][0-9a-fk-or]", "").replaceAll("<[^>]+>", "").trim();
    }

    private static String material(String written) {
        String name = written.trim().split("[\\s{]", 2)[0];
        if (name.contains(":")) name = name.substring(name.indexOf(':') + 1);
        return name.toUpperCase(Locale.ROOT);
    }
}
